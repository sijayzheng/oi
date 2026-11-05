package cn.sijay.oi.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * 用户登录入参模型。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：仅校验非空与长度上限——
 * 用户名是否存在、密码是否正确、账号是否禁用等<b>业务校验</b>由
 * {@code AuthService} 完成（用户名或密码错误统一返回 601003，不区分具体原因，
 * 防止账号枚举探测）。
 *
 * <p><b>密码传输模式（国密 SM2 加密）</b>：前端登录前经
 * {@code GET /auth/sm2/public-key} 获取公钥与 keyId，对明文密码做 SM2 加密
 * （C1C3C2，hex）后以 {@code password} 字段提交并携带 {@code keyId}；后端凭
 * keyId 定位配套私钥解密还原明文后再校验。keyId 缺省时按明文密码兼容处理。
 * 密文字段在任何日志中均不出明文与中间值（{@code password} 排除出 toString）。
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
     * 登录密码；携带 keyId 时为 SM2 密文（C1C3C2 hex），否则为明文（兼容模式）。
     * 长度上限覆盖密文形态；toString 排除，杜绝日志误打印。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 600, message = "密码最长 600 字符")
    @ToString.Exclude
    private String password;

    /**
     * SM2 密钥标识；可选——携带时 password 视为 SM2 密文并按其解密，
     * 缺省时按明文兼容处理。
     */
    @Size(max = 64, message = "密钥标识最长 64 字符")
    private String keyId;
}
