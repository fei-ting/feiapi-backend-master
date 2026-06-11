package com.feiting.feiapi.integration.service;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 集成测试
 * 使用 H2 内存数据库，每个测试方法事务回滚
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserServiceImpl 集成测试")
class UserServiceImplTest {

    /**
     * accessKey 预期长度
     */
    private static final int ACCESS_KEY_EXPECTED_LENGTH = 43;

    /**
     * secretKey 预期长度
     */
    private static final int SECRET_KEY_EXPECTED_LENGTH = 64;

    /**
     * Base64Url 密钥格式
     */
    private static final String BASE64_URL_KEY_PATTERN = "^[A-Za-z0-9_-]+$";

    /**
     * MD5 十六进制摘要格式
     */
    private static final String MD5_HEX_PATTERN = "^[a-fA-F0-9]{32}$";

    @Resource
    private UserService userService;

    @Nested
    @DisplayName("userRegister 用户注册")
    class UserRegisterTests {

        @Test
        @DisplayName("正常注册，返回新用户 id")
        void shouldRegisterSuccessfully() {
            long userId = userService.userRegister("testuser01", "password123", "password123");

            assertTrue(userId > 0);
        }

        @Test
        @DisplayName("注册后可通过 getById 查询到用户")
        void shouldFindUserAfterRegister() {
            long userId = userService.userRegister("testuser02", "password123", "password123");

            User user = userService.getById(userId);

            assertNotNull(user);
            assertEquals("testuser02", user.getUserAccount());
            assertNotNull(user.getAccessKey());
            assertNotNull(user.getSecretKey());
            assertEquals(ACCESS_KEY_EXPECTED_LENGTH, user.getAccessKey().length());
            assertEquals(SECRET_KEY_EXPECTED_LENGTH, user.getSecretKey().length());
            assertTrue(user.getAccessKey().matches(BASE64_URL_KEY_PATTERN));
            assertTrue(user.getSecretKey().matches(BASE64_URL_KEY_PATTERN));
            assertFalse(user.getAccessKey().matches(MD5_HEX_PATTERN));
            assertFalse(user.getSecretKey().matches(MD5_HEX_PATTERN));
        }

        @Test
        @DisplayName("注册用户时生成的密钥不可预测且不同用户不重复")
        void shouldGenerateSecureAndUniqueKeys() {
            long firstUserId = userService.userRegister("testuser07", "password123", "password123");
            long secondUserId = userService.userRegister("testuser08", "password123", "password123");

            User firstUser = userService.getById(firstUserId);
            User secondUser = userService.getById(secondUserId);

            assertNotNull(firstUser);
            assertNotNull(secondUser);
            assertNotEquals(firstUser.getAccessKey(), secondUser.getAccessKey());
            assertNotEquals(firstUser.getSecretKey(), secondUser.getSecretKey());
        }

        @Test
        @DisplayName("注册时密码被加密存储")
        void shouldEncryptPassword() {
            userService.userRegister("testuser03", "password123", "password123");

            User user = userService.lambdaQuery().eq(User::getUserAccount, "testuser03").one();
            assertNotNull(user);
            assertNotEquals("password123", user.getUserPassword());
            assertTrue(user.getUserPassword().startsWith("$2a$"));
        }

        @Test
        @DisplayName("账号为空时抛出异常")
        void shouldThrowWhenAccountBlank() {
            assertThrows(BusinessException.class,
                    () -> userService.userRegister("", "password123", "password123"));
        }

        @Test
        @DisplayName("账号长度不足 4 位时抛出异常")
        void shouldThrowWhenAccountTooShort() {
            assertThrows(BusinessException.class,
                    () -> userService.userRegister("abc", "password123", "password123"));
        }

        @Test
        @DisplayName("密码长度不足 8 位时抛出异常")
        void shouldThrowWhenPasswordTooShort() {
            assertThrows(BusinessException.class,
                    () -> userService.userRegister("testuser04", "1234567", "1234567"));
        }

        @Test
        @DisplayName("两次密码不一致时抛出异常")
        void shouldThrowWhenPasswordsMismatch() {
            assertThrows(BusinessException.class,
                    () -> userService.userRegister("testuser05", "password123", "password456"));
        }

        @Test
        @DisplayName("重复账号注册时抛出异常")
        void shouldThrowWhenAccountDuplicate() {
            userService.userRegister("testuser06", "password123", "password123");

            assertThrows(BusinessException.class,
                    () -> userService.userRegister("testuser06", "password456", "password456"));
        }

