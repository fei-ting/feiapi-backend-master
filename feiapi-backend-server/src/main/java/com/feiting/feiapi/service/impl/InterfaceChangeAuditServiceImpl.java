package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceChangeLogMapper;
import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;
import com.feiting.feiapi.service.InterfaceChangeAuditService;
import com.feiting.feiapicommon.model.entity.InterfaceChangeLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 接口变更审计服务实现。
 */
@Service
public class InterfaceChangeAuditServiceImpl implements InterfaceChangeAuditService {

    /** 接口变更审计日志 Mapper。 */
    private final InterfaceChangeLogMapper interfaceChangeLogMapper;

    /**
     * 创建接口变更审计服务。
     *
     * @param interfaceChangeLogMapper 接口变更审计日志 Mapper
     */
    public InterfaceChangeAuditServiceImpl(InterfaceChangeLogMapper interfaceChangeLogMapper) {
        this.interfaceChangeLogMapper = interfaceChangeLogMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordChange(Long interfaceInfoId, String interfaceName, InterfaceChangeTypeEnum changeType) {
        if (interfaceInfoId == null || interfaceInfoId <= 0 || changeType == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Date eventTime = new Date();
        InterfaceChangeLog changeLog = new InterfaceChangeLog();
        changeLog.setInterfaceInfoId(interfaceInfoId);
        changeLog.setInterfaceName(interfaceName == null ? "未命名接口" : interfaceName);
        changeLog.setChangeType(changeType.getCode());
        changeLog.setEventTime(eventTime);
        changeLog.setCreateTime(eventTime);
        if (interfaceChangeLogMapper.insert(changeLog) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口变更审计记录写入失败");
        }
    }
}
