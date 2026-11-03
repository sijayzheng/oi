package cn.sijay.oi.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改入参（当前登录人自助更新，status/username/password 不可经此修改）。
 *
 * <p>校验规则与 SysUserCreateCmd 对应字段保持一致。
 *
 * @author sijay
 */
@Data
public class SysProfileUpdateCmd {

    /**
     * 用户昵称；必填，最长 50 字符。
     */
    @NotBlank(message = "昵称不能为空")
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
     * 头像文件 id（字符串形态）；选填（先经 /auth/avatar 上传取得），只存统一文件
     * 管理服务返回的唯一 id，不存 URL 或路径。
     */
    @Size(max = 255, message = "头像文件标识最长 255 字符")
    private String avatar;
}
