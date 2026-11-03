package cn.sijay.oi.sys.controller;

import cn.sijay.oi.auth.service.StpInterfaceImpl;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.sijay.oi.common.Result;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.model.user.SysUserCreateCmd;
import cn.sijay.oi.sys.model.user.SysUserQuery;
import cn.sijay.oi.sys.model.user.SysUserUpdateCmd;
import cn.sijay.oi.sys.model.user.SysUserVO;
import cn.sijay.oi.sys.service.SysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器，提供用户模块的标准 CRUD 接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}（禁止手动构造）；入参格式校验由 Bean Validation（@Valid）触发，
 * 校验失败与业务异常均由全局异常处理器统一包装，控制器内不写 try-catch。
 *
 * <p>鉴权约定：系统管理模块，类级 {@code @SaCheckRole("admin")} 保护——仅超管
 * （角色数据源见 {@code StpInterfaceImpl}）可访问，普通用户返回 403（R-01 修复）。
 *
 * <p>接口清单：
 * <pre>
 * POST   /user        创建用户（Result&lt;Void&gt;）
 * DELETE /user/{id}   软删除用户（Result&lt;Void&gt;）
 * PUT    /user/{id}   更新用户档案（Result&lt;Void&gt;）
 * GET    /user/{id}   查询用户详情（Result&lt;SysUserVO&gt;）
 * GET    /user/page   分页条件查询（Result&lt;List&lt;SysUserVO&gt;&gt;，含 total）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
@SaCheckRole(StpInterfaceImpl.ROLE_ADMIN)
public class SysUserController extends BaseController {

    private final SysUserService userService;

    /**
     * 创建用户。
     *
     * @param cmd 创建入参；username/password/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysUserCreateCmd cmd) {
        userService.create(cmd);
        return ok();
    }

    /**
     * 删除用户（软删除，deleted 置为本行主键 id）。
     *
     * @param id 用户 id；必须为正整数
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "用户 id 必须为正整数") Long id) {
        userService.delete(id);
        return ok();
    }

    /**
     * 更新用户档案（username/password 不可修改）。
     *
     * @param id  用户 id；必须为正整数
     * @param cmd 更新入参；nickname/status 必填，格式校验失败返回 400 与字段错误信息
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "用户 id 必须为正整数") Long id,
                               @RequestBody @Valid SysUserUpdateCmd cmd) {
        userService.update(id, cmd);
        return ok();
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户 id；必须为正整数
     * @return 用户详情；用户不存在时返回 601002
     */
    @GetMapping("/{id}")
    public Result<SysUserVO> get(@PathVariable @Positive(message = "用户 id 必须为正整数") Long id) {
        return ok(SysUserVO.from(userService.getById(id)));
    }

    /**
     * 分页条件查询用户列表。
     *
     * <p>过滤条件全部选填：username/nickname/mobile/email 模糊匹配（CONTAINING、忽略大小写），
     * status 精确匹配；分页参数越界/未传在 {@link PageQuery} 构造期自动归一化，
     * 排序字段非法（注入风险）返回 600001。
     *
     * @param query 业务过滤条件（全部选填）
     * @param page  分页与排序参数（pageNum/pageSize/sortField/desc，全部选填）
     * @return 当前页数据与总记录数
     */
    @GetMapping("/page")
    public Result<List<SysUserVO>> page(@Valid SysUserQuery query, @Valid PageQuery page) {
        Page<SysUser> result = userService.page(query, page);
        List<SysUserVO> records = result.getContent().stream().map(SysUserVO::from).toList();
        return ok(records, result.getTotalElements());
    }
}