        @Test
        @DisplayName("并发注册同一账号时只成功一次，其余请求返回账号重复业务异常")
        void shouldTranslateDuplicateKeyWhenConcurrentRegisterSameAccount() throws Exception {
            String userAccount = "concurrent" + System.nanoTime();
            int threadCount = 8;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            try {
                List<Future<Object>> futures = IntStream.range(0, threadCount)
                        .mapToObj(index -> executorService.submit(() -> {
                            readyLatch.countDown();
                            startLatch.await();
                            try {
                                return (Object) userService.userRegister(userAccount, "password123", "password123");
                            } catch (BusinessException e) {
                                return e;
                            }
                        }))
                        .collect(Collectors.toList());

                assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
                startLatch.countDown();

                List<Object> results = futures.stream()
                        .map(future -> {
                            try {
                                return future.get(10, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toList());

                long successCount = results.stream()
                        .filter(Long.class::isInstance)
                        .count();
                long duplicateExceptionCount = results.stream()
                        .filter(BusinessException.class::isInstance)
                        .map(BusinessException.class::cast)
                        .filter(e -> ErrorCode.PARAMS_ERROR.getCode() == e.getCode())
                        .filter(e -> "账号重复".equals(e.getMessage()))
                        .count();
                long savedUserCount = userService.lambdaQuery()
                        .eq(User::getUserAccount, userAccount)
                        .count();

                assertEquals(1, successCount);
                assertEquals(threadCount - 1, duplicateExceptionCount);
                assertEquals(1, savedUserCount);
            } finally {
                executorService.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("userLogin 用户登录")
    class UserLoginTests {

        @Test
        @DisplayName("正确账号密码登录成功，返回用户信息")
        void shouldLoginSuccessfully() {
            userService.userRegister("loginuser01", "password123", "password123");

            User user = userService.userLogin("loginuser01", "password123");

            assertNotNull(user);
            assertEquals("loginuser01", user.getUserAccount());
        }

        @Test
        @DisplayName("登录成功后返回数据库用户 id")
        void shouldReturnUserIdAfterLogin() {
            long userId = userService.userRegister("loginuser02", "password123", "password123");

            User user = userService.userLogin("loginuser02", "password123");

            assertNotNull(user);
            assertEquals(userId, user.getId());
        }

        @Test
        @DisplayName("账号为空时抛出异常")
        void shouldThrowWhenAccountBlank() {
            assertThrows(BusinessException.class,
                    () -> userService.userLogin("", "password123"));
        }

        @Test
        @DisplayName("密码长度不足 8 位时抛出异常")
        void shouldThrowWhenPasswordTooShort() {
            assertThrows(BusinessException.class,
                    () -> userService.userLogin("loginuser03", "1234567"));
        }

        @Test
        @DisplayName("账号不存在时抛出异常")
        void shouldThrowWhenAccountNotFound() {
            assertThrows(BusinessException.class,
                    () -> userService.userLogin("nonexistent", "password123"));
        }

        @Test
        @DisplayName("密码错误时抛出异常")
        void shouldThrowWhenPasswordWrong() {
            userService.userRegister("loginuser04", "password123", "password123");

            assertThrows(BusinessException.class,
                    () -> userService.userLogin("loginuser04", "wrongpassword"));
        }

        @Test
        @DisplayName("连续失败 5 次后第 6 次登录直接返回锁定提示")
        void shouldBlockAfterFiveFailures() {
            userService.userRegister("loginuser05", "password123", "password123");

            for (int i = 0; i < 5; i++) {
                assertThrows(BusinessException.class,
                        () -> userService.userLogin("loginuser05", "wrongpassword"));
            }

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> userService.userLogin("loginuser05", "password123"));

            assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), exception.getCode());
            assertEquals("登录失败次数过多，请稍后再试", exception.getMessage());
        }

        @Test
        @DisplayName("登录成功后会清理失败记录")
        void shouldClearFailureRecordsAfterSuccess() {
            userService.userRegister("loginuser06", "password123", "password123");

            for (int i = 0; i < 4; i++) {
                assertThrows(BusinessException.class,
                        () -> userService.userLogin("loginuser06", "wrongpassword"));
            }

            User user = userService.userLogin("loginuser06", "password123");
            assertNotNull(user);

            assertThrows(BusinessException.class,
                    () -> userService.userLogin("loginuser06", "wrongpassword"));
        }
    }

    @Nested
    @DisplayName("getLoginUser 获取登录用户")
    class GetLoginUserTests {

        @Test
        @DisplayName("已登录用户可正常获取")
        void shouldGetLoggedInUser() {
            long userId = userService.userRegister("getuser01", "password123", "password123");
            User sessionUser = new User();
            sessionUser.setId(userId);

            User user = userService.getLoginUser(sessionUser);

            assertNotNull(user);
            assertEquals(userId, user.getId());
        }

        @Test
        @DisplayName("未登录时抛出 NOT_LOGIN_ERROR")
        void shouldThrowWhenNotLoggedIn() {
            assertThrows(BusinessException.class,
                    () -> userService.getLoginUser(null));
        }
    }

    @Nested
    @DisplayName("isAdmin 管理员判断")
    class IsAdminTests {

        @Test
        @DisplayName("普通用户返回 false")
        void shouldReturnFalseForNormalUser() {
            User user = new User();
            user.setUserRole("user");

            boolean result = userService.isAdmin(user);

            assertFalse(result);
        }

        @Test
        @DisplayName("管理员用户返回 true")
        void shouldReturnTrueForAdminUser() {
            User user = new User();
            user.setUserRole("admin");

            boolean result = userService.isAdmin(user);

            assertTrue(result);
        }

        @Test
        @DisplayName("用户为空时返回 false")
        void shouldReturnFalseWhenUserNull() {
            boolean result = userService.isAdmin(null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("userLogout 用户注销")
    class UserLogoutTests {

        @Test
        @DisplayName("已登录用户注销成功")
        void shouldLogoutSuccessfully() {
            User sessionUser = new User();
            sessionUser.setId(1L);

            boolean result = userService.userLogout(sessionUser);

            assertTrue(result);
        }

        @Test
        @DisplayName("未登录时注销抛出异常")
        void shouldThrowWhenNotLoggedIn() {
            assertThrows(BusinessException.class,
                    () -> userService.userLogout(null));
        }
    }

    @Nested
    @DisplayName("encodePassword 密码加密")
    class EncodePasswordTests {

        @Test
        @DisplayName("加密后的密码不等于原密码")
        void shouldReturnDifferentPassword() {
            String encoded = userService.encodePassword("password123");

            assertNotEquals("password123", encoded);
            assertTrue(encoded.startsWith("$2a$"));
        }

        @Test
        @DisplayName("相同密码每次加密结果不同（BCrypt salt）")
        void shouldProduceDifferentHashesForSameInput() {
            String encoded1 = userService.encodePassword("password123");
            String encoded2 = userService.encodePassword("password123");

            assertNotEquals(encoded1, encoded2);
        }
    }
}
