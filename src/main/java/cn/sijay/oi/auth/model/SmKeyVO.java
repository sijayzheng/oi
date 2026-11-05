package cn.sijay.oi.auth.model;

/**
 * SM2 登录加密公钥下发视图对象。
 *
 * <p>公钥为非压缩格式十六进制（04 || x || y，130 位）；digest 为公钥串的
 * SM3 摘要（hex），前端以独立 SM3 实现复算比对，校验传输完整性。
 * 配套私钥仅存于服务端，永不出现在本响应或任何日志中。
 *
 * @param keyId     密钥标识；登录请求须回传以供服务端定位配套私钥
 * @param publicKey 公钥十六进制（非压缩格式）
 * @param digest    公钥的 SM3 完整性摘要（hex）
 * @param expireAt  公钥过期时间（ISO-8601 字符串）；过期前端需重新获取
 * @author sijay
 */
public record SmKeyVO(String keyId, String publicKey, String digest, String expireAt) {
}
