package cn.sijay.oi.auth.service;

import cn.dev33.satoken.stp.StpInterface;
import cn.sijay.oi.sys.entity.SysMenu;
import cn.sijay.oi.sys.entity.SysRole;
import cn.sijay.oi.sys.entity.SysRoleMenu;
import cn.sijay.oi.sys.entity.SysUserRole;
import cn.sijay.oi.sys.repository.SysMenuRepository;
import cn.sijay.oi.sys.repository.SysRoleMenuRepository;
import cn.sijay.oi.sys.repository.SysRoleRepository;
import cn.sijay.oi.sys.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * sa-token 权限数据源：为注解鉴权（@SaCheckRole / @SaCheckPermission）提供
 * 当前登录用户的角色与权限清单。
 *
 * <p><b>角色模型</b>：与项目"超管约定"一致——用户 ID=1 为超级管理员，固定授予
 * {@code admin} 角色（种子数据未为超管建立 sys_user_role 关联，此处显式赋予）；
 * 普通用户经 用户-角色 关联取其启用角色的编码集合。
 *
 * <p><b>权限模型</b>：超管持有通配权限 {@code *}（通过一切 @SaCheckPermission 校验）；
 * 普通用户取其角色关联菜单（sys_menu.perms，非空项）的并集。当前项目注解仅使用
 * 角色校验（管理模块 @SaCheckRole("admin")），权限清单作为细粒度扩展点预留，
 * 新增权限注解时无需再改本类。
 *
 * <p><b>调用时机</b>：sa-token 在遇到注解鉴权时回调本类（每次校验实时查询，
 * 无缓存——角色变更即时生效；管理后台调用量级下开销可忽略）。
 *
 * @author sijay
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    /**
     * 超管用户 id（与 AuthService、sql/data.sql 种子数据约定一致）。
     */
    private static final long ADMIN_USER_ID = 1L;

    /**
     * 超管固定角色编码；管理类控制器以 @SaCheckRole("admin") 防护。
     */
    public static final String ROLE_ADMIN = "admin";

    /**
     * 超管通配权限串；命中一切 @SaCheckPermission 校验。
     */
    public static final String PERMISSION_WILDCARD = "*";

    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleRepository roleRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysMenuRepository menuRepository;

    /**
     * 返回当前登录用户的权限清单（sys_menu.perms 非空项并集）。
     *
     * @param loginId   登录用户 id
     * @param loginType 账号体系标识（本项目仅 default 单体系）
     * @return 权限串集合；超管返回通配权限，普通用户无授权时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (toUserId(loginId) == ADMIN_USER_ID) {
            return List.of(PERMISSION_WILDCARD);
        }
        List<Long> roleIds = roleIdsOf(toUserId(loginId));
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuRepository.findByRoleIdIn(roleIds)
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuRepository.findAllById(menuIds).stream()
                .map(SysMenu::getPerms)
                .filter(perms -> perms != null && !perms.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 返回当前登录用户的角色清单。
     *
     * @param loginId   登录用户 id
     * @param loginType 账号体系标识
     * @return 角色编码集合；超管固定 {@code [admin]}，普通用户取启用角色编码，无角色返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = toUserId(loginId);
        if (userId == ADMIN_USER_ID) {
            return List.of(ROLE_ADMIN);
        }
        return roleIdsOf(userId).stream()
                .map(roleRepository::findById)
                .flatMap(role -> role.stream()
                        .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                        .map(SysRole::getCode))
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 查询用户关联的启用角色 id 集合（软删行由 @SQLRestriction 全局过滤）。
     */
    private List<Long> roleIdsOf(long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
    }

    /**
     * sa-token 的 loginId 为 Object；本项目单一体制下恒为用户 id。
     */
    private long toUserId(Object loginId) {
        return Long.parseLong(String.valueOf(loginId));
    }
}
