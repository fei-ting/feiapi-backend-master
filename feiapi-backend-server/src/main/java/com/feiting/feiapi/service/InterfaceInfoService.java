package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Service
* @createDate 2023-02-20 21:59:30
*/
public interface InterfaceInfoService extends IService<InterfaceInfo> {

    /**
     * 校验
     *
     * @param interfaceInfo
     * @param add     是否为创建校验
     */
    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add);
}
