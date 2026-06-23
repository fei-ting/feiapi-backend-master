package com.feiting.feiapicommon.utils;

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;

/**
 * 接口真实目标地址安全校验器。
 *
 * <p>该工具用于拦截接口 targetHost 指向本机、内网、链路本地地址等高危目标，避免网关成为 SSRF 跳板。</p>
 */
public final class InterfaceTargetHostValidator {

    /**
     * IPv4 私有和特殊地址段定义。
     */
    private static final CidrBlock[] FORBIDDEN_IPV4_CIDR_BLOCKS = {
            new CidrBlock("0.0.0.0", 8),
            new CidrBlock("10.0.0.0", 8),
            new CidrBlock("100.64.0.0", 10),
            new CidrBlock("127.0.0.0", 8),
            new CidrBlock("169.254.0.0", 16),
            new CidrBlock("172.16.0.0", 12),
            new CidrBlock("192.0.0.0", 24),
            new CidrBlock("192.0.2.0", 24),
            new CidrBlock("192.168.0.0", 16),
            new CidrBlock("198.18.0.0", 15),
            new CidrBlock("198.51.100.0", 24),
            new CidrBlock("203.0.113.0", 24),
            new CidrBlock("224.0.0.0", 4),
            new CidrBlock("240.0.0.0", 4)
    };

    private InterfaceTargetHostValidator() {
    }

    /**
     * 判断目标地址是否可安全转发。
     *
     * @param targetHost       真实后端服务地址
     * @param allowedHostnames 允许访问的主机名白名单
     * @return 是否安全
     */
    public static boolean isSafeTargetHost(String targetHost, Collection<String> allowedHostnames) {
        if (StringUtils.isBlank(targetHost)) {
            return false;
        }
        URI uri = parseUri(targetHost);
        if (uri == null || !isAllowedScheme(uri.getScheme()) || StringUtils.isBlank(uri.getHost()) || uri.getUserInfo() != null) {
            return false;
        }

        String normalizedHost = normalizeHost(uri.getHost());
        if (!isAllowedHostname(normalizedHost, allowedHostnames)) {
            return false;
        }
        return !isForbiddenHost(normalizedHost);
    }

    /**
     * 判断协议是否只使用 HTTP/HTTPS。
     *
     * @param scheme URI 协议
     * @return 是否允许
     */
    private static boolean isAllowedScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    /**
     * 解析 URI。
     *
     * @param targetHost 真实后端服务地址
     * @return URI 对象，解析失败时返回 null
     */
    private static URI parseUri(String targetHost) {
        try {
            return new URI(targetHost.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 标准化主机名。
     *
     * @param host 原始主机名
     * @return 小写后的主机名
     */
    private static String normalizeHost(String host) {
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            return normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        return normalizedHost;
    }

    /**
     * 判断主机名是否在白名单中。
     *
     * @param host             标准化主机名
     * @param allowedHostnames 允许访问的主机名白名单
     * @return 是否允许
     */
    private static boolean isAllowedHostname(String host, Collection<String> allowedHostnames) {
        if (allowedHostnames == null || allowedHostnames.isEmpty()) {
            return false;
        }
        return allowedHostnames.stream()
                .filter(StringUtils::isNotBlank)
                .map(InterfaceTargetHostValidator::normalizeHost)
                .anyMatch(host::equals);
    }

    /**
     * 判断主机是否为禁止访问的本机、内网或特殊地址。
     *
     * @param host 标准化主机名
     * @return 是否禁止
     */
    private static boolean isForbiddenHost(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            return true;
        }
        if (isSingleLabelServiceName(host)) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isForbiddenAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /**
     * 判断是否为 Docker/Kubernetes 常见的单标签服务名。
     *
     * <p>单标签服务名必须先命中白名单才会走到这里；允许它不依赖本机 DNS 解析，避免本地测试和容器内解析结果不一致。</p>
     *
     * @param host 标准化主机名
     * @return 是否为单标签服务名
     */
    private static boolean isSingleLabelServiceName(String host) {
        return host.indexOf('.') < 0 && host.indexOf(':') < 0 && !isIpv4Literal(host);
    }

    /**
     * 判断主机是否为 IPv4 字面量。
     *
     * @param host 标准化主机名
     * @return 是否为 IPv4 字面量
     */
    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!StringUtils.isNumeric(part)) {
                return false;
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断解析后的 IP 地址是否命中禁止范围。
     *
     * @param address IP 地址
     * @return 是否禁止
     */
    private static boolean isForbiddenAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isForbiddenIpv4Address(address.getAddress());
    }

    /**
     * 判断 IPv4 地址是否命中私有和特殊地址段。
     *
     * @param addressBytes IP 地址字节
     * @return 是否禁止
     */
    private static boolean isForbiddenIpv4Address(byte[] addressBytes) {
        if (addressBytes.length != 4) {
            return false;
        }
        int address = ipv4ToInt(addressBytes);
        for (CidrBlock cidrBlock : FORBIDDEN_IPV4_CIDR_BLOCKS) {
            if (cidrBlock.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 IPv4 地址字节转换为整数。
     *
     * @param bytes IP 地址字节
     * @return 整数形式 IPv4 地址
     */
    private static int ipv4ToInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    /**
     * IPv4 CIDR 地址段。
     */
    private static final class CidrBlock {

        /**
         * 网络地址。
         */
        private final int network;

        /**
         * 网络掩码。
         */
        private final int mask;

        /**
         * 构造 IPv4 CIDR 地址段。
         *
         * @param networkAddress 网络地址
         * @param prefixLength   掩码长度
         */
        private CidrBlock(String networkAddress, int prefixLength) {
            this.network = ipv4StringToInt(networkAddress);
            this.mask = prefixLength == 0 ? 0 : (int) (0xffffffffL << (32 - prefixLength));
        }

        /**
         * 判断目标地址是否落入当前地址段。
         *
         * @param address 目标地址
         * @return 是否包含
         */
        private boolean contains(int address) {
            return (address & mask) == (network & mask);
        }

        /**
         * 将 IPv4 字符串转换为整数。
         *
         * @param address IPv4 字符串
         * @return 整数形式 IPv4 地址
         */
        private static int ipv4StringToInt(String address) {
            String[] parts = address.split("\\.");
            int result = 0;
            for (String part : parts) {
                result = (result << 8) | Integer.parseInt(part);
            }
            return result;
        }
    }
}
