package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysUser;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * 用户数据访问层，映射表 {@code sys_user}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    /**
     * 按登录名查询用户（自动过滤软删行）。
     *
     * <p>使用场景：创建时的用户名唯一性校验、后续登录认证的用户定位。
     *
     * @param username 登录名
     * @return 命中的用户；不存在或已软删返回 {@link Optional#empty()}
     */
    Optional<SysUser> findByUsername(@NonNull String username);

    /**
     * 判断登录名是否已存在（自动过滤软删行；软删后的同名用户不算存在）。
     *
     * <p>使用场景：创建用户时的业务合法性校验，配合唯一键 uk_user_username 双保险。
     *
     * @param username 登录名
     * @return 已存在返回 true
     */
    boolean existsByUsername(@NonNull String username);

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致，软删后原用户名可重建）。
     *
     * <p>使用场景：删除用户的唯一入口；不使用物理 DELETE，保留审计痕迹。
     * 注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id 用户 id
     * @return 影响行数；0 表示用户不存在或已软删
     */
    @Modifying
    @Query("update SysUser u set u.deleted = u.id, u.updateTime = CURRENT_TIMESTAMP where u.id = :id and u.deleted = 0")
    int softDelete(@NonNull Long id);
}
