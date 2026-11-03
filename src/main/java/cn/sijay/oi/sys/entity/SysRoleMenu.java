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
 * 角色-菜单关联实体，映射表 {@code sys_role_menu}（复合主键 role_id + menu_id）。
 *
 * <p>关键约定（与 DDL 对齐）：
 * <ul>
 *   <li>纯关联表：仅两个逻辑外键列，无审计/软删字段；角色授权即增删本表行；</li>
 *   <li>逻辑外键：role_id → sys_role.id、menu_id → sys_menu.id，一致性由应用层保证。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_role_menu")
@IdClass(SysRoleMenu.Pk.class)
public class SysRoleMenu {

    /**
     * 角色 id；逻辑外键 → sys_role.id。
     */
    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * 菜单/权限点 id；逻辑外键 → sys_menu.id。
     */
    @Id
    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    /**
     * 复合主键（role_id, menu_id）。
     */
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {

        /**
         * 序列化版本号。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 角色 id。
         */
        private Long roleId;

        /**
         * 菜单 id。
         */
        private Long menuId;
    }
}
