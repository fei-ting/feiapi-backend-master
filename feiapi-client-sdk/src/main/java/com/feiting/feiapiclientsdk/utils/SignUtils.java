package com.feiting.feiapiclientsdk.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;

import java.nio.charset.StandardCharsets;

/**
 * 生成签名
 */
public class SignUtils {

    public static final String SALT = "feiting";

    public static String getSign(String secretKey, String method, String path, String nonce, String timestamp, String body) {
        // 第二阶段整改的核心目标有两个：
        // 1. 将原来的“固定字符串摘要”升级为真正的 HMAC-SHA256
        // 2. 让签名与本次请求的真实语义绑定，而不是只和用户 secretKey 绑定
        //
        // 这里的 body 如果为空（例如 GET 请求），统一按空字符串处理。
        // 这样可以确保 SDK 与网关两端在“无请求体”的场景下得到完全一致的签名原文，
        // 避免一端使用 null、另一端使用 "" 导致验签失败。
        String signBody = body == null ? "" : body;

        // 这里构造的是规范化签名原文（canonical string）。
        // 第二阶段要求 method、path、nonce、timestamp、真实 body 都参与签名，
        // 目的是让这些字段一旦被篡改，最终签名就会变化。
        //
        // SALT 仍然保留在原文里，用来保持项目现有签名体系的连续性；
        // 但真正提供安全性的核心已经从“拼接字符串做 SHA256”
        // 变成“以 secretKey 作为密钥做 HMAC-SHA256”。
        //
        // 注意：SDK 与网关必须严格使用完全相同的字段顺序、分隔符和编码，
        // 否则即使业务字段相同，签名结果也会不一致。
        String canonicalString = SALT + "\n"
                + method + "\n"
                + path + "\n"
                + nonce + "\n"
                + timestamp + "\n"
                + signBody;

        // HMAC-SHA256 与第一阶段的裸 SHA256 不同：
        // - secretKey 在这里是“密钥”，不是普通字符串拼接项
        // - 更适合做 API 请求签名，语义也更标准
        HMac hMac = SecureUtil.hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8));

        // 统一按 UTF-8 对规范化原文做摘要，保证不同运行环境下签名结果稳定一致。
        return hMac.digestHex(canonicalString, StandardCharsets.UTF_8);
    }
}
