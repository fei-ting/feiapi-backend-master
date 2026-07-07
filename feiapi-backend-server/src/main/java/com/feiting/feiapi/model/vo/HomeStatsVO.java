package com.feiting.feiapi.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 首页统计视图。
 */
@Data
public class HomeStatsVO implements Serializable {

    /**
     * 平台已上线接口数量。
     */
    @NotNull(message = "平台接口数量不能为空")
    private Long platformInterfaceCount;

    /**
     * 全部已记录真实接口调用次数。
     */
    @NotNull(message = "累计调用次数不能为空")
    private Long totalInvocations;

    /**
     * 全部调用日志成功率百分比，无调用数据时为空。
     */
    private Double successRate;

    /**
     * 全部调用日志平均响应耗时，单位毫秒，无调用数据时为空。
     */
    private Long averageResponseTimeMs;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
