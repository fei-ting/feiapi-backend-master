package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceInvokeLogMapper;
import com.feiting.feiapi.model.vo.HomeInvokeAggregateVO;
import com.feiting.feiapi.model.vo.HomeStatsVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceInvokeLogService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.InterfaceInvokeLog;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 接口调用日志服务实现。
 */
@Service
public class InterfaceInvokeLogServiceImpl extends ServiceImpl<InterfaceInvokeLogMapper, InterfaceInvokeLog>
        implements InterfaceInvokeLogService {

    @Resource
    private InterfaceInvokeLogMapper interfaceInvokeLogMapper;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Override
    public boolean recordInvoke(long userId,
                                long interfaceInfoId,
                                String path,
                                String method,
                                Integer statusCode,
                                boolean success,
                                long responseTimeMs) {
        if (userId <= 0 || interfaceInfoId <= 0 || StringUtils.isAnyBlank(path, method)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "调用日志参数不完整");
        }
        InterfaceInvokeLog interfaceInvokeLog = new InterfaceInvokeLog();
        interfaceInvokeLog.setUserId(userId);
        interfaceInvokeLog.setInterfaceInfoId(interfaceInfoId);
        interfaceInvokeLog.setPath(path.trim());
        interfaceInvokeLog.setMethod(InterfaceInfoMethodEnum.normalize(method));
        interfaceInvokeLog.setStatusCode(statusCode);
        interfaceInvokeLog.setSuccess(success ? 1 : 0);
        interfaceInvokeLog.setResponseTimeMs(Math.max(responseTimeMs, 0L));
        interfaceInvokeLog.setInvokeTime(new Date());
        interfaceInvokeLog.setIsDelete(0);
        return save(interfaceInvokeLog);
    }

    @Override
    public HomeStatsVO getHomeStats() {
        HomeStatsVO homeStatsVO = new HomeStatsVO();
        homeStatsVO.setPlatformInterfaceCount(countOnlineInterfaces());

        HomeInvokeAggregateVO aggregate = interfaceInvokeLogMapper.getHomeInvokeAggregate();
        long totalInvocations = aggregate == null || aggregate.getTotalInvocations() == null
                ? 0L
                : aggregate.getTotalInvocations();
        homeStatsVO.setTotalInvocations(totalInvocations);
        if (totalInvocations <= 0) {
            homeStatsVO.setSuccessRate(null);
            homeStatsVO.setAverageResponseTimeMs(null);
            return homeStatsVO;
        }

        long successInvocations = aggregate.getSuccessInvocations() == null ? 0L : aggregate.getSuccessInvocations();
        homeStatsVO.setSuccessRate(calculateSuccessRate(successInvocations, totalInvocations));
        homeStatsVO.setAverageResponseTimeMs(aggregate.getAverageResponseTimeMs() == null
                ? null
                : aggregate.getAverageResponseTimeMs().setScale(0, RoundingMode.HALF_UP).longValue());
        return homeStatsVO;
    }

    /**
     * 统计已上线接口数量。
     *
     * @return 已上线接口数量
     */
    private Long countOnlineInterfaces() {
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", InterfaceInfoStatusEnum.ONLINE.getValue());
        return interfaceInfoService.count(queryWrapper);
    }

    /**
     * 计算成功率百分比。
     *
     * @param successInvocations 成功调用次数
     * @param totalInvocations   调用总次数
     * @return 成功率百分比
     */
    private Double calculateSuccessRate(long successInvocations, long totalInvocations) {
        return BigDecimal.valueOf(successInvocations)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalInvocations), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
