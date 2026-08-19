package com.feiting.feiapi.service;

/**
 * 系统管理员一次性初始化服务。
 */
public interface AdminBootstrapService {

    /**
     * 初始化系统管理员及管理员拥有的演示数据。
     *
     * <p>当系统中已经存在有效管理员时安全跳过，不覆盖任何已有凭据。</p>
     *
     * @param account         管理员账号
     * @param initialPassword 管理员初始密码
     * @param displayName     管理员显示名称
     * @param accessKey       管理员 AccessKey
     * @param secretKey       管理员 SecretKey
     * @return 是否创建了新的管理员
     */
    boolean initialize(String account, String initialPassword, String displayName,
                       String accessKey, String secretKey);
}
