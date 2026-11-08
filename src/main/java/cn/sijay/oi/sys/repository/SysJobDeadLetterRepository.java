package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 任务死信数据访问层，映射表 {@code sys_job_dead_letter}。
 *
 * <p>死信处理完毕后允许物理删除；组合条件检索由 {@link #search} 动态 JPQL 承载。
 *
 * @author sijay
 */
public interface SysJobDeadLetterRepository extends JpaRepository<SysJobDeadLetter, Long> {

    /**
     * 组合条件分页检索死信（未设置的条件不参与过滤）。
     *
     * @param jobKey    任务业务键过滤；空为全部
     * @param status    处理状态过滤；空为全部
     * @param beginTime 产生时间下限；空为不限
     * @param endTime   产生时间上限；空为不限
     * @param pageable  分页参数
     * @return 分页结果（排序由调用方 Sort 保证）
     */
    @Query("""
            select d from SysJobDeadLetter d
             where (:jobKey is null or d.jobKey = :jobKey)
               and (:status is null or d.status = :status)
               and (:beginTime is null or d.createTime >= :beginTime)
               and (:endTime is null or d.createTime <= :endTime)
            """)
    Page<SysJobDeadLetter> search(@Param("jobKey") String jobKey,
                                  @Param("status") String status,
                                  @Param("beginTime") LocalDateTime beginTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  Pageable pageable);

    /**
     * 统计任务待处理死信数量（重放前置校验与死信告警用）。
     *
     * @param jobKey 任务业务键
     * @return 待处理死信数量
     */
    long countByJobKeyAndStatus(String jobKey, String status);

    /**
     * 按任务业务键物理删除死信（任务配置删除时的连带清理入口）。
     *
     * @param jobKey 任务业务键
     * @return 影响行数
     */
    @Modifying
    int deleteByJobKey(@Param("jobKey") String jobKey);
}
