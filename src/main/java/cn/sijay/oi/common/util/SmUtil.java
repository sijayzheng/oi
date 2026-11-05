package cn.sijay.oi.common.util;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.CipherKeyGenerator;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

/**
 * 国密算法工具类（SM2 / SM3 / SM4），基于 BouncyCastle（bcprov-jdk18on）实现。
 *
 * <p><b>能力清单</b>：
 * <ul>
 *   <li><b>SM3</b>：哈希摘要（GB/T 32905-2016）；</li>
 *   <li><b>SM2</b>：非对称加密/解密、签名/验签、密钥对生成（GB/T 32907 曲线
 *       sm2p256v1；密文格式 C1C3C2；签名默认用户 ID {@code 1234567812345678}）；</li>
 *   <li><b>SM4</b>：对称加解密（ECB/CBC，PKCS#7 填充）与 128 位密钥生成。</li>
 * </ul>
 *
 * <p><b>设计约定</b>：
 * <ul>
 *   <li>纯静态方法 + 私有构造，无任何外部业务耦合，可直接复用于任意模块；</li>
 *   <li>类加载时注册 BouncyCastle 安全提供者（幂等，重复注册无副作用）；</li>
 *   <li>数据格式：字节数组为原生出入参，十六进制字符串经 {@link #bytesToHex}/
 *       {@link #hexToBytes} 转换；密钥/公钥/IV 均以十六进制字符串表达；</li>
 *   <li>错误处理：参数非法（null/空/长度不符/非 hex）抛
 *       {@link IllegalArgumentException}；算法运算失败（密文损坏、密钥不匹配等）
 *       统一包装为 {@link IllegalStateException}，不向上层泄露 BC 内部异常类型。</li>
 * </ul>
 *
 * @author sijay
 */
public final class SmUtil {

    /**
     * SM2 推荐曲线（sm2p256v1）参数。
     */
    private static final X9ECParameters SM2_EC_PARAMS = GMNamedCurves.getByName("sm2p256v1");

    /**
     * SM2 曲线域参数（密钥生成/签名/验签共用）。
     */
    private static final ECDomainParameters SM2_DOMAIN = new ECDomainParameters(
            SM2_EC_PARAMS.getCurve(), SM2_EC_PARAMS.getG(), SM2_EC_PARAMS.getN(), SM2_EC_PARAMS.getH());

    /**
     * SM2 签名默认用户 ID（GM/T 0009 约定的默认值）。
     */
    private static final byte[] SM2_DEFAULT_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    /**
     * SM4 密钥长度（字节）；SM4 分组长度同为 16 字节，CBC 模式 IV 亦为 16 字节。
     */
    private static final int SM4_KEY_LENGTH_BYTES = 16;

    /**
     * 公钥非压缩格式前缀（04 || x || y）。
     */
    private static final int PUBLIC_KEY_UNCOMPRESSED_FLAG = 0x04;

    static {
        registerBouncyCastle();
    }

    /**
     * 私有构造：工具类禁止实例化。
     */
    private SmUtil() {
    }

    /**
     * 注册 BouncyCastle 安全提供者（幂等：已注册时 addProvider 返回 -1，无副作用）。
     */
    public static void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /* ==================== SM3 哈希摘要 ==================== */

    /**
     * 计算 SM3 摘要（字节形式）。
     *
     * @param data 原始数据；不允许为 null
     * @return 32 字节摘要
     * @throws IllegalArgumentException 入参为 null
     */
    public static byte[] sm3(byte[] data) {
        requireNonNull(data, "sm3 摘要入参不能为 null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME);
            return digest.digest(data);
        }
        catch (Exception e) {
            throw new IllegalStateException("SM3 摘要计算失败", e);
        }
    }

    /**
     * 计算 SM3 摘要并转为十六进制字符串。
     *
     * @param data 原始数据；不允许为 null
     * @return 64 位十六进制小写字符串
     */
    public static String sm3Hex(byte[] data) {
        return bytesToHex(sm3(data));
    }

