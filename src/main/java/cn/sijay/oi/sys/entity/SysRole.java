package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 角色实体，映射表 {@code sys_role}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>唯一约束</b>：数据库层 {@code UNIQUE (code, deleted)}，软删后原角色编码可重建；</li>
 *   <li><b>RBAC 枢纽</b>：被 sys_user_role / sys_role_menu 引用，删除角色前应先解绑授权
 *       （应用层保证，删除前校验由后续授权模块接入时补充）；</li>
 *   <li><b>code</b>：创建后不建议修改，权限逻辑以 code 判断（如 admin/user）。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_role")
@SQLRestriction("deleted = 0")
public class SysRole {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色编码；与 deleted 组合唯一，代码中用于权限逻辑，创建后不建议修改。
     */
    @Column(nullable = false, length = 50)
    private String code;

    /**
     * 角色显示名；前端下拉/列表展示用。
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 排序号；同状态内数值越小越靠前，默认 0。
     */
    @Column(nullable = false)
    private Integer sort = 0;

    /**
     * 角色状态；NORMAL=正常，DISABLED=禁用（该角色的权限不再生效）。
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
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_role_code 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
