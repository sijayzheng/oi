package cn.sijay.oi.common.model;

/**
 * 下拉选择框选项对象，封装前端 Select/Radio/Checkbox 组件的标准选项结构（record 不可变对象）。
 *
 * <p>标准属性：label（显示文本）、value（提交值）。
 *
 * <p><b>扩展自定义字段</b>：record 为隐式 final，不可继承；需要附加字段时，
 * 建议业务侧定义自有选项 DTO 并组合/转换本类型（Jackson 序列化组合字段即可），示例：
 * <pre>{@code
 * public record UserOption(SelectOption<Long> base, String avatar) {}
 * }</pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * List<SelectOption<Long>> options = statusList.stream()
 *         .map(s -> SelectOption.of(s.getCode(), s.getLabel()))
 *         .toList();
 * return Result.ok(options);
 * }</pre>
 *
 * <p>序列化输出结构：{@code {"value": 1, "label": "正常"}}。
 *
 * @param value 选项提交值；与字典项 item_code / 枚举 code 对应，允许为 null（如占位选项"请选择"）
 * @param label 选项显示文本；面向用户展示（如"正常"、"管理员"），非空由使用方保证
 * @param <T>   选项值类型；常用 Long（枚举编码）、String（字符编码）
 * @author sijay
 */
public record SelectOption<T>(T value, String label) {

    /**
     * 构建选项（value 与 label 两要素）。
     *
     * <p>使用场景：绝大多数下拉构建场景；与直接构造器等价，链路更直观。
     *
     * @param value 选项提交值
     * @param label 选项显示文本
     * @param <T>   选项值类型
     * @return 选项对象
     */
    public static <T> SelectOption<T> of(T value, String label) {
        return new SelectOption<>(value, label);
    }
}
