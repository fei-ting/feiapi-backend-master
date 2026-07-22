package com.feiting.feiapi.component;

import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 用户会话管理组件。
 *
 * <p>集中封装 HTTP Session 读写逻辑，避免 Service 层直接依赖 Web 容器对象。</p>
 *
 * <p>安全设计（设计文档 5.5 节）：</p>
 * <ul>
 *   <li>登录保存用户前，如果请求已经存在 Session，则调用 {@code changeSessionId()} 防止会话固定攻击</li>
 *   <li>请求没有 Session 时创建新 Session 并保存用户快照</li>
 *   <li>退出时调用 {@code session.invalidate()}，确保整个 Session 与 Redis Session 数据失效</li>
 * </ul>
 */
@Component
public class UserSessionManager {

    /**
     * 保存登录用户到当前 HTTP 会话。
     *
     * <p>如果请求已经持有 Session（例如登录前访问过公开页面），
     * 先调用 {@code changeSessionId()} 轮换 Session ID，防止会话固定攻击。
     * 如果请求没有 Session，{@code request.getSession()} 会创建新 Session。</p>
     *
     * @param request HTTP 请求
     * @param user    登录用户
     */
    public void saveLoginUser(HttpServletRequest request, User user) {
        if (request.getSession(false) != null) {
            // 已有 Session，轮换 Session ID 防止会话固定攻击
            request.changeSessionId();
        }
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
    }

    /**
     * 从当前 HTTP 会话读取登录用户快照。
     *
     * @param request HTTP 请求
     * @return 会话中的用户快照，未登录时返回 {@code null}
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
     * 销毁当前 HTTP 会话，确保整个 Session 与 Redis Session 数据失效。
     *
     * <p>退出登录时调用此方法，而非仅删除用户属性。
     * {@code session.invalidate()} 会使 Session ID 失效，
     * 并清除该 Session 下所有属性，配合 Spring Session Redis 会同步删除远端数据。</p>
     *
     * @param request HTTP 请求
     */
    public void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
