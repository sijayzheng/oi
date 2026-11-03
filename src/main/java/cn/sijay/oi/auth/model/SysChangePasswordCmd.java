package cn.sijay.oi.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码入参（当前登录人自助操作；旧密码必验，新密码含强度校验）。
 *
 * @author sijay
 */
@Data
public class SysChangePasswordCmd {

    /**
     * 原密码；必填，BCrypt 比对失败返回 601005。
     */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /**
     * 新密码；必填，6~100 位且须同时包含字母与数字（强度校验），入库前 BCrypt 加密。
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度须在 6~100 位之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{6,100}$", message = "新密码须同时包含字母和数字")
    private String newPassword;

    /**
     * 确认新密码；必填，须与新密码一致（一致性由 Service 校验）。
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
