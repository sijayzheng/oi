package cn.sijay.oi.sys.controller;

import cn.sijay.oi.auth.service.StpInterfaceImpl;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysRole;
import cn.sijay.oi.sys.model.role.SysRoleCreateCmd;
import cn.sijay.oi.sys.model.role.SysRoleQuery;
import cn.sijay.oi.sys.model.role.SysRoleUpdateCmd;
import cn.sijay.oi.sys.model.role.SysRoleVO;
import cn.sijay.oi.sys.service.SysRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器，提供角色模块的标准 CRUD 接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>鉴权约定：系统管理模块，类级 {@code @SaCheckRole("admin")} 保护——仅超管可访问，
 * 普通用户返回 403（R-01 修复）。
 *
 * <p>接口清单：
 * <pre>
 * POST   /role        创建角色（Result&lt;Void&gt;）
 * DELETE /role/{id}   软删除角色（Result&lt;Void&gt;）
 * PUT    /role/{id}   更新角色档案（Result&lt;Void&gt;）
 * GET    /role/{id}   查询角色详情（Result&lt;SysRoleVO&gt;）
 * GET    /role/page   分页条件查询（Result&lt;List&lt;SysRoleVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/role")
@SaCheckRole(StpInterfaceImpl.ROLE_ADMIN)
public class SysRoleController extends BaseController {

    private final SysRoleService roleService;

    /**
     * 创建角色。
     *
     * @param cmd 创建入参；code/name/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysRoleCreateCmd cmd) {
        roleService.create(cmd);
        return ok();
    }

    /**
     * 删除角色（软删除，deleted 置为本行主键 id）。
     *
     * @param id 角色 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "角色 id 必须为正整数") Long id) {
        roleService.delete(id);
        return ok();
    }

    /**
     * 更新角色档案（code 不可修改）。
     *
     * @param id  角色 id；必须为正整数
     * @param cmd 更新入参；name/sort/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "角色 id 必须为正整数") Long id,
                               @RequestBody @Valid SysRoleUpdateCmd cmd) {
        roleService.update(id, cmd);
        return ok();
    }

    /**
     * 查询角色详情。
     *
     * @param id 角色 id；必须为正整数
     * @return 角色详情；角色不存在时返回 602001
     */
    @GetMapping("/{id}")
    public Result<SysRoleVO> get(@PathVariable @Positive(message = "角色 id 必须为正整数") Long id) {
        return ok(SysRoleVO.from(roleService.getById(id)));
    }

    /**
     * 分页条件查询角色列表。
     *
     * <p>过滤条件全部选填：code/name 模糊匹配（CONTAINING、忽略大小写），
     * status 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysRoleVO>> page(@Valid SysRoleQuery query, @Valid PageQuery page) {
        Page<SysRole> result = roleService.page(query, page);
        List<SysRoleVO> records = result.getContent().stream().map(SysRoleVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
