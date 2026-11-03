package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 菜单节点类型枚举，对应 {@code sys_menu.type} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名（{@code @Enumerated(EnumType.STRING)}）。
 *
 * <p>使用场景：菜单树的三级层级结构——目录（仅分组，无路由）→ 菜单（可跳转页面）
 * → 按钮（页面内操作权限点）；创建节点时按本枚举约束父子层级关系。
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum MenuType {

    /**
     * 目录；仅作分组容器，不对应路由页面，path/component 留空，只能作为根节点或目录的子节点。
     */
    DIRECTORY("目录"),

    /**
     * 菜单；对应前端路由页面，path 指向路由路径、component 指向视图组件，父节点须为目录。
     */
    MENU("菜单"),

    /**
     * 按钮；页面内操作权限点（perms 为权限标识），path/component 留空，父节点须为菜单。
     */
    BUTTON("按钮"),
    ;

    private final String desc;
}
