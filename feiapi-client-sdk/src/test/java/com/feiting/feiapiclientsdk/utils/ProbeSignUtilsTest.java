package com.feiting.feiapiclientsdk.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProbeSignUtils 探测签名工具测试")
class ProbeSignUtilsTest {

    private static final String PROBE_SECRET = "test-probe-secret";
    private static final String METHOD = "GET";
    private static final String PATH = "/api/love_words";
    private static final String NONCE = "probe-nonce-12345678901234567890";
    private static final String TIMESTAMP = "1700000000";

    @Nested
    @DisplayName("getSign 方法")
    class GetSignTests {

        /**
         * 黄金签名值：固定输入必须产生固定的输出。
         * 探测签名与普通签名使用不同的 SALT，确保两者不会混淆。
         */
        @Test
        @DisplayName("黄金值: 固定输入产生固定签名（协议变更检测）")
        void shouldProduceFixedSignatureForFixedInput() {
            String sign = ProbeSignUtils.getSign(
                    "my-probe-secret",
                    "GET",
                    "/api/love_words",
                    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
                    "1700000000"
            );

            // 黄金值：签名算法或签名原文的任何变更都会导致此值变化
            assertEquals("f8ae85e56adfb03ade65c035cdada9e71c36c9d28f954bc8253affd176131939", sign);
        }

        @Test
        @DisplayName("相同输入产生相同签名（确定性）")
        void shouldBeDeterministic() {
            String sign1 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, TIMESTAMP);
            String sign2 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, TIMESTAMP);

            assertEquals(sign1, sign2);
        }

        @Test
        @DisplayName("签名是非空的十六进制字符串")
        void shouldBeNonEmptyHexString() {
            String sign = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, TIMESTAMP);

            assertNotNull(sign);
            assertFalse(sign.isEmpty());
            assertEquals(64, sign.length());
            assertTrue(sign.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("不同 probeSecret 产生不同签名")
        void shouldDifferWithDifferentSecret() {
            String sign1 = ProbeSignUtils.getSign("secret1", METHOD, PATH, NONCE, TIMESTAMP);
            String sign2 = ProbeSignUtils.getSign("secret2", METHOD, PATH, NONCE, TIMESTAMP);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 method 产生不同签名")
        void shouldDifferWithDifferentMethod() {
            String sign1 = ProbeSignUtils.getSign(PROBE_SECRET, "GET", PATH, NONCE, TIMESTAMP);
            String sign2 = ProbeSignUtils.getSign(PROBE_SECRET, "POST", PATH, NONCE, TIMESTAMP);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 path 产生不同签名")
        void shouldDifferWithDifferentPath() {
            String sign1 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, "/api/a", NONCE, TIMESTAMP);
            String sign2 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, "/api/b", NONCE, TIMESTAMP);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 nonce 产生不同签名")
        void shouldDifferWithDifferentNonce() {
            String sign1 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, "nonce1", TIMESTAMP);
            String sign2 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, "nonce2", TIMESTAMP);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 timestamp 产生不同签名")
        void shouldDifferWithDifferentTimestamp() {
            String sign1 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, "1000");
            String sign2 = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, "2000");

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("探测签名与普通签名不同（不同SALT）")
        void shouldDifferFromNormalSign() {
            String probeSign = ProbeSignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, TIMESTAMP);
            String normalSign = SignUtils.getSign(PROBE_SECRET, METHOD, PATH, NONCE, TIMESTAMP, "");

            assertNotEquals(probeSign, normalSign);
        }
    }
}
