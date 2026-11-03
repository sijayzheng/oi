package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 字典类型实体，映射表 {@code sys_dict_type}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>唯一约束</b>：数据库层 {@code UNIQUE (dict_type, deleted)}，软删后原字典编码可重建；</li>
 *   <li><b>dict_type</b>：小写下划线命名（如 user_status），被 sys_dict_item.dict_type
 *       冗余引用，修改前须同步字典项——本模块禁止通过更新接口修改 dictType；</li>
 *   <li><b>级联</b>：删除字典类型时由应用层级联软删其全部字典项。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_dict_type")
@SQLRestriction("deleted = 0")
public class SysDictType {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 字典编码；小写下划线命名（如 user_status），与 deleted 组合唯一，
     * 被 sys_dict_item.dict_type 冗余引用。
     */
    @Column(name = "dict_type", nullable = false, length = 50)
    private String dictType;

    /**
     * 字典名称；如"用户状态"。
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 状态；NORMAL=正常，DISABLED=禁用（其字典项不对外提供）。
     */
    @Column(nullable = false)
    private Boolean enabled;

    /**
     * 备注。
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
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_dict_type_code 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
