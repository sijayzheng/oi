package cn.sijay.oi.sys.entity;

import cn.sijay.oi.sys.enums.ConfigValueType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 参数配置实体，映射表 {@code sys_config}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>唯一约束</b>：数据库层 {@code UNIQUE (config_key, deleted)}，软删后原参数键可重建；</li>
 *   <li><b>config_key</b>：点分命名风格（如 site.name），代码中以常量引用，创建后禁止修改；</li>
 *   <li><b>config_value</b>：统一按字符串存储，按 valueType 解析为实际类型。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_config")
@SQLRestriction("deleted = 0")
public class SysConfig {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 参数键；点分命名风格（如 site.name），与 deleted 组合唯一，创建后禁止修改。
     */
    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    /**
     * 参数值；统一按字符串存储，按 valueType 解析，最长 2000 字符。
     */
    @Column(name = "config_value", nullable = false, length = 2000)
    private String configValue = "";

    /**
     * 值类型；决定解析方式与前端编辑控件。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ConfigValueType valueType = ConfigValueType.STRING;

    /**
     * 参数名称；管理界面展示用（如"站点名称"）。
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 备注；说明该参数的作用与取值建议。
     */
    @Column(length = 255)
    private String remark;

    /**
     * 创建人用户 id；0 或 NULL=系统初始化；审计字段，应用层写入。
     */
    @Column(name = "create_by")
    private Long createBy;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 最后修改人用户 id；审计字段。
     */
    @Column(name = "update_by")
    private Long updateBy;

    /**
     * 更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_config_key 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
