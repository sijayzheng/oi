package cn.sijay.oi.sys.model.menu;

import cn.sijay.oi.sys.enums.MenuType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建菜单入参模型，字段与表 {@code sys_menu} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>name：必填，最长 50 字符；</li>
 *   <li>type：必填，仅接受 DIRECTORY/MENU/BUTTON（{@link MenuType}）；</li>
 *   <li>parentId：选填，0=根节点；非 0 时父菜单必须存在且类型层级合法
 *       （目录→菜单→按钮，业务校验由 Service 完成）；</li>
 *   <li>path/component：选填，最长 200 字符，按 type 使用（BUTTON 留空）；</li>
 *   <li>perms：选填，最长 100 字符，建议"模块:动作"格式（如 user:add）；</li>
 *   <li>icon：选填，最长 50 字符；</li>
 *   <li>sort：必填，非负整数；</li>
 *   <li>visible/status：必填。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysMenuCreateCmd {

    /**
     * 父菜单 id；选填，0=根节点，不传默认 0；非 0 时须指向已存在的菜单且层级合法。
     */
    @PositiveOrZero(message = "父菜单 id 必须为非负整数（0 表示根节点）")
    private Long parentId;

    /**
     * 菜单/按钮名称；必填，导航与授权树展示用，最长 50 字符。
     */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称最长 50 字符")
    private String name;

    /**
     * 节点类型；必填，取值 DIRECTORY（目录）/ MENU（菜单）/ BUTTON（按钮）。
     */
    @NotNull(message = "菜单类型不能为空")
    private MenuType type;

    /**
     * 前端路由路径；选填，最长 200 字符，DIRECTORY/MENU 使用。
     */
    @Size(max = 200, message = "路由路径最长 200 字符")
    private String path;

    /**
     * 前端组件路径；选填，最长 200 字符，MENU 使用。
     */
    @Size(max = 200, message = "组件路径最长 200 字符")
    private String component;

    /**
     * 权限标识；选填，最长 100 字符，BUTTON 使用（如 user:add）。
     */
    @Size(max = 100, message = "权限标识最长 100 字符")
    private String perms;

    /**
     * 图标标识；选填，最长 50 字符。
     */
    @Size(max = 50, message = "图标标识最长 50 字符")
    private String icon;

    /**
     * 同级排序号；必填，非负整数，越小越靠前。
     */
    @NotNull(message = "排序号不能为 null")
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sort;

    /**
     * 显示状态；必填，true=显示 / false=隐藏（隐藏菜单不下发前端）。
     */
    @NotNull(message = "显示状态不能为空")
    private Boolean visible;

    /**
     * 状态；必填，取值 NORMAL（正常）/ DISABLED（禁用）。
     */
    @NotNull(message = "状态不能为空")
    private Boolean enabled;
}
