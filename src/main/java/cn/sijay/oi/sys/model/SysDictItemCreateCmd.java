package cn.sijay.oi.sys.model.dict;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建字典项入参模型，字段与表 {@code sys_dict_item} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>dictType：必填，须为已存在的字典类型编码（业务校验由 Service 完成）；</li>
 *   <li>itemCode：必填，1~50 位字母/数字/下划线/连字符（如 NORMAL、male）；</li>
 *   <li>itemLabel：必填，最长 100 字符；</li>
 *   <li>sort：选填，非负整数，不传默认 0；</li>
 *   <li>status：必填，仅接受 NORMAL/DISABLED（{@link CommonStatus}）；</li>
 *   <li>remark：选填，最长 255 字符。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysDictItemCreateCmd {

    /**
     * 所属字典编码；必填，须为已存在的字典类型（存在性由 Service 校验）。
     */
    @NotBlank(message = "所属字典编码不能为空")
    @Size(max = 50, message = "所属字典编码最长 50 字符")
    private String dictType;

    /**
     * 项编码；必填，业务实际存储值，同字典内唯一（唯一性由 Service 校验）。
     */
    @NotBlank(message = "项编码不能为空")
    @Size(max = 50, message = "项编码最长 50 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "项编码仅允许字母、数字、下划线、连字符")
    private String itemCode;

    /**
     * 项显示名；必填，前端展示的文案，最长 100 字符。
     */
    @NotBlank(message = "项显示名不能为空")
    @Size(max = 100, message = "项显示名最长 100 字符")
    private String itemLabel;

    /**
     * 排序号；选填，非负整数，越小越靠前，不传默认 0。
     */
    @Min(value = 0, message = "排序号不能为负数")
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
