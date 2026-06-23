package com.feiting.feiapicommon.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口真实目标地址安全校验器测试。
 */
@DisplayName("InterfaceTargetHostValidator 测试")
class InterfaceTargetHostValidatorTest {

    private static final List<String> ALLOWED_HOSTNAMES = List.of("feiapi-interface");

    @Test
    @DisplayName("白名单内的 Docker 服务名允许通过")
    void shouldAllowConfiguredDockerServiceName() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://feiapi-interface:8123", ALLOWED_HOSTNAMES);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("非白名单主机拒绝通过")
    void shouldRejectHostNotInAllowlist() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("https://example.com", ALLOWED_HOSTNAMES);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("localhost 拒绝通过")
    void shouldRejectLocalhost() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://localhost:8123", List.of("localhost"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("回环地址拒绝通过")
    void shouldRejectLoopbackAddress() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://127.0.0.1:8123", List.of("127.0.0.1"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("私有网段地址拒绝通过")
    void shouldRejectPrivateAddress() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://192.168.1.10:8080", List.of("192.168.1.10"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("云元数据链路本地地址拒绝通过")
    void shouldRejectCloudMetadataAddress() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://169.254.169.254/latest/meta-data",
                List.of("169.254.169.254"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("非 HTTP 协议拒绝通过")
    void shouldRejectNonHttpScheme() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("file:///etc/passwd", List.of("etc"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("携带用户信息的地址拒绝通过")
    void shouldRejectUserInfo() {
        boolean result = InterfaceTargetHostValidator.isSafeTargetHost("http://user:pwd@feiapi-interface:8123", ALLOWED_HOSTNAMES);

        assertThat(result).isFalse();
    }
}
