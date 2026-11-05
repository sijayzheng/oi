package cn.sijay.oi.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 国密算法工具类单元测试（纯 JUnit，无 Spring 上下文）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li><b>标准向量</b>：SM3（GB/T 32905-2016 "abc" 向量）、SM4（GB/T 32907-2016 标准向量）；</li>
 *   <li><b>正常场景</b>：SM2 加解密/签名验签/密钥对生成、SM4 ECB/CBC 往返、hex 编解码；</li>
 *   <li><b>边界与异常</b>：null 入参、空串、密钥/IV 长度不符、非法 hex、
 *       错误密钥解密失败、篡改密文/签名失败、跨密钥验签失败。</li>
 * </ul>
 *
 * @author sijay
 */
@DisplayName("国密算法工具类测试（SM2/SM3/SM4）")
class SmUtilTest {

    /* ==================== SM3 ==================== */

    @Nested
    @DisplayName("SM3 哈希摘要")
    class Sm3Test {

        @Test
        @DisplayName("标准向量：SM3(\"abc\") 与 GB/T 32905-2016 一致")
        void standardVector() {
            byte[] digest = SmUtil.sm3("abc".getBytes(StandardCharsets.UTF_8));
            assertThat(SmUtil.bytesToHex(digest))
                    .isEqualTo("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
        }

        @Test
        @DisplayName("十六进制形式与字节形式结果一致；摘要固定 32 字节")
        void hexConsistency() {
            byte[] data = "国密算法测试数据".getBytes(StandardCharsets.UTF_8);
            assertThat(SmUtil.sm3Hex(data)).isEqualTo(SmUtil.bytesToHex(SmUtil.sm3(data))).hasSize(64);
            assertThat(SmUtil.sm3Hex("国密算法测试数据")).hasSize(64);
        }

        @Test
        @DisplayName("空数据可正常计算；不同数据摘要不同")
        void emptyAndDistinct() {
            byte[] emptyDigest = SmUtil.sm3(new byte[0]);
            assertThat(emptyDigest).hasSize(32);
            assertThat(emptyDigest).isNotEqualTo(SmUtil.sm3(new byte[] {1}));
        }

        @Test
        @DisplayName("异常：null 入参返回 400 语义的 IllegalArgumentException")
        void nullInput() {
            assertThatThrownBy(() -> SmUtil.sm3(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为 null");
            assertThatThrownBy(() -> SmUtil.sm3Hex((String) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ==================== SM2 ==================== */

    @Nested
    @DisplayName("SM2 非对称加解密与签名")
    class Sm2Test {

        @Test
        @DisplayName("密钥对生成：公钥 130 位（04||x||y）、私钥固定 64 位 hex，连续生成不重复")
        void generateKeyPair() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            assertThat(keyPair.publicHex()).startsWith("04").hasSize(130);
            assertThat(keyPair.privateHex()).hasSize(64);
            // 连续两次生成密钥对不重复（随机性）
            assertThat(SmUtil.generateSm2KeyPair().privateHex()).isNotEqualTo(keyPair.privateHex());
        }

        @Test
        @DisplayName("加解密往返：中英文与二进制数据均可还原")
        void encryptDecryptRoundTrip() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            byte[] plain = "SM2 加密测试数据 abc123！".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = SmUtil.sm2Encrypt(plain, keyPair.publicHex());
            assertThat(cipher).isNotEqualTo(plain);
            assertThat(SmUtil.sm2Decrypt(cipher, keyPair.privateHex())).isEqualTo(plain);
            // 二进制边界：单字节与全零块
            assertThat(SmUtil.sm2Decrypt(SmUtil.sm2Encrypt(new byte[] {0}, keyPair.publicHex()), keyPair.privateHex()))
                    .isEqualTo(new byte[] {0});
            byte[] zeros = new byte[64];
            assertThat(SmUtil.sm2Decrypt(SmUtil.sm2Encrypt(zeros, keyPair.publicHex()), keyPair.privateHex())).isEqualTo(zeros);
        }

        @Test
        @DisplayName("十六进制字符串入口：hex 密文经 hexToBytes 可正常解密")
        void hexEntry() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            byte[] plain = "hex entry".getBytes(StandardCharsets.UTF_8);
            String cipherHex = SmUtil.bytesToHex(SmUtil.sm2Encrypt(plain, keyPair.publicHex()));
            assertThat(cipherHex).hasSizeGreaterThan(100);
            byte[] decrypted = SmUtil.sm2Decrypt(SmUtil.hexToBytes(cipherHex), keyPair.privateHex());
            assertThat(decrypted).isEqualTo(plain);
        }

        @Test
        @DisplayName("签名验签：正确签名通过，篡改数据/签名不通过；输出为 DER 编码")
        void signAndVerify() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            byte[] data = "待签名数据".getBytes(StandardCharsets.UTF_8);
            byte[] sign = SmUtil.sm2Sign(data, keyPair.privateHex());
            // BC SM2Signer 输出 ASN.1 DER 编码（SEQUENCE{r, s}），长度 70 字节左右
            assertThat(sign.length).isGreaterThanOrEqualTo(64);
            assertThat(SmUtil.sm2Verify(data, sign, keyPair.publicHex())).isTrue();

            byte[] tamperedData = "被篡改的数据".getBytes(StandardCharsets.UTF_8);
            byte[] tamperedSign = sign.clone();
            tamperedSign[tamperedSign.length - 1] ^= 0x01;
            assertThat(SmUtil.sm2Verify(tamperedData, sign, keyPair.publicHex())).isFalse();
            assertThat(SmUtil.sm2Verify(data, tamperedSign, keyPair.publicHex())).isFalse();
        }

        @Test
        @DisplayName("跨密钥：错误私钥解密失败，其他公钥验签失败")
        void wrongKeys() {
            SmUtil.Sm2KeyPair pairA = SmUtil.generateSm2KeyPair();
            SmUtil.Sm2KeyPair pairB = SmUtil.generateSm2KeyPair();
            byte[] plain = "cross key".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = SmUtil.sm2Encrypt(plain, pairA.publicHex());
            assertThatThrownBy(() -> SmUtil.sm2Decrypt(cipher, pairB.privateHex()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("解密失败");

            byte[] sign = SmUtil.sm2Sign(plain, pairA.privateHex());
            assertThat(SmUtil.sm2Verify(plain, sign, pairB.publicHex())).isFalse();
        }

        @Test
        @DisplayName("异常：密钥格式非法（非 hex / 非 04 前缀 / 私钥越界）")
        void invalidKeyFormats() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            byte[] plain = "x".getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> SmUtil.sm2Encrypt(plain, "zz-not-hex"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm2Encrypt(plain, "0102"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非压缩格式");
            assertThatThrownBy(() -> SmUtil.sm2Sign(plain, "00"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("私钥取值非法");
            assertThatCode(() -> SmUtil.sm2Sign(plain, keyPair.privateHex())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("异常：null 入参")
        void nullInputs() {
            SmUtil.Sm2KeyPair keyPair = SmUtil.generateSm2KeyPair();
            assertThatThrownBy(() -> SmUtil.sm2Encrypt(null, keyPair.publicHex()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm2Decrypt(null, keyPair.privateHex()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm2Sign(null, keyPair.privateHex()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm2Verify(null, new byte[64], keyPair.publicHex()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm2Verify(new byte[1], null, keyPair.publicHex()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ==================== SM4 ==================== */

    @Nested
    @DisplayName("SM4 对称加解密")
    class Sm4Test {

        /**
         * GB/T 32907-2016 标准测试密钥/明文。
         */
        private static final byte[] STANDARD_KEY = SmUtil.hexToBytes("0123456789abcdeffedcba9876543210");
        private static final byte[] STANDARD_IV = STANDARD_KEY;

        @Test
        @DisplayName("标准向量：ECB 首分组加密结果与 GB/T 32907-2016 一致（PKCS#7 填充追加于尾部）")
        void standardVectorEcb() {
            byte[] plain = SmUtil.hexToBytes("0123456789abcdeffedcba9876543210");
            byte[] cipher = SmUtil.sm4Encrypt(plain, STANDARD_KEY, SmUtil.Sm4Mode.ECB, null);
            // 16 字节明文经 PKCS#7 填充为 32 字节密文；首个分组即标准向量的裸加密结果
            assertThat(cipher).hasSize(32);
            assertThat(SmUtil.bytesToHex(Arrays.copyOf(cipher, 16)))
                    .isEqualTo("681edf34d206965e86b3e94f536e4246");
        }

        @Test
        @DisplayName("ECB 往返：任意长度明文（含空数组）可还原")
        void ecbRoundTrip() {
            byte[] key = SmUtil.hexToBytes(SmUtil.generateSm4KeyHex());
            byte[] plain = "SM4 ECB 往返测试".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.ECB, null);
            assertThat(SmUtil.sm4Decrypt(cipher, key, SmUtil.Sm4Mode.ECB, null)).isEqualTo(plain);
            assertThat(SmUtil.sm4Decrypt(
                    SmUtil.sm4Encrypt(new byte[0], key, SmUtil.Sm4Mode.ECB, null),
                    key, SmUtil.Sm4Mode.ECB, null)).isEmpty();
        }

        @Test
        @DisplayName("CBC 往返：相同明文相同 IV 密文一致，不同 IV 密文不同")
        void cbcRoundTripAndIvImpact() {
            byte[] key = SmUtil.hexToBytes(SmUtil.generateSm4KeyHex());
            byte[] plain = "SM4 CBC 模式测试".getBytes(StandardCharsets.UTF_8);
            byte[] cipher1 = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, STANDARD_IV);
            byte[] cipher2 = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, STANDARD_IV);
            assertThat(cipher1).isEqualTo(cipher2);
            byte[] cipher3 = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC,
                    SmUtil.hexToBytes("ffffffffffffffffffffffffffffffff"));
            assertThat(cipher3).isNotEqualTo(cipher1);
            assertThat(SmUtil.sm4Decrypt(cipher1, key, SmUtil.Sm4Mode.CBC, STANDARD_IV)).isEqualTo(plain);
        }

        @Test
        @DisplayName("ECB 与 CBC 密文互不相同；ECB 误传 IV 不影响结果")
        void modeDistinct() {
            byte[] key = STANDARD_KEY;
            byte[] plain = SmUtil.hexToBytes("aabbccddeeff00112233445566778899");
            byte[] ecb = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.ECB, null);
            byte[] cbc = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, STANDARD_IV);
            assertThat(ecb).isNotEqualTo(cbc);
            // ECB 模式忽略 IV 参数（标准用法允许传 null；误传非 null 亦无副作用）
            assertThat(SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.ECB, STANDARD_IV)).isEqualTo(ecb);
        }

        @Test
        @DisplayName("密钥生成：32 位 hex，每次随机")
        void generateKey() {
            String keyHex = SmUtil.generateSm4KeyHex();
            assertThat(keyHex).hasSize(32);
            assertThat(SmUtil.generateSm4KeyHex()).isNotEqualTo(keyHex);
            assertThat(SmUtil.hexToBytes(keyHex)).hasSize(16);
        }

        @Test
        @DisplayName("异常：密钥/IV 长度不符、密文损坏、null 入参")
        void invalidInputs() {
            byte[] key = STANDARD_KEY;
            byte[] plain = "x".getBytes(StandardCharsets.UTF_8);
            byte[] shortKey = Arrays.copyOf(key, 15);
            assertThatThrownBy(() -> SmUtil.sm4Encrypt(plain, shortKey, SmUtil.Sm4Mode.ECB, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("16 字节");
            assertThatThrownBy(() -> SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IV");
            assertThatThrownBy(() -> SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, new byte[15]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IV");
            assertThatThrownBy(() -> SmUtil.sm4Encrypt(null, key, SmUtil.Sm4Mode.ECB, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.sm4Encrypt(plain, key, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            // 篡改密文：解密失败（填充非法）
            byte[] cipher = SmUtil.sm4Encrypt(plain, key, SmUtil.Sm4Mode.CBC, STANDARD_IV);
            byte[] tampered = cipher.clone();
            tampered[0] ^= 0x01;
            assertThatThrownBy(() -> SmUtil.sm4Decrypt(tampered, key, SmUtil.Sm4Mode.CBC, STANDARD_IV))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("解密失败");
        }
    }

    /* ==================== hex 编解码 ==================== */

    @Nested
    @DisplayName("十六进制编解码")
    class HexTest {

        @Test
        @DisplayName("往返一致；大小写输入均可解码；输出统一小写")
        void roundTrip() {
            byte[] data = {(byte) 0xAB, 0x01, 0x00, (byte) 0xFF};
            String hex = SmUtil.bytesToHex(data);
            assertThat(hex).isEqualTo("ab0100ff");
            assertThat(SmUtil.hexToBytes(hex)).isEqualTo(data);
            assertThat(SmUtil.hexToBytes("AB01 00FF".replace(" ", ""))).isEqualTo(data);
        }

        @Test
        @DisplayName("异常：null/空串/奇数长度/非法字符")
        void invalidHex() {
            assertThatThrownBy(() -> SmUtil.bytesToHex(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.hexToBytes(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.hexToBytes("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SmUtil.hexToBytes("abc")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("偶数");
            assertThatThrownBy(() -> SmUtil.hexToBytes("zz11")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法字符");
        }
    }

    /* ==================== Provider 注册 ==================== */

    @Test
    @DisplayName("BouncyCastle 提供者已注册且重复注册幂等")
    void providerRegistered() {
        SmUtil.registerBouncyCastle();
        SmUtil.registerBouncyCastle();
        assertThatCode(() -> SmUtil.sm3Hex("provider ok")).doesNotThrowAnyException();
    }
}
