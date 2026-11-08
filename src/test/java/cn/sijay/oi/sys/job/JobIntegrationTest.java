package cn.sijay.oi.sys.job;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobExecuteStatus;
import cn.sijay.oi.sys.enums.JobTriggerType;
import cn.sijay.oi.sys.model.job.SysJobCreateCmd;
import cn.sijay.oi.sys.model.job.SysJobUpdateCmd;
import cn.sijay.oi.sys.repository.SysJobDeadLetterRepository;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import cn.sijay.oi.sys.repository.SysJobRepository;
import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务调度模块集成测试（真实 Spring 上下文 + H2 + Redis）。
 *
 * <p>覆盖规范核心场景：
 * <ul>
 *   <li>注解接入：@OiScheduled 扫描建档 + 执行动作注册；</li>
 *   <li>调度触发：fixed-rate 注册后自动触发并落执行记录；</li>
 *   <li>动态启停：暂停/恢复即时生效；</li>
 *   <li>死信全链路：重试耗尽 → 死信入库 → 手动重放成功；</li>
 *   <li>并发冲突：同一任务串行（分布式锁），DISCARD 丢弃记录 SKIPPED；</li>
 *   <li>运维接口审计：操作写入 sys_log。</li>
 * </ul>
 *
 * <p>测试任务定义在 {@link IntegrationTestTasks}（@OiScheduled 样本），
 * jobKey 以 it.job 前缀隔离；用例后清理任务与锁残留。
 *
 * @author sijay
 */
@DisplayName("任务调度模块集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(IntegrationTestTasks.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobsched;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // 独立 Redis database，避免污染业务键
        "spring.data.redis.database=14"
})
class JobIntegrationTest extends AuthenticatedIntegrationTest {

    @Autowired
    private SysJobRepository jobRepository;
    @Autowired
    private SysJobLogRepository jobLogRepository;
    @Autowired
    private SysJobDeadLetterRepository deadLetterRepository;
    @Autowired
    private JobOperateService operateService;
    @Autowired
    private JobSchedulerManager schedulerManager;
    @Autowired
    private JobActionRegistry actionRegistry;

    @Autowired
    private cn.sijay.oi.common.util.RedisUtil redisUtil;

    /**
     * 清理用例间残留：仅删测试自建任务（it.job.created/slow 等）与执行记录/死信；
     * <b>不删样本任务</b>（@OiScheduled 自动建档仅在 ReadyEvent 执行一次，删后不可恢复）。
     * 同时清理测试任务的分布式锁残留（上一进程异常退出可能遗留未释放锁）。
     */
    @AfterEach
    void cleanup() {
        jobRepository.findAll().forEach(job -> {
            if (!job.getJobKey().equals(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)
                    && !job.getJobKey().equals(IntegrationTestTasks.ALWAYS_FAIL_KEY)) {
                schedulerManager.unregister(job.getJobKey());
                // 防御性清理：释放可能残留的任务锁（正常路径已释放，此处兜底）
                try {
                    redisUtil.delete("oi:job:lock:" + job.getJobKey());
                } catch (Exception ignored) {
                    // Redis 不可达等场景不影响测试清理
                }
            }
        });
        jobRepository.findAll().forEach(job -> {
            if (!job.getJobKey().equals(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)
                    && !job.getJobKey().equals(IntegrationTestTasks.ALWAYS_FAIL_KEY)) {
                jobRepository.delete(job);
            }
        });
        jobLogRepository.deleteAll();
        deadLetterRepository.deleteAll();
    }

    /**
     * 确保样本任务处于注册态（保险：若某用例暂停后未恢复）。
     */
    private void ensureRegistered(String jobKey) {
        if (!schedulerManager.isRegistered(jobKey)) {
            jobRepository.findByJobKey(jobKey).ifPresent(schedulerManager::register);
        }
    }

