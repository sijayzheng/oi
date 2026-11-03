package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志数据访问层，映射表 {@code sys_log}。
 *
 * <p>约定：append-only——仅提供插入与查询，不提供更新/删除方法（审计留痕不可变）；
 * 表按 create_time 建有索引（idx_log_time），组合条件检索统一走 {@link #search}。
 *
 * @author sijay
 */
public interface SysLogRepository extends JpaRepository<SysLog, Long> {

    /**
     * 组合条件分页检索操作日志（日志级别/时间范围/关键词）。
     *
     * <p>关键词匹配范围：日志内容（params/errorMsg）、动作、模块、操作人、
     * 请求路径与请求 ID（均为 contains 模糊、忽略大小写）；各条件为空时自动忽略
     * （Hibernate 对 null 参数的 is null 判断在 H2 下按绑定值内联，安全无注入）。
     *
     * @param level     日志级别；null 不过滤
     * @param startTime 开始时间（含）；null 不过滤
     * @param endTime   结束时间（含）；null 不过滤
     * @param keyword   关键词；null 不过滤
     * @param pageable  分页参数
     * @return 分页结果（按 createTime/id 倒序，最新在前）
     */
    @Query("""
            select l from SysLog l
            where (:level is null or l.level = :level)
              and (:startTime is null or l.createTime >= :startTime)
              and (:endTime is null or l.createTime <= :endTime)
              and (:keyword is null
                   or lower(l.params) like concat('%', lower(:keyword), '%')
                   or lower(l.errorMsg) like concat('%', lower(:keyword), '%')
                   or lower(l.action) like concat('%', lower(:keyword), '%')
                   or lower(l.module) like concat('%', lower(:keyword), '%')
                   or lower(l.username) like concat('%', lower(:keyword), '%')
                   or lower(l.uri) like concat('%', lower(:keyword), '%')
                   or lower(l.requestId) like concat('%', lower(:keyword), '%'))
            order by l.createTime desc, l.id desc
            """)
    Page<SysLog> search(@Param("level") String level,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    /**
     * 组合条件查询全部命中记录（导出用，调用方须限制条数防止超量导出）。
     *
     * @param level     日志级别；null 不过滤
     * @param startTime 开始时间（含）；null 不过滤
     * @param endTime   结束时间（含）；null 不过滤
     * @param keyword   关键词；null 不过滤
     * @return 命中记录列表（按 createTime/id 倒序）
     */
    @Query("""
            select l from SysLog l
            where (:level is null or l.level = :level)
              and (:startTime is null or l.createTime >= :startTime)
              and (:endTime is null or l.createTime <= :endTime)
              and (:keyword is null
                   or lower(l.params) like concat('%', lower(:keyword), '%')
                   or lower(l.errorMsg) like concat('%', lower(:keyword), '%')
                   or lower(l.action) like concat('%', lower(:keyword), '%')
                   or lower(l.module) like concat('%', lower(:keyword), '%')
                   or lower(l.username) like concat('%', lower(:keyword), '%')
                   or lower(l.uri) like concat('%', lower(:keyword), '%')
                   or lower(l.requestId) like concat('%', lower(:keyword), '%'))
            order by l.createTime desc, l.id desc
            """)
    List<SysLog> searchAll(@Param("level") String level,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime,
                           @Param("keyword") String keyword);
}
