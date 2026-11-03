package cn.sijay.oi.sys.model.dict;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典类型入参模型；dictType 不可通过本方法修改
 * （DDL 约定：被 sys_dict_item.dict_type 冗余引用，修改前须同步字典项——为一致性直接禁止）。
 *
 * <p>name/status 为必填（显式提交完整档案，防止遗漏字段导致语义漂移）。
 *
 * @author sijay
 */
@Data
public class SysDictTypeUpdateCmd {

    /**
     * 字典名称；必填，最长 100 字符。
     */
    @NotNull(message = "字典名称不能为 null")
    @Size(max = 100, message = "字典名称最长 100 字符")
    private String name;

    /**
     * 状态；必填，取值 NORMAL（正常）/ DISABLED（禁用）。
     */
    @NotNull(message = "状态不能为空")
    private Boolean enabled;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
