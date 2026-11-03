package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 配置值类型枚举，对应 {@code sys_config.value_type} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名（{@code @Enumerated(EnumType.STRING)}）。
 *
 * <p>使用场景：决定配置值 {@code config_value} 的解析方式与前端编辑控件——
 * STRING 用普通输入框、NUMBER 用数字输入框、BOOLEAN 用开关、JSON 用代码编辑器；
 * 读取方据此将统一按字符串存储的值反序列化为对应类型。
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum ConfigValueType {

    /**
     * 字符串；原样读取，不做类型转换。
     */
    STRING("字符串"),

    /**
     * 数字；读取时解析为数值（Long/Double 由使用方决定），解析失败按默认值兜底。
     */
    NUMBER("数字"),

    /**
     * 布尔；合法取值 true/false（不区分大小写），其余值按 false 处理。
     */
    BOOLEAN("布尔"),

    /**
     * 结构化对象；值为合法 JSON 文本，读取时反序列化为对象/Map。
     */
    JSON("结构化对象"),
    ;

    private final String desc;
}
