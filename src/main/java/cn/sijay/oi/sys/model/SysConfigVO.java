package cn.sijay.oi.sys.model.config;

import cn.sijay.oi.sys.entity.SysConfig;
import cn.sijay.oi.sys.enums.ConfigValueType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参数配置出参模型（视图对象），字段为 {@code sys_config} 的对外全量子集。
 *
 * <p>输出约定：id 为 {@code Long}，由全局 Jackson 配置序列化为字符串（防前端精度丢失）；
 * 时间字段为 {@link LocalDateTime}，由全局 Jackson 配置序列化为 {@code yyyy-MM-dd HH:mm:ss}；
 * valueType 同时输出枚举名与中文描述（valueTypeDesc）。
 *
 * @author sijay
 */
@Data
public class SysConfigVO {

    /**
     * 配置 id。
     */
    private Long id;

    /**
     * 参数键。
     */
    private String configKey;

    /**
     * 参数值（统一字符串形态）。
     */
    private String configValue;

    /**
     * 值类型；STRING / NUMBER / BOOLEAN / JSON。
     */
    private String valueType;

    /**
     * 值类型中文描述；字符串 / 数字 / 布尔 / 结构化对象。
     */
    private String valueTypeDesc;

    /**
     * 参数名称。
     */
    private String name;

    /**
     * 备注。
     */
    private String remark;

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
     * @param entity 配置实体
     * @return 视图对象
     */
    public static SysConfigVO from(SysConfig entity) {
        SysConfigVO vo = new SysConfigVO();
        vo.setId(entity.getId());
        vo.setConfigKey(entity.getConfigKey());
        vo.setConfigValue(entity.getConfigValue());
        vo.setValueType(entity.getValueType() == null ? null : entity.getValueType().name());
        vo.setValueTypeDesc(entity.getValueType() == null ? null : entity.getValueType().getDesc());
        vo.setName(entity.getName());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
