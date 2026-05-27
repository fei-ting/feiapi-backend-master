package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;

import java.util.List;

/**
* @author asus
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Mapper
* @createDate 2023-03-02 22:31:51
* @Entity com.feiting.feiapi.model.entity.UserInterfaceInfo
*/
public interface UserInterfaceInfoMapper extends BaseMapper<UserInterfaceInfo> {

    /**
     * 接口调用次数排行的集合
     * @param limit
     * @return
     */
    List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit);

}




