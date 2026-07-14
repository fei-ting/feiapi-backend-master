package com.feiting.feiapi.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 接口文档内容安全校验器。
 *
 * <p>统一校验 JSON 示例、自由文本、参数校验规则和错误解决建议，避免不同入口使用不一致的安全规则。</p>
 */
@Component
public class InterfaceDocContentSecurityValidator {

    /** JSON 示例安全扫描允许的最大嵌套深度。 */
    private static final int MAX_JSON_SCAN_DEPTH = 64;

    /** 敏感字段标准化别名集合。 */
    private static final Set<String> SENSITIVE_FIELD_ALIASES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "accesskey", "secretkey", "apikey", "clientsecret",
            "token", "accesstoken", "refreshtoken", "idtoken", "authtoken",
            "authorization", "password", "userpassword", "passwd", "pwd", "密码", "密钥")));

    /** 允许使用的脱敏占位符名称集合。 */
    private static final Set<String> MASK_PLACEHOLDER_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "TOKEN", "ACCESS_KEY", "SECRET_KEY", "PASSWORD", "MASKED")));

    /** 手机号高置信度匹配规则。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 邮箱高置信度匹配规则。 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 身份证号高置信度匹配规则。 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    /** 自由文本中的敏感标签和值匹配规则。 */
    private static final Pattern SENSITIVE_LABEL_PATTERN = Pattern.compile(
            "(?im)(?<![A-Za-z0-9_])(?:access[\\s_.-]*key|secret[\\s_.-]*key|api[\\s_.-]*key|"
                    + "client[\\s_.-]*secret|access[\\s_.-]*token|refresh[\\s_.-]*token|id[\\s_.-]*token|"
                    + "auth[\\s_.-]*token|authorization|token|user[\\s_.-]*password|password|passwd|pwd|密码|密钥)"
                    + "\\s*['\"]?\\s*[:=]\\s*(.+)$");

    /** 自由文本中的认证方案和值匹配规则。 */
    private static final Pattern AUTH_SCHEME_PATTERN = Pattern.compile(
            "(?im)(?<![A-Za-z0-9_])(?:bearer|basic)[ \\t]+([^\\r\\n]+)");

    /** 脚本标签、协议和事件属性匹配规则。 */
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "(?i)<\\s*script|javascript\\s*:|(?:^|[^a-zA-Z0-9_])on[a-z]+\\s*=",
            Pattern.MULTILINE);

    /** 英文内部实现关键词匹配规则，使用 ASCII 标识符边界。 */
    private static final Pattern INTERNAL_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(?:db|database|mysql|postgresql|oracle|mongodb|redis|dubbo|nacos|jdbc)"
                    + "(?![A-Za-z0-9_])");

    /** 中文内部实现信息匹配规则。 */
    private static final Pattern INTERNAL_CHINESE_PATTERN = Pattern.compile("数据库|数据表|服务器绝对路径");

    /** Windows 服务器绝对路径匹配规则。 */
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("(?i)(?<![A-Za-z0-9_])[A-Za-z]:\\\\");

    /** Unix 服务器绝对路径匹配规则。 */
    private static final Pattern UNIX_PATH_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])/(?:var|etc|usr|opt|home|root|data)(?:/|$)");

    /**
     * 校验 JSON 示例的语法、深度和敏感内容。
     *
     * @param example      JSON 示例
     * @param errorMessage JSON 语法错误提示
     */
    public void validateJsonExample(String example, String errorMessage) {
        if (StringUtils.isBlank(example)) {
            return;
        }
        try {
            scanJsonElement(JsonParser.parseString(example), 0);
        } catch (JsonSyntaxException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 校验普通文档文本中的敏感信息。
     *
     * @param value 待校验文本
     */
    public void validateText(String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        String text = value.trim();
        if (PHONE_PATTERN.matcher(text).find()
                || (text.indexOf('@') >= 0 && EMAIL_PATTERN.matcher(text).find())
                || ID_CARD_PATTERN.matcher(text).find()
                || containsUnmaskedSensitiveValue(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档内容不能包含未脱敏敏感信息");
        }
    }

    /**
     * 校验参数校验规则中的敏感信息和脚本内容。
     *
     * @param validationRule 参数校验规则
     */
    public void validateValidationRule(String validationRule) {
        validateText(validationRule);
        if (StringUtils.isNotBlank(validationRule) && SCRIPT_PATTERN.matcher(validationRule).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "校验规则不能包含脚本内容");
        }
    }

    /**
     * 校验错误解决建议中的敏感信息和内部实现信息。
     *
     * @param solution 错误解决建议
     */
    public void validateSolution(String solution) {
        validateText(solution);
        if (StringUtils.isBlank(solution)) {
            return;
        }
        if (INTERNAL_KEYWORD_PATTERN.matcher(solution).find()
                || INTERNAL_CHINESE_PATTERN.matcher(solution).find()
                || WINDOWS_PATH_PATTERN.matcher(solution).find()
                || UNIX_PATH_PATTERN.matcher(solution).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误解决建议不能包含内部实现信息");
        }
    }

    /**
     * 递归扫描 JSON 元素。
     *
     * @param element 当前 JSON 元素
     * @param depth   当前嵌套深度
     */
    private void scanJsonElement(JsonElement element, int depth) {
        if (depth > MAX_JSON_SCAN_DEPTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "JSON 示例嵌套深度不能超过 64");
        }
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            scanJsonObject(element.getAsJsonObject(), depth);
            return;
        }
        if (element.isJsonArray()) {
            StreamSupport.stream(element.getAsJsonArray().spliterator(), false)
                    .forEach(child -> scanJsonElement(child, depth + 1));
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            validateText(element.getAsString());
        }
    }

    /**
     * 扫描 JSON 对象的字段名和值。
     *
     * @param object JSON 对象
     * @param depth  当前嵌套深度
     */
    private void scanJsonObject(JsonObject object, int depth) {
        object.entrySet().stream().forEach(entry -> {
            JsonElement fieldValue = entry.getValue();
            if (SENSITIVE_FIELD_ALIASES.contains(normalizeFieldName(entry.getKey()))) {
                validateSensitiveJsonFieldValue(fieldValue);
                return;
            }
            scanJsonElement(fieldValue, depth + 1);
        });
    }

    /**
     * 校验敏感 JSON 字段值，只允许空值和明确脱敏占位符。
     *
     * @param value 敏感字段值
     */
    private void validateSensitiveJsonFieldValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (StringUtils.isEmpty(text) || isAllowedPlaceholder(text)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档内容不能包含未脱敏敏感信息");
    }

    /**
     * 判断自由文本是否包含未脱敏的敏感标签值或认证凭据。
     *
     * @param text 待检查文本
     * @return 是否包含未脱敏敏感值
     */
    private boolean containsUnmaskedSensitiveValue(String text) {
        Matcher labelMatcher = SENSITIVE_LABEL_PATTERN.matcher(text);
        while (labelMatcher.find()) {
            if (!isEmptyOrAllowedPlaceholder(labelMatcher.group(1))) {
                return true;
            }
        }
        Matcher schemeMatcher = AUTH_SCHEME_PATTERN.matcher(text);
        while (schemeMatcher.find()) {
            String credential = stripOptionalQuotes(schemeMatcher.group(1));
            if (credential.length() >= 8 && !isAllowedPlaceholder(credential)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否为空或为允许的脱敏占位符。
     *
     * @param value 待检查文本
     * @return 是否允许
     */
    private boolean isEmptyOrAllowedPlaceholder(String value) {
        String normalizedValue = stripOptionalQuotes(value);
        return normalizedValue.isEmpty() || isAllowedPlaceholder(normalizedValue);
    }

    /**
     * 判断文本是否为白名单脱敏占位符。
     *
     * @param value 待检查文本
     * @return 是否为允许的占位符
     */
    private boolean isAllowedPlaceholder(String value) {
        String normalizedValue = stripOptionalQuotes(value);
        String placeholderValue = normalizedValue.replaceFirst("(?i)^(?:bearer|basic)[ \\t]+", "").trim();
        if (placeholderValue.matches("\\*{3,}")) {
            return true;
        }
        return MASK_PLACEHOLDER_NAMES.stream()
                .flatMap(name -> Stream.of("<" + name + ">", "${" + name + "}"))
                .anyMatch(placeholder -> placeholder.equalsIgnoreCase(placeholderValue));
    }

    /**
     * 移除值两端可选的成对单引号或双引号。
     *
     * @param value 原始文本
     * @return 去除可选引号后的文本
     */
    private String stripOptionalQuotes(String value) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.length() < 2) {
            return normalizedValue;
        }
        char first = normalizedValue.charAt(0);
        char last = normalizedValue.charAt(normalizedValue.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return normalizedValue.substring(1, normalizedValue.length() - 1).trim();
        }
        return normalizedValue;
    }

    /**
     * 按安全规则标准化 JSON 字段名。
     *
     * @param fieldName 原始字段名
     * @return 标准化字段名
     */
    private String normalizeFieldName(String fieldName) {
        String normalizedName = Normalizer.normalize(fieldName == null ? "" : fieldName, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalizedName.codePoints()
                .filter(Character::isLetterOrDigit)
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .collect(Collectors.joining());
    }
}
