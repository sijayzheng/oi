package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysJobRetryDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 任务重试明细数据访问层，映射表 {@code sys_job_retry_detail}。
 *
 * <p>明细为 append-only 审计数据，本接口仅提供插入与按执行记录查询。
 *
 * @author sijay
 */
public interface SysJobRetryDetailRepository extends JpaRepository<SysJobRetryDetail, Long> {

    /**
     * 按执行记录 id 查询全部尝试明细（执行详情页展示重试过程）。
     *
     * @param logId 执行记录 id
     * @return 尝试明细，按轮次升序
     */
    List<SysJobRetryDetail> findByLogIdOrderByAttemptNoAsc(Long logId);

    /**
     * 按执行记录 id 集合批量查询（列表页展开重试过程用）。
     *
     * @param logIds 执行记录 id 集合
     * @return 尝试明细集合
     */
    List<SysJobRetryDetail> findByLogIdInOrderByAttemptNoAsc(Collection<Long> logIds);
}
