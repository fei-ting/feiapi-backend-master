package com.feiting.feiapi.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 管理员工作台告警阈值配置。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "feiapi.dashboard")
public class DashboardProperties {

    /** 失败率阈值。 */
    @DecimalMin(value = "0.0", inclusive = true)
    private double failureRateThreshold = 0.05D;

    /** 触发失败率告警所需的最小调用量。 */
    @Min(1)
    private int minimumInvocations = 5;

    /** 慢响应阈值，单位毫秒。 */
    @Min(0)
    private long slowResponseThresholdMs = 1000L;

    /** 调用突增倍数。 */
    @DecimalMin(value = "1.0", inclusive = true)
    private double spikeMultiplier = 1.5D;

    /** 无调用判定天数。 */
    @Min(1)
    private int unusedDays = 7;

    /** 重点关注最多返回条数。 */
    @Min(1)
    private int alertLimit = 10;
}
