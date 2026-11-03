package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.SelectOption;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysDictItem;
import cn.sijay.oi.sys.model.dict.SysDictItemCreateCmd;
import cn.sijay.oi.sys.model.dict.SysDictItemQuery;
import cn.sijay.oi.sys.model.dict.SysDictItemUpdateCmd;
import cn.sijay.oi.sys.model.dict.SysDictItemVO;
import cn.sijay.oi.sys.service.SysDictService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典项控制器，提供字典项模块的标准 CRUD 接口与下拉数据源接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /dict/item                  创建字典项（Result&lt;Void&gt;）
 * DELETE /dict/item/{id}             软删除字典项（Result&lt;Void&gt;）
 * PUT    /dict/item/{id}             更新字典项（Result&lt;Void&gt;）
 * GET    /dict/item/{id}             查询字典项详情（Result&lt;SysDictItemVO&gt;）
 * GET    /dict/item/options/{dictType}  按字典编码取下拉选项（sort 升序）
 * GET    /dict/item/page             分页条件查询（Result&lt;List&lt;SysDictItemVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/dict/item")
public class SysDictItemController extends BaseController {

    private final SysDictService dictService;

    /**
     * 创建字典项。
     *
     * @param cmd 创建入参；dictType/itemCode/itemLabel/status 必填，
     *            所属类型不存在返回 605002，同字典内编码重复返回 605003
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysDictItemCreateCmd cmd) {
        dictService.createItem(cmd);
        return ok();
    }

    /**
     * 删除字典项（软删除，deleted 置为本行主键 id）。
     *
     * @param id 字典项 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "字典项 id 必须为正整数") Long id) {
        dictService.deleteItem(id);
        return ok();
    }

    /**
     * 更新字典项（dictType/itemCode 不可修改）。
     *
     * @param id  字典项 id；必须为正整数
     * @param cmd 更新入参；itemLabel/sort/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "字典项 id 必须为正整数") Long id,
                               @RequestBody @Valid SysDictItemUpdateCmd cmd) {
        dictService.updateItem(id, cmd);
        return ok();
    }

    /**
     * 查询字典项详情。
     *
     * @param id 字典项 id；必须为正整数
     * @return 字典项详情；不存在时返回 605004
     */
    @GetMapping("/{id}")
    public Result<SysDictItemVO> get(@PathVariable @Positive(message = "字典项 id 必须为正整数") Long id) {
        return ok(SysDictItemVO.from(dictService.getItemById(id)));
    }

    /**
     * 按字典编码取下拉选项（下拉框数据源）。
     *
     * <p>映射规则：value=itemCode（业务实际存储值）、label=itemLabel（显示文案）；
     * 按 sort 升序、同序号按 id 升序排列；字典类型不存在时返回 605002。
     *
     * @param dictType 字典编码；非空
     * @return 下拉选项列表
     */
    @GetMapping("/options/{dictType}")
    public Result<List<SelectOption<String>>> options(
            @PathVariable @NotBlank(message = "字典编码不能为空") String dictType) {
        return ok(dictService.listItemOptions(dictType));
    }

    /**
     * 分页条件查询字典项列表。
     *
     * <p>过滤条件全部选填：dictType 精确匹配、itemLabel 模糊匹配（CONTAINING、忽略大小写），
     * status 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysDictItemVO>> page(@Valid SysDictItemQuery query, @Valid PageQuery page) {
        Page<SysDictItem> result = dictService.pageItem(query, page);
        List<SysDictItemVO> records = result.getContent().stream().map(SysDictItemVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
