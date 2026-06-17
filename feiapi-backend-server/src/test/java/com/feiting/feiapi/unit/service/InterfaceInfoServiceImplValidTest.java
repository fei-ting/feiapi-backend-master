package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.impl.InterfaceInfoServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("必填字段（name、path、targetHost、method）非空时校验通过")
        void shouldPassWhenRequiredFieldsPresent() {
            InterfaceInfo info = buildMinimalInterfaceInfo();

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("name 为空时抛出异常")
        void shouldThrowWhenNameBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setName("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("name 为 null 时抛出异常")
        void shouldThrowWhenNameNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setName(null);

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("path 为空时抛出异常")
        void shouldThrowWhenPathBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setPath("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("path 为 null 时抛出异常")
        void shouldThrowWhenPathNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setPath(null);

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("targetHost 为空时抛出异常")
        void shouldThrowWhenTargetHostBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setTargetHost("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("targetHost 为 null 时抛出异常")
        void shouldThrowWhenTargetHostNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setTargetHost(null);

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("method 为空时抛出异常")
        void shouldThrowWhenMethodBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setMethod("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("method 为 null 时抛出异常")
        void shouldThrowWhenMethodNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setMethod(null);

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("description 为空允许通过（可选字段）")
        void shouldAllowDescriptionBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setDescription("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("description 为 null 允许通过（可选字段）")
        void shouldAllowDescriptionNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setDescription(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("requestHeader 为空允许通过（可选字段）")
        void shouldAllowRequestHeaderBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setRequestHeader("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("requestHeader 为 null 允许通过（可选字段）")
        void shouldAllowRequestHeaderNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setRequestHeader(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("responseHeader 为空允许通过（可选字段）")
        void shouldAllowResponseHeaderBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setResponseHeader("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("responseHeader 为 null 允许通过（可选字段）")
        void shouldAllowResponseHeaderNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setResponseHeader(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("requestParams 为空允许通过（可选字段，无参数接口可以为空）")
        void shouldAllowRequestParamsBlank() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setRequestParams("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("requestParams 为 null 允许通过（可选字段）")
        void shouldAllowRequestParamsNull() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setRequestParams(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        /**
         * 以下字段由服务端维护，不应由通用校验方法校验：
         * - id: 由数据库自动生成
         * - status: 由 /online、/offline 接口维护
         * - userId: 由登录用户上下文设置
         * - createTime/updateTime: 由 MyBatis Plus 自动填充
         * - isDelete: 由逻辑删除机制默认设为 0
         */
        @Test
        @DisplayName("id 为 null 允许通过（服务端维护字段）")
        void shouldAllowIdNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setId(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("status 为 null 允许通过（服务端维护字段）")
        void shouldAllowStatusNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setStatus(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("userId 为 null 允许通过（服务端维护字段）")
        void shouldAllowUserIdNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setUserId(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("createTime 为 null 允许通过（服务端维护字段）")
        void shouldAllowCreateTimeNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setCreateTime(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("updateTime 为 null 允许通过（服务端维护字段）")
        void shouldAllowUpdateTimeNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setUpdateTime(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("isDelete 为 null 允许通过（服务端维护字段）")
        void shouldAllowIsDeleteNullOnCreate() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setIsDelete(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }
    }

    @Nested
    @DisplayName("更新校验 (add=false)")
    class UpdateValidationTests {

        @Test
        @DisplayName("所有字段为 null 时通过校验（更新时允许不传字段）")
        void shouldPassWhenAllFieldsNull() {
            InterfaceInfo info = new InterfaceInfo();

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("name 传入正常值时通过校验")
        void shouldPassWhenNameValid() {
            InterfaceInfo info = new InterfaceInfo();
            info.setName("updatedApi");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("name 传入空白字符串时抛出异常")
        void shouldThrowWhenNameBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setName("   ");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("name 传入空字符串时抛出异常")
        void shouldThrowWhenNameEmptyOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setName("");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("url 传入正常值时通过校验")
        void shouldPassWhenUrlValid() {
            InterfaceInfo info = new InterfaceInfo();
            info.setUrl("/api/updated");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("url 传入空白字符串时抛出异常")
        void shouldThrowWhenUrlBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setUrl("   ");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("method 传入正常值时通过校验")
        void shouldPassWhenMethodValid() {
            InterfaceInfo info = new InterfaceInfo();
            info.setMethod("POST");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("method 传入空白字符串时抛出异常")
        void shouldThrowWhenMethodBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setMethod("   ");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("method 传入非法请求方法时抛出异常")
        void shouldThrowWhenMethodInvalidOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setMethod("INVALID");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("path 传入正常值时通过校验")
        void shouldPassWhenPathValid() {
            InterfaceInfo info = new InterfaceInfo();
            info.setPath("/api/updated");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("path 不是以 / 开头时抛出异常")
        void shouldThrowWhenPathWithoutSlash() {
            InterfaceInfo info = new InterfaceInfo();
            info.setPath("api/updated");

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("description 为空允许通过（可用于清空描述）")
        void shouldAllowDescriptionBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setDescription("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("requestHeader 为空允许通过（可用于清空请求头）")
        void shouldAllowRequestHeaderBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setRequestHeader("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("responseHeader 为空允许通过（可用于清空响应头）")
        void shouldAllowResponseHeaderBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setResponseHeader("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("requestParams 为空允许通过（可用于清空参数说明）")
        void shouldAllowRequestParamsBlankOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setRequestParams("");

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("status 为 null 允许通过（由 /online、/offline 接口维护）")
        void shouldAllowStatusNullOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setStatus(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }

        @Test
        @DisplayName("userId 为 null 允许通过（由登录用户上下文维护）")
        void shouldAllowUserIdNullOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setUserId(null);

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, false));
        }
    }

    @Nested
    @DisplayName("name 长度校验")
    class NameLengthTests {

        @Test
        @DisplayName("name 长度超过 50 时抛出异常")
        void shouldThrowWhenNameTooLong() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setName(padding(51));

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("name 长度等于 50 时通过")
        void shouldPassWhenNameExactly50() {
            InterfaceInfo info = buildMinimalInterfaceInfo();
            info.setName(padding(50));

            assertDoesNotThrow(() -> service.validInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("更新时 name 长度超过 50 也抛出异常")
        void shouldThrowWhenNameTooLongOnUpdate() {
            InterfaceInfo info = new InterfaceInfo();
            info.setName(padding(51));

            assertThrows(BusinessException.class, () -> service.validInterfaceInfo(info, false));
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

    /**
     * 构建最小化的接口信息对象，只包含必填字段
     */
    private InterfaceInfo buildMinimalInterfaceInfo() {
        InterfaceInfo info = new InterfaceInfo();
        info.setName("testApi");
        info.setPath("/api/test");
        info.setTargetHost("http://localhost:8123");
        info.setUrl("http://localhost:8123/api/test");
        info.setMethod("GET");
        return info;
    }
}
