package cn.sijay.oi.sys.model.dict;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新字典项入参模型；dictType/itemCode 不可通过本方法修改
 * （itemCode 为业务实际存储值，修改会导致历史数据无法翻译）。
 *
 * <p>itemLabel/sort/status 为必填（显式提交完整档案，防止遗漏字段导致语义漂移）。
 *
 * @author sijay
 */
@Data
public class SysDictItemUpdateCmd {

    /**
     * 项显示名；必填，最长 100 字符。
     */
    @NotNull(message = "项显示名不能为 null")
    @Size(max = 100, message = "项显示名最长 100 字符")
    private String itemLabel;

    /**
     * 排序号；必填，非负整数。
     */
    @NotNull(message = "排序号不能为 null")
    @jakarta.validation.constraints.Min(value = 0, message = "排序号不能为负数")
    private Integer sort;

    /**
     * 项状态；必填，取值 NORMAL（正常）/ DISABLED（禁用）。
     */
    @NotNull(message = "项状态不能为空")
    private Boolean enabled;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
