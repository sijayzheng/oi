package cn.sijay.oi.auth.service;

import cn.sijay.oi.common.util.SmUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录密码 SM2 加密密钥管理器。
 *
 * <p><b>职责</b>：生成与轮换登录加密用的 SM2 密钥对，向前端下发公钥（携 keyId
 * 与 SM3 完整性摘要），并在登录请求到达时按 keyId 取出配套私钥解密。
 *
 * <p><b>密钥模型</b>：
 * <ul>
 *   <li>当前密钥（{@link #current}）：有效期 {@value #ROTATE_INTERVAL_MINUTES}
 *       分钟，过期后惰性轮换（请求触发时生成新密钥对），另由定时任务兜底轮换；</li>
 *   <li>在途密钥（{@link #inFlight}）：轮换后旧密钥保留
 *       {@value #IN_FLIGHT_RETENTION_MINUTES} 分钟，覆盖"前端取到旧公钥后请求
 *       尚未到达"的窗口，保证在途登录请求可解密；窗口过期即从内存清除。</li>
 * </ul>
 *
 * <p><b>私钥存储安全约定</b>：私钥仅存在于服务端（可选经配置注入的固定私钥，
 * 供多实例共享/持久化场景；默认内存随机生成），永不进入响应体、日志或传输链路；
 * 公钥本身可公开，下发响应附带 {@code digest = SM3(publicKey)} 供前端校验
 * 传输完整性。
 *
 * @author sijay
 */
@Slf4j
@Component
public class SmLoginKeyManager {

    /**
     * 密钥轮换周期（分钟）；到期即生成新密钥对。
     */
    public static final long ROTATE_INTERVAL_MINUTES = 30;

    /**
     * 在途旧密钥保留时长（分钟）；覆盖公钥下发与登录请求到达之间的网络窗口。
     */
    public static final long IN_FLIGHT_RETENTION_MINUTES = 10;

    /**
     * 当前生效密钥；过期后由 {@link #currentKey()} 惰性轮换。
     */
    private volatile SmKey current;

    /**
     * 在途旧密钥表：keyId → 密钥（轮换后保留一段时间用于解密在途请求）。
     */
    private final Map<String, SmKey> inFlight = new ConcurrentHashMap<>();

    /**
     * 可选的固定私钥（配置注入；多实例共享或持久化场景使用）；为空时内存随机生成。
     */
    @Value("${security.sm2.private-key:}")
    private String configuredPrivateKey;

    /**
     * 获取当前生效密钥；不存在或已过期时先轮换。
     *
     * @return 当前密钥
     */
    public synchronized SmKey currentKey() {
        SmKey key = current;
        if (key == null || key.isExpired()) {
            rotate();
            key = current;
        }
        return key;
    }

    /**
     * 按 keyId 查找可用私钥（当前密钥或在途旧密钥）。
     *
     * @param keyId 密钥标识
     * @return 配套私钥（十六进制）；keyId 未知或已过期返回 null
     */
    public String privateKeyOf(String keyId) {
        SmKey active = currentKey();
        if (active.keyId().equals(keyId)) {
            return active.privateHex();
        }
        SmKey retained = inFlight.get(keyId);
        return retained == null ? null : retained.privateHex();
    }

    /**
     * 轮换密钥：当前密钥转入在途表（限时保留），生成新密钥对成为当前密钥。
     */
    public synchronized void rotate() {
        SmKey old = current;
        if (old != null) {
            Instant retireAt = Instant.now().plus(Duration.ofMinutes(IN_FLIGHT_RETENTION_MINUTES));
            inFlight.put(old.keyId(), new SmKey(old.keyId(), old.publicHex(), old.privateHex(), retireAt));
        }
        String keyId = UUID.randomUUID().toString().replace("-", "");
        SmKey key = createKey(keyId);
        current = key;
        log.info("[SmLoginKeyManager] SM2 登录密钥已轮换: keyId={}, 有效期 {} 分钟", keyId, ROTATE_INTERVAL_MINUTES);
    }

    /**
     * 在途密钥清理：每小时清除超过保留窗口的旧密钥（兜底，避免长期驻留内存）。
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void purgeExpired() {
        Instant now = Instant.now();
        inFlight.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expireAt().isBefore(now);
            if (expired) {
                log.info("[SmLoginKeyManager] 在途密钥已过期清除: keyId={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 生成密钥对；配置了固定私钥时按配置构造（公钥由私钥推算）。
     */
    private SmKey createKey(String keyId) {
        Instant expireAt = Instant.now().plus(Duration.ofMinutes(ROTATE_INTERVAL_MINUTES));
        if (configuredPrivateKey != null && !configuredPrivateKey.isBlank()) {
            String publicHex = SmUtil.publicKeyOfPrivate(configuredPrivateKey.trim());
            return new SmKey(keyId, publicHex, configuredPrivateKey.trim(), expireAt);
        }
        SmUtil.Sm2KeyPair pair = SmUtil.generateSm2KeyPair();
        return new SmKey(keyId, pair.publicHex(), pair.privateHex(), expireAt);
    }

    /**
     * SM2 密钥（keyId / 公钥 hex / 私钥 hex / 过期时间）。
     */
    public record SmKey(String keyId, String publicHex, String privateHex, Instant expireAt) {

        /**
         * 是否已过期。
         */
        public boolean isExpired() {
            return expireAt.isBefore(Instant.now());
        }
    }

    /**
     * 完整性摘要计算：SM3(公钥十六进制串，UTF-8)。
     *
     * @param publicHex 公钥十六进制串
     * @return 64 位十六进制摘要
     */
    public static String digestOf(String publicHex) {
        return SmUtil.bytesToHex(SmUtil.sm3(publicHex.getBytes(StandardCharsets.UTF_8)));
    }
}
