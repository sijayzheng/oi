package cn.sijay.oi.sys.model.config;

import cn.sijay.oi.sys.enums.ConfigValueType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建参数配置入参模型，字段与表 {@code sys_config} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>configKey：必填，2~100 位点分命名规范（字母/数字/下划线 + 点分隔符，
 *       如 site.name、upload.max-size），长度上限对齐 DDL VARCHAR(100)；</li>
 *   <li>configValue：必填，最长 2000 字符（对齐 DDL VARCHAR(2000)）；</li>
 *   <li>valueType：必填，仅接受 STRING/NUMBER/BOOLEAN/JSON（{@link ConfigValueType}）；</li>
 *   <li>name：必填，最长 100 字符；</li>
 *   <li>remark：选填，最长 255 字符。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysConfigCreateCmd {

    /**
     * 参数键；必填，点分命名规范（字母/数字/下划线/连字符，点号分组），
     * 与 deleted 组合唯一（唯一性由 Service 校验），创建后禁止修改。
     */
    @NotBlank(message = "参数键不能为空")
    @Size(min = 2, max = 100, message = "参数键长度须在 2~100 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*$",
            message = "参数键须为点分命名格式（如 site.name），仅允许字母、数字、下划线、连字符与点号")
    private String configKey;

    /**
     * 参数值；必填，统一按字符串存储，按 valueType 解析，最长 2000 字符。
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
     * 参数名称；必填，管理界面展示用，最长 100 字符。
     */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称最长 100 字符")
    private String name;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
