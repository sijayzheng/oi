package cn.sijay.oi.sys.controller;

import cn.sijay.oi.auth.service.StpInterfaceImpl;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.TreeNode;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysMenu;
import cn.sijay.oi.sys.model.menu.SysMenuCreateCmd;
import cn.sijay.oi.sys.model.menu.SysMenuQuery;
import cn.sijay.oi.sys.model.menu.SysMenuUpdateCmd;
import cn.sijay.oi.sys.model.menu.SysMenuVO;
import cn.sijay.oi.sys.service.SysMenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理控制器，提供菜单模块的标准 CRUD 与树形查询接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>接口清单：
 * <pre>
 * POST   /menu        创建菜单（Result&lt;Void&gt;）
 * DELETE /menu/{id}   软删除菜单（Result&lt;Void&gt;）
 * PUT    /menu/{id}   更新菜单档案（Result&lt;Void&gt;）
 * GET    /menu/{id}   查询菜单详情（Result&lt;SysMenuVO&gt;）
 * GET    /menu/page   分页条件查询（Result&lt;List&lt;SysMenuVO&gt;&gt;，含 total）
 * GET    /menu/tree   完整菜单树（Result&lt;List&lt;TreeNode&lt;Long&gt;&gt;&gt;）
 * </pre>
 *
 * <p>鉴权约定：系统管理模块，类级 {@code @SaCheckRole("admin")} 保护——仅超管可访问，
 * 普通用户返回 403（R-01 修复）。
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/menu")
@SaCheckRole(StpInterfaceImpl.ROLE_ADMIN)
public class SysMenuController extends BaseController {

    private final SysMenuService menuService;

    /**
     * 创建菜单。
     *
     * @param cmd 创建入参；name/type/sort/visible/status 必填，parentId 选填（0=根节点）；
     *            格式校验失败返回 400 与字段错误信息，层级非法返回 603003
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysMenuCreateCmd cmd) {
        menuService.create(cmd);
        return ok();
    }

    /**
     * 删除菜单（软删除，deleted 置为本行主键 id）。
     *
     * @param id 菜单 id；必须为正整数
     * @return 成功响应（无数据）；存在子菜单时返回 603002
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "菜单 id 必须为正整数") Long id) {
        menuService.delete(id);
        return ok();
    }

    /**
     * 更新菜单档案（parentId/type 不可修改）。
     *
     * @param id  菜单 id；必须为正整数
     * @param cmd 更新入参；name/sort/visible/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "菜单 id 必须为正整数") Long id,
                               @RequestBody @Valid SysMenuUpdateCmd cmd) {
        menuService.update(id, cmd);
        return ok();
    }

    /**
     * 查询菜单详情。
     *
     * @param id 菜单 id；必须为正整数
     * @return 菜单详情；菜单不存在时返回 603001
     */
    @GetMapping("/{id}")
    public Result<SysMenuVO> get(@PathVariable @Positive(message = "菜单 id 必须为正整数") Long id) {
        return ok(SysMenuVO.from(menuService.getById(id)));
    }

    /**
     * 分页条件查询菜单列表（平铺结构）。
     *
     * <p>过滤条件全部选填：name 模糊匹配（CONTAINING、忽略大小写），
     * type/status 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysMenuVO>> page(@Valid SysMenuQuery query, @Valid PageQuery page) {
        Page<SysMenu> result = menuService.page(query, page);
        List<SysMenuVO> records = result.getContent().stream().map(SysMenuVO::from).toList();
        return ok(records, result.getTotalElements());
    }

    /**
     * 获取完整菜单树。
     *
     * <p>返回通用树节点结构（id/label/parentId/disabled/children），
     * parentId=0 的节点为根节点；无菜单时返回空数组。
     *
     * @return 根节点列表
     */
    @GetMapping("/tree")
    public Result<List<TreeNode<Long>>> tree() {
        return ok(menuService.tree());
    }
}
