package cn.sijay.oi.${moduleName}.controller;

import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.${moduleName}.entity.${entityName};
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}CreateCmd;
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}Query;
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}UpdateCmd;
import cn.sijay.oi.${moduleName}.model.${businessName}.Sys${className}VO;
import cn.sijay.oi.${moduleName}.service.Sys${className}Service;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ${functionName}管理控制器，提供${functionName}模块的标准 CRUD 接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /${businessName}        创建${functionName}（Result&lt;Void&gt;）
 * DELETE /${businessName}/{id}   软删除${functionName}（Result&lt;Void&gt;）
 * PUT    /${businessName}/{id}   更新${functionName}档案（Result&lt;Void&gt;）
 * GET    /${businessName}/{id}   查询${functionName}详情（Result&lt;Sys${className}VO&gt;）
 * GET    /${businessName}/page   分页条件查询（Result&lt;List&lt;Sys${className}VO&gt;&gt;，含 total）
 * </pre>
 *
 * @author ${author}
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/${businessName}")
public class Sys${className}Controller extends BaseController {

    private final Sys${className}Service ${businessName}Service;

    /**
     * 创建${functionName}。
     *
     * @param cmd 创建入参；格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid Sys${className}CreateCmd cmd) {
        ${businessName}Service.create(cmd);
        return ok();
    }

    /**
     * 删除${functionName}（软删除，deleted 置为本行主键 id）。
     *
     * @param id ${functionName} id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "${functionName} id 必须为正整数") Long id) {
        ${businessName}Service.delete(id);
        return ok();
    }

    /**
     * 更新${functionName}档案。
     *
     * @param id  ${functionName} id；必须为正整数
     * @param cmd 更新入参；格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "${functionName} id 必须为正整数") Long id,
                               @RequestBody @Valid Sys${className}UpdateCmd cmd) {
        ${businessName}Service.update(id, cmd);
        return ok();
    }

    /**
     * 查询${functionName}详情。
     *
     * @param id ${functionName} id；必须为正整数
     * @return ${functionName}详情；不存在时返回 600002
     */
    @GetMapping("/{id}")
    public Result<Sys${className}VO> get(@PathVariable @Positive(message = "${functionName} id 必须为正整数") Long id) {
        return ok(Sys${className}VO.from(${businessName}Service.getById(id)));
    }

    /**
     * 分页条件查询${functionName}列表。
     *
     * <p>过滤条件全部选填；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<Sys${className}VO>> page(@Valid Sys${className}Query query, @Valid PageQuery page) {
        Page<${entityName}> result = ${businessName}Service.page(query, page);
        List<Sys${className}VO> records = result.getContent().stream().map(Sys${className}VO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
