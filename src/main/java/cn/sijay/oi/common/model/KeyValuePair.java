package cn.sijay.oi.common.model;

/**
 * 通用键值对，二元对象结构（record 不可变对象）。
 *
 * <p>使用场景：下拉选项的原始数据对、字典项的编码-文案映射、Map 缺失时的轻量二元组、
 * 接口间传递 (code, value) 类结构等。键与值均为泛型，编译期即约束类型，访问方法天然类型安全。
 *
 * <p>使用示例：
 * <pre>{@code
 * KeyValuePair<Long, String> pair = KeyValuePair.of(1L, "正常");
 * Long key = pair.key();       // 无需强转
 * String value = pair.value(); // 无需强转
 * }</pre>
 *
 * <p>扩展性：泛型参数可组合任意业务类型；如需第三维数据请使用 {@link TripleObject}。
 * 序列化输出结构：{@code {"key": ..., "value": ...}}。
 *
 * @param key   键；业务上通常为编码/ID，非空约束由使用方保证
 * @param value 值；可为任意类型（含 null）
 * @param <K>   键类型
 * @param <V>   值类型
 * @author sijay
 */
public record KeyValuePair<K, V>(K key, V value) {

    /**
     * 构建键值对（静态工厂，与直接构造器等价，链路更直观）。
     *
     * @param key   键
     * @param value 值
     * @param <K>   键类型
     * @param <V>   值类型
     * @return 键值对
     */
    public static <K, V> KeyValuePair<K, V> of(K key, V value) {
        return new KeyValuePair<>(key, value);
    }
}
