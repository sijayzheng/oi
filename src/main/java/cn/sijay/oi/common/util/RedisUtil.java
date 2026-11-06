package cn.sijay.oi.common.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis 通用操作工具类，基于 Redisson 客户端封装字符串、哈希、列表、集合、
 * 有序集合的基础 CRUD、过期时间管理与分布式锁能力。
 *
 * <p><b>数据结构映射</b>：Redisson 以分布式对象为抽象，本类各方法均直接操作
 * 对应的 Redis 原生结构——
 * <pre>
 * String   → RBucket           （{@code getBucket}）
 * Hash     → RMap              （{@code getMap}）
 * List     → RList             （{@code getList}）
 * Set      → RSet              （{@code getSet}）
 * ZSet     → RScoredSortedSet  （{@code getScoredSortedSet}）
 * 计数器   → RAtomicLong       （{@code getAtomicLong}）
 * 分布式锁 → RLock             （{@code getLock}）
 * </pre>
 *
 * <p><b>空值约定</b>：所有 key 参数必须非空白，写入类方法的 value/field 必须非 null，
 * 违反时抛 {@link IllegalArgumentException}，避免产生误导性的 Redis 键值与运行时
 * 序列化异常；读取不存在的 key 统一返回 {@code null}（集合类返回空集合）。
 *
 * <p><b>分布式锁约定</b>：{@link #executeWithLock} 以 try-finally 保证解锁，
 * 解锁前校验 {@code isHeldByCurrentThread()}；指定持有时间（leaseTime）时
 * Redisson 看门狗不生效，业务必须在持有时间内完成，否则锁会自动释放。
 *
 * <p>使用示例：
 * <pre>{@code
 * redisUtil.set("oi:config:global", config, 30, TimeUnit.MINUTES);
 * Config c = redisUtil.get("oi:config:global", Config.class);
 * redisUtil.executeWithLock("lock:order:" + orderId, 10, 30, TimeUnit.SECONDS, () -> doPay(orderId));
 * }</pre>
 *
 * @author sijay
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    /**
     * Redisson 客户端；由 redisson-spring-boot-starter 依据 spring.data.redis 自动装配。
     */
    private final RedissonClient redissonClient;

    // ==================== 通用 Key 操作 ====================

    /**
     * 判断 key 是否存在。
     *
     * @param key 键；不可空白
     * @return 存在返回 true
     */
    public boolean hasKey(String key) {
        return redissonClient.getBucket(requireKey(key)).isExists();
    }

    /**
     * 批量删除 key（不存在时忽略）。
     *
     * @param keys 键列表；不可为空
     * @return 实际删除的数量
     */
    public long delete(String... keys) {
        Assert.notEmpty(keys, "待删除的 Redis key 不能为空");
        Arrays.stream(keys).forEach(RedisUtil::requireKey);
        return redissonClient.getKeys().delete(keys);
    }

    /**
     * 设置 key 的过期时间。
     *
     * @param key     键；不可空白
     * @param timeout 过期时长；必须大于 0
     * @param unit    时间单位
     * @return key 存在且设置成功返回 true
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Assert.isTrue(timeout > 0, "过期时长必须大于 0");
        Assert.notNull(unit, "时间单位不能为空");
        return redissonClient.getBucket(requireKey(key)).expire(timeout, unit);
    }

    /**
     * 获取 key 的剩余存活时间。
     *
     * @param key 键；不可空白
     * @param unit 时间单位
     * @return 剩余时长；-1 表示未设置过期，-2 表示 key 不存在
     */
    public long getExpire(String key, TimeUnit unit) {
        Assert.notNull(unit, "时间单位不能为空");
        long remainMillis = redissonClient.getBucket(requireKey(key)).remainTimeToLive();
        return remainMillis < 0 ? remainMillis : unit.convert(remainMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 移除 key 的过期时间，使其永久有效。
     *
     * @param key 键；不可空白
     * @return 成功移除返回 true
     */
    public boolean persist(String key) {
        return redissonClient.getBucket(requireKey(key)).clearExpire();
    }

    /**
     * 按模式匹配 key（如 {@code oi:user:*}）。
     *
     * <p>注意：模式匹配基于 SCAN 遍历，键数量极大时请限制匹配范围。
     *
     * @param pattern 匹配模式；不可空白
     * @return 命中的 key 集合；无命中返回空集合
     */
    public Set<String> keys(String pattern) {
        Assert.hasText(pattern, "Redis key 匹配模式不能为空");
        Set<String> result = new LinkedHashSet<>();
        redissonClient.getKeys().getKeysByPattern(pattern).forEach(result::add);
        return result;
    }

    // ==================== String（字符串） ====================

    /**
     * 写入字符串值（无过期时间）。
     *
     * <p>使用方需自行评估 TTL；长期缓存的数据建议使用带过期时间的重载方法，
     * 避免键无限堆积占用内存。
     *
     * @param key   键；不可空白
     * @param value 值；不可为 null
     */
    public void set(String key, Object value) {
        redissonClient.getBucket(requireKey(key)).set(requireValue(value));
    }

    /**
     * 写入字符串值并设置过期时间。
     *
     * @param key     键；不可空白
     * @param value   值；不可为 null
     * @param timeout 过期时长；必须大于 0
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        Assert.isTrue(timeout > 0, "过期时长必须大于 0");
        Assert.notNull(unit, "时间单位不能为空");
        redissonClient.getBucket(requireKey(key)).set(requireValue(value), timeout, unit);
    }

    /**
     * 读取字符串值。
     *
     * @param key 键；不可空白
     * @return 值；key 不存在返回 null
     */
    public Object get(String key) {
        return redissonClient.getBucket(requireKey(key)).get();
    }

    /**
     * 按指定类型读取字符串值。
     *
     * @param key  键；不可空白
     * @param type 期望类型；不可为 null
     * @param <T>  返回类型
     * @return 值；key 不存在返回 null
     * @throws IllegalStateException 实际存储类型与期望类型不一致
     */
    public <T> T get(String key, Class<T> type) {
        Assert.notNull(type, "目标类型不能为空");
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Redis 值类型不匹配: key=" + key
                    + ", expected=" + type.getName() + ", actual=" + value.getClass().getName());
        }
        return type.cast(value);
    }

    /**
     * 读取并删除 key（原子操作）。
     *
     * @param key 键；不可空白
     * @return 被删除的值；key 不存在返回 null
     */
    public Object getAndDelete(String key) {
        return redissonClient.getBucket(requireKey(key)).getAndDelete();
    }

    /**
     * key 不存在时写入（SETNX 语义）。
     *
     * @param key     键；不可空白
     * @param value   值；不可为 null
     * @param timeout 过期时长；必须大于 0
     * @param unit    时间单位
     * @return 写入成功返回 true；key 已存在返回 false
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        Assert.isTrue(timeout > 0, "过期时长必须大于 0");
        Assert.notNull(unit, "时间单位不能为空");
        // Redisson 4.x 的 RBucket#setIfAbsent 仅接受 Duration，不接受 (long, TimeUnit)
        return redissonClient.getBucket(requireKey(key))
                .setIfAbsent(requireValue(value), Duration.of(timeout, unit.toChronoUnit()));
    }

    /**
     * 自增计数（key 不存在时从 0 开始）。
     *
     * @param key  键；不可空白
     * @param delta 增量；可为负数
     * @return 自增后的值
     */
    public long increment(String key, long delta) {
        return atomic(key).addAndGet(delta);
    }

    /**
     * 自减计数。
     *
     * @param key  键；不可空白
     * @param delta 减量；必须大于 0
     * @return 自减后的值
     */
    public long decrement(String key, long delta) {
        Assert.isTrue(delta > 0, "自减量必须大于 0");
        return atomic(key).addAndGet(-delta);
    }

    // ==================== Hash（哈希） ====================

    /**
     * 写入哈希字段。
     *
     * @param key   键；不可空白
     * @param field 字段名；不可空白
     * @param value 字段值；不可为 null
     */
    public void hSet(String key, String field, Object value) {
        hash(key).put(requireField(field), requireValue(value));
    }

    /**
     * 批量写入哈希字段。
     *
     * @param key 键；不可空白
     * @param map 字段映射；不可为空
     */
    public void hSetAll(String key, Map<String, ?> map) {
        Assert.notEmpty(map, "待写入的哈希字段不能为空");
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        map.forEach((field, value) -> data.put(requireField(field), requireValue(value)));
        hash(key).putAll(data);
    }

    /**
     * 读取哈希字段值。
     *
     * @param key   键；不可空白
     * @param field 字段名；不可空白
     * @return 字段值；不存在返回 null
     */
    public Object hGet(String key, String field) {
        return hash(key).get(requireField(field));
    }

    /**
     * 判断哈希字段是否存在。
     *
     * @param key   键；不可空白
     * @param field 字段名；不可空白
     * @return 存在返回 true
     */
    public boolean hExists(String key, String field) {
        return hash(key).containsKey(requireField(field));
    }

    /**
     * 删除一个或多个哈希字段。
     *
     * @param key    键；不可空白
     * @param fields 字段名列表；不可为空
     * @return 实际删除的字段数量
     */
    public long hDelete(String key, String... fields) {
        Assert.notEmpty(fields, "待删除的哈希字段不能为空");
        Arrays.stream(fields).forEach(RedisUtil::requireField);
        RMap<String, Object> map = hash(key);
        long removed = 0;
        for (String field : fields) {
            if (map.remove(field) != null) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * 读取全部哈希字段。
     *
     * @param key 键；不可空白
     * @return 字段映射；key 不存在返回空 Map
     */
    public Map<String, Object> hGetAll(String key) {
        return hash(key).readAllMap();
    }

    /**
     * 读取全部哈希字段名。
     *
     * @param key 键；不可空白
     * @return 字段名集合；key 不存在返回空集合
     */
    public Set<String> hKeys(String key) {
        return hash(key).readAllKeySet();
    }

    /**
     * 获取哈希字段数量。
     *
     * @param key 键；不可空白
     * @return 字段数量；key 不存在返回 0
     */
    public long hSize(String key) {
        return hash(key).size();
    }

    // ==================== List（列表） ====================

    /**
     * 左侧（头部）批量插入列表元素。
     *
     * @param key    键；不可空白
     * @param values 元素列表；不可为空
     * @return 插入后的列表长度
     */
    public long lPush(String key, Object... values) {
        List<Object> data = requireValues(values);
        RList<Object> list = list(key);
        list.addAll(0, data);
        return list.size();
    }

    /**
     * 右侧（尾部）批量追加列表元素。
     *
     * @param key    键；不可空白
     * @param values 元素列表；不可为空
     * @return 追加后的列表长度
     */
    public long rPush(String key, Object... values) {
        RList<Object> list = list(key);
        list.addAll(requireValues(values));
        return list.size();
    }

    /**
     * 弹出列表头部元素。
     *
     * @param key 键；不可空白
     * @return 元素；列表为空或不存在返回 null
     */
    public Object lPop(String key) {
        RList<Object> list = list(key);
        return list.isEmpty() ? null : list.remove(0);
    }

    /**
     * 弹出列表尾部元素。
     *
     * @param key 键；不可空白
     * @return 元素；列表为空或不存在返回 null
     */
    public Object rPop(String key) {
        RList<Object> list = list(key);
        return list.isEmpty() ? null : list.remove(list.size() - 1);
    }

    /**
     * 读取列表区间元素（含首尾索引，按 Redis 语义支持负数索引）。
     *
     * @param key   键；不可空白
     * @param start 起始索引（0 起；-1 表示最后一个）
     * @param end   结束索引（含）
     * @return 区间元素；无数据返回空列表
     */
    public List<Object> lRange(String key, int start, int end) {
        RList<Object> list = list(key);
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.range(start, end);
    }

    /**
     * 获取列表长度。
     *
     * @param key 键；不可空白
     * @return 长度；key 不存在返回 0
     */
    public long lSize(String key) {
        return list(key).size();
    }

    /**
     * 按索引读取列表元素。
     *
     * @param key   键；不可空白
     * @param index 索引（支持负数，-1 表示最后一个）
     * @return 元素；越界或不存在返回 null
     */
    public Object lIndex(String key, int index) {
        RList<Object> list = list(key);
        int size = list.size();
        int actual = index < 0 ? size + index : index;
        if (actual < 0 || actual >= size) {
            return null;
        }
        return list.get(actual);
    }

    // ==================== Set（集合） ====================

    /**
     * 向集合批量添加元素（重复元素自动忽略）。
     *
     * @param key    键；不可空白
     * @param values 元素列表；不可为空
     * @return 实际新增的元素数量
     */
    public long sAdd(String key, Object... values) {
        List<Object> data = requireValues(values);
        RSet<Object> set = set(key);
        long before = set.size();
        set.addAll(data);
        return set.size() - before;
    }

    /**
     * 从集合批量移除元素。
     *
     * @param key    键；不可空白
     * @param values 元素列表；不可为空
     * @return 实际移除的元素数量
     */
    public long sRemove(String key, Object... values) {
        List<Object> data = requireValues(values);
        RSet<Object> set = set(key);
        long before = set.size();
        set.removeAll(data);
        return before - set.size();
    }

    /**
     * 读取集合全部元素。
     *
     * @param key 键；不可空白
     * @return 元素集合；key 不存在返回空集合
     */
    public Set<Object> sMembers(String key) {
        return set(key).readAll();
    }

    /**
     * 获取集合元素数量。
     *
     * @param key 键；不可空白
     * @return 数量；key 不存在返回 0
     */
    public long sSize(String key) {
        return set(key).size();
    }

    /**
     * 判断元素是否属于集合。
     *
     * @param key   键；不可空白
     * @param value 元素；不可为 null
     * @return 属于返回 true
     */
    public boolean sIsMember(String key, Object value) {
        return set(key).contains(requireValue(value));
    }

    // ==================== ZSet（有序集合） ====================

    /**
     * 向有序集合添加元素（已存在则更新分值）。
     *
     * @param key   键；不可空白
     * @param value 元素；不可为 null
     * @param score 分值
     * @return 新增元素返回 true；仅更新分值返回 false
     */
    public boolean zAdd(String key, Object value, double score) {
        return zset(key).add(score, requireValue(value));
    }

    /**
     * 从有序集合批量移除元素。
     *
     * @param key    键；不可空白
     * @param values 元素列表；不可为空
     * @return 实际移除的元素数量
     */
    public long zRemove(String key, Object... values) {
        RScoredSortedSet<Object> zset = zset(key);
        long before = zset.size();
        zset.removeAll(requireValues(values));
        return before - zset.size();
    }

    /**
     * 获取元素分值。
     *
     * @param key   键；不可空白
     * @param value 元素；不可为 null
     * @return 分值；元素不存在返回 null
     */
    public Double zScore(String key, Object value) {
        return zset(key).getScore(requireValue(value));
    }

    /**
     * 获取元素排名（按分值升序，0 起）。
     *
     * @param key   键；不可空白
     * @param value 元素；不可为 null
     * @return 排名；元素不存在返回 null
     */
    public Integer zRank(String key, Object value) {
        return zset(key).rank(requireValue(value));
    }

    /**
     * 按排名区间读取元素（按分值升序，含首尾索引）。
     *
     * @param key   键；不可空白
     * @param start 起始排名
     * @param end   结束排名（含）
     * @return 元素列表；无数据返回空列表
     */
    public Collection<Object> zRange(String key, int start, int end) {
        return zset(key).valueRange(start, end);
    }

    /**
     * 获取有序集合元素数量。
     *
     * @param key 键；不可空白
     * @return 数量；key 不存在返回 0
     */
    public long zSize(String key) {
        return zset(key).size();
    }

    // ==================== 分布式锁 ====================

    /**
     * 尝试获取分布式锁（可重入），不阻塞等待。
     *
     * <p><b>注意</b>：指定持有时间（leaseTime）时 Redisson 看门狗<b>不生效</b>，
     * 业务必须在持有时间内完成；需要自动续期请改用 {@code RLock#lock()}。
     *
     * @param key       锁键；不可空白
     * @param waitTime  最长等待时间（秒）；0 表示不等待
     * @param leaseTime 锁持有时间（秒）；必须大于 0
     * @return 获取成功返回 true；超时返回 false
     * @throws IllegalStateException 等待过程中线程被中断
     */
    public boolean tryLock(String key, long waitTime, long leaseTime) {
        return tryLock(key, waitTime, leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 尝试获取分布式锁（可重入），支持自定义时间单位。
     *
     * @param key       锁键；不可空白
     * @param waitTime  最长等待时间；不可为负
     * @param leaseTime 锁持有时间；必须大于 0
     * @param unit      时间单位
     * @return 获取成功返回 true；超时返回 false
     * @throws IllegalStateException 等待过程中线程被中断
     */
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
        Assert.isTrue(waitTime >= 0, "锁等待时间不能为负");
        Assert.isTrue(leaseTime > 0, "锁持有时间必须大于 0");
        Assert.notNull(unit, "时间单位不能为空");
        RLock lock = redissonClient.getLock(requireKey(key));
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            // 恢复中断标识，交由上层感知（避免吞掉中断信号）
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取分布式锁被中断: key=" + key, e);
        }
    }

    /**
     * 释放分布式锁（仅当前线程持有者有效）。
     *
     * @param key 锁键；不可空白
     */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock(requireKey(key));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 在分布式锁保护下执行有返回值的业务逻辑，获取失败抛异常。
     *
     * <p>实现说明：try-finally 保证锁释放；释放前校验当前线程是否仍持有锁，
     * 避免超时自动释放后误抛 {@code IllegalMonitorStateException}。
     *
     * @param key       锁键；不可空白
     * @param waitTime  最长等待时间
     * @param leaseTime 锁持有时间
     * @param unit      时间单位
     * @param action    业务逻辑；不可为 null
     * @param <T>       返回值类型
     * @return 业务逻辑返回值
     * @throws IllegalStateException 获取锁超时或线程被中断
     */
    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> action) {
        Assert.notNull(action, "分布式锁业务逻辑不能为空");
        if (!tryLock(key, waitTime, leaseTime, unit)) {
            throw new IllegalStateException("获取分布式锁超时: key=" + key);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }

    // ==================== 内部结构与参数校验 ====================

    /**
     * 获取哈希结构。
     *
     * @param key 键
     * @return 分布式哈希对象
     */
    private RMap<String, Object> hash(String key) {
        return redissonClient.getMap(requireKey(key));
    }

    /**
     * 获取列表结构。
     *
     * @param key 键
     * @return 分布式列表对象
     */
    private RList<Object> list(String key) {
        return redissonClient.getList(requireKey(key));
    }

    /**
     * 获取集合结构。
     *
     * @param key 键
     * @return 分布式集合对象
     */
    private RSet<Object> set(String key) {
        return redissonClient.getSet(requireKey(key));
    }

    /**
     * 获取有序集合结构。
     *
     * @param key 键
     * @return 分布式有序集合对象
     */
    private RScoredSortedSet<Object> zset(String key) {
        return redissonClient.getScoredSortedSet(requireKey(key));
    }

    /**
     * 获取计数器结构。
     *
     * @param key 键
     * @return 分布式原子长整型对象
     */
    private RAtomicLong atomic(String key) {
        return redissonClient.getAtomicLong(requireKey(key));
    }

    /**
     * 校验 key 非空白。
     *
     * @param key 键
     * @return 原 key
     */
    private static String requireKey(String key) {
        Assert.hasText(key, "Redis key 不能为空");
        return key;
    }

    /**
     * 校验哈希字段名非空白。
     *
     * @param field 字段名
     * @return 原字段名
     */
    private static String requireField(String field) {
        Assert.hasText(field, "Redis 哈希字段名不能为空");
        return field;
    }

    /**
     * 校验写入值非 null。
     *
     * @param value 值
     * @return 原值
     */
    private static Object requireValue(Object value) {
        Assert.notNull(value, "Redis 写入值不能为空");
        return value;
    }

    /**
     * 校验批量元素列表非空且元素均非 null。
     *
     * @param values 元素列表
     * @return 元素列表
     */
    private static List<Object> requireValues(Object... values) {
        Assert.notEmpty(values, "Redis 元素列表不能为空");
        List<Object> data = new ArrayList<>(values.length);
        for (Object value : values) {
            data.add(Objects.requireNonNull(value, "Redis 元素不能为 null"));
        }
        return data;
    }
}
