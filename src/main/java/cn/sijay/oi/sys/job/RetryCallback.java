package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;

/**
 * 重试完成回调 SPI；任务完成所有重试流程（无论最终成功或失败）时统一触发。
 *
 * <p><b>扩展语义</b>：业务方自定义实现（注册为 Spring Bean 即自动接入，
 * 多实现按 Ordered 排序依次执行），用于实现：
 * <ul>
 *   <li>业务补偿：任务终态失败后触发补偿逻辑；</li>
 *   <li>数据对账：任务终态成功后发起上下游对账；</li>
 *   <li>外部通知：向第三方系统推送任务执行结果。</li>
 * </ul>
 *
 * <p><b>隔离性约定</b>（由框架保证）：
 * <ul>
 *   <li>回调执行流程完全独立于任务主流程——在任务终态落库<b>之后</b>触发，
 *       回调产生的任何异常不影响任务本身的执行状态与结果记录；</li>
 *   <li>回调异常仅记录告警日志（含堆栈），不重试、不阻断其他回调实现。</li>
 </ul>
 *
 * <p>实现示例：
 * <pre>{@code
 * @Component
 * public class OrderSyncCompensationCallback implements RetryCallback {
 *     @Override
 *     public void onCompleted(RetryResult result) {
 *         if (!result.success()) {
 *             compensationService.enqueue(result.getJobKey(), result.getLastError());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author sijay
 */
public interface RetryCallback {

    /**
     * 任务重试流程完成回调（终态触发：成功/失败/超时/死信均回调）。
     *
     * <p>框架保证：回调在任务终态记录落库后触发；实现内抛出的异常被框架捕获
     * 记录告警日志，不影响任务状态与记录。
     *
     * @param result 重试流程结果（终态、重试计数、异常摘要等）
     */
    void onCompleted(RetryResult result);

    /**
     * 重试流程结果上下文；携带任务与本次执行的关键信息。
     *
     * @param jobKey       任务业务键
     * @param jobName      任务名称快照
     * @param success      最终是否成功（true=SUCCESS；false=FAILED/TIMEOUT/DEAD）
     * @param finalStatus  终态状态名（JobExecuteStatus 枚举名）
     * @param retryCount   累计重试次数
     * @param durationMillis 总执行耗时（毫秒，含全部重试轮次）
     * @param logId        执行记录 id（sys_job_log.id；回调方可关联查询重试明细）
     * @param lastError    最终异常摘要（message + 有限堆栈）；成功时为 null
     */
    record RetryResult(String jobKey,
                       String jobName,
                       boolean success,
                       String finalStatus,
                       int retryCount,
                       long durationMillis,
                       Long logId,
                       String lastError) {
    }
}
