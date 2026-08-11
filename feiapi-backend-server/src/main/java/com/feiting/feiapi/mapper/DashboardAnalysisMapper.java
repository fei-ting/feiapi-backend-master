package com.feiting.feiapi.mapper;

import com.feiting.feiapi.model.vo.dashboard.DashboardInterfaceMetricVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardInvokeAggregateVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendAggregateVO;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 管理员工作台聚合查询 Mapper。
 */
public interface DashboardAnalysisMapper {

    /**
     * 聚合指定时间范围内的调用统计。
     *
     * @param startTime 起始时间
     * @param endTime 结束时间
     * @return 调用聚合结果
     */
    @Select("SELECT COUNT(*) AS total_invocations, "
            + "COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS error_invocations "
            + "FROM interface_invoke_log WHERE is_delete = 0 "
            + "AND invoke_time >= #{startTime} AND invoke_time < #{endTime}")
    DashboardInvokeAggregateVO selectInvokeAggregate(@Param("startTime") Date startTime,
                                                       @Param("endTime") Date endTime);

    /**
     * 聚合最近8小时趋势数据。
     *
     * @param startTime 起始时间
     * @param endTime 结束时间
     * @return 自然小时桶聚合结果
     */
    @Select("SELECT bucket_index, COUNT(*) AS total_invocations, "
            + "COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_invocations, "
            + "AVG(response_time_ms) AS average_response_time_ms FROM ("
            + "SELECT FLOOR(TIMESTAMPDIFF(MINUTE, #{startTime}, invoke_time) / 60) AS bucket_index, "
            + "success, response_time_ms FROM interface_invoke_log WHERE is_delete = 0 "
            + "AND invoke_time >= #{startTime} AND invoke_time < #{endTime}) bucket_data "
            + "GROUP BY bucket_index ORDER BY bucket_index")
    List<DashboardTrendAggregateVO> selectTrendAggregates(@Param("startTime") Date startTime,
                                                            @Param("endTime") Date endTime);

    /**
     * 查询接口告警所需的最近七日聚合指标。
     *
     * @param startTime 七日起始时间
     * @param recentStartTime 最近24小时起始时间
     * @param endTime 当前时间
     * @param currentHourStart 当前小时起始时间
     * @param previousHourStart 前一小时起始时间
     * @return 接口聚合指标
     */
    @Select("SELECT i.id AS interface_info_id, i.name AS interface_name, i.status, "
            + "COALESCE(SUM(CASE WHEN l.invoke_time >= #{recentStartTime} THEN 1 ELSE 0 END), 0) AS total_invocations, "
            + "COALESCE(SUM(CASE WHEN l.invoke_time >= #{recentStartTime} AND l.success = 0 THEN 1 ELSE 0 END), 0) AS failed_invocations, "
            + "AVG(CASE WHEN l.invoke_time >= #{recentStartTime} THEN l.response_time_ms END) AS average_response_time_ms, "
            + "COALESCE(SUM(CASE WHEN l.invoke_time >= #{currentHourStart} THEN 1 ELSE 0 END), 0) AS current_hour_invocations, "
            + "COALESCE(SUM(CASE WHEN l.invoke_time >= #{previousHourStart} AND l.invoke_time < #{currentHourStart} THEN 1 ELSE 0 END), 0) AS previous_hour_invocations, "
            + "MAX(l.invoke_time) AS last_invoke_time "
            + "FROM interface_info i LEFT JOIN interface_invoke_log l "
            + "ON l.interface_info_id = i.id AND l.is_delete = 0 AND l.invoke_time >= #{startTime} AND l.invoke_time < #{endTime} "
            + "WHERE i.is_delete = 0 GROUP BY i.id, i.name, i.status")
    List<DashboardInterfaceMetricVO> selectInterfaceMetrics(@Param("startTime") Date startTime,
                                                              @Param("recentStartTime") Date recentStartTime,
                                                              @Param("endTime") Date endTime,
                                                              @Param("currentHourStart") Date currentHourStart,
                                                              @Param("previousHourStart") Date previousHourStart);
}
