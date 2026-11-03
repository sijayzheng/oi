package cn.sijay.oi.file.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sijay.oi.auth.service.StpInterfaceImpl;
import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.file.entity.SysFile;
import cn.sijay.oi.file.model.SysFileQuery;
import cn.sijay.oi.file.model.SysFileVO;
import cn.sijay.oi.file.service.FileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理控制器，提供统一文件上传、查询、临时访问地址签发、公开访问与删除接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}；入参格式校验由 Bean Validation 触发，校验失败与业务异常
 * 均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>鉴权约定：{@code /file/access/{id}} 为带签名的公开端点（见
 * {@code SaTokenConfigure} 豁免清单），凭有效期内的合法签名即可访问；
 * 其余 /file/** 接口均需登录态。
 *
 * <p>接口清单：
 * <pre>
 * POST   /file/upload        上传文件（multipart，业务标识可选），返回文件 id 与临时访问地址
 * GET    /file/{id}          文件元数据（含临时访问地址）
 * GET    /file/page          分页条件查询文件列表（管理端检索）
 * GET    /file/{id}/url      签发新的临时访问地址（旧地址过期后调用）
 * GET    /file/access/{id}   公开访问端点：凭有效签名输出文件流（豁免登录）
 * DELETE /file/{id}          删除文件（软删记录 + 清理物理文件）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
public class FileController extends BaseController {

    private final FileService fileService;

    /**
     * 上传文件。
     *
     * <p>统一文件入口：扩展名白名单、大小限制、唯一标识与落盘均由 FileService 处理；
     * 调用方只接收返回的文件 id（持久化引用）与临时访问地址（即时预览用），
     * 禁止保存存储路径。
     *
     * @param file        上传的文件（multipart 字段名 file）
     * @param businessKey 业务标识（可选）；标记文件归属场景，如 avatar
     * @return 文件信息（id、原始文件名、大小、临时访问地址等）
     */
    @PostMapping("/upload")
    public Result<SysFileVO> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "businessKey", required = false) String businessKey) {
        return ok(fileService.upload(file, businessKey));
    }

    /**
     * 查询文件元数据。
     *
     * @param id 文件 id
     * @return 文件信息（含新签发的临时访问地址）
     */
    @GetMapping("/{id}")
    public Result<SysFileVO> get(@PathVariable @Positive(message = "文件 id 必须为正整数") Long id) {
        return ok(fileService.getById(id));
    }

    /**
     * 分页条件查询文件列表（管理端检索）。
     *
     * <p>鉴权：文件列表含全部用户的文件元数据，仅超管可访问（R-01 修复）；
     * 上传/详情/取地址等业务接口保持登录态即可（头像上传等普通用户场景）。
     *
     * @param query 业务过滤条件（originalName 模糊 / businessKey、extension 精确）
     * @param page  分页与排序参数
     * @return 当前页文件集合与总记录数
     */
    @SaCheckRole(StpInterfaceImpl.ROLE_ADMIN)
    @GetMapping("/page")
    public Result<List<SysFileVO>> page(@Valid SysFileQuery query, @Valid PageQuery page) {
        var result = fileService.page(query, page);
        return ok(result.getContent(), result.getTotalElements());
    }

    /**
     * 签发新的临时访问地址。
     *
     * <p>使用场景：此前获取的访问地址已过有效期，凭文件 id 重新换取
     * 带新有效期与签名的地址；前端展示类场景在渲染前调用。
     *
     * @param id 文件 id
     * @return 临时访问地址（相对路径，如 /file/access/1?expires=...&sign=...）
     */
    @GetMapping("/{id}/url")
    public Result<String> issueUrl(@PathVariable @Positive(message = "文件 id 必须为正整数") Long id) {
        return ok(fileService.issueAccessUrl(id));
    }

    /**
     * 公开访问文件流（豁免登录，凭有效签名访问）。
     *
     * <p>响应约定：Content-Type 回显上传时的 MIME 类型（缺省 application/octet-stream）；
     * 图片类 inline 内联展示，其余 attachment 下载；文件名按 RFC 5987 编码支持中文。
     * 签名无效/过期返回 606005，文件不存在返回 606004。
     *
     * @param id      文件 id
     * @param expires 过期时间（epoch 秒）
     * @param sign    访问签名
     * @return 文件流
     */
    @GetMapping("/access/{id}")
    public ResponseEntity<FileSystemResource> access(@PathVariable Long id,
                                                     @RequestParam("expires") long expires,
                                                     @RequestParam("sign") String sign) {
        SysFile entity = fileService.validateAccess(id, expires, sign);
        FileSystemResource resource = new FileSystemResource(fileService.resolvePhysicalPath(entity));
        String contentType = entity.getContentType() == null || entity.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : entity.getContentType();
        boolean inline = contentType.startsWith("image/");
        String encodedName = URLEncoder.encode(entity.getOriginalName(), StandardCharsets.UTF_8)
                                      .replace("+", "%20");
        return ResponseEntity.ok()
                             .header(HttpHeaders.CONTENT_TYPE, contentType)
                             .header(HttpHeaders.CONTENT_DISPOSITION,
                                     (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedName)
                             .body(resource);
    }

    /**
     * 删除文件（软删记录 + 清理物理文件）。
     *
     * <p>调用规范：删除前调用方须自行确认无业务数据引用该文件 id
     * （删除后引用方将无法再获取访问地址）。
     *
     * @param id 文件 id
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "文件 id 必须为正整数") Long id) {
        fileService.delete(id);
        return ok();
    }
}
