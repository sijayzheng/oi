package cn.sijay.oi.common.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Stream 流处理通用工具类，封装集合处理的高频 Stream 操作为静态方法。
 *
 * <p>设计约定（遵循阿里巴巴 Java 开发规范）：
 * <ul>
 *   <li><b>空安全</b>：集合类入参允许为 null（标注 {@code @Nullable}），一律返回对应类型的
 *       空集合或默认值（0），绝不抛 NullPointerException；</li>
 *   <li><b>函数参数非空</b>：提取器/转换器函数参数（标注 {@code @NonNull}）不允许为 null，
 *       违规抛 NullPointerException；</li>
 *   <li><b>无副作用</b>：所有方法不修改入参集合，返回值均为新集合；</li>
 *   <li><b>类型安全</b>：全量泛型化，调用方无需强制类型转换。</li>
 * </ul>
 *
 * <p>使用方式：静态导入后直接调用——
 * <pre>{@code
 * import static cn.sijay.oi.common.util.StreamUtils.*;
 *
 * Map<Long, String> nameById = toMap(users, User::getId, User::getUsername);
 * Map<String, List<User>> byCity = groupBy(users, User::getCity);
 * List<User> distinct = distinctBy(users, User::getIdCard);
 * }</pre>
 *
 * @author sijay
 */
public final class StreamUtils {

    /**
     * 私有构造方法；工具类禁止实例化。
     */
    private StreamUtils() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    /**
     * 集合转 Map（键提取器 + 值提取器）。
     *
     * <p>使用场景：按 ID 建立实体索引、编码-文案映射等（如
     * {@code toMap(users, User::getId, User::getUsername)}）。
     *
     * <p>注意事项：键重复时<b>后出现者覆盖先出现者</b>（如需保留首个或合并语义，请先去重）；
     * 键/值提取结果允许为 null（LinkedHashMap 可容纳 null 键值）。
     *
     * @param list   源集合；允许为 null（返回空 Map）
     * @param keyFn  键提取函数；不允许为 null
     * @param valueFn 值提取函数；不允许为 null
     * @param <T>    源元素类型
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 保持插入顺序的 Map；空输入返回空 Map
     */
    public static <T, K, V> Map<K, V> toMap(@Nullable Collection<T> list,
                                            @NonNull Function<T, K> keyFn,
                                            @NonNull Function<T, V> valueFn) {
        Objects.requireNonNull(keyFn, "keyFn 不允许为 null");
        Objects.requireNonNull(valueFn, "valueFn 不允许为 null");
        if (isEmpty(list)) {
            return new LinkedHashMap<>();
        }
        return list.stream().collect(Collectors.toMap(keyFn, valueFn, (first, second) -> second, LinkedHashMap::new));
    }

