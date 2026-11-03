package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysMenu;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 菜单数据访问层，映射表 {@code sys_menu}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    /**
     * 统计指定父菜单下的直接子菜单数量（自动过滤软删行）。
     *
     * <p>使用场景：删除菜单前的影响检查——存在子菜单时禁止删除，防止子树成为孤儿节点。
     *
     * @param parentId 父菜单 id
     * @return 直接子菜单数量；0 表示无子节点
     */
    long countByParentId(@NonNull Long parentId);

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致，软删后子菜单挂载关系自然失效）。
     *
     * <p>使用场景：删除菜单的唯一入口；不使用物理 DELETE，保留审计痕迹。
     * 注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id 菜单 id
     * @return 影响行数；0 表示菜单不存在或已软删
     */
    @Modifying
    @Query("update SysMenu m set m.deleted = m.id, m.updateTime = CURRENT_TIMESTAMP where m.id = :id and m.deleted = 0")
    int softDelete(@NonNull Long id);
}
