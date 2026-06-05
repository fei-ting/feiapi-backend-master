package com.feiting.feiapiclientsdk.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignUtils 签名工具测试")
class SignUtilsTest {

    private static final String SECRET_KEY = "test-secret-key-12345";
    private static final String METHOD = "POST";
    private static final String PATH = "/api/name/user";
    private static final String NONCE = "abc123def456";
    private static final String TIMESTAMP = "1700000000";
    private static final String BODY = "{\"username\":\"test\"}";

    @Nested
    @DisplayName("getSign 方法")
    class GetSignTests {

        /**
         * 黄金签名值：固定输入必须产生固定的输出。
         * 如果这个测试失败，说明签名算法或签名原文发生了变化，
         * 必须同步更新 SDK 和网关两端，否则所有请求验签都会失败。
         */
        @Test
        @DisplayName("黄金值: 固定输入产生固定签名（协议变更检测）")
        void shouldProduceFixedSignatureForFixedInput() {
            String sign = SignUtils.getSign(
                    "my-secret-key",
                    "POST",
                    "/api/name/user",
                    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
                    "1700000000",
                    "{\"username\":\"test\"}"
            );

            // 黄金值：签名算法或签名原文的任何变更都会导致此值变化
            assertEquals("346446d12f4a0c5225ae311b9ace1a0990c0b47f0f5b4ea941491fecfddb7733", sign);
        }

        @Test
        @DisplayName("相同输入产生相同签名（确定性）")
        void shouldBeDeterministic() {
            String sign1 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, BODY);
            String sign2 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, BODY);

            assertEquals(sign1, sign2);
        }

        @Test
        @DisplayName("签名是非空的十六进制字符串")
        void shouldBeNonEmptyHexString() {
            String sign = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, BODY);

            assertNotNull(sign);
            assertFalse(sign.isEmpty());
            // HMAC-SHA256 输出 64 个十六进制字符
            assertEquals(64, sign.length());
            assertTrue(sign.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("不同 secretKey 产生不同签名")
        void shouldDifferWithDifferentSecretKey() {
            String sign1 = SignUtils.getSign("key1", METHOD, PATH, NONCE, TIMESTAMP, BODY);
            String sign2 = SignUtils.getSign("key2", METHOD, PATH, NONCE, TIMESTAMP, BODY);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 method 产生不同签名")
        void shouldDifferWithDifferentMethod() {
            String sign1 = SignUtils.getSign(SECRET_KEY, "GET", PATH, NONCE, TIMESTAMP, BODY);
            String sign2 = SignUtils.getSign(SECRET_KEY, "POST", PATH, NONCE, TIMESTAMP, BODY);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 path 产生不同签名")
        void shouldDifferWithDifferentPath() {
            String sign1 = SignUtils.getSign(SECRET_KEY, METHOD, "/api/a", NONCE, TIMESTAMP, BODY);
            String sign2 = SignUtils.getSign(SECRET_KEY, METHOD, "/api/b", NONCE, TIMESTAMP, BODY);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 nonce 产生不同签名")
        void shouldDifferWithDifferentNonce() {
            String sign1 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, "nonce1", TIMESTAMP, BODY);
            String sign2 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, "nonce2", TIMESTAMP, BODY);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 timestamp 产生不同签名")
        void shouldDifferWithDifferentTimestamp() {
            String sign1 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, "1000", BODY);
            String sign2 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, "2000", BODY);

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("不同 body 产生不同签名")
        void shouldDifferWithDifferentBody() {
            String sign1 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, "{\"a\":1}");
            String sign2 = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, "{\"b\":2}");

            assertNotEquals(sign1, sign2);
        }

        @Test
        @DisplayName("body 为 null 时等同于空字符串")
        void shouldTreatNullBodyAsEmpty() {
            String signNull = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, null);
            String signEmpty = SignUtils.getSign(SECRET_KEY, METHOD, PATH, NONCE, TIMESTAMP, "");

            assertEquals(signNull, signEmpty);
        }
    }
}
