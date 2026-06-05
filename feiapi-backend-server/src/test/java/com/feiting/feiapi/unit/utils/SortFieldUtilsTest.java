package com.feiting.feiapi.unit.utils;

import com.feiting.feiapi.utils.SortFieldUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SortFieldUtils 工具类测试")
class SortFieldUtilsTest {

    @Nested
    @DisplayName("allowedFields 方法")
    class AllowedFieldsTests {

        @Test
        @DisplayName("传入多个字段，返回包含所有字段的不可变集合")
        void shouldReturnSetContainingAllFields() {
            Set<String> fields = SortFieldUtils.allowedFields("id", "name", "createTime");

            assertEquals(3, fields.size());
            assertTrue(fields.contains("id"));
            assertTrue(fields.contains("name"));
            assertTrue(fields.contains("createTime"));
        }

        @Test
        @DisplayName("返回的集合是不可变的")
        void shouldReturnImmutableSet() {
            Set<String> fields = SortFieldUtils.allowedFields("id");

            assertThrows(UnsupportedOperationException.class, () -> fields.add("name"));
        }

        @Test
        @DisplayName("传入空参数，返回空集合")
        void shouldReturnEmptySetWhenNoArgs() {
            Set<String> fields = SortFieldUtils.allowedFields();

            assertTrue(fields.isEmpty());
        }
    }

    @Nested
    @DisplayName("resolveSortField 方法")
    class ResolveSortFieldTests {

        @Test
        @DisplayName("字段在白名单中，返回下划线格式")
        void shouldConvertAllowedFieldToSnakeCase() {
            Set<String> allowed = SortFieldUtils.allowedFields("createTime", "userId", "interfaceInfoId");

            assertEquals("create_time", SortFieldUtils.resolveSortField("createTime", allowed));
            assertEquals("user_id", SortFieldUtils.resolveSortField("userId", allowed));
            assertEquals("interface_info_id", SortFieldUtils.resolveSortField("interfaceInfoId", allowed));
        }

        @Test
        @DisplayName("字段不在白名单中，返回 null")
        void shouldReturnNullForDisallowedField() {
            Set<String> allowed = SortFieldUtils.allowedFields("id", "name");

            assertNull(SortFieldUtils.resolveSortField("hackerField", allowed));
        }

        @Test
        @DisplayName("字段为空字符串，返回 null")
        void shouldReturnNullForBlankField() {
            Set<String> allowed = SortFieldUtils.allowedFields("id");

            assertNull(SortFieldUtils.resolveSortField("", allowed));
            assertNull(SortFieldUtils.resolveSortField("   ", allowed));
        }

        @Test
        @DisplayName("字段为 null，返回 null")
        void shouldReturnNullForNullField() {
            Set<String> allowed = SortFieldUtils.allowedFields("id");

            assertNull(SortFieldUtils.resolveSortField(null, allowed));
        }

        @Test
        @DisplayName("白名单为 null，返回 null")
        void shouldReturnNullForNullAllowedSet() {
            assertNull(SortFieldUtils.resolveSortField("id", null));
        }

        @Test
        @DisplayName("已经是全小写的字段，直接返回")
        void shouldReturnSameFieldIfAlreadyLowercase() {
            Set<String> allowed = SortFieldUtils.allowedFields("id", "name");

            assertEquals("id", SortFieldUtils.resolveSortField("id", allowed));
            assertEquals("name", SortFieldUtils.resolveSortField("name", allowed));
        }
    }

    @Nested
    @DisplayName("camelToSnake 方法")
    class CamelToSnakeTests {

        @Test
        @DisplayName("标准驼峰转下划线")
        void shouldConvertStandardCamelCase() {
            assertEquals("create_time", SortFieldUtils.camelToSnake("createTime"));
            assertEquals("user_name", SortFieldUtils.camelToSnake("userName"));
        }

        @Test
        @DisplayName("多段驼峰转下划线")
        void shouldConvertMultiSegmentCamelCase() {
            assertEquals("interface_info_id", SortFieldUtils.camelToSnake("interfaceInfoId"));
        }

        @Test
        @DisplayName("连续大写字母")
        void shouldHandleConsecutiveUppercase() {
            assertEquals("get_u_r_l", SortFieldUtils.camelToSnake("getURL"));
        }

        @Test
        @DisplayName("全大写单词")
        void shouldHandleAllUppercase() {
            assertEquals("_i_d", SortFieldUtils.camelToSnake("ID"));
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void shouldReturnEmptyForEmptyString() {
            assertEquals("", SortFieldUtils.camelToSnake(""));
        }

        @Test
        @DisplayName("null 返回 null")
        void shouldReturnNullForNull() {
            assertNull(SortFieldUtils.camelToSnake(null));
        }

        @Test
        @DisplayName("纯小写单词不变")
        void shouldReturnSameForLowercase() {
            assertEquals("name", SortFieldUtils.camelToSnake("name"));
        }

        @Test
        @DisplayName("首字母大写")
        void shouldHandleLeadingUppercase() {
            assertEquals("_name", SortFieldUtils.camelToSnake("Name"));
        }
    }
}
