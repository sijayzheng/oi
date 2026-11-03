package cn.sijay.oi.sys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户-角色关联实体，映射表 {@code sys_user_role}（复合主键 user_id + role_id）。
 *
 * <p>关键约定（与 DDL 对齐）：
 * <ul>
 *   <li>纯关联表：仅两个逻辑外键列，无审计/软删字段；解绑即物理删除行；</li>
 *   <li>逻辑外键：user_id → sys_user.id、role_id → sys_role.id，一致性由应用层保证。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_user_role")
@IdClass(SysUserRole.Pk.class)
public class SysUserRole {

    /**
     * 用户 id；逻辑外键 → sys_user.id。
     */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 角色 id；逻辑外键 → sys_role.id。
     */
    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * 复合主键（user_id, role_id）。
     */
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {

        /**
         * 序列化版本号。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 用户 id。
         */
        private Long userId;

        /**
         * 角色 id。
         */
        private Long roleId;
    }
}