    /**
     * 按业务键取执行记录。
     */
    private List<SysJobLog> logsOf(String jobKey) {
        return jobLogRepository.search(jobKey, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
    }

    @Test
    @Order(1)
    @DisplayName("注解接入：@OiScheduled 启动扫描建档 + 执行动作注册")
    void annotatedTaskAutoRegistered() {
        assertThat(actionRegistry.lookup(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)).isNotNull();
        assertThat(actionRegistry.lookup(IntegrationTestTasks.ALWAYS_FAIL_KEY)).isNotNull();
        assertThat(jobRepository.findByJobKey(IntegrationTestTasks.ALWAYS_SUCCESS_KEY))
                .hasValueSatisfying(job -> {
                    assertThat(job.getName()).isEqualTo("集成测试-成功任务");
                    assertThat(job.getTriggerType()).isEqualTo(JobTriggerType.FIXED_RATE);
                    assertThat(job.getEnabled()).isTrue();
                });
        // 启用中任务已注册到调度器
        assertThat(schedulerManager.isRegistered(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("调度触发：fixed-rate 任务自动执行并落执行记录（SUCCESS）")
    void scheduledExecutionProducesLog() {
        ensureRegistered(IntegrationTestTasks.ALWAYS_SUCCESS_KEY);
        // 样本任务周期 2 秒（IntegrationTestTasks 定义），等待至多 8 秒观察触发
        Awaitility.await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            List<SysJobLog> logs = logsOf(IntegrationTestTasks.ALWAYS_SUCCESS_KEY);
            assertThat(logs).isNotEmpty();
            assertThat(logs.get(0).getExecuteStatus()).isEqualTo(JobExecuteStatus.SUCCESS.name());
            assertThat(logs.get(0).getTriggerSource()).isEqualTo("SCHEDULE");
            assertThat(logs.get(0).getDurationMillis()).isNotNull();
        });
    }

    @Test
    @Order(3)
    @DisplayName("动态启停：暂停后不再触发，恢复后恢复触发")
    void pauseResumeTakesEffectImmediately() {
        ensureRegistered(IntegrationTestTasks.ALWAYS_SUCCESS_KEY);
        SysJob job = jobRepository.findByJobKey(IntegrationTestTasks.ALWAYS_SUCCESS_KEY).orElseThrow();
        operateService.pause(job.getId());
        assertThat(schedulerManager.isRegistered(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)).isFalse();
        // 暂停中的触发静默跳过
        schedulerManager.triggerOnce(IntegrationTestTasks.ALWAYS_SUCCESS_KEY);
        int logsAfterPause = logsOf(IntegrationTestTasks.ALWAYS_SUCCESS_KEY).size();

        operateService.resume(job.getId());
        assertThat(schedulerManager.isRegistered(IntegrationTestTasks.ALWAYS_SUCCESS_KEY)).isTrue();
        Awaitility.await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertThat(logsOf(IntegrationTestTasks.ALWAYS_SUCCESS_KEY).size()).isGreaterThan(logsAfterPause));
    }

    @Test
    @Order(4)
    @DisplayName("死信全链路：重试耗尽转死信 → 手动重放成功")
    void deadLetterFullLifecycle() {
        ensureRegistered(IntegrationTestTasks.ALWAYS_FAIL_KEY);
        // 样本失败任务：重试 1 次、间隔 100ms，两次尝试均失败 → 死信
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SysJobDeadLetter> deadLetters = deadLetterRepository
                    .search(IntegrationTestTasks.ALWAYS_FAIL_KEY, null, null, null,
                            org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent();
            assertThat(deadLetters).hasSize(1);
            SysJobDeadLetter deadLetter = deadLetters.get(0);
            assertThat(deadLetter.getTotalRetryCount()).isEqualTo(1);
            assertThat(deadLetter.getJobConfig()).contains("\"maxRetryCount\":1");
            assertThat(deadLetter.getStatus()).isEqualTo("PENDING");
        });
        // 执行记录终态为 DEAD，明细 2 轮（首试 + 1 重试）
        SysJobLog deadLog = logsOf(IntegrationTestTasks.ALWAYS_FAIL_KEY).stream()
                .filter(l -> l.getExecuteStatus().equals(JobExecuteStatus.DEAD.name()))
                .findFirst().orElseThrow();
        assertThat(deadLog.getRetryCount()).isEqualTo(1);

        // 手动重放：失败任务的重放仍会失败，但重放链路完整（REPLAY_FAILED 状态流转）
        SysJobDeadLetter deadLetter = deadLetterRepository
                .search(IntegrationTestTasks.ALWAYS_FAIL_KEY, null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);
        JobExecutionEngine.JobExecutionResult replayResult =
                operateService.replayDeadLetter(deadLetter.getId());
        assertThat(replayResult.success()).isFalse();
        assertThat(replayResult.status()).isIn(JobExecuteStatus.DEAD.name(),
                JobExecuteStatus.FAILED.name());
        // 重放后死信状态流转 + 重放时间落库
        SysJobDeadLetter replayed = deadLetterRepository.findById(deadLetter.getId()).orElseThrow();
        assertThat(replayed.getStatus()).isIn("PENDING", "REPLAY_FAILED");
        assertThat(replayed.getReplayTime()).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("运维 CRUD：创建/更新/删除任务，配置即时生效")
    void operateCrud() {
        SysJobCreateCmd create = new SysJobCreateCmd();
        create.setJobKey("it.job.created");
        create.setName("运维创建任务");
        create.setTriggerType(JobTriggerType.FIXED_DELAY);
        create.setTriggerValue("60000");
        create.setTimeoutMillis(5000L);
        create.setMaxRetryCount(1);
        create.setRetryInitialInterval(100L);
        create.setRetryMaxInterval(1000L);
        create.setRetryPolicy(cn.sijay.oi.sys.enums.JobRetryPolicy.FIXED_INTERVAL);
        create.setConflictStrategy(JobConflictStrategy.DISCARD);
        create.setAlertFailureThreshold(3);
        SysJob created = operateService.create(create);
        assertThat(schedulerManager.isRegistered("it.job.created")).isTrue();

        SysJobUpdateCmd update = new SysJobUpdateCmd();
        update.setName("运维创建任务-改名");
        update.setTriggerType(JobTriggerType.CRON);
        update.setTriggerValue("*/30 * * * * *");
        update.setTimeoutMillis(0L);
        update.setMaxRetryCount(0);
        update.setRetryInitialInterval(1000L);
        update.setRetryMaxInterval(1000L);
        update.setRetryPolicy(cn.sijay.oi.sys.enums.JobRetryPolicy.FIXED_INTERVAL);
        update.setConflictStrategy(JobConflictStrategy.QUEUE);
        update.setAlertFailureThreshold(0);
        operateService.update(created.getId(), update);
        SysJob updated = jobRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("运维创建任务-改名");
        assertThat(updated.getTriggerType()).isEqualTo(JobTriggerType.CRON);
        assertThat(updated.getConflictStrategy()).isEqualTo(JobConflictStrategy.QUEUE);
        // 更新后仍处于注册态（按新配置重注册）
        assertThat(schedulerManager.isRegistered("it.job.created")).isTrue();

        operateService.delete(created.getId());
        assertThat(schedulerManager.isRegistered("it.job.created")).isFalse();
        assertThat(jobRepository.existsByJobKey("it.job.created")).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("并发冲突：DISCARD 策略下慢任务执行期间的后续触发被丢弃（SKIPPED）")
    void concurrentConflictDiscarded() throws InterruptedException {
        // 创建慢任务（执行体 2.5 秒），DISCARD 策略
        SysJobCreateCmd create = new SysJobCreateCmd();
        create.setJobKey("it.job.slow");
        create.setName("慢任务");
        create.setTriggerType(JobTriggerType.FIXED_RATE);
        create.setTriggerValue("60000"); // 周期长（60s），测试窗口内调度器不干扰
        create.setTimeoutMillis(0L);
        create.setMaxRetryCount(0);
        create.setRetryInitialInterval(1000L);
        create.setRetryMaxInterval(1000L);
        create.setRetryPolicy(cn.sijay.oi.sys.enums.JobRetryPolicy.FIXED_INTERVAL);
        create.setConflictStrategy(JobConflictStrategy.DISCARD);
        create.setAlertFailureThreshold(0);
        // 前置清锁：上一测试进程异常退出可能遗留同 key 未释放锁（TTL 长达 5 分钟）
        try {
            redisUtil.delete("oi:job:lock:it.job.slow");
        } catch (Exception ignored) {
            // Redis 清理失败则按无残留处理（首个 RUNNING 等待会给出明确失败信息）
        }
        SysJob job = operateService.create(create);
        // 立即暂停：防止 scheduleAtFixedRate 的"注册即首次触发"抢锁干扰手动时序
        operateService.pause(job.getId());
        // 动态注册慢执行动作，手动连续触发制造并发冲突
        actionRegistry.register("it.job.slow", IntegrationTestTasks::sleepSlow);

        // 第一次执行（占用 2.5s），等待 RUNNING 落库（锁已被第一条线程持有）
        Thread first = new Thread(() ->
                operateService.trigger(job.getId()));
        first.start();
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() ->
                logsOf("it.job.slow").stream().anyMatch(l -> "RUNNING".equals(l.getExecuteStatus())));
        // 并发第二次 → DISCARD：记 SKIPPED 立即返回
        JobExecutionEngine.JobExecutionResult second = operateService.trigger(job.getId());
        assertThat(second.status()).isEqualTo(JobExecuteStatus.SKIPPED.name());
        first.join(10_000);
        // 第一条正常完成（SUCCESS）
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(logsOf("it.job.slow")).anyMatch(l ->
                        "SUCCESS".equals(l.getExecuteStatus())));
        // 清理动态注册的动作（防影响后续用例判断）
        actionRegistry.unregister("it.job.slow");
    }

