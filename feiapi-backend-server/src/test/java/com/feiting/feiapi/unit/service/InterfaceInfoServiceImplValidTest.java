package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.impl.InterfaceInfoServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterfaceInfoServiceImpl.validInterfaceInfo 校验逻辑测试")
class InterfaceInfoServiceImplValidTest {

    private final InterfaceInfoServiceImpl service = new InterfaceInfoServiceImpl();

    @Nested
    @DisplayName("通用校验")
    class GeneralValidationTests {

        @Test
        @DisplayName("interfaceInfo 为 null 时抛出 PARAMS_ERROR")
        void shouldThrowWhenNull() {
            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(null, true));
        }
    }

    @Nested
    @DisplayName("创建校验 (add=true)")
    class AddValidationTests {

        @Test
        @DisplayName("所有字段非空时校验通过")
        void shouldPassWhenAllFieldsPresent() {
            InterfaceInfo info = buildCompleteInterfaceInfo();

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("name 为空时抛出异常")
        void shouldThrowWhenNameBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setName("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("description 为空时抛出异常")
        void shouldThrowWhenDescriptionBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setDescription("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("url 为空时抛出异常")
        void shouldThrowWhenUrlBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setUrl("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("method 为空时抛出异常")
        void shouldThrowWhenMethodBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setMethod("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("requestHeader 为空时抛出异常")
        void shouldThrowWhenRequestHeaderBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setRequestHeader("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("responseHeader 为空时抛出异常")
        void shouldThrowWhenResponseHeaderBlank() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setResponseHeader("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        /**
         * 以下字段在创建时允许为 null，由数据库或框架自动生成：
         * - status: 数据库默认值 0（OFFLINE）
         * - userId: 由 controller 层从登录态获取后设置
         * - createTime/updateTime: 由 MyBatis Plus 自动填充
         * - isDelete: 由逻辑删除机制默认设为 0
         *
         * 校验器不校验这些字段的 null 值是正确的行为，因为它们不应由调用方传入。
         */
        @Test
        @DisplayName("status 为 null 允许通过（创建时由数据库默认值生成）")
        void shouldAllowStatusNullOnCreate() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setStatus(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("userId 为 null 允许通过（创建时由 controller 从登录态设置）")
        void shouldAllowUserIdNullOnCreate() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setUserId(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("createTime 为 null 允许通过（创建时由框架自动填充）")
        void shouldAllowCreateTimeNullOnCreate() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setCreateTime(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("updateTime 为 null 允许通过（创建时由框架自动填充）")
        void shouldAllowUpdateTimeNullOnCreate() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setUpdateTime(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("isDelete 为 null 允许通过（创建时由逻辑删除机制默认设为 0）")
        void shouldAllowIsDeleteNullOnCreate() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setIsDelete(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }
    }

    @Nested
    @DisplayName("更新校验 (add=false)")
    class UpdateValidationTests {

        @Test
        @DisplayName("interfaceInfo 不为 null 时通过通用校验")
        void shouldPassWhenNotNull() {
            InterfaceInfo info = new InterfaceInfo();
            info.setName("test");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("name 为 null 时不抛异常（更新时允许部分字段为空）")
        void shouldPassWhenNameNull() {
            InterfaceInfo info = new InterfaceInfo();

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }
    }

    @Nested
    @DisplayName("name 长度校验")
    class NameLengthTests {

        @Test
        @DisplayName("name 长度超过 50 时抛出异常")
        void shouldThrowWhenNameTooLong() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setName(padding(51));

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("name 长度等于 50 时通过")
        void shouldPassWhenNameExactly50() {
            InterfaceInfo info = buildCompleteInterfaceInfo();
            info.setName(padding(50));

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }
    }

    /**
     * 生成指定长度的 'a' 字符串
     */
    private String padding(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    private InterfaceInfo buildCompleteInterfaceInfo() {
        InterfaceInfo info = new InterfaceInfo();
        info.setId(1L);
        info.setName("testApi");
        info.setDescription("测试接口");
        info.setUrl("/api/test");
        info.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        info.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        info.setStatus(0);
        info.setMethod("GET");
        info.setUserId(1L);
        info.setCreateTime(new Date());
        info.setUpdateTime(new Date());
        info.setIsDelete(0);
        return info;
    }
}
