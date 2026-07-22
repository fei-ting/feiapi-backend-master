package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapicommon.model.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

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
    @DisplayName("没有 Session 时保存登录用户会创建新 Session")
    void shouldCreateSessionWhenSavingLoginUserWithoutExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(1L);
        user.setUserAccount("sessionUser");

        assertThat(request.getSession(false)).isNull();

        userSessionManager.saveLoginUser(request, user);

        User sessionUser = userSessionManager.getLoginUser(request);
        assertThat(request.getSession(false)).isNotNull();
        assertThat(sessionUser).isSameAs(user);
        assertThat(request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE)).isSameAs(user);
    }

    @Test
    @DisplayName("已有 Session 时保存登录用户会轮换 Session ID")
    void shouldRotateSessionIdWhenSavingLoginUserWithExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        String originalSessionId = session.getId();
        User user = new User();
        user.setId(1L);

        userSessionManager.saveLoginUser(request, user);

        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        assertThat(userSessionManager.getLoginUser(request)).isSameAs(user);
    }

    @Test
    @DisplayName("Session 中没有用户时返回 null")
    void shouldReturnNullWhenSessionUserMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        User sessionUser = userSessionManager.getLoginUser(request);

        assertThat(sessionUser).isNull();
    }

    @Test
    @DisplayName("销毁 Session 后原会话失效且不能读取登录用户")
    void shouldInvalidateSessionAndRemoveLoginUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(1L);
        userSessionManager.saveLoginUser(request, user);
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        userSessionManager.invalidateSession(request);

        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNull();
        assertThat(userSessionManager.getLoginUser(request)).isNull();
    }

    @Test
    @DisplayName("不存在 Session 时销毁操作不会创建新 Session")
    void shouldNotCreateSessionWhenInvalidatingWithoutExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        userSessionManager.invalidateSession(request);

        assertThat(request.getSession(false)).isNull();
    }
}
