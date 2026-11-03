package cn.sijay.oi.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录入参模型。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：仅校验非空与长度上限——
 * 用户名是否存在、密码是否正确、账号是否禁用等<b>业务校验</b>由
 * {@code AuthService} 完成（用户名或密码错误统一返回 601003，不区分具体原因，
 * 防止账号枚举探测）。
 *
 * @author sijay
 */
@Data
public class SysLoginCmd {

    /**
     * 登录名；必填，长度上限对齐 DDL VARCHAR(50)。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名最长 50 字符")
    private String username;

    /**
     * 登录密码；必填，明文传输（HTTPS 保障），长度上限对齐创建入参约定。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 100, message = "密码最长 100 字符")
    private String password;
}
