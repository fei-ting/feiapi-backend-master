package com.feiting.feiapi.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储服务
 */
public interface ObjectStorageService {

    /**
     * 上传用户头像并返回可访问地址
     *
     * @param file   头像文件
     * @param userId 当前登录用户 id
     * @return 头像访问地址
     */
    String uploadUserAvatar(MultipartFile file, Long userId);
}