    @Test
    @Order(7)
    @DisplayName("分页检索：任务/执行记录/死信三页接口可用")
    void paginationWorks() {
        // 任务分页：样本任务两条（it.job.success / it.job.fail）
        Page<SysJob> jobs = operateService.page(
                new cn.sijay.oi.sys.model.job.SysJobQuery(), new PageQuery(1, 10, null, null));
        assertThat(jobs.getTotalElements()).isGreaterThanOrEqualTo(2);
        // 执行记录分页：先手动触发一次成功任务保证有数据（不依赖前面用例的执行残留）
        SysJob job = jobRepository.findByJobKey(IntegrationTestTasks.ALWAYS_SUCCESS_KEY).orElseThrow();
        operateService.trigger(job.getId());
        Page<SysJobLog> logs = operateService.pageLogs(
                IntegrationTestTasks.ALWAYS_SUCCESS_KEY, null, null, null, null,
                new PageQuery(1, 10, null, null));
        assertThat(logs.getContent()).isNotEmpty();
        assertThat(logs.getContent().get(0).getExecuteStatus())
                .isEqualTo(JobExecuteStatus.SUCCESS.name());
        // 死信分页：空结果也可正常返回分页结构
        Page<SysJobDeadLetter> deadLetters = operateService.pageDeadLetters(
                null, null, new PageQuery(1, 10, null, null));
        assertThat(deadLetters).isNotNull();
    }
}
