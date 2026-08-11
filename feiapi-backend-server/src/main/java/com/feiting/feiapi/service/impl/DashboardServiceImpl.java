package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.config.DashboardProperties;
import com.feiting.feiapi.mapper.DashboardAnalysisMapper;
import com.feiting.feiapi.mapper.InterfaceChangeLogMapper;
import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;
import com.feiting.feiapi.model.vo.dashboard.DashboardAlertVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardChangeVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardInterfaceMetricVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardInvokeAggregateVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardOverviewVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendAggregateVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendPointVO;
import com.feiting.feiapi.model.vo.dashboard.DashboardTrendsVO;
import com.feiting.feiapi.service.DashboardService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceChangeLog;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员工作台统计服务实现。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 趋势图固定展示的数据桶数量。 */
    private static final int TREND_BUCKET_COUNT = 8;

    /** 每个趋势数据桶覆盖的小时数。 */
    private static final int TREND_BUCKET_HOURS = 1;

    /** 工作台统计 Mapper。 */
    private final DashboardAnalysisMapper dashboardAnalysisMapper;

    /** 接口变更审计 Mapper。 */
    private final InterfaceChangeLogMapper interfaceChangeLogMapper;

    /** 接口信息服务。 */
    private final InterfaceInfoService interfaceInfoService;

    /** 工作台阈值配置。 */
    private final DashboardProperties properties;

    /** 标准时间格式化器。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 创建工作台统计服务。
     *
     * @param dashboardAnalysisMapper 工作台统计 Mapper
     * @param interfaceChangeLogMapper 变更审计 Mapper
     * @param interfaceInfoService 接口信息服务
     * @param properties 工作台配置
     */
    public DashboardServiceImpl(DashboardAnalysisMapper dashboardAnalysisMapper,
                                InterfaceChangeLogMapper interfaceChangeLogMapper,
                                InterfaceInfoService interfaceInfoService,
                                DashboardProperties properties) {
        this.dashboardAnalysisMapper = dashboardAnalysisMapper;
        this.interfaceChangeLogMapper = interfaceChangeLogMapper;
        this.interfaceInfoService = interfaceInfoService;
        this.properties = properties;
    }

    /** {@inheritDoc} */
    @Override
    public DashboardOverviewVO getOverview() {
        Date now = new Date();
        Date todayStart = startOfDay(now);
        DashboardInvokeAggregateVO aggregate = dashboardAnalysisMapper.selectInvokeAggregate(todayStart, now);
        List<DashboardInterfaceMetricVO> metrics = loadMetrics(now);
        DashboardOverviewVO overview = new DashboardOverviewVO();
        overview.setTotalInterfaces(interfaceInfoService.count(new QueryWrapper<InterfaceInfo>().eq("is_delete", 0)));
        overview.setOnlineInterfaces(interfaceInfoService.count(new QueryWrapper<InterfaceInfo>()
                .eq("status", InterfaceInfoStatusEnum.ONLINE.getValue())));
        overview.setOfflineInterfaces(interfaceInfoService.count(new QueryWrapper<InterfaceInfo>()
                .eq("status", InterfaceInfoStatusEnum.OFFLINE.getValue())));
        overview.setTodayInvocations(valueOrZero(aggregate == null ? null : aggregate.getTotalInvocations()));
        overview.setTodayErrors(valueOrZero(aggregate == null ? null : aggregate.getErrorInvocations()));
        overview.setAbnormalInterfaces(metrics.stream().filter(this::isFailureOrSlow).count());
        return overview;
    }

    /** {@inheritDoc} */
    @Override
    public DashboardTrendsVO getTrends() {
        Date end = new Date();
        Date currentHour = startOfHour(end);
        Date start = plusHours(currentHour, -(TREND_BUCKET_COUNT - 1L) * TREND_BUCKET_HOURS);
        Map<Integer, DashboardTrendAggregateVO> aggregateMap = dashboardAnalysisMapper
                .selectTrendAggregates(start, end).stream()
                .collect(Collectors.toMap(DashboardTrendAggregateVO::getBucketIndex, item -> item,
                        (left, right) -> left));
        DashboardTrendsVO trends = new DashboardTrendsVO();
        for (int index = 0; index < TREND_BUCKET_COUNT; index++) {
            DashboardTrendAggregateVO aggregate = aggregateMap.get(index);
            long total = valueOrZero(aggregate == null ? null : aggregate.getTotalInvocations());
            long success = valueOrZero(aggregate == null ? null : aggregate.getSuccessInvocations());
            double successRate = total == 0 ? 0D : percentage(success, total);
            double errorRate = total == 0 ? 0D : percentage(total - success, total);
            double responseTime = aggregate == null || aggregate.getAverageResponseTimeMs() == null
                    ? 0D : aggregate.getAverageResponseTimeMs().setScale(1, RoundingMode.HALF_UP).doubleValue();
            String bucketTime = formatTime(plusHours(start, index * (long) TREND_BUCKET_HOURS));
            trends.getSuccessRate().add(point(bucketTime, successRate));
            trends.getInvocationCount().add(point(bucketTime, total));
            trends.getErrorRate().add(point(bucketTime, errorRate));
            trends.getResponseTime().add(point(bucketTime, responseTime));
        }
        return trends;
    }

    /** {@inheritDoc} */
    @Override
    public List<DashboardAlertVO> getAlerts() {
        Date now = new Date();
        Date currentHour = startOfHour(now);
        Date previousHour = plusHours(currentHour, -1);
        List<DashboardAlertVO> alerts = new ArrayList<>();
        for (DashboardInterfaceMetricVO metric : loadMetrics(now)) {
            DashboardAlertVO alert = buildAlert(metric, now, currentHour, previousHour);
            if (alert != null) {
                alerts.add(alert);
            }
        }
        return alerts.stream().sorted(Comparator.comparing(DashboardAlertVO::getTime).reversed())
                .limit(properties.getAlertLimit()).collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public List<DashboardChangeVO> getChanges() {
        return interfaceChangeLogMapper.selectRecent(10).stream().map(this::toChangeVO).collect(Collectors.toList());
    }

    /** 加载最近七日接口指标。 */
    private List<DashboardInterfaceMetricVO> loadMetrics(Date now) {
        Date start = plusDays(now, -properties.getUnusedDays());
        Date currentHour = startOfHour(now);
        return dashboardAnalysisMapper.selectInterfaceMetrics(
                start, plusHours(now, -24), now, currentHour, plusHours(currentHour, -1));
    }

    /** 构造接口最高优先级告警。 */
    private DashboardAlertVO buildAlert(DashboardInterfaceMetricVO metric, Date now, Date currentHour, Date previousHour) {
        long total = valueOrZero(metric.getTotalInvocations());
        long failed = valueOrZero(metric.getFailedInvocations());
        if (total >= properties.getMinimumInvocations()
                && ((double) failed / total) >= properties.getFailureRateThreshold()) {
            return alert(metric, "highFailureRate", String.format("失败率达到 %.1f%%，超过阈值", failed * 100D / total), metric.getLastInvokeTime());
        }
        if (total > 0 && valueOrZero(metric.getAverageResponseTimeMs()) >= properties.getSlowResponseThresholdMs()) {
            return alert(metric, "abnormal", "平均响应时间超过配置阈值", metric.getLastInvokeTime());
        }
        long current = valueOrZero(metric.getCurrentHourInvocations());
        long previous = valueOrZero(metric.getPreviousHourInvocations());
        if (current >= properties.getMinimumInvocations()
                && (previous == 0 || (double) current / previous >= properties.getSpikeMultiplier())) {
            return alert(metric, "spike", "最近一小时调用量较前一小时明显增长", now);
        }
        Date cutoff = plusDays(now, -properties.getUnusedDays());
        if (Objects.equals(metric.getStatus(), InterfaceInfoStatusEnum.ONLINE.getValue())
                && (metric.getLastInvokeTime() == null || metric.getLastInvokeTime().before(cutoff))) {
            return alert(metric, "unused", "已超过配置天数未被调用", metric.getLastInvokeTime() == null ? now : metric.getLastInvokeTime());
        }
        return null;
    }

    /** 创建告警视图。 */
    private DashboardAlertVO alert(DashboardInterfaceMetricVO metric, String type, String description, Date time) {
        DashboardAlertVO alert = new DashboardAlertVO();
        alert.setId(metric.getInterfaceInfoId());
        alert.setName(metric.getInterfaceName());
        alert.setAlertType(type);
        alert.setDescription(description);
        alert.setTime(formatTime(time == null ? new Date() : time));
        return alert;
    }

    /** 创建趋势点。 */
    private DashboardTrendPointVO point(String time, double value) {
        DashboardTrendPointVO point = new DashboardTrendPointVO();
        point.setTime(time);
        point.setValue(value);
        return point;
    }

    /** 将审计实体转换为前端视图。 */
    private DashboardChangeVO toChangeVO(InterfaceChangeLog log) {
        DashboardChangeVO change = new DashboardChangeVO();
        change.setId(log.getInterfaceInfoId());
        change.setName(log.getInterfaceName());
        change.setChangeType(log.getChangeType());
        change.setTime(formatTime(log.getEventTime()));
        return change;
    }

    /** 判断接口是否命中失败率或慢响应规则。 */
    private boolean isFailureOrSlow(DashboardInterfaceMetricVO metric) {
        long total = valueOrZero(metric.getTotalInvocations());
        long failed = valueOrZero(metric.getFailedInvocations());
        return (total >= properties.getMinimumInvocations()
                && ((double) failed / total) >= properties.getFailureRateThreshold())
                || (total > 0 && valueOrZero(metric.getAverageResponseTimeMs()) >= properties.getSlowResponseThresholdMs());
    }

    /** 计算百分比。 */
    private double percentage(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator * 100D / denominator).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 读取 Long 空值。 */
    private long valueOrZero(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /** 获取当天零点。 */
    private Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /** 获取当前小时零点。 */
    private Date startOfHour(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    /** 加小时。 */
    private Date plusHours(Date date, long hours) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR_OF_DAY, Math.toIntExact(hours));
        return calendar.getTime();
    }

    /** 加天数。 */
    private Date plusDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTime();
    }

    /** 格式化 ISO 时间。 */
    private String formatTime(Date date) {
        return date == null ? null : TIME_FORMATTER.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }
}
