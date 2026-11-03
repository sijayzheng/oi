package cn.sijay.oi.gen.controller;

import cn.sijay.oi.auth.service.StpInterfaceImpl;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.gen.entity.GenTable;
import cn.sijay.oi.gen.entity.GenTableColumn;
import cn.sijay.oi.gen.model.GenTableSaveCmd;
import cn.sijay.oi.gen.service.GenRenderService;
import cn.sijay.oi.gen.service.GenTableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成管理控制器，提供元数据表配置的导入/维护/预览/生成接口与可导入表查询接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * GET    /gen/db/tables        查询可导入的业务表清单（排除已导入与 gen 元数据表）
 * POST   /gen/table/import/{tableName}
 *                              导入表结构并自动推导字段默认配置（Result&lt;Void&gt;）
 * GET    /gen/table/page       分页查询表配置（Result&lt;List&lt;GenTable&gt;&gt;，含 total）
 * GET    /gen/table/{id}       查询表配置详情（含字段配置）
 * PUT    /gen/table/{id}       保存表/字段配置（全量覆盖，Result&lt;Void&gt;）
 * DELETE /gen/table/{id}       删除表配置及其字段配置（Result&lt;Void&gt;）
 * GET    /gen/table/preview    预览生成产物（相对路径 → 文件内容）
 * POST   /gen/table/generate   生成并落盘 + 前端路由/菜单锚点注册（Result&lt;List&lt;String&gt;&gt;）
 * </pre>
 *
 * <p>说明：gen 为内部工具模块，配置详情直接返回元数据实体（不设 VO 转换层），
 * 生成产物路径相对项目根目录。
 *
 * <p>鉴权约定：系统管理模块，类级 {@code @SaCheckRole("admin")} 保护——仅超管可访问，
 * 普通用户返回 403（R-01 修复）。
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/gen")
@SaCheckRole(StpInterfaceImpl.ROLE_ADMIN)
public class GenTableController extends BaseController {

    private final GenTableService tableService;
    private final GenRenderService renderService;

    /**
     * 查询可导入的业务表清单。
     *
     * @return 表名 + 表注释列表（已排除 gen 元数据表与已导入表）
     */
    @GetMapping("/db/tables")
    public Result<List<Map<String, String>>> listImportableTables() {
        return ok(tableService.listImportableTables());
    }

    /**
     * 导入业务表结构（自动推导字段默认配置，导入后可在编辑页微调）。
     *
     * @param tableName 业务表名；须存在于当前数据库且未被导入过
     * @return 成功响应（无数据）；表不存在返回 600002，已导入返回 600003
     */
    @PostMapping("/table/import/{tableName}")
    public Result<Void> importTable(@PathVariable @NotBlank(message = "业务表名不能为空") String tableName) {
        tableService.importTable(tableName);
        return ok();
    }

    /**
     * 分页查询表配置列表。
     *
     * @param page 分页参数（pageNum/pageSize，全部选填）
     * @return 当前页表配置与总记录数
     */
    @GetMapping("/table/page")
    public Result<List<GenTable>> page(@Valid PageQuery page) {
        page.validate();
        Page<GenTable> result = tableService.page(page);
        return ok(result.getContent(), result.getTotalElements());
    }

    /**
     * 查询表配置详情（含字段配置）。
     *
     * @param id 表配置 id
     * @return table + columns 两段式结构
     */
    @GetMapping("/table/{id}")
    public Result<Map<String, Object>> get(@PathVariable @Positive(message = "表配置 id 必须为正整数") Long id) {
        GenTable table = tableService.getTableById(id);
        List<GenTableColumn> columns = tableService.getColumnsByTableId(id);
        Map<String, Object> body = new HashMap<>();
        body.put("table", table);
        body.put("columns", columns);
        return ok(body);
    }

    /**
     * 保存表/字段配置（编辑页全量提交；tableName 不可修改）。
     *
     * @param id  表配置 id
     * @param cmd 保存入参；格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/table/{id}")
    public Result<Void> save(@PathVariable @Positive(message = "表配置 id 必须为正整数") Long id,
                             @RequestBody @Valid GenTableSaveCmd cmd) {
        tableService.save(id, cmd);
        return ok();
    }

    /**
     * 删除表配置及其全部字段配置。
     *
     * @param id 表配置 id
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/table/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "表配置 id 必须为正整数") Long id) {
        tableService.delete(id);
        return ok();
    }

    /**
     * 预览生成产物（渲染全部文件但不落盘、不执行锚点注册）。
     *
     * @param id 表配置 id
     * @return 相对项目根路径 → 文件内容
     */
    @GetMapping("/table/preview/{id}")
    public Result<Map<String, String>> preview(@PathVariable @Positive(message = "表配置 id 必须为正整数") Long id) {
        return ok(renderService.preview(id));
    }

    /**
     * 生成并落盘：写入前后端源码文件，并自动注册前端路由与菜单（幂等）。
     *
     * @param id 表配置 id
     * @return 已写入文件的相对路径清单
     */
    @PostMapping("/table/generate/{id}")
    public Result<List<String>> generate(@PathVariable @Positive(message = "表配置 id 必须为正整数") Long id) {
        return ok(renderService.generate(id));
    }
}
