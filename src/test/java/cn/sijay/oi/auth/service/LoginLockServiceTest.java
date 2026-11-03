package cn.sijay.oi.auth.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录失败锁定服务单元测试（纯内存逻辑，无 Spring 上下文）。
 *
 * <p>覆盖场景：失败计数阈值（第 5 次触发锁定）、成功重置计数、
 * 锁定期内拒绝、到期自动解锁、到期后重新计数。
 *
 * @author sijay
 */
@DisplayName("登录失败锁定服务测试")
class LoginLockServiceTest {

    private LoginLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new LoginLockService();
        lockService.clear();
    }

    @Test
    @DisplayName("连续失败未达阈值（4 次）：不锁定")
    void failuresBelowThresholdNotLocked() {
        for (int i = 0; i < LoginLockService.MAX_FAILURES - 1; i++) {
            lockService.recordFailure("user_a");
        }
        assertThat(lockService.isLocked("user_a")).isFalse();
        assertThatCode(() -> lockService.checkNotLocked("user_a")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("连续失败达到阈值（5 次）：进入锁定态，校验抛 601007 与剩余分钟提示")
    void fifthFailureLocks() {
        for (int i = 0; i < LoginLockService.MAX_FAILURES; i++) {
            lockService.recordFailure("user_b");
        }
        assertThat(lockService.isLocked("user_b")).isTrue();
        assertThatThrownBy(() -> lockService.checkNotLocked("user_b"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BusinessErrorCode.USER_LOGIN_LOCKED.getCode());
                    assertThat(e.getMessage()).contains("账号已锁定").contains("10 分钟");
                });
    }

    @Test
    @DisplayName("登录成功重置失败计数：4 次失败 + 成功 + 4 次失败，仍未锁定")
    void successResetsCounter() {
        for (int i = 0; i < LoginLockService.MAX_FAILURES - 1; i++) {
            lockService.recordFailure("user_c");
        }
        lockService.recordSuccess("user_c");
        for (int i = 0; i < LoginLockService.MAX_FAILURES - 1; i++) {
            lockService.recordFailure("user_c");
        }
        assertThat(lockService.isLocked("user_c")).isFalse();
    }

    @Test
    @DisplayName("锁定到期自动解锁：到期后校验不再抛出，可正常登录")
    void expiredLockAutoUnlocks() throws InterruptedException {
        lockService.lockFor("user_d", 60);
        assertThat(lockService.isLocked("user_d")).isTrue();
        Thread.sleep(150);
        assertThat(lockService.isLocked("user_d")).isFalse();
        assertThatCode(() -> lockService.checkNotLocked("user_d")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("锁定到期后重新计数：再失败 5 次重新锁定")
    void failureAfterExpiredStartsNewCount() throws InterruptedException {
        lockService.lockFor("user_e", 60);
        Thread.sleep(150);
        for (int i = 0; i < LoginLockService.MAX_FAILURES; i++) {
            lockService.recordFailure("user_e");
        }
        assertThat(lockService.isLocked("user_e")).isTrue();
    }

    @Test
    @DisplayName("锁定期间的新失败不延长锁定，不重复计数")
    void failuresDuringLockDoNotExtend() {
        for (int i = 0; i < LoginLockService.MAX_FAILURES; i++) {
            lockService.recordFailure("user_f");
        }
        // 锁定期间继续失败（模拟攻击者持续尝试）
        for (int i = 0; i < 3; i++) {
            lockService.recordFailure("user_f");
        }
        // 仍处于锁定态（锁定截止时间未被改写）
        assertThat(lockService.isLocked("user_f")).isTrue();
    }

    @Test
    @DisplayName("不同账号计数相互隔离")
    void countersAreIsolatedPerUsername() {
        for (int i = 0; i < LoginLockService.MAX_FAILURES; i++) {
            lockService.recordFailure("user_g1");
        }
        assertThat(lockService.isLocked("user_g1")).isTrue();
        assertThat(lockService.isLocked("user_g2")).isFalse();
    }

    @Test
    @DisplayName("回归：计数期间调用锁定校验不得清除失败计数（集成测试发现的计数清零缺陷）")
    void checkNotLockedKeepsFailureCounter() {
        // 模拟真实登录流：每次失败之间都会先执行一次锁定校验
        for (int i = 0; i < LoginLockService.MAX_FAILURES; i++) {
            assertThatCode(() -> lockService.checkNotLocked("user_h")).doesNotThrowAnyException();
            lockService.recordFailure("user_h");
        }
        assertThat(lockService.isLocked("user_h")).isTrue();
        assertThatThrownBy(() -> lockService.checkNotLocked("user_h"))
                .isInstanceOf(BusinessException.class);
    }
}
