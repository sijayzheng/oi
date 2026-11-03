package cn.sijay.oi.auth.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定服务：同一账号连续登录失败达到阈值后临时锁定，到期自动解锁。
 *
 * <p><b>规则（默认）</b>：同一用户名连续失败 {@value #MAX_FAILURES} 次后锁定
 * {@value #LOCK_DURATION_MS} 毫秒（10 分钟）；锁定期间该账号的一切登录请求
 * （含密码正确的请求）均被拒绝并返回剩余锁定时长提示；锁定到期后自动解锁，
 * 失败计数清零；登录成功立即重置该账号的失败计数。
 *
 * <p><b>实现说明</b>：计数态保存在进程内 {@link ConcurrentHashMap}（与 sa-token
 * 默认的内存会话存储同生命周期，重启即清零），单实例部署下无需外部存储；
 * 计数按用户名维度（不区分 IP），在凭据校验失败时累计，账号禁用不计入
 * （禁用属于账号状态问题而非凭据尝试）。到期解锁采用惰性判定：
 * 校验时发现已到期即清除锁定态，无后台线程。
 *
 * <p><b>防枚举边界</b>：锁定提示仅在"该用户名确实被连续尝试失败"后出现，
 * 不泄露账号是否存在；不存在的用户名同样计入失败（不区分，防止探测）。
 *
 * @author sijay
 */
@Component
public class LoginLockService {

    /**
     * 触发锁定的连续失败次数阈值。
     */
    public static final int MAX_FAILURES = 5;

    /**
     * 锁定时长（毫秒）；默认 10 分钟。
     */
    public static final long LOCK_DURATION_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, LockState> states = new ConcurrentHashMap<>();

    /**
     * 校验账号当前是否处于锁定期；锁定中抛出带剩余分钟数的业务异常。
     *
     * @param username 登录用户名
     * @throws BusinessException 账号锁定中（601007，提示剩余等待分钟数）
     */
    public void checkNotLocked(String username) {
        LockState state = states.get(username);
        if (state == null) {
            return;
        }
        // lockUntil=0 表示"有失败计数但未锁定"：直接放行，且不得清除计数状态
        if (state.lockUntil <= 0) {
            return;
        }
        long remainingMs = state.lockUntil - System.currentTimeMillis();
        if (remainingMs <= 0) {
            // 锁定已到期：惰性解锁（清除状态，后续请求正常）
            states.remove(username, state);
            return;
        }
        long remainingMinutes = (remainingMs + 59_999) / 60_000;
        throw new BusinessException(BusinessErrorCode.USER_LOGIN_LOCKED.getCode(),
                "登录失败次数过多，账号已锁定，请约 " + remainingMinutes + " 分钟后重试");
    }

    /**
     * 记录一次登录失败；累计达到阈值即进入锁定态。
     *
     * @param username 登录用户名
     */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        states.compute(username, (k, state) -> {
            // 已锁定期间的新失败不重复计数；锁定已到期则从零重新计数
            boolean expired = state != null && state.lockUntil > 0 && state.lockUntil <= now;
            if (state != null && state.lockUntil > now) {
                return state;
            }
            int failures = (state == null || expired ? 0 : state.failures) + 1;
            long lockUntil = failures >= MAX_FAILURES ? now + LOCK_DURATION_MS : 0L;
            return new LockState(failures, lockUntil);
        });
    }

    /**
     * 登录成功：重置（清除）该账号的失败计数与锁定态。
     *
     * @param username 登录用户名
     */
    public void recordSuccess(String username) {
        states.remove(username);
    }

    /**
     * 锁定态快照（failures=当前连续失败次数，lockUntil=锁定截止时间戳，0 表示未锁定）。
     */
    private record LockState(int failures, long lockUntil) {
    }

    /* ==================== 测试辅助（包内可见） ==================== */

    /**
     * 以指定时长直接置为锁定态（仅测试使用：验证到期自动解锁逻辑，避免真实等待 10 分钟）。
     */
    void lockFor(String username, long durationMs) {
        states.put(username, new LockState(MAX_FAILURES, System.currentTimeMillis() + durationMs));
    }

    /**
     * 查询账号是否处于锁定态（仅测试使用）。
     */
    boolean isLocked(String username) {
        LockState state = states.get(username);
        return state != null && state.lockUntil > System.currentTimeMillis();
    }

    /**
     * 清空全部锁定状态（仅测试使用：保证用例间隔离）。
     */
    void clear() {
        states.clear();
    }
}
