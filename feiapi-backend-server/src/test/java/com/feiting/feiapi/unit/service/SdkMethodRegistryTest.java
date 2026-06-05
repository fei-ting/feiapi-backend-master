package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SdkMethodRegistry SDK方法注册器测试")
class SdkMethodRegistryTest {

    private SdkMethodRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SdkMethodRegistry();
        registry.init();
    }

    @Nested
    @DisplayName("init 方法")
    class InitTests {

        @Test
        @DisplayName("初始化后注册了带 @SdkInvoke 注解的方法")
        void shouldRegisterAnnotatedMethods() {
            Map<String, Method> methodMap = registry.getMethodMap();

            assertTrue(methodMap.containsKey("getLoveWords"));
            assertTrue(methodMap.containsKey("getUsernameByPost"));
        }

        @Test
        @DisplayName("注册的方法 Map 是不可变的")
        void shouldReturnImmutableMap() {
            Map<String, Method> methodMap = registry.getMethodMap();

            assertThrows(UnsupportedOperationException.class, () -> methodMap.put("test", null));
        }

        @Test
        @DisplayName("不注册没有 @SdkInvoke 注解的方法")
        void shouldNotRegisterUnannotatedMethods() {
            Map<String, Method> methodMap = registry.getMethodMap();

            assertFalse(methodMap.containsKey("getHeaderMap"));
            assertFalse(methodMap.containsKey("executeRequest"));
            assertFalse(methodMap.containsKey("normalizeGatewayHost"));
        }

        @Test
        @DisplayName("注册的方法数量与 @SdkInvoke 注解方法数一致")
        void shouldMatchAnnotationCount() {
            Map<String, Method> methodMap = registry.getMethodMap();

            // FeiApiClient 中有 2 个 @SdkInvoke 方法：getLoveWords, getUsernameByPost
            assertEquals(2, methodMap.size());
        }
    }

    @Nested
    @DisplayName("invoke 方法 - 方法名校验")
    class MethodNameValidationTests {

        @Test
        @DisplayName("调用不存在的方法名，抛出不支持的接口方法异常")
        void shouldThrowForUnknownMethod() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "nonExistentMethod", null));

            assertTrue(exception.getMessage().contains("不支持的接口方法"));
            assertTrue(exception.getMessage().contains("nonExistentMethod"));
        }

        @Test
        @DisplayName("空方法名抛出异常")
        void shouldThrowForEmptyMethodName() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "", null));
        }
    }

    @Nested
    @DisplayName("invoke 方法 - needParams=true 参数校验")
    class NeedParamsTrueTests {

        @Test
        @DisplayName("需要参数的方法传入 null，抛出参数不能为空异常")
        void shouldThrowWhenParamsNull() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getUsernameByPost", null));

            assertTrue(exception.getMessage().contains("参数不能为空"));
            assertTrue(exception.getMessage().contains("getUsernameByPost"));
        }

        @Test
        @DisplayName("需要参数的方法传入空字符串，抛出参数不能为空异常")
        void shouldThrowWhenParamsEmpty() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getUsernameByPost", ""));

            assertTrue(exception.getMessage().contains("参数不能为空"));
        }

        @Test
        @DisplayName("需要参数的方法传入纯空白，抛出参数不能为空异常")
        void shouldThrowWhenParamsBlank() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getUsernameByPost", "   "));

            assertTrue(exception.getMessage().contains("参数不能为空"));
        }
    }

    @Nested
    @DisplayName("invoke 方法 - needParams=false 参数校验")
    class NeedParamsFalseTests {

        @Test
        @DisplayName("不需要参数的方法传入非空字符串，抛出不需要参数异常")
        void shouldThrowWhenParamsProvidedForNoParamMethod() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getLoveWords", "someParam"));

            assertTrue(exception.getMessage().contains("不需要参数"));
            assertTrue(exception.getMessage().contains("getLoveWords"));
        }

        @Test
        @DisplayName("不需要参数的方法传入非空 JSON，抛出不需要参数异常")
        void shouldThrowWhenJsonProvidedForNoParamMethod() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getLoveWords", "{\"key\":\"value\"}"));

            assertTrue(exception.getMessage().contains("不需要参数"));
        }
    }

    @Nested
    @DisplayName("invoke 方法 - 参数校验逻辑与方法调用解耦")
    class ValidationDecoupledTests {

        @Test
        @DisplayName("参数校验在方法调用之前执行（needParams=true, null 参数不触发调用）")
        void shouldValidateBeforeInvokeForNeedParams() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            // 如果参数校验在调用之后，这里会先触发 HTTP 调用再抛异常
            // 但实际应该在调用前就抛出参数异常
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getUsernameByPost", null));

            // 异常消息明确指向参数校验，而非网络调用失败
            assertTrue(exception.getMessage().contains("参数不能为空"));
            assertFalse(exception.getMessage().contains("调用失败"));
        }

        @Test
        @DisplayName("参数校验在方法调用之前执行（needParams=false, 非空参数不触发调用）")
        void shouldValidateBeforeInvokeForNoParams() {
            FeiApiClient client = new FeiApiClient("ak", "sk");

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> registry.invoke(client, "getLoveWords", "unexpected"));

            assertTrue(exception.getMessage().contains("不需要参数"));
            assertFalse(exception.getMessage().contains("调用失败"));
        }

        @Test
        @DisplayName("注册方法的 @SdkInvoke.needParams 值与方法签名一致")
        void shouldHaveCorrectNeedParamsAnnotation() {
            Map<String, Method> methodMap = registry.getMethodMap();

            // getLoveWords: @SdkInvoke(needParams = false), 无参方法
            Method getLoveWords = methodMap.get("getLoveWords");
            assertNotNull(getLoveWords);
            com.feiting.feiapiclientsdk.annotation.SdkInvoke ann1 =
                    getLoveWords.getAnnotation(com.feiting.feiapiclientsdk.annotation.SdkInvoke.class);
            assertNotNull(ann1);
            assertFalse(ann1.needParams(), "getLoveWords 应标记为不需要参数");

            // getUsernameByPost: @SdkInvoke(needParams = true), 有参方法
            Method getUsernameByPost = methodMap.get("getUsernameByPost");
            assertNotNull(getUsernameByPost);
            com.feiting.feiapiclientsdk.annotation.SdkInvoke ann2 =
                    getUsernameByPost.getAnnotation(com.feiting.feiapiclientsdk.annotation.SdkInvoke.class);
            assertNotNull(ann2);
            assertTrue(ann2.needParams(), "getUsernameByPost 应标记为需要参数");
        }
    }
}
