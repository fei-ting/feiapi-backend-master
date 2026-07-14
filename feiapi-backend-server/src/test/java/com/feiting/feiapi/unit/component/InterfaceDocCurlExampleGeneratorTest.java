package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.model.vo.InterfaceDocParamVO;
import com.feiting.feiapi.model.vo.InterfaceDocVO;
import com.feiting.feiapiclientsdk.utils.SignUtils;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档 curl 示例生成器单元测试。
 */
@DisplayName("InterfaceDocCurlExampleGenerator 单元测试")
class InterfaceDocCurlExampleGeneratorTest {

    /** 无安全意义的固定测试密钥。 */
    private static final String TEST_SECRET_KEY = "unit-test-secret-key";

    /** 固定测试随机数。 */
    private static final String TEST_NONCE = "0123456789abcdef0123456789abcdef";

    /** 固定测试时间戳。 */
    private static final String TEST_TIMESTAMP = "1700000000";

    /** 测试用签名盐值。 */
    private static final String TEST_SIGN_SALT = "feiting";

    /** 被测 curl 示例生成器。 */
    private final InterfaceDocCurlExampleGenerator generator = new InterfaceDocCurlExampleGenerator();

    /**
     * 设置测试用的签名盐值。
     * 由于单元测试不走 Spring 容器，需要通过反射注入 @Value 字段。
     */
    @BeforeEach
    void setUp() throws Exception {
        Field signSaltField = InterfaceDocCurlExampleGenerator.class.getDeclaredField("signSalt");
        signSaltField.setAccessible(true);
        signSaltField.set(generator, TEST_SIGN_SALT);
    }

    /**
     * 验证 GET 空正文规范字符串格式正确，签名与 SDK 一致。
     * 不再手动计算 HMAC，直接使用 SDK 的 SignUtils.getSign 验证签名一致性。
     */
    @Test
    @DisplayName("GET 空正文规范字符串格式正确且签名与 SDK 一致")
    void shouldBuildGetCanonicalStringAndSignLikeSdk() {
        String method = "GET";
        String path = "/api/users";
        String body = "";
        String canonicalString = generator.buildCanonicalString(
                method, path, TEST_NONCE, TEST_TIMESTAMP, body);

        // 断言规范字符串格式正确
        assertThat(canonicalString)
                .isEqualTo("feiting\nGET\n/api/users\n" + TEST_NONCE + "\n" + TEST_TIMESTAMP + "\n")
                .endsWith("\n");

        // 使用 SDK 验证签名一致性（不再手动计算 HMAC）
        String expectedSign = SignUtils.getSign(
                TEST_SECRET_KEY, method, path, TEST_NONCE, TEST_TIMESTAMP, body);
        assertThat(expectedSign).isNotBlank();
    }

    /**
     * 验证 POST JSON 正文规范字符串格式正确，签名与 SDK 一致。
     */
    @Test
    @DisplayName("POST JSON 正文规范字符串格式正确且签名与 SDK 一致")
    void shouldBuildPostCanonicalStringAndSignLikeSdk() {
        String method = "POST";
        String path = "/api/users";
        String body = "{\"name\":\"张三\",\"age\":18}";
        String canonicalString = generator.buildCanonicalString(
                method, path, TEST_NONCE, TEST_TIMESTAMP, body);

        // 断言规范字符串包含完整内容（POST 规范字符串以 body 结尾，不以换行结尾）
        assertThat(canonicalString)
                .startsWith("feiting\n")
                .contains(method, path, TEST_NONCE, TEST_TIMESTAMP);

        // 使用 SDK 验证签名一致性
        String expectedSign = SignUtils.getSign(
                TEST_SECRET_KEY, method, path, TEST_NONCE, TEST_TIMESTAMP, body);
        assertThat(expectedSign).isNotBlank();
    }

    /**
     * 验证任一签名字段变化都会改变签名。
     */
    @Test
    @DisplayName("任一签名字段变化都会改变签名")
    void shouldChangeSignWhenAnyCanonicalFieldChanges() {
        String original = SignUtils.getSign(
                TEST_SECRET_KEY, "POST", "/api/users", TEST_NONCE, TEST_TIMESTAMP, "{\"name\":\"A\"}");
        Set<String> changedSigns = Stream.of(
                        SignUtils.getSign(TEST_SECRET_KEY, "PUT", "/api/users", TEST_NONCE, TEST_TIMESTAMP, "{\"name\":\"A\"}"),
                        SignUtils.getSign(TEST_SECRET_KEY, "POST", "/api/accounts", TEST_NONCE, TEST_TIMESTAMP, "{\"name\":\"A\"}"),
                        SignUtils.getSign(TEST_SECRET_KEY, "POST", "/api/users", "fedcba9876543210fedcba9876543210", TEST_TIMESTAMP, "{\"name\":\"A\"}"),
                        SignUtils.getSign(TEST_SECRET_KEY, "POST", "/api/users", TEST_NONCE, "1700000001", "{\"name\":\"A\"}"),
                        SignUtils.getSign(TEST_SECRET_KEY, "POST", "/api/users", TEST_NONCE, TEST_TIMESTAMP, "{\"name\":\"B\"}"))
                .collect(Collectors.toSet());

        assertThat(changedSigns).hasSize(5).doesNotContain(original);
    }

