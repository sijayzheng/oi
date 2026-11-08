package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysJob;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 任务定义数据访问层，映射表 {@code sys_job}。
 *
 * <p>查询约定：实体标注 {@code @SQLRestriction("deleted = 0")}，本接口所有查询方法
 * 自动过滤软删行；删除统一走 {@link #softDelete(Long)}。
 *
 * @author sijay
 */
public interface SysJobRepository extends JpaRepository<SysJob, Long> {

    /**
     * 按业务键查询任务（自动过滤软删行）。
     *
     * <p>使用场景：动态调度注册时按注解 jobKey 定位任务配置；
     * 运维操作与死信重放的业务键定位入口。
     *
     * @param jobKey 任务业务键
     * @return 命中的任务；不存在或已软删返回 {@link Optional#empty()}
     */
    Optional<SysJob> findByJobKey(@NonNull String jobKey);

    /**
     * 按业务键查询全部启用的任务（调度启动时全量注册）。
     *
     * @return enabled=true 且未软删的任务列表
     */
    List<SysJob> findByEnabledTrue();

    /**
     * 判断业务键是否已存在（自动过滤软删行）。
     *
     * @param jobKey 任务业务键
     * @return 已存在返回 true
     */
    boolean existsByJobKey(@NonNull String jobKey);

    /**
     * 软删除：将 deleted 置为本行主键 id。
     *
     * <p>注意：JPQL 批量更新不走 @SQLRestriction，条件中显式携带 deleted = 0。
     *
     * @param id 任务 id
     * @return 影响行数；0 表示任务不存在或已软删
     */
    @Modifying
    @Query("update SysJob j set j.deleted = j.id, j.updateTime = CURRENT_TIMESTAMP "
            + "where j.id = :id and j.deleted = 0")
    int softDelete(@NonNull Long id);

    /**
     * 按任务 id 集合查询任务（死信列表关联回填名称用；自动过滤软删行）。
     *
     * @param ids 任务 id 集合
     * @return 命中的任务列表
     */
    List<SysJob> findByIdIn(@NonNull Collection<Long> ids);
}
