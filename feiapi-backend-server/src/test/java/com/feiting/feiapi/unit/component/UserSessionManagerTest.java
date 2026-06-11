package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapicommon.model.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户会话管理组件测试
 */
@DisplayName("UserSessionManager 单元测试")
class UserSessionManagerTest {

    /**
     * 被测用户会话管理组件
     */
    private final UserSessionManager userSessionManager = new UserSessionManager();

    @Test
    @DisplayName("保存登录用户后可从 Session 读取")
    void shouldSaveAndGetLoginUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(1L);
        user.setUserAccount("sessionUser");

        userSessionManager.saveLoginUser(request, user);

        User sessionUser = userSessionManager.getLoginUser(request);
        assertThat(sessionUser).isSameAs(user);
        assertThat(request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE)).isSameAs(user);
    }

    @Test
    @DisplayName("Session 中没有用户时返回 null")
    void shouldReturnNullWhenSessionUserMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        User sessionUser = userSessionManager.getLoginUser(request);

        assertThat(sessionUser).isNull();
    }

    @Test
    @DisplayName("清除登录用户后 Session 中不再保留用户")
    void shouldRemoveLoginUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(1L);
        userSessionManager.saveLoginUser(request, user);

        userSessionManager.removeLoginUser(request);

        assertThat(userSessionManager.getLoginUser(request)).isNull();
        assertThat(request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE)).isNull();
    }
}
