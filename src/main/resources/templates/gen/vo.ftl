package cn.sijay.oi.${moduleName}.model.${businessName};

import cn.sijay.oi.${moduleName}.entity.${entityName};
import lombok.Data;

import java.time.LocalDateTime;
<#if hasBigDecimal>
import java.math.BigDecimal;
</#if>

/**
 * ${functionName}出参模型（视图对象），字段为 {@code ${tableName}} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * 时间字段为 {@link LocalDateTime}，由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}。
 *
 * @author ${author}
 */
@Data
public class Sys${className}VO {

    /**
     * ${functionName} id。
     */
    private Long id;

<#list columns as col><#if !col.pk && !col.systemHidden && !col.auditTime>
    /**
     * ${col.label}。
     */
    private ${col.javaType} ${col.javaField};

</#if></#list>
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口，新增对外字段只改此处）。
     *
     * @param entity ${functionName}实体
     * @return 视图对象
     */
    public static Sys${className}VO from(${entityName} entity) {
        Sys${className}VO vo = new Sys${className}VO();
        vo.setId(entity.getId());
<#list columns as col><#if !col.pk && !col.systemHidden && !col.auditTime>
        vo.set${col.javaField?cap_first}(entity.get${col.javaField?cap_first}());
</#if></#list>
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
