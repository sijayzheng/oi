package cn.sijay.oi.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析工具类（final 修饰 + 私有构造，禁止实例化）。
 *
 * <p>解析优先级：{@code X-Forwarded-For}（取首个，兼容多级代理逗号分隔写法）→
 * {@code X-Real-IP} → {@code request.getRemoteAddr()}；IPv6 回环地址
 * （0:0:0:0:0:0:0:1）归一化为 127.0.0.1 便于日志阅读。
 *
 * <p>使用场景：登录 IP 记录（在线用户展示）与操作日志溯源。
 *
 * @author sijay
 */
public final class IpUtils {

    /**
     * IPv6 回环地址；归一化为 IPv4 回环写法。
     */
    private static final String IPV6_LOCALHOST = "0:0:0:0:0:0:0:1";

    private IpUtils() {
    }

    /**
     * 从请求中解析客户端真实 IP。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端 IP；无法解析时返回空串
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = firstNonBlank(request.getHeader("X-Forwarded-For"), request.getHeader("X-Real-IP"));
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 多级代理时取首个（最初客户端）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (IPV6_LOCALHOST.equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip == null ? "" : ip;
    }

    /**
     * 返回首个非空白参数；全部为空白时返回 null。
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
