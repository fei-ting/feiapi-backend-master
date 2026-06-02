package com.feiting.feiapi.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Part;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日志脱敏工具类
 */
public final class LogDesensitizeUtils {

    private static final Gson GSON = new Gson();

    private static final String MASK = "******";

    private static final Set<String> SENSITIVE_FIELD_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password",
            "userpassword",
            "checkpassword",
            "oldpassword",
            "newpassword",
            "secretkey",
            "accesskey",
            "sign",
            "token",
            "authorization",
            "authorizationtoken",
            "refreshtoken"
    )));

    private LogDesensitizeUtils() {
    }

    /**
     * 脱敏参数数组，跳过不适合记录的对象。
     */
    public static String toSafeJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        JsonArray jsonArray = new JsonArray();
        for (Object arg : args) {
            if (!isLoggableObject(arg)) {
                continue;
            }
            jsonArray.add(maskJsonElement(toJsonElement(arg)));
        }
        return GSON.toJson(jsonArray);
    }

    /**
     * 脱敏 Map 结构，适合 query 参数等场景。
     */
    public static Map<String, Object> toSafeMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveField(key)) {
                result.put(key, MASK);
            } else {
                result.put(key, fromJsonElement(maskJsonElement(toJsonElement(value))));
            }
        }
        return result;
    }

    private static JsonElement toJsonElement(Object data) {
        if (data == null) {
            return JsonNull.INSTANCE;
        }
        try {
            return GSON.toJsonTree(data);
        } catch (RuntimeException e) {
            return new JsonPrimitive(String.valueOf(data));
        }
    }

    private static JsonElement maskJsonElement(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (jsonElement.isJsonObject()) {
            JsonObject sourceObject = jsonElement.getAsJsonObject();
            JsonObject targetObject = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : sourceObject.entrySet()) {
                String fieldName = entry.getKey();
                if (isSensitiveField(fieldName)) {
                    targetObject.addProperty(fieldName, MASK);
                } else {
                    targetObject.add(fieldName, maskJsonElement(entry.getValue()));
                }
            }
            return targetObject;
        }
        if (jsonElement.isJsonArray()) {
            JsonArray targetArray = new JsonArray();
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                targetArray.add(maskJsonElement(element));
            }
            return targetArray;
        }
        return jsonElement;
    }

    private static Object fromJsonElement(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        }
        return GSON.fromJson(jsonElement, Object.class);
    }

    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_FIELD_NAMES.contains(normalizedFieldName)) {
            return true;
        }
        return normalizedFieldName.contains("password")
                || normalizedFieldName.contains("secret")
                || normalizedFieldName.contains("token")
                || normalizedFieldName.contains("sign")
                || normalizedFieldName.contains("authorization")
                || normalizedFieldName.contains("accesskey");
    }

    private static boolean isLoggableObject(Object arg) {
        if (arg == null) {
            return false;
        }
        if (arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof Part
                || arg instanceof BindingResult) {
            return false;
        }
        Class<?> argClass = arg.getClass();
        if (argClass.isArray()) {
            int length = Array.getLength(arg);
            for (int i = 0; i < length; i++) {
                if (isLoggableObject(Array.get(arg, i))) {
                    return true;
                }
            }
            return false;
        }
        if (arg instanceof Collection) {
            for (Object item : (Collection<?>) arg) {
                if (isLoggableObject(item)) {
                    return true;
                }
            }
            return false;
        }
        if (arg instanceof Map) {
            for (Object value : ((Map<?, ?>) arg).values()) {
                if (isLoggableObject(value)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
