package cn.sijay.oi.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功出参模型：向前端返回 sa-token 令牌对。
 *
 * <p>使用约定：前端后续访问受保护接口时，将 {@code tokenValue} 放入名为
 * {@code tokenName} 的请求头（默认 {@code satoken: <tokenValue>}）；
 * 令牌过期/被踢下线后请求返回 401，需重新登录。
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysLoginVO {

    /**
     * 令牌名称；即受保护接口需携带的请求头名（默认 satoken）。
     */
    private String tokenName;

    /**
     * 令牌值；登录凭证，泄露即等于账号被盗用，前端需妥善保管。
     */
    private String tokenValue;

    /**
     * 用户昵称；前端顶栏展示用（nickname 优先于 username 的展示约定）。
     */
    private String nickname;
}