    /**
     * 计算文本的 SM3 摘要（UTF-8 编码），十六进制字符串形式。
     *
     * @param text 原始文本；不允许为 null
     * @return 64 位十六进制小写字符串
     */
    public static String sm3Hex(String text) {
        requireNonNull(text, "sm3 摘要文本不能为 null");
        return sm3Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /* ==================== SM2 非对称 ==================== */

    /**
     * 生成 SM2 密钥对。
     *
     * @return 密钥对；公钥为非压缩格式十六进制（130 位，04 || x || y），私钥为十六进制（64 位）
     */
    public static Sm2KeyPair generateSm2KeyPair() {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(SM2_DOMAIN, new SecureRandom()));
        org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();
        String publicHex = bytesToHex(publicKey.getQ().getEncoded(false));
        // 私钥固定 64 位十六进制（d 数值小于 2^248 时 toString 会丢失前导零，导致密钥格式不稳定）
        String privateHex = String.format("%064x", privateKey.getD());
        return new Sm2KeyPair(publicHex, privateHex);
    }

    /**
     * SM2 加密（密文格式 C1C3C2，字节形式）。
     *
     * @param data        明文字节；不允许为 null
     * @param publicHex   公钥十六进制（非压缩格式，04 || x || y）
     * @return 密文字节
     * @throws IllegalArgumentException 明文为 null、公钥为空或格式非法
     * @throws IllegalStateException    加密运算失败
     */
    public static byte[] sm2Encrypt(byte[] data, String publicHex) {
        requireNonNull(data, "SM2 加密明文不能为 null");
        if (data.length == 0) {
            throw new IllegalArgumentException("SM2 加密明文不能为空");
        }
        ECPublicKeyParameters publicKey = parsePublicKey(publicHex);
        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(true, publicKey);
        try {
            return engine.processBlock(data, 0, data.length);
        }
        catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    /**
     * SM2 解密（密文格式 C1C3C2，字节形式）。
     *
     * @param cipher      密文字节；不允许为 null
     * @param privateHex  私钥十六进制
     * @return 明文字节
     * @throws IllegalArgumentException 密文为 null、私钥为空或格式非法
     * @throws IllegalStateException    密文损坏或私钥不匹配导致解密失败
     */
    public static byte[] sm2Decrypt(byte[] cipher, String privateHex) {
        requireNonNull(cipher, "SM2 解密密文不能为 null");
        ECPrivateKeyParameters privateKey = parsePrivateKey(privateHex);
        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(false, privateKey);
        try {
            return engine.processBlock(cipher, 0, cipher.length);
        }
        catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 解密失败（密文损坏或私钥不匹配）", e);
        }
    }

