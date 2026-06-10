package com.feiting.feiapicommon.service;

/**
 * 用户接口调用关系内部服务。
 */
public interface InnerUserInterfaceInfoService {

    /**
     * 统计用户调用接口次数。
     *
     * @param userId 用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 是否统计成功
     */
    boolean invokeCount(long userId, long interfaceInfoId);

    /**
     * 返还一次已预扣的用户接口调用次数。
     *
     * @param userId 用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 是否返还成功
     */
    boolean rollbackInvokeCount(long userId, long interfaceInfoId);

    /**
     * 检查用户是否还有指定接口的剩余调用次数。
     *
     * @param userId 用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 剩余次数是否足够
     */
    boolean leftNumIsEnough(long userId, long interfaceInfoId);
}
