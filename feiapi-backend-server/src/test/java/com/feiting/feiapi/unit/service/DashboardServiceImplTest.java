package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.config.DashboardProperties;
import com.feiting.feiapi.mapper.DashboardAnalysisMapper;
import com.feiting.feiapi.mapper.InterfaceChangeLogMapper;
import com.feiting.feiapi.model.vo.dashboard.DashboardInterfaceMetricVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardInvokeAggregateVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardOverviewVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendAggregateVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendsVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.impl.DashboardServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceChangeLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理员工作台统计服务单元测试。
 */
@DisplayName("管理员工作台统计服务单元测试")
class DashboardServiceImplTest {

    /** 工作台统计 Mapper。 */
    private DashboardAnalysisMapper dashboardAnalysisMapper;

    /** 变更审计 Mapper。 */
    private InterfaceChangeLogMapper interfaceChangeLogMapper;

    /** 接口信息服务。 */
    private InterfaceInfoService interfaceInfoService;

    /** 被测服务。 */
    private DashboardServiceImpl dashboardService;

    /** 初始化被测服务。 */
    @BeforeEach
    void setUp() {
        dashboardAnalysisMapper = mock(DashboardAnalysisMapper.class);
        interfaceChangeLogMapper = mock(InterfaceChangeLogMapper.class);
        interfaceInfoService = mock(InterfaceInfoService.class);
        dashboardService = new DashboardServiceImpl(
                dashboardAnalysisMapper, interfaceChangeLogMapper, interfaceInfoService, new DashboardProperties());
    }

    /** 概览应聚合真实接口数量和调用错误数量。 */
    @Test
    @DisplayName("概览统计聚合真实数据")
    void shouldAggregateOverviewFromRealMetrics() {
        when(interfaceInfoService.count(any())).thenReturn(6L, 4L, 2L);
        DashboardInvokeAggregateVO aggregate = new DashboardInvokeAggregateVO();
        aggregate.setTotalInvocations(100L);
        aggregate.setErrorInvocations(4L);
        when(dashboardAnalysisMapper.selectInvokeAggregate(any(), any())).thenReturn(aggregate);
        DashboardInterfaceMetricVO metric = metric(10L, 2L, 1200L);
        when(dashboardAnalysisMapper.selectInterfaceMetrics(any(), any(), any(), any(), any())).thenReturn(List.of(metric));

        DashboardOverviewVO result = dashboardService.getOverview();

        assertThat(result.getTotalInterfaces()).isEqualTo(6L);
        assertThat(result.getOnlineInterfaces()).isEqualTo(4L);
        assertThat(result.getOfflineInterfaces()).isEqualTo(2L);
        assertThat(result.getTodayInvocations()).isEqualTo(100L);
        assertThat(result.getTodayErrors()).isEqualTo(4L);
        assertThat(result.getAbnormalInterfaces()).isEqualTo(1L);
    }

    /** 趋势无调用时应补齐八个间隔一小时的整点零值数据点。 */
    @Test
    @DisplayName("趋势无数据补齐空桶")
    void shouldFillEmptyTrendBucketsWithZero() {
        when(dashboardAnalysisMapper.selectTrendAggregates(any(), any())).thenReturn(List.of());

        DashboardTrendsVO trends = dashboardService.getTrends();
        assertThat(trends.getSuccessRate()).hasSize(8)
                .allSatisfy(point -> {
                    assertThat(point.getValue()).isZero();
                    OffsetDateTime pointTime = OffsetDateTime.parse(point.getTime());
                    assertThat(pointTime.getMinute()).isZero();
                    assertThat(pointTime.getSecond()).isZero();
                });
        assertThat(trends.getInvocationCount()).hasSize(8);
        List<OffsetDateTime> bucketTimes = trends.getSuccessRate().stream()
                .map(point -> OffsetDateTime.parse(point.getTime()))
                .collect(java.util.stream.Collectors.toList());
        assertThat(IntStream.range(1, bucketTimes.size())
                .allMatch(index -> Duration.between(bucketTimes.get(index - 1), bucketTimes.get(index)).toHours() == 1L))
                .isTrue();
    }

    /** 失败率达到阈值时应生成高失败率告警。 */
    @Test
    @DisplayName("失败率达到阈值生成告警")
    void shouldCreateHighFailureRateAlert() {
        DashboardInterfaceMetricVO metric = metric(20L, 2L, 100L);
        when(dashboardAnalysisMapper.selectInterfaceMetrics(any(), any(), any(), any(), any())).thenReturn(List.of(metric));

        assertThat(dashboardService.getAlerts()).singleElement()
                .satisfies(alert -> {
                    assertThat(alert.getAlertType()).isEqualTo("highFailureRate");
                    assertThat(alert.getId()).isEqualTo(1L);
                });
    }

    /** 最近变更应转换审计快照并限制查询数量。 */
    @Test
    @DisplayName("最近变更读取审计记录")
    void shouldReadRecentChangesFromAuditLog() {
        InterfaceChangeLog log = new InterfaceChangeLog();
        log.setInterfaceInfoId(9L);
        log.setInterfaceName("测试接口");
        log.setChangeType("online");
        log.setEventTime(new Date());
        when(interfaceChangeLogMapper.selectRecent(anyInt())).thenReturn(List.of(log));

        assertThat(dashboardService.getChanges()).singleElement()
                .satisfies(change -> {
                    assertThat(change.getId()).isEqualTo(9L);
                    assertThat(change.getName()).isEqualTo("测试接口");
                    assertThat(change.getChangeType()).isEqualTo("online");
                });
    }

    /** 构造接口指标。 */
    private DashboardInterfaceMetricVO metric(long total, long failed, long responseTime) {
        DashboardInterfaceMetricVO metric = new DashboardInterfaceMetricVO();
        metric.setInterfaceInfoId(1L);
        metric.setInterfaceName("异常接口");
        metric.setTotalInvocations(total);
        metric.setFailedInvocations(failed);
        metric.setAverageResponseTimeMs(BigDecimal.valueOf(responseTime));
        metric.setCurrentHourInvocations(0L);
        metric.setPreviousHourInvocations(0L);
        metric.setLastInvokeTime(new Date());
        return metric;
    }
}
