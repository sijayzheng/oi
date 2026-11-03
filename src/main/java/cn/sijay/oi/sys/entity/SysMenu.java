package cn.sijay.oi.sys.entity;

import cn.sijay.oi.sys.enums.MenuType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 菜单/权限点实体，映射表 {@code sys_menu}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>树形结构</b>：parent_id=0 为根节点，层级 目录(DIRECTORY) → 菜单(MENU) → 按钮(BUTTON)，
 *       父子类型约束由应用层校验；</li>
 *   <li><b>无 create_by/update_by</b>：DDL 未定义该两列，本实体亦不声明；</li>
 *   <li><b>visible 与 status 独立</b>：visible=false 的节点整棵子树不下发前端
 *       （不渲染、不生成路由、不参与权限计算），status=DISABLED 时整棵子树权限不生效。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_menu")
@SQLRestriction("deleted = 0")
public class SysMenu {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 父菜单 id；0=根节点；树形结构，层级：目录 → 菜单 → 按钮。
     */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = 0L;

    /**
     * 菜单/按钮名称；导航与授权树展示用。
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 节点类型；DIRECTORY=目录 / MENU=菜单 / BUTTON=按钮。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuType type;

    /**
     * 前端路由路径；DIRECTORY/MENU 使用（如 /system/user），BUTTON 留空。
     */
    @Column(length = 200)
    private String path;

    /**
     * 前端组件路径；MENU 使用（指向视图组件），DIRECTORY/BUTTON 留空。
     */
    @Column(length = 200)
    private String component;

    /**
     * 权限标识；BUTTON 使用，格式"模块:动作"（如 user:add），后端接口鉴权依据。
     */
    @Column(length = 100)
    private String perms;

    /**
     * 图标标识；导航栏渲染。
     */
    @Column(length = 50)
    private String icon;

    /**
     * 同级排序号；越小越靠前，默认 0。
     */
    @Column(nullable = false)
    private Integer sort = 0;

    /**
     * 显示状态；true=显示（导航正常渲染），false=隐藏（整棵子树不下发前端：
     * 不渲染、不生成路由、不参与权限计算）。
     */
    @Column(nullable = false)
    private Boolean visible = Boolean.TRUE;

    /**
     * 状态；NORMAL=正常，DISABLED=禁用（整棵子树权限不生效）。
     */
    @Column(nullable = false)
    private Boolean enabled;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 软删标记；0=未删除，删除时=本行主键 id。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
