package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.impl.UserInterfaceInfoServiceImpl;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserInterfaceInfoServiceImpl.validUserInterfaceInfo 校验逻辑测试")
class UserInterfaceInfoServiceImplValidTest {

    private final UserInterfaceInfoServiceImpl service = new UserInterfaceInfoServiceImpl();

    @Nested
    @DisplayName("通用校验")
    class GeneralValidationTests {

        @Test
        @DisplayName("userInterfaceInfo 为 null 时抛出 PARAMS_ERROR")
        void shouldThrowWhenNull() {
            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(null, true));
        }
    }

    @Nested
    @DisplayName("创建校验 (add=true)")
    class AddValidationTests {

        @Test
        @DisplayName("userId 和 interfaceInfoId 都大于 0 时通过")
        void shouldPassWhenBothIdsPositive() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);

            assertDoesNotThrow(() -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("userId 为 0 时抛出异常")
        void shouldThrowWhenUserIdZero() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(0L);
            info.setInterfaceInfoId(1L);

            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("userId 为负数时抛出异常")
        void shouldThrowWhenUserIdNegative() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(-1L);
            info.setInterfaceInfoId(1L);

            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("interfaceInfoId 为 0 时抛出异常")
        void shouldThrowWhenInterfaceInfoIdZero() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(0L);

            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("interfaceInfoId 为负数时抛出异常")
        void shouldThrowWhenInterfaceInfoIdNegative() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(-1L);

            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(info, true));
        }
    }

    @Nested
    @DisplayName("更新校验 (add=false)")
    class UpdateValidationTests {

        @Test
        @DisplayName("userInterfaceInfo 不为 null 时通过")
        void shouldPassWhenNotNull() {
            UserInterfaceInfo info = new UserInterfaceInfo();

            assertDoesNotThrow(() -> service.validUserInterfaceInfo(info, false));
        }
    }

    @Nested
    @DisplayName("leftNum 校验")
    class LeftNumValidationTests {

        @Test
        @DisplayName("leftNum 为 null 时不抛异常")
        void shouldPassWhenLeftNumNull() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(null);

            assertDoesNotThrow(() -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("leftNum 为 0 时通过")
        void shouldPassWhenLeftNumZero() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(0);

            assertDoesNotThrow(() -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("leftNum 为正数时通过")
        void shouldPassWhenLeftNumPositive() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(100);

            assertDoesNotThrow(() -> service.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("leftNum 为负数时抛出异常")
        void shouldThrowWhenLeftNumNegative() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(-1);

            assertThrows(BusinessException.class, () -> service.validUserInterfaceInfo(info, true));
        }
    }
}
