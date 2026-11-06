package cn.sijay.oi.common.util;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoForRedisson;
import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 集成测试：验证 Redisson 客户端可用、{@link RedisUtil} 各数据结构操作正确，
 * 以及 sa-token 的会话数据确实经由 Redisson 写入 Redis。
 *
 * <p><b>环境依赖</b>：测试要求可用的 Redis（连接参数取自 {@code spring.data.redis}，
 * 本地 dev profile 已配置 127.0.0.1:6379 与口令）。测试固定使用 database 15
 * 与独立 key 前缀，避免污染业务数据；每个用例结束后按前缀清理。
 *
 * <p><b>覆盖范围</b>：字符串/哈希/列表/集合/有序集合 CRUD、过期时间、
 * 分布式锁（可重入、跨线程互斥、锁内执行），以及 sa-token 登录令牌落 Redis。
 *
 * @author sijay
 */
@DisplayName("Redis 集成测试（RedisUtil + sa-token 存储）")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:redis;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // 独立 database，避免误删业务库中的键
        "spring.data.redis.database=15"
})
class RedisUtilTest extends AuthenticatedIntegrationTest {

    /**
     * 本次测试实例的 key 前缀；每个用例使用唯一前缀，保证用例间互不影响。
     */
    private final String prefix = "oi:test:redis:" + UUID.randomUUID() + ":";

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 清理本次用例产生的全部键（含分布式锁键）。
     */
    @AfterEach
    void cleanUp() {
        Set<String> keys = redisUtil.keys(prefix + "*");
        if (!keys.isEmpty()) {
            redisUtil.delete(keys.toArray(new String[0]));
        }
    }

