package cn.sijay.oi.${moduleName}.model.${businessName};

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ${functionName}分页条件查询入参模型，仅承载业务过滤字段。
 *
 * <p>分页与排序参数（pageNum/pageSize/sortField/desc）由独立的
 * {@link cn.sijay.oi.common.model.PageQuery} 接收，Controller 方法签名形如
 * {@code page(@Valid Sys${className}Query query, @Valid PageQuery page)}；
 * 本类字段全部为选填——未设置的字段不参与条件匹配。
 *
 * <p>匹配约定：字符串条件走 {@code ExampleMatcher.StringMatcher.CONTAINING}
 * （忽略大小写）；其余类型精确匹配。
 *
 * @author ${author}
 */
@Data
public class Sys${className}Query {

<#list queryColumns as col>
    /**
     * ${col.label}过滤；<#if col.likeQuery>模糊匹配（包含）<#else>精确匹配</#if>，选填。
     */
<#if col.isString && col.hasMax>
    @Size(max = ${col.maxLength}, message = "${col.label}过滤条件最长 ${col.maxLength} 字符")
</#if>
    private ${col.javaType} ${col.javaField};

</#list>}
