package cn.sijay.oi.sys.model.config;

import cn.sijay.oi.sys.enums.ConfigValueType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新参数配置入参模型；configKey 不可通过本方法修改
 * （DDL 约定：代码中以常量引用，创建后禁止修改）。
 *
 * <p>configValue/valueType/name 为必填（显式提交完整档案，防止遗漏字段导致语义漂移）。
 *
 * @author sijay
 */
@Data
public class SysConfigUpdateCmd {

    /**
     * 参数值；必填，统一按字符串存储，最长 2000 字符。
     */
    @NotNull(message = "参数值不能为 null")
    @Size(max = 2000, message = "参数值最长 2000 字符")
    private String configValue;

    /**
     * 值类型；必填，取值 STRING / NUMBER / BOOLEAN / JSON。
     */
    @NotNull(message = "值类型不能为空")
    private ConfigValueType valueType;

    /**
     * 参数名称；必填，最长 100 字符。
     */
    @NotNull(message = "参数名称不能为 null")
    @Size(max = 100, message = "参数名称最长 100 字符")
    private String name;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
