package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * 参数配置数据访问层，映射表 {@code sys_config}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    /**
     * 按参数键查询配置（自动过滤软删行）。
     *
     * <p>使用场景：运行时按 key 读取配置值（如 site.name）。
     *
     * @param configKey 参数键
     * @return 命中的配置；不存在或已软删返回 {@link Optional#empty()}
     */
    Optional<SysConfig> findByConfigKey(@NonNull String configKey);

    /**
     * 判断参数键是否已存在（自动过滤软删行；软删后的同键配置不算存在）。
     *
     * <p>使用场景：创建配置时的业务合法性校验，配合唯一键 uk_config_key 双保险。
     *
     * @param configKey 参数键
     * @return 已存在返回 true
     */
    boolean existsByConfigKey(@NonNull String configKey);

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致，软删后原参数键可重建）。
     *
     * <p>使用场景：删除配置的唯一入口；不使用物理 DELETE，保留审计痕迹。
     * 注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id 配置 id
     * @return 影响行数；0 表示配置不存在或已软删
     */
    @Modifying
    @Query("update SysConfig c set c.deleted = c.id, c.updateTime = CURRENT_TIMESTAMP "
            + "where c.id = :id and c.deleted = 0")
    int softDelete(@NonNull Long id);
}
