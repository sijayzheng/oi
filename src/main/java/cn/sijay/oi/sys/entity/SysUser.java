package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体，映射表 {@code sys_user}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件，
 *       软删行对业务查询不可见；</li>
 *   <li><b>唯一约束</b>：数据库层 {@code UNIQUE (username, deleted)}，软删后原用户名可重建；</li>
 *   <li><b>审计字段</b>：createTime/updateTime 由 Hibernate 时间戳注解维护（与 DB 默认值双保险），
 *       createBy/updateBy 由 Service 层写入（0=系统，后续接入 sa-token 后改为当前登录人）；</li>
 *   <li><b>password</b>：仅存 BCrypt 密文，不对外输出（VO 不映射该字段）。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_user")
@SQLRestriction("deleted = 0")
public class SysUser {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录名；与 deleted 组合唯一，创建后不建议修改。
     */
    @Column(nullable = false, length = 50)
    private String username;

    /**
     * 登录密码密文；BCrypt 哈希（约 60 字符），严禁明文。
     */
    @Column(nullable = false, length = 100)
    private String password;

    /**
     * 用户昵称/显示名；默认空串；前端展示优先级 nickname > username。
     */
    @Column(nullable = false, length = 50)
    private String nickname = "";

    /**
     * 手机号；可用于登录、找回密码、短信通知；最长 20 字符。
     */
    @Column(length = 20)
    private String mobile;

    /**
     * 邮箱；用于通知与找回密码；格式校验由应用层完成。
     */
    @Column(length = 100)
    private String email;

    /**
     * 头像文件 id（字符串形态）；统一文件管理服务（sys_file）的唯一引用，
     * 个人中心维护；展示时经文件服务换取带有效期的临时访问地址。
     */
    @Column(length = 255)
    private String avatar;

    /**
     * 账号状态；NORMAL=正常（可登录），DISABLED=禁用（拒绝登录）。
     */
    @Column(nullable = false)
    private Boolean enabled;

    /**
     * 备注；管理员维护的补充说明，不参与业务逻辑。
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
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_user_username 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
