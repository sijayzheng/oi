package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysJobLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 任务执行记录数据访问层，映射表 {@code sys_job_log}。
 *
 * <p>执行记录为审计数据，不做软删；组合条件检索（任务/状态/来源/时间范围）
 * 由 {@link #search} 动态 JPQL 承载。
 *
 * @author sijay
 */
public interface SysJobLogRepository extends JpaRepository<SysJobLog, Long> {

    /**
     * 组合条件分页检索执行记录（未设置的条件不参与过滤）。
     *
     * @param jobKey     任务业务键过滤；空为全部
     * @param status     执行状态过滤；空为全部
     * @param source     触发来源过滤；空为全部
     * @param beginTime  开始时间下限；空为不限
     * @param endTime    开始时间上限；空为不限
     * @param pageable   分页参数
     * @return 分页结果（固定按开始时间倒序由调用方 Sort 保证）
     */
    @Query("""
            select l from SysJobLog l
             where (:jobKey is null or l.jobKey = :jobKey)
               and (:status is null or l.executeStatus = :status)
               and (:source is null or l.triggerSource = :source)
               and (:beginTime is null or l.startTime >= :beginTime)
               and (:endTime is null or l.startTime <= :endTime)
            """)
    Page<SysJobLog> search(@Param("jobKey") String jobKey,
                           @Param("status") String status,
                           @Param("source") String source,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime,
                           Pageable pageable);

    /**
     * 查询任务最近的 N 条执行记录（告警内容"最近 3 次执行历史"用）。
     *
     * @param jobKey   任务业务键
     * @param pageable 分页参数（取第一页 N 条）
     * @return 最近的执行记录，按开始时间倒序
     */
    @Query("select l from SysJobLog l where l.jobKey = :jobKey order by l.startTime desc, l.id desc")
    List<SysJobLog> findRecent(@Param("jobKey") String jobKey, Pageable pageable);

    /**
     * 统计任务自指定时刻以来的连续失败次数（从最新一条记录向前数，
     * 遇到非失败状态即停；失败 = FAILED/TIMEOUT/DEAD）。
     *
     * <p>实现说明：取该窗口内的记录按时间倒序，在应用层扫描计数，
     * 记录数以告警阈值 + 1 为上限，扫描代价可控。
     *
     * @param jobKey     任务业务键
     * @param beginTime  统计起点
     * @param pageable   取多少条参与扫描
     * @return 倒序执行记录列表（应用层计算连续失败次数）
     */
    @Query("select l from SysJobLog l where l.jobKey = :jobKey and l.startTime >= :beginTime "
            + "order by l.startTime desc, l.id desc")
    List<SysJobLog> findSinceForConsecutive(@Param("jobKey") String jobKey,
                                            @Param("beginTime") LocalDateTime beginTime,
                                            Pageable pageable);

    /**
     * 按执行记录 id 集合查询（重试明细展示入口）。
     *
     * @param ids 执行记录 id 集合
     * @return 命中的执行记录
     */
    List<SysJobLog> findByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * 将滞留 RUNNING 的记录标记为失败（应用重启后残留的孤儿状态修正）。
     *
     * <p>使用场景：应用优雅停机或异常重启后，上次执行中断导致记录停留在 RUNNING；
     * 启动时将全部 RUNNING 行标记为 FAILED（异常信息=应用重启中断）。
     *
     * @param endTime  修正时间（写入 end_time）
     * @param message  失败原因
     * @return 影响行数
     */
    @Modifying
    @Query("update SysJobLog l set l.executeStatus = 'FAILED', l.endTime = :endTime, "
            + "l.errorType = 'cn.sijay.oi.sys.job.ApplicationRestartException', "
            + "l.errorStack = :message, l.durationMillis = 0 "
            + "where l.executeStatus = 'RUNNING'")
    int markRunningAsFailed(@Param("endTime") LocalDateTime endTime, @Param("message") String message);
}
