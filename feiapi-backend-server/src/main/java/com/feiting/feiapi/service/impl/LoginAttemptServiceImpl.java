package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.service.LoginAttemptService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制服务实现类
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /**
     * 连续失败次数上限
     */
    static final int MAX_FAILURE_COUNT = 5;

    /**
     * 锁定时长
     */
    static final long LOCK_DURATION_MILLIS = Duration.ofMinutes(15).toMillis();

    /**
     * 登录失败记录表
     */
    private final ConcurrentHashMap<String, LoginAttemptRecord> loginAttemptRecordMap = new ConcurrentHashMap<>();

    @Override
    public boolean isLoginAllowed(String userAccount) {
        String normalizedAccount = normalizeUserAccount(userAccount);
        if (normalizedAccount.isEmpty()) {
            return false;
        }

        LoginAttemptRecord record = loginAttemptRecordMap.get(normalizedAccount);
        if (record == null) {
            return true;
        }

        synchronized (record) {
            long currentTimeMillis = System.currentTimeMillis();
            if (record.lockedUntilMillis > currentTimeMillis) {
                return false;
            }
            return true;
        }
    }

    @Override
    public void recordLoginFailure(String userAccount) {
        String normalizedAccount = normalizeUserAccount(userAccount);
        if (normalizedAccount.isEmpty()) {
            return;
        }

        LoginAttemptRecord record = loginAttemptRecordMap.computeIfAbsent(normalizedAccount,
                key -> new LoginAttemptRecord());
        synchronized (record) {
            long currentTimeMillis = System.currentTimeMillis();
            if (record.lockedUntilMillis > currentTimeMillis) {
                return;
            }
            if (record.lockedUntilMillis > 0 && record.lockedUntilMillis <= currentTimeMillis) {
                record.reset();
            }

            record.failureCount++;
            if (record.failureCount >= MAX_FAILURE_COUNT) {
                record.lockedUntilMillis = currentTimeMillis + LOCK_DURATION_MILLIS;
                record.failureCount = MAX_FAILURE_COUNT;
            }
        }
    }

    @Override
    public void recordLoginSuccess(String userAccount) {
        String normalizedAccount = normalizeUserAccount(userAccount);
        if (normalizedAccount.isEmpty()) {
            return;
        }
        loginAttemptRecordMap.remove(normalizedAccount);
    }

    /**
     * 规范化账号
     *
     * @param userAccount 用户账号
     * @return 规范化后的账号
     */
    private String normalizeUserAccount(String userAccount) {
        return StringUtils.trimToEmpty(userAccount);
    }

    /**
     * 登录失败状态
     */
    private static class LoginAttemptRecord {

        /**
         * 连续失败次数
         */
        private int failureCount;

        /**
         * 锁定截止时间
         */
        private long lockedUntilMillis;

        /**
         * 重置失败状态
         */
        private void reset() {
            failureCount = 0;
            lockedUntilMillis = 0L;
        }
    }
}
