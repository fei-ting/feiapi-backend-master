package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapi.model.vo.HomeStatsVO;
import com.feiting.feiapicommon.model.entity.InterfaceInvokeLog;

/**
 * 接口调用日志服务。
 */
public interface InterfaceInvokeLogService extends IService<InterfaceInvokeLog> {

    /**
     * 记录一次接口调用结果。
     *
     * @param userId         调用用户 ID
     * @param interfaceInfoId 接口 ID
     * @param path           接口请求路径
     * @param method         HTTP 请求方法
     * @param statusCode     下游响应状态码
     * @param success        是否调用成功
     * @param responseTimeMs 响应耗时，单位毫秒
     * @return 是否记录成功
     */
    boolean recordInvoke(long userId,
                         long interfaceInfoId,
                         String path,
                         String method,
                         Integer statusCode,
                         boolean success,
                         long responseTimeMs);

    /**
     * 获取首页统计数据。
     *
     * @return 首页统计数据
     */
    HomeStatsVO getHomeStats();
}
