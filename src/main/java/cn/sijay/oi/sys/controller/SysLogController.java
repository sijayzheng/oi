package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.model.SysLogQuery;
import cn.sijay.oi.sys.model.SysLogVO;
import cn.sijay.oi.sys.service.SysLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志控制器，提供日志分页检索与 CSV 导出接口（仅系统管理员可用）。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}；入参格式校验由 Bean Validation 触发，业务异常由全局异常处理器统一包装。
 *
 * <p>鉴权约定：本模块全部接口仅允许超管（用户 ID=1）访问（见 {@code SysLogService.checkAdmin}），
 * 其余登录账号统一返回 403；菜单侧通过动态路由权限（viewer 角色未授权）实现入口隔离。
 *
 * <p>接口清单：
 * <pre>
 * GET /log/page    分页检索操作日志（级别/时间范围/关键词筛选，Result&lt;List&lt;SysLogVO&gt;&gt;）
 * GET /log/export  按当前筛选条件导出日志 CSV 文本（Result&lt;String&gt;，上限 10000 条）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/log")
public class SysLogController extends BaseController {

    private final SysLogService logService;

    /**
     * 分页检索操作日志。
     *
     * <p>筛选条件：日志级别（DEBUG/INFO/WARN/ERROR）、时间范围、关键词
     * （匹配日志内容/动作/模块/操作人/请求路径/请求 ID）；固定按记录时间倒序。
     *
     * @param query 业务过滤条件
     * @param page  分页参数（pageNum/pageSize 自定义每页条数）
     * @return 当前页日志集合与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysLogVO>> page(@Valid SysLogQuery query, @Valid PageQuery page) {
        logService.checkAdmin();
        var result = logService.page(query, page);
        return ok(result.getContent(), result.getTotalElements());
    }

    /**
     * 导出操作日志 CSV 文本。
     *
     * <p>按当前筛选条件全量导出（上限 10000 条），返回 CSV 字符串（首行表头、
     * 内容按 CSV 规范转义）；前端包装为 Blob 触发下载（追加 UTF-8 BOM 防 Excel 乱码）。
     *
     * @param query 业务过滤条件（与列表一致的筛选）
     * @return CSV 文本
     */
    @GetMapping("/export")
    public Result<String> export(@Valid SysLogQuery query) {
        logService.checkAdmin();
        return ok(logService.exportCsv(query));
    }
}
