package cn.sijay.oi.sys.model.dict;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建字典类型入参模型，字段与表 {@code sys_dict_type} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>dictType：必填，2~50 位小写下划线命名（DDL 约定，如 user_status），
 *       首字符须为小写字母；</li>
 *   <li>name：必填，最长 100 字符；</li>
 *   <li>status：必填，仅接受 NORMAL/DISABLED（{@link CommonStatus}）；</li>
 *   <li>remark：选填，最长 255 字符。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysDictTypeCreateCmd {

    /**
     * 字典编码；必填，小写下划线命名规范（如 user_status），
     * 与 deleted 组合唯一（唯一性由 Service 校验）。
     */
    @NotBlank(message = "字典编码不能为空")
    @Size(min = 2, max = 50, message = "字典编码长度须在 2~50 位之间")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典编码须为小写下划线格式（如 user_status），以小写字母开头，仅含小写字母、数字、下划线")
    private String dictType;

    /**
     * 字典名称；必填，最长 100 字符。
     */
    @NotBlank(message = "字典名称不能为空")
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
