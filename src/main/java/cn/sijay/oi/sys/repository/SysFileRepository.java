package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysFile;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 文件数据访问层，映射表 {@code sys_file}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行，无需在方法名或 JPQL 中重复条件；删除统一走 {@link #softDelete(Long)}。
 *
 * <p>分页条件查询使用 Query-by-Example（{@code findAll(Example, Pageable)}），
 * 由 Service 层装配 {@code ExampleMatcher}（StringMatcher.CONTAINING 模糊匹配）。
 *
 * @author sijay
 */
public interface SysFileRepository extends JpaRepository<SysFile, Long> {

    /**
     * 软删除：将 deleted 置为本行主键 id（与 DDL 约定一致）。
     *
     * <p>使用场景：删除文件的唯一入口；不使用物理 DELETE，保留审计痕迹
     * （物理文件由 Service 层在软删后一并清理）。
     * 注意：JPQL 批量更新不走 @SQLRestriction，故条件中显式携带 deleted = 0，
     * 防止对已软删行重复执行。
     *
     * @param id 文件 id
     * @return 影响行数；0 表示文件不存在或已软删
     */
    @Modifying
    @Query("update SysFile f set f.deleted = f.id, f.updateTime = CURRENT_TIMESTAMP where f.id = :id and f.deleted = 0")
    int softDelete(@NonNull Long id);
}