    /**
     * 按指定字段分组。
     *
     * <p>使用场景：按状态/类型/归属维度归类列表
     * （如 {@code groupBy(users, User::getStatus)}）。
     *
     * <p>注意事项：分组键为 null 的元素归入 null 键组；结果 Map 与组内列表均可变，
     * 修改返回值不影响入参集合。
     *
     * @param list  源集合；允许为 null（返回空 Map）
     * @param keyFn 分组键提取函数；不允许为 null
     * @param <T>   源元素类型
     * @param <K>   分组键类型
     * @return 键到元素列表的分组 Map（保持首次出现顺序）；空输入返回空 Map
     */
    public static <T, K> Map<K, List<T>> groupBy(@Nullable Collection<T> list,
                                                 @NonNull Function<T, K> keyFn) {
        Objects.requireNonNull(keyFn, "keyFn 不允许为 null");
        if (isEmpty(list)) {
            return new LinkedHashMap<>();
        }
        return list.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * 按指定字段分组并计数（分组聚合）。
     *
     * <p>使用场景：统计各状态/类型的数量分布（如各状态用户数）。
     *
     * @param list  源集合；允许为 null（返回空 Map）
     * @param keyFn 分组键提取函数；不允许为 null
     * @param <T>   源元素类型
     * @param <K>   分组键类型
     * @return 键到计数的 Map（保持首次出现顺序）；空输入返回空 Map
     */
    public static <T, K> Map<K, Long> countBy(@Nullable Collection<T> list,
                                              @NonNull Function<T, K> keyFn) {
        Objects.requireNonNull(keyFn, "keyFn 不允许为 null");
        if (isEmpty(list)) {
            return new LinkedHashMap<>();
        }
        return list.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.counting()));
    }

    /**
     * 过滤集合中的 null 元素。
     *
     * <p>使用场景：数据库查询、外部接口返回的列表可能含 null 元素，先过滤再做流处理。
     *
     * @param list 源集合；允许为 null（返回空 List），集合内的 null 元素将被剔除
     * @param <T>  元素类型
     * @return 不含 null 元素的新 List（保持原顺序）；空输入返回空 List
     */
    public static <T> List<T> filterNonNull(@Nullable Collection<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 按指定字段去重（保留首个出现者，顺序不变）。
     *
     * <p>使用场景：多表关联或合并数据源后按业务键（如身份证号、编码）去重。
     *
     * <p>注意事项：与 {@code Stream.distinct()} 不同，本方法按提取键去重而非整对象相等；
     * 键为 null 的元素视为相同键，仅保留首个。
     *
     * @param list  源集合；允许为 null（返回空 List）
     * @param keyFn 去重键提取函数；不允许为 null
     * @param <T>   元素类型
     * @param <K>   去重键类型
     * @return 按键去重后的新 List（保留原相对顺序）；空输入返回空 List
     */
    public static <T, K> List<T> distinctBy(@Nullable Collection<T> list,
                                            @NonNull Function<T, K> keyFn) {
        Objects.requireNonNull(keyFn, "keyFn 不允许为 null");
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        Set<K> seen = new HashSet<>();
        return list.stream().filter(item -> seen.add(keyFn.apply(item))).collect(Collectors.toList());
    }

    /**
     * 对数值字段求和（统计计算）。
     *
     * <p>使用场景：金额、数量等 long/int 字段的列表汇总（int 字段经方法引用自动适配）。
     *
     * @param list    源集合；允许为 null（返回 0）
     * @param valueFn 数值提取函数；不允许为 null
     * @param <T>     元素类型
     * @return 求和结果；空输入返回 0。注意：null 元素会被跳过（提取函数不会收到 null 入参）
     */
    public static <T> long sumBy(@Nullable Collection<T> list,
                                 @NonNull ToLongFunction<T> valueFn) {
        Objects.requireNonNull(valueFn, "valueFn 不允许为 null");
        if (isEmpty(list)) {
            return 0L;
        }
        return list.stream().filter(Objects::nonNull).mapToLong(valueFn).sum();
    }

    /**
     * 集合转换（映射为另一种类型的新 List）。
     *
     * <p>使用场景：Entity → VO 转换、字段裁剪等（如
     * {@code map(users, SysUserVO::from)}）。
     *
     * <p>注意事项：null 元素原样保留（映射函数不会收到 null 入参）；如需剔除 null
     * 请先调用 {@link #filterNonNull(Collection)}。
     *
     * @param list   源集合；允许为 null（返回空 List）
     * @param mapper 转换函数；不允许为 null
     * @param <T>    源元素类型
     * @param <R>    目标元素类型
     * @return 转换后的新 List（保持原顺序）；空输入返回空 List
     */
    public static <T, R> List<R> map(@Nullable Collection<T> list,
                                     @NonNull Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper 不允许为 null");
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().filter(Objects::nonNull).map(mapper).collect(Collectors.toList());
    }

    /**
     * 分页截取（内存分页，取指定页的数据切片）。
     *
     * <p>使用场景：已全量加载的小数据集（如配置表、字典项）在内存中切片分页返回，
     * 或对聚合计算结果分页展示；数据库大数据量分页请使用 SQL 分页，禁止全表加载后调用本方法。
     *
     * <p>注意事项：页码从 1 开始；请求页超出范围（如页码过大）返回空 List；
     * 返回值为<b>独立副本</b>（非原列表视图），修改返回值不影响入参。
     *
     * @param list     源列表；允许为 null（参数合法时返回空 List）
     * @param pageNum  页码；从 1 开始，小于 1 抛 IllegalArgumentException
     * @param pageSize 每页条数；小于 1 抛 IllegalArgumentException
     * @param <T>      元素类型
     * @return 该页数据的独立副本 List；空输入或页码越界返回空 List
     * @throws IllegalArgumentException pageNum 或 pageSize 小于 1 时抛出
     */
    public static <T> List<T> page(@Nullable List<T> list, int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new IllegalArgumentException("pageNum 必须从 1 开始，实际值: " + pageNum);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize 必须大于 0，实际值: " + pageSize);
        }
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        int fromIndex = (pageNum - 1) * pageSize;
        if (fromIndex >= list.size()) {
            return new ArrayList<>();
        }
        int toIndex = Math.min(fromIndex + pageSize, list.size());
        return new ArrayList<>(list.subList(fromIndex, toIndex));
    }

    /**
     * 判断集合是否为空（null 或无元素）。
     *
     * <p>使用场景：本工具类内部的空安全判断基础；亦可公开供业务代码复用，
     * 语义等价于 {@code list == null || list.isEmpty()}。
     *
     * @param collection 待判断集合；允许为 null
     * @return null 或空集合时返回 true
     */
    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
}
