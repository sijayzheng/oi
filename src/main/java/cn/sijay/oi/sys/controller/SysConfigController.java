package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysConfig;
import cn.sijay.oi.sys.model.config.SysConfigCreateCmd;
import cn.sijay.oi.sys.model.config.SysConfigQuery;
import cn.sijay.oi.sys.model.config.SysConfigUpdateCmd;
import cn.sijay.oi.sys.model.config.SysConfigVO;
import cn.sijay.oi.sys.service.SysConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 参数配置控制器，提供配置模块的标准 CRUD 接口与运行时取值接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /config                  创建配置（Result&lt;Void&gt;）
 * DELETE /config/{id}             软删除配置（Result&lt;Void&gt;）
 * PUT    /config/{id}             更新配置（Result&lt;Void&gt;）
 * GET    /config/{id}             查询配置详情（Result&lt;SysConfigVO&gt;）
 * GET    /config/key/{configKey}  按参数键读取配置（运行时取值入口）
 * GET    /config/page             分页条件查询（Result&lt;List&lt;SysConfigVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/config")
public class SysConfigController extends BaseController {

    private final SysConfigService configService;

    /**
     * 创建配置。
     *
     * @param cmd 创建入参；configKey/configValue/valueType/name 必填，
     *            格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysConfigCreateCmd cmd) {
        configService.create(cmd);
        return ok();
    }

    /**
     * 删除配置（软删除，deleted 置为本行主键 id）。
     *
     * @param id 配置 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "配置 id 必须为正整数") Long id) {
        configService.delete(id);
        return ok();
    }

    /**
     * 更新配置（configKey 不可修改）。
     *
     * @param id  配置 id；必须为正整数
     * @param cmd 更新入参；configValue/valueType/name 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "配置 id 必须为正整数") Long id,
                               @RequestBody @Valid SysConfigUpdateCmd cmd) {
        configService.update(id, cmd);
        return ok();
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 id；必须为正整数
     * @return 配置详情；配置不存在时返回 604002
     */
    @GetMapping("/{id}")
    public Result<SysConfigVO> get(@PathVariable @Positive(message = "配置 id 必须为正整数") Long id) {
        return ok(SysConfigVO.from(configService.getById(id)));
    }

    /**
     * 按参数键读取配置（运行时取值入口）。
     *
     * @param configKey 参数键；点分命名格式
     * @return 配置详情；参数不存在时返回 604002
     */
    @GetMapping("/key/{configKey}")
    public Result<SysConfigVO> getByKey(
            @PathVariable @NotBlank(message = "参数键不能为空") String configKey) {
        return ok(SysConfigVO.from(configService.getByKey(configKey)));
    }

    /**
     * 分页条件查询配置列表。
     *
     * <p>过滤条件全部选填：configKey/name 模糊匹配（CONTAINING、忽略大小写），
     * valueType 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysConfigVO>> page(@Valid SysConfigQuery query, @Valid PageQuery page) {
        Page<SysConfig> result = configService.page(query, page);
        List<SysConfigVO> records = result.getContent().stream().map(SysConfigVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
