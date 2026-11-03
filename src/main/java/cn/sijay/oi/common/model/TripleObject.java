package cn.sijay.oi.common.model;

/**
 * 通用三元对象，三属性元组结构（record 不可变对象）。
 *
 * <p><b>字段语义约定</b>（按位置依次表达）：
 * <ul>
 *   <li>{@code first}：主数据——主体标识或核心值（如实体 ID、编码）；</li>
 *   <li>{@code second}：关联数据——与主体绑定的次级值（如名称、数量、状态）；</li>
 *   <li>{@code third}：补充数据——附加信息（如扩展标记、统计值），允许为 null。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // (用户ID, 用户名, 在职状态)
 * TripleObject<Long, String, Boolean> triple = TripleObject.of(1L, "sijay", true);
 * }</pre>
 *
 * <p>扩展性：三个位置均为泛型，可组合任意业务类型；仅两维时请使用 {@link KeyValuePair}。
 * 序列化输出结构：{@code {"first": ..., "second": ..., "third": ...}}。
 *
 * @param first  第一位：主数据；主体标识或核心值
 * @param second 第二位：关联数据；与主体绑定的次级值
 * @param third  第三位：补充数据；附加信息，允许为 null
 * @param <A>    第一位（主数据）类型
 * @param <B>    第二位（关联数据）类型
 * @param <C>    第三位（补充数据）类型
 * @author sijay
 */
public record TripleObject<A, B, C>(A first, B second, C third) {

    /**
     * 构建三元组（静态工厂，与直接构造器等价，链路更直观）。
     *
     * @param first  第一位（主数据）
     * @param second 第二位（关联数据）
     * @param third  第三位（补充数据，允许 null）
     * @param <A>    第一位（主数据）类型
     * @param <B>    第二位（关联数据）类型
     * @param <C>    第三位（补充数据）类型
     * @return 三元组
     */
    public static <A, B, C> TripleObject<A, B, C> of(A first, B second, C third) {
        return new TripleObject<>(first, second, third);
    }
}
