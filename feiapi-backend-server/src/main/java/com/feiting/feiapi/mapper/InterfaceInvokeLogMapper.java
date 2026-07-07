package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapi.model.vo.HomeInvokeAggregateVO;
import com.feiting.feiapicommon.model.entity.InterfaceInvokeLog;

/**
 * 接口调用日志 Mapper。
 */
public interface InterfaceInvokeLogMapper extends BaseMapper<InterfaceInvokeLog> {

    /**
     * 聚合全部首页调用统计。
     *
     * @return 首页调用聚合数据
     */
    HomeInvokeAggregateVO getHomeInvokeAggregate();
}
