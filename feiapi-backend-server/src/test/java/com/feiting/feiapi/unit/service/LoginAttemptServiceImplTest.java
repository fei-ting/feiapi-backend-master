package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.service.impl.LoginAttemptServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录失败次数限制服务测试
 */
@DisplayName("LoginAttemptServiceImpl 单元测试")
class LoginAttemptServiceImplTest {

    /**
     * 校验连续失败达到上限后账号会被锁定
     */
    @Test
    @DisplayName("连续失败 5 次后第 6 次不允许继续登录")
    void shouldBlockAfterFiveFailures() {
        LoginAttemptServiceImpl service = new LoginAttemptServiceImpl();
        String account = "lock-user";

        assertTrue(service.isLoginAllowed(account));
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(account);
        }

        assertFalse(service.isLoginAllowed(account));
    }

    /**
     * 校验登录成功后会清理失败记录
     */
    @Test
    @DisplayName("登录成功后会清理失败记录")
    void shouldClearFailuresAfterSuccess() {
        LoginAttemptServiceImpl service = new LoginAttemptServiceImpl();
        String account = "success-user";

        for (int i = 0; i < 4; i++) {
            service.recordLoginFailure(account);
        }
        assertTrue(service.isLoginAllowed(account));

        service.recordLoginSuccess(account);

        for (int i = 0; i < 5; i++) {
            assertTrue(service.isLoginAllowed(account));
            service.recordLoginFailure(account);
        }

        assertFalse(service.isLoginAllowed(account));
    }

    /**
     * 校验不同账号之间互不影响
     */
    @Test
    @DisplayName("不同账号的失败记录互不影响")
    void shouldIsolateDifferentAccounts() {
        LoginAttemptServiceImpl service = new LoginAttemptServiceImpl();
        String lockedAccount = "locked-user";
        String otherAccount = "other-user";

        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(lockedAccount);
        }

        assertFalse(service.isLoginAllowed(lockedAccount));
        assertTrue(service.isLoginAllowed(otherAccount));
    }
}
