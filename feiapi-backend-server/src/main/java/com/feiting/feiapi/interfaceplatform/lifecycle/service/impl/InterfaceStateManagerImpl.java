package com.feiting.feiapi.interfaceplatform.lifecycle.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.lifecycle.model.snapshot.LockedInterfaceSnapshot;
import com.feiting.feiapi.interfaceplatform.lifecycle.service.api.InterfaceStateManager;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

/**
 * 接口生命周期状态管理服务实现。
 *
 * <p>集中维护接口主记录行锁、状态断言、发布状态迁移和逻辑删除规则。</p>
 */
@Service
public class InterfaceStateManagerImpl implements InterfaceStateManager {

    /**
     * 发布中状态的最大保留时间。
     */
    private static final long PUBLISHING_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    /**
     * 接口信息数据访问对象。
     */
    private final InterfaceInfoMapper interfaceInfoMapper;

    /**
     * 创建接口生命周期状态管理服务实现。
     *
     * @param interfaceInfoMapper 接口信息数据访问对象
     */
    public InterfaceStateManagerImpl(InterfaceInfoMapper interfaceInfoMapper) {
        this.interfaceInfoMapper = interfaceInfoMapper;
    }

    /**
     * 锁定接口主记录并返回只读快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 已锁定接口快照
     */
    @Override
    public LockedInterfaceSnapshot lockForUpdate(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoMapper.selectByIdForUpdate(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return toLockedSnapshot(interfaceInfo);
    }

    /**
     * 断言接口处于下线状态。
     *
     * @param interfaceInfo 已锁定接口快照
     */
    @Override
    public void assertOffline(LockedInterfaceSnapshot interfaceInfo) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅允许在下线状态修改");
        }
    }

    /**
     * 断言接口处于上线状态。
     *
     * @param interfaceInfo 已锁定接口快照
     */
    @Override
    public void assertOnline(LockedInterfaceSnapshot interfaceInfo) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.ONLINE.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅支持从上线状态下线");
        }
    }

    /**
     * 断言接口允许删除。
     *
     * @param interfaceInfo 已锁定接口快照
     */
    @Override
    public void assertDeletableOffline(LockedInterfaceSnapshot interfaceInfo) {
        if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            return;
        }
        if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.ONLINE.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先下线接口后再删除");
        }
        if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.PUBLISHING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口正在发布验证中，不能删除");
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态异常，不能删除");
    }

    /**
     * 在持有行锁时恢复超时的发布中状态。
     *
     * @param interfaceInfo 已锁定接口快照
     * @return 恢复后的接口快照
     */
    @Override
    public LockedInterfaceSnapshot recoverExpiredPublishingStatus(LockedInterfaceSnapshot interfaceInfo) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.PUBLISHING.getValue())) {
            return interfaceInfo;
        }
        Date updateTime = interfaceInfo.getUpdateTime();
        if (updateTime == null || System.currentTimeMillis() - updateTime.getTime() <= PUBLISHING_TIMEOUT_MILLIS) {
            return interfaceInfo;
        }
        updateStatusWithTime(interfaceInfo.getInterfaceInfoId(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口发布验证状态恢复失败，请刷新后重试");
        return LockedInterfaceSnapshot.builder()
                .interfaceInfoId(interfaceInfo.getInterfaceInfoId())
                .name(interfaceInfo.getName())
                .status(InterfaceInfoStatusEnum.OFFLINE.getValue())
                .updateTime(new Date())
                .build();
    }

    /**
     * 将接口标记为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void markPublishing(Long interfaceInfoId) {
        updateStatusWithTime(interfaceInfoId,
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                "接口发布状态更新失败，请刷新后重试");
    }

    /**
     * 将接口标记为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void markOnline(Long interfaceInfoId) {
        updateStatusWithTime(interfaceInfoId,
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.ONLINE.getValue(),
                "接口发布状态已变化，请刷新后重试");
    }

    /**
     * 将接口从发布中回滚为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void rollbackToOffline(Long interfaceInfoId) {
        updateStatusWithTime(interfaceInfoId,
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口发布验证失败后回滚状态失败");
    }

    /**
     * 将接口从上线状态切换为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void markOffline(Long interfaceInfoId) {
        updateStatus(interfaceInfoId,
                InterfaceInfoStatusEnum.ONLINE.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口下线状态已变化，请刷新后重试");
    }

    /**
     * 逻辑删除下线接口主记录。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    public void deleteOffline(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        int deletedRows = interfaceInfoMapper.logicDeleteOfflineById(
                interfaceInfoId,
                InterfaceInfoStatusEnum.OFFLINE.getValue());
        if (deletedRows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
    }

    /**
     * 按期望状态条件更新接口状态。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param expectedStatus  期望状态
     * @param targetStatus    目标状态
     * @param errorMessage    更新失败提示
     */
    private void updateStatus(Long interfaceInfoId,
                              int expectedStatus,
                              int targetStatus,
                              String errorMessage) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(interfaceInfoId);
        interfaceInfo.setStatus(targetStatus);
        UpdateWrapper<InterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", interfaceInfoId);
        updateWrapper.eq("status", expectedStatus);
        int updatedRows = interfaceInfoMapper.update(interfaceInfo, updateWrapper);
        if (updatedRows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 按期望状态条件更新接口状态和更新时间。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param expectedStatus  期望状态
     * @param targetStatus    目标状态
     * @param errorMessage    更新失败提示
     */
    private void updateStatusWithTime(Long interfaceInfoId,
                                      int expectedStatus,
                                      int targetStatus,
                                      String errorMessage) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(interfaceInfoId);
        interfaceInfo.setStatus(targetStatus);
        interfaceInfo.setUpdateTime(new Date());
        UpdateWrapper<InterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", interfaceInfoId);
        updateWrapper.eq("status", expectedStatus);
        int updatedRows = interfaceInfoMapper.update(interfaceInfo, updateWrapper);
        if (updatedRows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 将接口信息实体转换为已锁定快照。
     *
     * @param interfaceInfo 接口信息实体
     * @return 已锁定接口快照
     */
    private LockedInterfaceSnapshot toLockedSnapshot(InterfaceInfo interfaceInfo) {
        return LockedInterfaceSnapshot.builder()
                .interfaceInfoId(interfaceInfo.getId())
                .name(interfaceInfo.getName())
                .status(interfaceInfo.getStatus())
                .updateTime(interfaceInfo.getUpdateTime())
                .build();
    }

    /**
     * 校验接口信息 ID。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void validateInterfaceInfoId(Long interfaceInfoId) {
        if (interfaceInfoId == null || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }
}
