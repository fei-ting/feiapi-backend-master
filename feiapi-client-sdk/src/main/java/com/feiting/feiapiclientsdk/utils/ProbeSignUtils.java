package com.feiting.feiapiclientsdk.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;

import java.nio.charset.StandardCharsets;

/**
 * 发布探测请求内部签名工具。
 */
public final class ProbeSignUtils {

    private static final String SALT = "feiapi-probe";

    private ProbeSignUtils() {
    }

    public static String getSign(String probeSecret, String method, String path, String nonce, String timestamp) {
        String canonicalString = SALT + "\n"
                + method + "\n"
                + path + "\n"
                + nonce + "\n"
                + timestamp;
        HMac hMac = SecureUtil.hmacSha256(probeSecret.getBytes(StandardCharsets.UTF_8));
        return hMac.digestHex(canonicalString, StandardCharsets.UTF_8);
    }
}
