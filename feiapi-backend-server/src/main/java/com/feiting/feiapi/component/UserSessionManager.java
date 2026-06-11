package com.feiting.feiapi.component;

import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 用户会话管理组件
 *
 * <p>集中封装 HTTP Session 读写逻辑，避免 Service 层直接依赖 Web 容器对象。</p>
 */
@Component
public class UserSessionManager {

    /**
     * 保存登录用户到当前 HTTP 会话
     *
     * @param request HTTP 请求
     * @param user    登录用户
     */
    public void saveLoginUser(HttpServletRequest request, User user) {
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
    }

    /**
     * 从当前 HTTP 会话读取登录用户快照
     *
     * @param request HTTP 请求
     * @return 会话中的用户快照，未登录时返回 null
     */
    public User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userObj = session.getAttribute(UserConstant.USER_LOGIN_STATE);
        return userObj instanceof User ? (User) userObj : null;
    }

    /**
     * 清除当前 HTTP 会话中的登录用户
     *
     * @param request HTTP 请求
     */
    public void removeLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(UserConstant.USER_LOGIN_STATE);
        }
    }
}
