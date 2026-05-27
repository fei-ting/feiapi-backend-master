package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;

import java.util.List;

/**
* @author asus
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service
* @createDate 2023-03-02 22:31:51
*/
public interface UserInterfaceInfoService extends IService<UserInterfaceInfo> {


    /**
     * 校验
     * @param userInterfaceInfo
     * @param add     是否为创建校验
     */
    void validUserInterfaceInfo(UserInterfaceInfo userInterfaceInfo, boolean add);


    /**
     * 统计调用次数
     * @param userId
     * @param interfaceInfoId
     * @return
     */
    boolean invokeCount(long userId, long interfaceInfoId);

    /**
     * 接口剩余调用次数是否足够
     * @param userId
     * @param interfaceInfoId
     * @return
     */
    boolean leftNumIsEnough(long userId, long interfaceInfoId);

    /**
     * 接口调用次数排行的集合
     * @param limit
     * @return
     */
    List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit);
}
