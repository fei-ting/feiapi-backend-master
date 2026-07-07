package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储占位实现
 * <p>
 * 当前版本仅预留阿里云 OSS 对接入口，不执行文件落盘或远程上传。
 * </p>
 */
@Service
public class PlaceholderObjectStorageServiceImpl implements ObjectStorageService {

    /**
     * 上传用户头像并返回可访问地址
     *
     * @param file   头像文件
     * @param userId 当前登录用户 id
     * @return 头像访问地址
     */
    @Override
    public String uploadUserAvatar(MultipartFile file, Long userId) {
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "对象存储暂未接入");
    }
}
