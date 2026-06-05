package com.feiting.feiapi.integration.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserInterfaceInfoServiceImpl 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserInterfaceInfoServiceImpl 集成测试")
class UserInterfaceInfoServiceImplTest {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    private void insertUserInterfaceInfo(long userId, long interfaceInfoId, int leftNum, int totalNum) {
        UserInterfaceInfo info = new UserInterfaceInfo();
        info.setUserId(userId);
        info.setInterfaceInfoId(interfaceInfoId);
        info.setLeftNum(leftNum);
        info.setTotalNum(totalNum);
        info.setStatus(0);
        info.setIsDelete(0);
        userInterfaceInfoService.save(info);
    }

    @Nested
    @DisplayName("invokeCount 调用计数")
    class InvokeCountTests {

        @Test
        @DisplayName("正常扣减成功，leftNum 减 1，totalNum 加 1")
        void shouldDecreaseLeftNumAndIncreaseTotalNum() {
            insertUserInterfaceInfo(1L, 1L, 10, 0);

            boolean result = userInterfaceInfoService.invokeCount(1L, 1L);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, 1L)
                    .one();
            assertNotNull(info);
            assertEquals(9, info.getLeftNum());
            assertEquals(1, info.getTotalNum());
        }

        @Test
        @DisplayName("userId 为 0 时抛出异常")
        void shouldThrowWhenUserIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.invokeCount(0L, 1L));
        }

        @Test
        @DisplayName("interfaceInfoId 为 0 时抛出异常")
        void shouldThrowWhenInterfaceInfoIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.invokeCount(1L, 0L));
        }

        @Test
        @DisplayName("leftNum 为 0 时扣减失败（SQL 条件 left_num > 0）")
        void shouldFailWhenLeftNumIsZero() {
            insertUserInterfaceInfo(1L, 2L, 0, 10);

            boolean result = userInterfaceInfoService.invokeCount(1L, 2L);

            assertFalse(result);
        }

        @Test
        @DisplayName("不存在的记录扣减失败")
        void shouldFailWhenRecordNotFound() {
            boolean result = userInterfaceInfoService.invokeCount(999L, 999L);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("leftNumIsEnough 剩余次数检查")
    class LeftNumIsEnoughTests {

        @Test
        @DisplayName("已有记录且 leftNum > 0 时返回 true")
        void shouldReturnTrueWhenEnough() {
            insertUserInterfaceInfo(1L, 3L, 10, 0);

            boolean result = userInterfaceInfoService.leftNumIsEnough(1L, 3L);

            assertTrue(result);
        }

        @Test
        @DisplayName("userId 为 0 时抛出异常")
        void shouldThrowWhenUserIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(0L, 1L));
        }

        @Test
        @DisplayName("interfaceInfoId 为 0 时抛出异常")
        void shouldThrowWhenInterfaceInfoIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(1L, 0L));
        }

        @Test
        @DisplayName("leftNum 为 0 时抛出次数不足异常")
        void shouldThrowWhenLeftNumExhausted() {
            insertUserInterfaceInfo(1L, 4L, 0, 10);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(1L, 4L));
        }
    }

    @Nested
    @DisplayName("validUserInterfaceInfo 校验")
    class ValidUserInterfaceInfoTests {

        @Test
        @DisplayName("null 对象抛出 PARAMS_ERROR")
        void shouldThrowWhenNull() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(null, true));
        }

        @Test
        @DisplayName("创建时 userId <= 0 抛出异常")
        void shouldThrowWhenUserIdInvalidOnAdd() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(0L);
            info.setInterfaceInfoId(1L);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("leftNum 为负数抛出异常")
        void shouldThrowWhenLeftNumNegative() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(-1);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(info, true));
        }
    }
}
