package cn.sijay.oi.sys.model.menu;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新菜单入参模型；parentId/type 不可通过本方法修改
 * （变更挂载点/类型涉及子树层级重校验，留待专门的移动接口实现，防止更新接口产生环）。
 *
 * <p>name/sort/visible/status 为必填（显式提交完整档案，防止遗漏字段导致语义漂移）。
 *
 * @author sijay
 */
@Data
public class SysMenuUpdateCmd {

    /**
     * 菜单/按钮名称；必填，最长 50 字符。
     */
    @NotNull(message = "菜单名称不能为 null")
    @Size(max = 50, message = "菜单名称最长 50 字符")
    private String name;

    /**
     * 前端路由路径；选填，最长 200 字符。
     */
    @Size(max = 200, message = "路由路径最长 200 字符")
    private String path;

    /**
     * 前端组件路径；选填，最长 200 字符。
     */
    @Size(max = 200, message = "组件路径最长 200 字符")
    private String component;

    /**
     * 权限标识；选填，最长 100 字符。
     */
    @Size(max = 100, message = "权限标识最长 100 字符")
    private String perms;

    /**
     * 图标标识；选填，最长 50 字符。
     */
    @Size(max = 50, message = "图标标识最长 50 字符")
    private String icon;

    /**
     * 同级排序号；必填，非负整数。
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
