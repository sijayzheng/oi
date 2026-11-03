package cn.sijay.oi.sys.model.role;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 更新角色入参模型；code 不可通过本方法修改（DDL 约定：创建后不建议修改，权限逻辑引用 code）。
 *
 * <p>与创建入参的差异：code 不在更新范围内；name/sort/status 为必填（显式提交完整档案，
 * 防止遗漏字段导致语义漂移）。
 *
 * @author sijay
 */
@Data
public class SysRoleUpdateCmd {

    /**
     * 角色显示名；必填，最长 50 字符。
     */
    @NotNull(message = "角色名称不能为 null")
    @Size(max = 50, message = "角色名称最长 50 字符")
    private String name;

    /**
     * 排序号；必填，非负整数。
     */
    @NotNull(message = "排序号不能为 null")
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
