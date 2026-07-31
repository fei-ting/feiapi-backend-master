package com.feiting.feiapi.utils;

import java.nio.charset.StandardCharsets;

/**
 * 文本字符数与字节数计算工具。
 */
public final class TextSizeUtils {

    /**
     * 工具类禁止实例化。
     */
    private TextSizeUtils() {
    }

    /**
     * 去除文本首尾的 Unicode 空白字符。
     *
     * @param value 原始文本
     * @return 去除首尾 Unicode 空白后的文本，原值为空时返回空字符串
     */
    public static String stripUnicodeWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /**
     * 计算去除首尾 Unicode 空白后的码点数量。
     *
     * @param value 原始文本
     * @return Unicode 码点数量
     */
    public static int unicodeLengthAfterStrip(String value) {
        String strippedValue = stripUnicodeWhitespace(value);
        return strippedValue.codePointCount(0, strippedValue.length());
    }

    /**
     * 计算文本编码为 UTF-8 后的实际字节数。
     *
     * @param value 原始文本
     * @return UTF-8 字节数
     */
    public static int utf8ByteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 判断码点是否属于 Unicode 空白或空格字符。
     *
     * @param codePoint Unicode 码点
     * @return 是否为空白字符
     */
    public static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
