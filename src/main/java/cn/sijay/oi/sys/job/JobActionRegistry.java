package cn.sijay.oi.sys.job;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行动作注册表：jobKey → 业务执行体映射。
 *
 * <p><b>数据来源</b>：应用启动时由 {@code JobRegistrationService} 扫描全部
 * {@code @OiScheduled} 方法填充；运行期内容恒定（重启才变），
 * 查找为 O(1) Map 读取，无锁并发安全。
 *
 * <p><b>设计动机</b>：将"任务配置"（库中可变）与"任务逻辑"（代码中固定）解耦——
 * 库中删除配置不影响代码；代码删除方法则调度触发时告警跳过。
 *
 * @author sijay
 */
@Component
public class JobActionRegistry {

    /**
     * jobKey → 执行动作映射。
     */
    private final Map<String, Runnable> actions = new ConcurrentHashMap<>();

    /**
     * 注册执行动作（启动扫描调用；重复 jobKey 后注册覆盖前者并告警）。
     *
     * @param jobKey  任务业务键
     * @param action  执行动作
     */
    public void register(String jobKey, Runnable action) {
        Runnable previous = actions.put(jobKey, action);
        if (previous != null) {
            throw new IllegalStateException("任务业务键重复: jobKey=" + jobKey
                    + "；@OiScheduled 的 jobKey 必须全局唯一");
        }
    }

    /**
     * 查找执行动作。
     *
     * @param jobKey 任务业务键
     * @return 执行动作；未注册（代码未提供该任务）返回 null
     */
    public Runnable lookup(String jobKey) {
        return actions.get(jobKey);
    }

    /**
     * 注销执行动作（测试动态注册的清理；生产启动扫描后通常无需调用）。
     *
     * @param jobKey 任务业务键
     */
    void unregister(String jobKey) {
        actions.remove(jobKey);
    }

    /**
     * 当前已注册动作总数（测试观测用）。
     *
     * @return 注册数量
     */
    public int size() {
        return actions.size();
    }
}