    /**
     * SM2 签名（SM3withSM2，默认用户 ID，字节形式）。
     *
     * <p>签名输出为 ASN.1 DER 编码（SEQUENCE{r, s}），长度 70 字节左右；
     * {@link #sm2Verify} 可直接验签，无需自行解码。
     *
     * @param data       待签名字节；不允许为 null
     * @param privateHex 私钥十六进制
     * @return 签名字节（DER 编码）
     * @throws IllegalArgumentException 数据为 null、私钥为空或格式非法
     * @throws IllegalStateException    签名运算失败
     */
    public static byte[] sm2Sign(byte[] data, String privateHex) {
        requireNonNull(data, "SM2 签名数据不能为 null");
        ECPrivateKeyParameters privateKey = parsePrivateKey(privateHex);
        SM2Signer signer = new SM2Signer();
        CipherParameters parameters = new org.bouncycastle.crypto.params.ParametersWithID(privateKey, SM2_DEFAULT_ID);
        signer.init(true, parameters);
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        }
        catch (org.bouncycastle.crypto.CryptoException e) {
            throw new IllegalStateException("SM2 签名运算失败", e);
        }
    }

    /**
     * SM2 验签（SM3withSM2，默认用户 ID，字节形式）。
     *
     * @param data      原始数据字节；不允许为 null
     * @param sign      签名字节；不允许为 null
     * @param publicHex 公钥十六进制（非压缩格式）
     * @return 验签通过返回 true
     * @throws IllegalArgumentException 任一入参为 null、密钥为空或格式非法
     * @throws IllegalStateException    签名数据结构非法导致验签无法执行
     */
    public static boolean sm2Verify(byte[] data, byte[] sign, String publicHex) {
        requireNonNull(data, "SM2 验签数据不能为 null");
        requireNonNull(sign, "SM2 签名不能为 null");
        ECPublicKeyParameters publicKey = parsePublicKey(publicHex);
        SM2Signer signer = new SM2Signer();
        CipherParameters parameters = new org.bouncycastle.crypto.params.ParametersWithID(publicKey, SM2_DEFAULT_ID);
        signer.init(false, parameters);
        signer.update(data, 0, data.length);
        try {
            return signer.verifySignature(sign);
        }
        catch (Exception e) {
            // 签名长度/结构非法时 BC 抛运行时异常，结构性非法统一包装为状态异常
            throw new IllegalStateException("SM2 验签执行失败（签名格式非法）", e);
        }
    }

    /* ==================== SM4 对称 ==================== */

    /**
     * SM4 工作模式。
     */
    public enum Sm4Mode {
        /**
         * ECB 模式（无需 IV）。
         */
        ECB,
        /**
         * CBC 模式（需要 16 字节 IV）。
         */
        CBC
    }

    /**
     * 生成 SM4 密钥（128 位）。
     *
     * @return 32 位十六进制小写密钥字符串
     */
    public static String generateSm4KeyHex() {
        CipherKeyGenerator generator = new CipherKeyGenerator();
        generator.init(new KeyGenerationParameters(new SecureRandom(), SM4_KEY_LENGTH_BYTES * 8));
        return bytesToHex(generator.generateKey());
    }

    /**
     * SM4 加密（PKCS#7 填充，字节形式）。
     *
     * @param data 明文字节；不允许为 null
     * @param key  16 字节密钥
     * @param mode 工作模式（ECB/CBC）
     * @param iv   IV；CBC 模式必须为 16 字节，ECB 模式传 null
     * @return 密文字节
     * @throws IllegalArgumentException 明文/密钥非法、模式为 null、CBC 缺 IV 或 IV 长度不符
     * @throws IllegalStateException    加密运算失败
     */
    public static byte[] sm4Encrypt(byte[] data, byte[] key, Sm4Mode mode, byte[] iv) {
        return sm4Process(true, data, key, mode, iv);
    }

    /**
     * SM4 解密（PKCS#7 填充，字节形式）。
     *
     * @param cipher 密文字节；不允许为 null
     * @param key    16 字节密钥
     * @param mode   工作模式（ECB/CBC）
     * @param iv     IV；CBC 模式必须为 16 字节，ECB 模式传 null
     * @return 明文字节
     * @throws IllegalArgumentException 密文/密钥非法、模式为 null、CBC 缺 IV 或 IV 长度不符
     * @throws IllegalStateException    密文损坏或填充非法导致解密失败
     */
    public static byte[] sm4Decrypt(byte[] cipher, byte[] key, Sm4Mode mode, byte[] iv) {
        return sm4Process(false, cipher, key, mode, iv);
    }

    /**
     * SM4 加解密统一入口。
     */
    private static byte[] sm4Process(boolean encrypt, byte[] data, byte[] key, Sm4Mode mode, byte[] iv) {
        requireNonNull(data, encrypt ? "SM4 加密明文不能为 null" : "SM4 解密密文不能为 null");
        requireNonNull(mode, "SM4 工作模式不能为 null");
        requireNonNull(key, "SM4 密钥不能为 null");
        if (key.length != SM4_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("SM4 密钥长度必须为 16 字节（128 位），实际 " + key.length + " 字节");
        }
        org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher cipher = createSm4Cipher(mode, iv);
        cipher.init(encrypt, sm4Parameters(key, mode, iv));
        try {
            return processBlock(cipher, data);
        }
        catch (org.bouncycastle.crypto.InvalidCipherTextException e) {
            throw new IllegalStateException("SM4 " + (encrypt ? "加密" : "解密") + "失败（密文损坏或填充非法）", e);
        }
    }

    /**
     * 构造 SM4 分组密码器（ECB 直用 SM4Engine，CBC 包装 CBCBlockCipher；统一 PKCS#7 填充）。
     */
    private static org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher createSm4Cipher(Sm4Mode mode, byte[] iv) {
        org.bouncycastle.crypto.BlockCipher engine = new SM4Engine();
        org.bouncycastle.crypto.BlockCipher blockCipher = mode == Sm4Mode.CBC
                ? new org.bouncycastle.crypto.modes.CBCBlockCipher(engine)
                : engine;
        return new org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher(
                blockCipher, new org.bouncycastle.crypto.paddings.PKCS7Padding());
    }

    /**
     * 组装 SM4 密钥参数（CBC 模式附加 IV 并校验长度）。
     */
    private static CipherParameters sm4Parameters(byte[] key, Sm4Mode mode, byte[] iv) {
        KeyParameter keyParameter = new KeyParameter(key);
        if (mode == Sm4Mode.CBC) {
            requireNonNull(iv, "SM4 CBC 模式必须提供 IV");
            if (iv.length != SM4_KEY_LENGTH_BYTES) {
                throw new IllegalArgumentException("SM4 CBC 模式 IV 长度必须为 16 字节，实际 " + iv.length + " 字节");
            }
            return new ParametersWithIV(keyParameter, iv);
        }
        return keyParameter;
    }

    /* ==================== 十六进制编解码 ==================== */

    /**
     * 字节转十六进制字符串（小写）。
     *
     * @param bytes 字节；不允许为 null
     * @return 十六进制小写字符串
     * @throws IllegalArgumentException 入参为 null
     */
    public static String bytesToHex(byte[] bytes) {
        requireNonNull(bytes, "字节入参不能为 null");
        return Hex.toHexString(bytes).toLowerCase();
    }

    /**
     * 十六进制字符串转字节（大小写均可）。
     *
     * @param hex 十六进制字符串；不允许为 null/空，且长度必须为偶数、字符合法
     * @return 字节数组
     * @throws IllegalArgumentException 入参为 null/空、长度为奇数或包含非 hex 字符
     */
    public static byte[] hexToBytes(String hex) {
        requireNonBlank(hex, "十六进制字符串不能为 null 或空");
        String normalized = hex.trim();
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数，实际 " + normalized.length() + " 位");
        }
        try {
            return Hex.decode(normalized);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("十六进制字符串包含非法字符: " + normalized, e);
        }
    }

    /* ==================== 私有辅助 ==================== */

    /**
     * 由私钥推算对应公钥（非压缩格式十六进制）。
     *
     * <p>供服务端以固定配置私钥启动时计算配套公钥（避免配置中重复维护公钥）。
     *
     * @param privateHex 私钥十六进制
     * @return 公钥十六进制（04 || x || y，130 位）
     * @throws IllegalArgumentException 私钥为空或取值非法
     */
    public static String publicKeyOfPrivate(String privateHex) {
        ECPrivateKeyParameters privateKey = parsePrivateKey(privateHex);
        return bytesToHex(SM2_EC_PARAMS.getG().multiply(privateKey.getD()).normalize().getEncoded(false));
    }

    /**
     * 解析 SM2 公钥（非压缩格式十六进制 04 || x || y）。
     */
    private static ECPublicKeyParameters parsePublicKey(String publicHex) {
        requireNonBlank(publicHex, "SM2 公钥不能为 null 或空");
        byte[] encoded = hexToBytes(publicHex);
        if (encoded.length < 1 || encoded[0] != PUBLIC_KEY_UNCOMPRESSED_FLAG) {
            throw new IllegalArgumentException("SM2 公钥必须为非压缩格式（04 || x || y）");
        }
        org.bouncycastle.math.ec.ECPoint point = SM2_EC_PARAMS.getCurve().decodePoint(encoded);
        return new ECPublicKeyParameters(point, SM2_DOMAIN);
    }

    /**
     * 解析 SM2 私钥（十六进制大整数）。
     */
    private static ECPrivateKeyParameters parsePrivateKey(String privateHex) {
        requireNonBlank(privateHex, "SM2 私钥不能为 null 或空");
        BigInteger d = new BigInteger(1, hexToBytes(privateHex));
        if (d.signum() <= 0 || d.compareTo(SM2_DOMAIN.getN()) >= 0) {
            throw new IllegalArgumentException("SM2 私钥取值非法（须为 1 ≤ d < n）");
        }
        return new ECPrivateKeyParameters(d, SM2_DOMAIN);
    }

    /**
     * 带填充的分组加解密执行（加密/解密共用）。
     */
    private static byte[] processBlock(org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher cipher, byte[] data)
            throws org.bouncycastle.crypto.InvalidCipherTextException {
        int maxOutput = cipher.getOutputSize(data.length);
        byte[] output = new byte[maxOutput];
        int produced = cipher.processBytes(data, 0, data.length, output, 0);
        produced += cipher.doFinal(output, produced);
        return produced == maxOutput ? output : Arrays.copyOf(output, produced);
    }

    /**
     * 非空校验。
     */
    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 非空白校验。
     */
    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * SM2 密钥对（公钥/私钥十六进制载体）。
     *
     * @param publicHex  公钥十六进制（非压缩格式，04 || x || y）
     * @param privateHex 私钥十六进制
     */
    public record Sm2KeyPair(String publicHex, String privateHex) {
    }
}