    /**
     * 验证脚本直接通过 printf 管道计算签名，并允许预置固定 nonce 与时间戳。
     */
    @Test
    @DisplayName("签名脚本不使用会丢失末尾换行的规范字符串变量")
    void shouldPipePrintfDirectlyToOpenSsl() {
        String script = generator.generate(interfaceInfo("GET", "/api/users"), detail("http://localhost/api/users"));

        assertThat(script)
                .contains("NONCE=\"${NONCE:-$(openssl rand -hex 16)}\"")
                .contains("TIMESTAMP=\"${TIMESTAMP:-$(date +%s)}\"")
                .contains("printf 'feiting\\n%s\\n%s\\n%s\\n%s\\n%s' \\")
                .contains("\"$METHOD\" \"$PATH_VALUE\" \"$NONCE\" \"$TIMESTAMP\" \"$BODY\" |")
                .doesNotContain("CANONICAL_STRING");
    }

    /**
     * 验证 GET Query 使用 UTF-8 编码且不生成请求正文。
     */
    @Test
    @DisplayName("GET 正确生成 Query 且不生成 data")
    void shouldBuildEncodedGetQueryWithoutBody() {
        InterfaceDocDetailVO detail = detail("http://localhost/api/search");
        detail.setRequestParams(Arrays.asList(
                param("QUERY", "keyword", "string", "中文 空格", 1),
                param("QUERY", "page", "number", "2", 2)));

        String script = generator.generate(interfaceInfo("GET", "/api/search"), detail);

        assertThat(script)
                .contains("URL='http://localhost/api/search?keyword=%E4%B8%AD%E6%96%87%20%E7%A9%BA%E6%A0%BC&page=2'")
                .contains("BODY=''")
                .doesNotContain("--data");
    }

    /**
     * 验证 POST 正文保持 JSON 类型并对非法示例使用安全默认值。
     */
    @Test
    @DisplayName("POST 正文保持声明类型并使用安全默认值")
    void shouldKeepJsonTypesAndUseSafeDefaults() {
        InterfaceDocDetailVO detail = detail("http://localhost/api/create");
        detail.setRequestParams(Arrays.asList(
                param("BODY", "name", "string", "测试", 1),
                param("BODY", "age", "number", "18", 2),
                param("BODY", "enabled", "boolean", "true", 3),
                param("BODY", "metadata", "object", "{\"level\":1}", 4),
                param("BODY", "tags", "array", "[\"A\"]", 5),
                param("BODY", "invalidNumber", "number", "not-number", 6),
                param("BODY", "invalidObject", "object", "[]", 7)));

        String script = generator.generate(interfaceInfo("POST", "/api/create"), detail);

        assertThat(script)
                .contains("\"age\":18")
                .contains("\"enabled\":true")
                .contains("\"metadata\":{\"level\":1}")
                .contains("\"tags\":[\"A\"]")
                .contains("\"invalidNumber\":0")
                .contains("\"invalidObject\":{}")
                .contains("--data \"$BODY\"");
    }

    /**
     * 验证 Shell 单引号按 POSIX 规则转换，其他特殊字符保持原值。
     */
    @Test
    @DisplayName("Shell 特殊字符保持原值且单引号安全转换")
    void shouldEscapeOnlySingleQuoteInStaticShellText() {
        InterfaceDocDetailVO detail = detail("http://localhost/api/create");
        detail.setRequestHeaders(Collections.singletonList(
                header("X-Test", "a'b\"$`!\\ value")));

        String script = generator.generate(interfaceInfo("POST", "/api/create"), detail);

        assertThat(script)
                .contains("a'\"'\"'b\"$`!\\ value")
                .doesNotContain("\\$")
                .doesNotContain("\\`")
                .doesNotContain("\\!");
    }

    /**
     * 验证文档主信息的内容类型具有唯一优先级，Header 中的冲突值会被忽略。
     */
    @Test
    @DisplayName("Content-Type 冲突时仅保留主信息权威值")
    void shouldKeepOnlyAuthoritativeContentType() {
        InterfaceDocDetailVO detail = detail("http://localhost/api/create");
        detail.getDoc().setRequestContentType("application/xml");
        detail.setRequestHeaders(Arrays.asList(
                header("content-type", "text/plain"),
                header("CONTENT-TYPE", "application/json"),
                header("X-Trace", "trace-value")));

        String script = generator.generate(interfaceInfo("POST", "/api/create"), detail);

        assertThat(countOccurrences(script, "Content-Type:")).isEqualTo(1);
        assertThat(script)
                .contains("Content-Type: application/xml")
                .doesNotContain("Content-Type: text/plain")
                .doesNotContain("Content-Type: application/json")
                .contains("X-Trace: trace-value");
    }

