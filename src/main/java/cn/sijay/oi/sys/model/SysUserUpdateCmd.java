package cn.sijay.oi.sys.model.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户入参模型，字段与表 {@code sys_user} DDL 定义严格对应。
 *
 * <p>与 {@link SysUserCreateCmd} 的差异（业务约束，见 DDL 注释）：
 * <ul>
 *   <li><b>username 不可修改</b>（登录凭证，创建后不建议修改——DDL 注释约定）；</li>
 *   <li><b>password 不随本接口修改</b>（改密属独立安全操作，应单独提供修改密码接口并校验旧密码）；</li>
 *   <li>nickname/status 为必填：整档案更新语义，避免部分字段被意外清空时调用方无感知。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysUserUpdateCmd {

    /**
     * 用户昵称；必填（传空串表示清空昵称），最长 50 字符。
     */
    @NotNull(message = "昵称不能为 null")
    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;

    /**
     * 手机号；选填，最长 20 字符。
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