    @Test
    @DisplayName("字符串：写入/读取/过期/不存在时写入/自增自减/读取并删除")
    void stringOperations() {
        String key = prefix + "str";
        redisUtil.set(key, "v1");
        assertThat(redisUtil.get(key)).isEqualTo("v1");
        assertThat(redisUtil.get(key, String.class)).isEqualTo("v1");
        assertThat(redisUtil.hasKey(key)).isTrue();

        redisUtil.set(key, "v2", 60, TimeUnit.SECONDS);
        assertThat(redisUtil.get(key)).isEqualTo("v2");
        assertThat(redisUtil.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
        assertThat(redisUtil.persist(key)).isTrue();
        assertThat(redisUtil.getExpire(key, TimeUnit.SECONDS)).isEqualTo(-1L);

        String nx = prefix + "str:nx";
        assertThat(redisUtil.setIfAbsent(nx, "first", 60, TimeUnit.SECONDS)).isTrue();
        assertThat(redisUtil.setIfAbsent(nx, "second", 60, TimeUnit.SECONDS)).isFalse();
        assertThat(redisUtil.get(nx)).isEqualTo("first");

        String counter = prefix + "counter";
        assertThat(redisUtil.increment(counter, 5)).isEqualTo(5L);
        assertThat(redisUtil.decrement(counter, 2)).isEqualTo(3L);

        assertThat(redisUtil.getAndDelete(key)).isEqualTo("v2");
        assertThat(redisUtil.hasKey(key)).isFalse();
        assertThat(redisUtil.get(key)).isNull();
    }

    @Test
    @DisplayName("哈希：字段写入/批量写入/读取/存在性/字段名/数量/删除")
    void hashOperations() {
        String key = prefix + "hash";
        redisUtil.hSet(key, "f1", "v1");
        redisUtil.hSetAll(key, java.util.Map.of("f2", "v2", "f3", "v3"));

        assertThat(redisUtil.hGet(key, "f1")).isEqualTo("v1");
        assertThat(redisUtil.hExists(key, "f2")).isTrue();
        assertThat(redisUtil.hExists(key, "absent")).isFalse();
        assertThat(redisUtil.hKeys(key)).containsExactlyInAnyOrder("f1", "f2", "f3");
        assertThat(redisUtil.hSize(key)).isEqualTo(3L);
        assertThat(redisUtil.hGetAll(key))
                .containsEntry("f1", "v1")
                .containsEntry("f2", "v2")
                .containsEntry("f3", "v3");

        assertThat(redisUtil.hDelete(key, "f1", "f2", "absent")).isEqualTo(2L);
        assertThat(redisUtil.hSize(key)).isEqualTo(1L);
    }

    @Test
    @DisplayName("列表：左右插入/区间读取/索引（含负数）/左右弹出/长度")
    void listOperations() {
        String key = prefix + "list";
        assertThat(redisUtil.rPush(key, "a", "b", "c")).isEqualTo(3L);
        assertThat(redisUtil.lIndex(key, 0)).isEqualTo("a");
        assertThat(redisUtil.lIndex(key, -1)).isEqualTo("c");
        assertThat(redisUtil.lIndex(key, 99)).isNull();
        assertThat(redisUtil.lRange(key, 0, -1)).containsExactly("a", "b", "c");

        assertThat(redisUtil.lPush(key, "z")).isEqualTo(4L);
        assertThat(redisUtil.lPop(key)).isEqualTo("z");
        assertThat(redisUtil.rPop(key)).isEqualTo("c");
        assertThat(redisUtil.lSize(key)).isEqualTo(2L);

        assertThat(redisUtil.lPop(prefix + "list:absent")).isNull();
        assertThat(redisUtil.lRange(prefix + "list:absent", 0, -1)).isEmpty();
    }

    @Test
    @DisplayName("集合：批量新增（去重）/成员判断/移除/全量读取/数量")
    void setOperations() {
        String key = prefix + "set";
        assertThat(redisUtil.sAdd(key, "a", "b", "a")).isEqualTo(2L);
        assertThat(redisUtil.sIsMember(key, "a")).isTrue();
        assertThat(redisUtil.sIsMember(key, "absent")).isFalse();
        assertThat(redisUtil.sMembers(key)).containsExactlyInAnyOrder("a", "b");
        assertThat(redisUtil.sSize(key)).isEqualTo(2L);

        assertThat(redisUtil.sRemove(key, "a", "absent")).isEqualTo(1L);
        assertThat(redisUtil.sSize(key)).isEqualTo(1L);
    }

    @Test
    @DisplayName("有序集合：添加/分值/排名/区间读取/移除/数量")
    void sortedSetOperations() {
        String key = prefix + "zset";
        assertThat(redisUtil.zAdd(key, "a", 1.0)).isTrue();
        assertThat(redisUtil.zAdd(key, "b", 2.0)).isTrue();
        assertThat(redisUtil.zAdd(key, "a", 3.0)).isFalse();

        assertThat(redisUtil.zScore(key, "a")).isEqualTo(3.0);
        assertThat(redisUtil.zScore(key, "absent")).isNull();
        assertThat(redisUtil.zRank(key, "b")).isZero();
        assertThat(redisUtil.zRank(key, "a")).isEqualTo(1);
        assertThat(redisUtil.zRange(key, 0, -1)).containsExactly("b", "a");
        assertThat(redisUtil.zSize(key)).isEqualTo(2L);

        assertThat(redisUtil.zRemove(key, "a", "absent")).isEqualTo(1L);
        assertThat(redisUtil.zSize(key)).isEqualTo(1L);
    }

    @Test
    @DisplayName("分布式锁：可重入、跨线程互斥、锁内执行后自动释放")
    void distributedLock() throws InterruptedException {
        String key = prefix + "lock";

        // 同一线程可重入
        assertThat(redisUtil.tryLock(key, 0, 30)).isTrue();
        assertThat(redisUtil.tryLock(key, 0, 30)).isTrue();

        // 其他线程在锁被持有时获取失败
        AtomicBoolean acquiredByOther = new AtomicBoolean(true);
        Thread other = new Thread(() -> acquiredByOther.set(redisUtil.tryLock(key, 0, 30)));
        other.start();
        other.join();
        assertThat(acquiredByOther).isFalse();

        redisUtil.unlock(key);
        redisUtil.unlock(key);

        // 锁内执行：正常返回并在结束后释放锁
        assertThat(redisUtil.executeWithLock(key, 0, 30, TimeUnit.SECONDS, () -> "done")).isEqualTo("done");
        assertThat(redisUtil.tryLock(key, 0, 30)).isTrue();
        redisUtil.unlock(key);

        // 锁内异常同样释放锁
        assertThatThrownBy(() -> redisUtil.executeWithLock(key, 0, 30, TimeUnit.SECONDS, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(redisUtil.tryLock(key, 0, 30)).isTrue();
        redisUtil.unlock(key);
    }

    @Test
    @DisplayName("sa-token：登录令牌与会话经由 Redisson 写入 Redis")
    void saTokenStoredInRedis() throws Exception {
        // DAO 已被替换为基于 Redisson 的实现
        assertThat(SaManager.getSaTokenDao()).isInstanceOf(SaTokenDaoForRedisson.class);

        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        Set<String> tokenKeys = redisUtil.keys("*" + token + "*");

        assertThat(tokenKeys)
                .as("sa-token 登录令牌应写入 Redis（key 含令牌值）")
                .isNotEmpty();
        assertThat(Arrays.stream(tokenKeys.toArray(new String[0]))
                .anyMatch(k -> k.endsWith(token)))
                .as("应存在以令牌值结尾的登录键")
                .isTrue();

        // 清理本用例创建的令牌相关键（避免影响其他用例/环境）
        redisUtil.delete(tokenKeys.toArray(new String[0]));
    }
}
