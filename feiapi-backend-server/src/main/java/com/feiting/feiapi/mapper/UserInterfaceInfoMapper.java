package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;

import org.apache.ibatis.annotations.Param;

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

    /**
     * 幂等初始化用户与接口的调用关系
     *
     * @param userId 用户id
     * @param interfaceInfoId 接口id
     * @param leftNum 剩余调用次数
     * @param totalNum 总调用次数
     * @return 影响行数
     */
    int insertIgnoreIfAbsent(@Param("userId") long userId,
                             @Param("interfaceInfoId") long interfaceInfoId,
                             @Param("leftNum") int leftNum,
                             @Param("totalNum") int totalNum);

    /**
     * 批量幂等初始化用户与接口的调用关系。
     *
     * @param userInterfaceInfoList 用户接口调用关系列表
     * @return 影响行数
     */
    int batchInsertIgnoreIfAbsent(@Param("userInterfaceInfoList") List<UserInterfaceInfo> userInterfaceInfoList);

    /**
     * 幂等创建免费无限接口统计关系，并累加一次总调用次数。
     *
     * @param userId 用户id
     * @param interfaceInfoId 接口id
     * @return 影响行数
     */
    int insertOrIncreaseTotalNum(@Param("userId") long userId,
                                 @Param("interfaceInfoId") long interfaceInfoId);

    /**
     * 返还免费无限接口的一次统计次数，不修改剩余额度。
     *
     * @param userId 用户id
     * @param interfaceInfoId 接口id
     * @return 影响行数
     */
    int decreaseTotalNumOnly(@Param("userId") long userId,
                             @Param("interfaceInfoId") long interfaceInfoId);

}