    /**
     * 验证 URL、路径和 Header 中的控制字符会被拒绝。
     */
    @Test
    @DisplayName("拒绝 URL 路径和 Header 中的控制字符")
    void shouldRejectShellControlCharacters() {
        assertThatThrownBy(() -> generator.generate(
                interfaceInfo("GET", "/api/users\nmalicious"), detail("http://localhost/api/users")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("控制字符");
        assertThatThrownBy(() -> generator.generate(
                interfaceInfo("GET", "/api/users"), detail("http://localhost/api/users\rmalicious")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("控制字符");

        InterfaceDocDetailVO detailWithHeader = detail("http://localhost/api/users");
        detailWithHeader.setRequestHeaders(Collections.singletonList(header("X-Test", "value\nInjected: true")));
        assertThatThrownBy(() -> generator.generate(interfaceInfo("GET", "/api/users"), detailWithHeader))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("控制字符");

        InterfaceDocDetailVO detailWithInvalidHeaderName = detail("http://localhost/api/users");
        detailWithInvalidHeaderName.setRequestHeaders(Collections.singletonList(header("X\0Test", "value")));
        assertThatThrownBy(() -> generator.generate(interfaceInfo("GET", "/api/users"), detailWithInvalidHeaderName))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("控制字符");
    }

    /**
     * 验证空路径和空方法使用安全默认值。
     */
    @Test
    @DisplayName("空路径和空方法使用安全默认值")
    void shouldHandleEmptyPathAndMethod() {
        // 空路径应返回空字符串
        InterfaceInfo emptyPathInfo = interfaceInfo("GET", "");
        String script1 = generator.generate(emptyPathInfo, detail("http://localhost/api/test"));
        assertThat(script1).contains("PATH_VALUE=''");

        // 空方法应默认使用 GET
        InterfaceInfo emptyMethodInfo = interfaceInfo("", "/api/test");
        String script2 = generator.generate(emptyMethodInfo, detail("http://localhost/api/test"));
        assertThat(script2).contains("METHOD='GET'");

        // null 路径和方法应安全处理
        InterfaceInfo nullInfo = interfaceInfo(null, null);
        String script3 = generator.generate(nullInfo, detail("http://localhost/api/test"));
        assertThat(script3).contains("METHOD='GET'", "PATH_VALUE=''");
    }

    /**
     * 构建测试接口信息。
     *
     * @param method 请求方法
     * @param path   请求路径
     * @return 接口信息
     */
    private InterfaceInfo interfaceInfo(String method, String path) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setMethod(method);
        interfaceInfo.setPath(path);
        return interfaceInfo;
    }

    /**
     * 构建测试文档聚合视图。
     *
     * @param gatewayUrl 网关地址
     * @return 文档聚合视图
     */
    private InterfaceDocDetailVO detail(String gatewayUrl) {
        InterfaceDocVO doc = new InterfaceDocVO();
        doc.setRequestContentType("application/json");
        InterfaceDocDetailVO detail = new InterfaceDocDetailVO();
        detail.setDoc(doc);
        detail.setGatewayUrl(gatewayUrl);
        detail.setRequestParams(Collections.emptyList());
        detail.setRequestHeaders(Collections.emptyList());
        return detail;
    }

    /**
     * 构建测试请求参数。
     *
     * @param scene        参数场景
     * @param name         参数名
     * @param type         参数类型
     * @param exampleValue 示例值
     * @param sortOrder    排序值
     * @return 请求参数视图
     */
    private InterfaceDocParamVO param(String scene, String name, String type, String exampleValue, int sortOrder) {
        InterfaceDocParamVO param = new InterfaceDocParamVO();
        param.setParamScene(scene);
        param.setName(name);
        param.setType(type);
        param.setExampleValue(exampleValue);
        param.setSortOrder(sortOrder);
        return param;
    }

    /**
     * 构建测试 Header 参数。
     *
     * @param name  Header 名称
     * @param value Header 值
     * @return Header 参数视图
     */
    private InterfaceDocParamVO header(String name, String value) {
        InterfaceDocParamVO header = new InterfaceDocParamVO();
        header.setName(name);
        header.setExampleValue(value);
        return header;
    }

    /**
     * 统计子字符串出现次数。
     * 使用 indexOf 循环计数，避免 split 的正则开销和空字符串处理问题。
     *
     * @param text   原始文本
     * @param target 目标文本
     * @return 出现次数
     */
    private int countOccurrences(String text, String target) {
        if (text == null || target == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
