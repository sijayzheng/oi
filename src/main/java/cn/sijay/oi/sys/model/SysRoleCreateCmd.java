package cn.sijay.oi.sys.model.role;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建角色入参模型，字段与表 {@code sys_role} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>code：必填，2~50 位字母/数字/下划线/连字符（权限逻辑标识符规范，
 *       长度上限对齐 DDL VARCHAR(50)）；</li>
 *   <li>name：必填，最长 50 字符；</li>
 *   <li>sort：选填，非负整数，不传默认 0；</li>
 *   <li>status：必填，仅接受 NORMAL/DISABLED（{@link CommonStatus}）；</li>
 *   <li>remark：选填，最长 255 字符。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysRoleCreateCmd {

    /**
     * 角色编码；必填，2~50 位，与 deleted 组合唯一（唯一性由 Service 校验）。
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 50, message = "角色编码长度须在 2~50 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "角色编码仅允许字母、数字、下划线、连字符")
    private String code;

    /**
     * 角色显示名；必填，最长 50 字符。
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称最长 50 字符")
    private String name;

    /**
     * 排序号；选填，非负整数，越小越靠前，不传默认 0。
     */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sort;

    /**
     * 角色状态；必填，取值 NORMAL（正常）/ DISABLED（禁用）。
     */
    @NotNull(message = "角色状态不能为空")
    private Boolean enabled;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
