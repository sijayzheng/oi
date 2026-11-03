package cn.sijay.oi.${moduleName}.model.${businessName};

import jakarta.validation.constraints.*;
import lombok.Data;
<#if hasBigDecimal>
import java.math.BigDecimal;
</#if>
<#if hasLocalDateTime>
import java.time.LocalDateTime;
</#if>

/**
 * 创建${functionName}入参模型，字段与表 {@code ${tableName}} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>必填列生成 @NotBlank/@NotNull；</li>
 *   <li>字符串长度生成 @Size、格式约束生成 @Pattern；数值范围生成 @Min。</li>
 * </ul>
 *
 * @author ${author}
 */
@Data
public class Sys${className}CreateCmd {

<#list insertColumns as col>
    /**
     * ${col.label}；<#if col.required>必填<#else>选填</#if>。
     */
<#if col.required><#if col.isString>
    @NotBlank(message = "${col.label}不能为空")
<#else>
    @NotNull(message = "${col.label}不能为空")
</#if>
</#if><#if col.isString && col.hasMin && col.hasMax>
    @Size(min = ${col.minLength}, max = ${col.maxLength}, message = "${col.label}长度须在 ${col.minLength}~${col.maxLength} 位之间")
<#elseif col.isString && col.hasMax>
    @Size(max = ${col.maxLength}, message = "${col.label}最长 ${col.maxLength} 字符")
</#if><#if col.hasRegex>
    @Pattern(regexp = "${col.regex}", message = "${col.label}格式不正确")
</#if><#if col.isNumber && col.hasMin>
    @Min(value = ${col.minLength}, message = "${col.label}不能为负数")
</#if>
    private ${col.javaType} ${col.javaField};

</#list>}
