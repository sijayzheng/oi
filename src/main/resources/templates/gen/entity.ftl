package cn.sijay.oi.${moduleName}.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

<#if hasLocalDateTime>
import java.time.LocalDateTime;
</#if>
<#if hasBigDecimal>
import java.math.BigDecimal;
</#if>

/**
 * ${functionName}实体，映射表 {@code ${tableName}}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 * </ul>
 *
 * @author ${author}
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "${tableName}")
@SQLRestriction("deleted = 0")
public class ${entityName} {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

<#list columns as col><#if !col.pk>
    /**
     * ${col.label}。<#if col.required>必填。</#if>
     */
<#if col.auditTime && col.columnName == 'create_time'>
    @CreationTimestamp
    @Column(name = "${col.columnName}", nullable = false)
<#elseif col.auditTime && col.columnName == 'update_time'>
    @UpdateTimestamp
    @Column(name = "${col.columnName}")
<#else>
    @Column(name = "${col.columnName}"<#if col.required>, nullable = false</#if><#if col.isString && col.hasMax && !col.auditTime>, length = ${col.maxLength}</#if>)
</#if>
    private ${col.javaType} ${col.javaField}<#if col.columnName == 'deleted'> = 0L</#if>;

</#if></#list>}
