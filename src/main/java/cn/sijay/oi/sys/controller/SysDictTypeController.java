package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysDictType;
import cn.sijay.oi.sys.model.dict.SysDictTypeCreateCmd;
import cn.sijay.oi.sys.model.dict.SysDictTypeQuery;
import cn.sijay.oi.sys.model.dict.SysDictTypeUpdateCmd;
import cn.sijay.oi.sys.model.dict.SysDictTypeVO;
import cn.sijay.oi.sys.service.SysDictService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典类型控制器，提供字典类型模块的标准 CRUD 接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /dict/type        创建字典类型（Result&lt;Void&gt;）
 * DELETE /dict/type/{id}   软删除字典类型（级联软删其全部字典项）（Result&lt;Void&gt;）
 * PUT    /dict/type/{id}   更新字典类型（Result&lt;Void&gt;）
 * GET    /dict/type/{id}   查询字典类型详情（Result&lt;SysDictTypeVO&gt;）
 * GET    /dict/type/page   分页条件查询（Result&lt;List&lt;SysDictTypeVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/dict/type")
public class SysDictTypeController extends BaseController {

    private final SysDictService dictService;

    /**
     * 创建字典类型。
     *
     * @param cmd 创建入参；dictType/name/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysDictTypeCreateCmd cmd) {
        dictService.createType(cmd);
        return ok();
    }

    /**
     * 删除字典类型（软删除，级联软删其全部字典项）。
     *
     * @param id 字典类型 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "字典类型 id 必须为正整数") Long id) {
        dictService.deleteType(id);
        return ok();
    }

    /**
     * 更新字典类型（dictType 不可修改）。
     *
     * @param id  字典类型 id；必须为正整数
     * @param cmd 更新入参；name/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "字典类型 id 必须为正整数") Long id,
                               @RequestBody @Valid SysDictTypeUpdateCmd cmd) {
        dictService.updateType(id, cmd);
        return ok();
    }

    /**
     * 查询字典类型详情。
     *
     * @param id 字典类型 id；必须为正整数
     * @return 字典类型详情；不存在时返回 605002
     */
    @GetMapping("/{id}")
    public Result<SysDictTypeVO> get(@PathVariable @Positive(message = "字典类型 id 必须为正整数") Long id) {
        return ok(SysDictTypeVO.from(dictService.getTypeById(id)));
    }

    /**
     * 分页条件查询字典类型列表。
     *
     * <p>过滤条件全部选填：dictType/name 模糊匹配（CONTAINING、忽略大小写），
     * status 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysDictTypeVO>> page(@Valid SysDictTypeQuery query, @Valid PageQuery page) {
        Page<SysDictType> result = dictService.pageType(query, page);
        List<SysDictTypeVO> records = result.getContent().stream().map(SysDictTypeVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
