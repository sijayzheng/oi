package cn.sijay.oi.sys.model.user;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 创建用户入参模型，字段与表 {@code sys_user} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>username：必填，2~50 位字母/数字/下划线（登录名规范，长度上限对齐 DDL VARCHAR(50)）；</li>
 *   <li>password：必填，6~100 位（BCrypt 哈希后不超过 DDL VARCHAR(100)）；</li>
 *   <li>nickname/mobile/email/remark：选填，长度上限对齐 DDL；email 额外做格式校验；</li>
 *   <li>status：必填，仅接受 NORMAL/DISABLED（{@link CommonStatus}）。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysUserCreateCmd {

    /**
     * 登录名；必填，2~50 位字母/数字/下划线，与 deleted 组合唯一（唯一性由 Service 校验）。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度须在 2~50 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字、下划线")
    private String username;

    /**
     * 登录密码；必填，明文长度 6~100，入库前 BCrypt 加密。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度须在 6~100 位之间")
    private String password;

    /**
     * 用户昵称；选填，最长 50 字符；不传时默认空串。
     */
    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;

    /**
     * 手机号；选填，最长 20 字符（含国际区号写法）。
     */
    @Size(max = 20, message = "手机号最长 20 字符")
    private String mobile;

    /**
     * 邮箱；选填，最长 100 字符，须符合邮箱格式。
     */
    @Size(max = 100, message = "邮箱最长 100 字符")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 账号状态；必填，取值 NORMAL（正常）/ DISABLED（禁用）。
     */
    @NotNull(message = "账号状态不能为空")
    private Boolean enabled;

    /**
     * 备注；选填，最长 255 字符。
     */
    @Size(max = 255, message = "备注最长 255 字符")
    private String remark;
}
