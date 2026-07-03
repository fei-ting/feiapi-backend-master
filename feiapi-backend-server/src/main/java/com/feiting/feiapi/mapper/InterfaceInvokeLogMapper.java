package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapi.model.vo.HomeInvokeAggregateVO;
import com.feiting.feiapicommon.model.entity.InterfaceInvokeLog;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * 接口调用日志 Mapper。
 */
public interface InterfaceInvokeLogMapper extends BaseMapper<InterfaceInvokeLog> {

    /**
     * 聚合指定时间范围内的首页调用统计。
     *
     * @param startTime 统计开始时间（含）
     * @param endTime   统计结束时间（不含）
     * @return 首页调用聚合数据
     */
    HomeInvokeAggregateVO getHomeInvokeAggregate(@Param("startTime") Date startTime,
                                                @Param("endTime") Date endTime);
}
