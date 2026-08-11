package com.feiting.feiapi.service;

import com.feiting.feiapi.model.vo.dashboard.DashboardAlertVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardChangeVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardOverviewVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendsVO;

import java.util.List;

/**
 * 管理员工作台统计服务。
 */
public interface DashboardService {

    /**
     * 获取工作台概览数据。
     *
     * @return 概览数据
     */
    DashboardOverviewVO getOverview();

    /**
     * 获取最近8小时趋势数据。
     *
     * @return 趋势数据
     */
    DashboardTrendsVO getTrends();

    /**
     * 获取重点关注接口。
     *
     * @return 告警列表
     */
    List<DashboardAlertVO> getAlerts();

    /**
     * 获取最近接口变更记录。
     *
     * @return 变更列表
     */
    List<DashboardChangeVO> getChanges();
}
