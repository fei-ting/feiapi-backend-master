package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.InterfaceDefinitionChangeDetector;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocLifecycleService;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfacePublishCheckService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import java.util.Date;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接口信息生命周期服务实现。
 */
@Service
public class InterfaceInfoLifecycleServiceImpl implements InterfaceInfoLifecycleService {

    /**
     * 发布中状态的最大保留时间。
     */
    private static final long PUBLISHING_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 接口信息数据访问对象。
     */
    private final InterfaceInfoMapper interfaceInfoMapper;

    /**
     * 接口文档服务。
     */
    private final InterfaceDocLifecycleService interfaceDocLifecycleService;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 接口定义变更检测器。
     */
    private final InterfaceDefinitionChangeDetector definitionChangeDetector;

    /**
     * 发布前静态检查服务。
     */
    private final InterfacePublishCheckService interfacePublishCheckService;

    /**
     * 创建接口信息生命周期服务。
     *
     * @param interfaceInfoService 接口信息服务
     * @param interfaceInfoMapper  接口信息数据访问对象
     * @param interfaceDocLifecycleService 文档生命周期协作服务
     * @param sdkMethodRegistry            SDK 方法注册器
     * @param definitionChangeDetector     接口定义变更检测器
     */
    public InterfaceInfoLifecycleServiceImpl(InterfaceInfoService interfaceInfoService,
                                             InterfaceInfoMapper interfaceInfoMapper,
                                             InterfaceDocLifecycleService interfaceDocLifecycleService,
                                             SdkMethodRegistry sdkMethodRegistry,
                                             InterfaceDefinitionChangeDetector definitionChangeDetector,
                                             InterfacePublishCheckService interfacePublishCheckService) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceInfoMapper = interfaceInfoMapper;
        this.interfaceDocLifecycleService = interfaceDocLifecycleService;
        this.sdkMethodRegistry = sdkMethodRegistry;
        this.definitionChangeDetector = definitionChangeDetector;
        this.interfacePublishCheckService = interfacePublishCheckService;
    }

    /**
     * 新增接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        boolean result = interfaceInfoService.save(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        interfaceDocLifecycleService.initializeFromDefinition(toDefinitionSnapshot(interfaceInfo));
        return interfaceInfo.getId();
    }

    /**
     * 更新接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validateInterfaceInfoId(interfaceInfo.getId());
        InterfaceInfo oldInterfaceInfo = interfaceInfoMapper.selectByIdForUpdate(interfaceInfo.getId());
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        assertOffline(oldInterfaceInfo, "接口仅允许在下线状态修改");
        boolean result = interfaceInfoService.lambdaUpdate()
                .eq(InterfaceInfo::getId, interfaceInfo.getId())
                .eq(InterfaceInfo::getStatus, InterfaceInfoStatusEnum.OFFLINE.getValue())
                .update(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
        InterfaceInfo latestInterfaceInfo = interfaceInfoService.getById(interfaceInfo.getId());
        if (latestInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean controlledConfigChanged = definitionChangeDetector.controlledConfigChanged(oldInterfaceInfo, latestInterfaceInfo);
        if (definitionChangeDetector.requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)) {
            interfaceDocLifecycleService.synchronizeRequestParams(toDefinitionSnapshot(latestInterfaceInfo));
        }
        if (controlledConfigChanged) {
            interfaceDocLifecycleService.downgradeToDraft(latestInterfaceInfo.getId());
        }
        return true;
    }

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoMapper.selectByIdForUpdate(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        assertDeletableOffline(interfaceInfo);
        interfaceDocLifecycleService.deleteAllByInterfaceInfoId(interfaceInfoId);
        int deletedRows = interfaceInfoMapper.logicDeleteOfflineById(
                interfaceInfoId,
                InterfaceInfoStatusEnum.OFFLINE.getValue()
        );
        if (deletedRows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
        return true;
    }

    /**
     * 校验发布条件并将下线接口切换为发布中状态。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布中的接口快照
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterfaceInfo startPublishing(Long interfaceInfoId) {
        return startPublishingWithContext(interfaceInfoId).getInterfaceInfo();
    }

    /**
     * 校验发布条件并将下线接口切换为发布中状态，返回完整发布上下文。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布上下文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InterfacePublishContext startPublishingWithContext(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoMapper.selectByIdForUpdate(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        recoverExpiredPublishingStatus(interfaceInfo);
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            if (Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.PUBLISHING.getValue())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口正在发布验证中，请稍后重试");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅支持从下线状态发布");
        }

        InterfacePublishContext publishContext = interfacePublishCheckService.buildContextForPublish(interfaceInfo);
        updatePublishingStatus(interfaceInfoId,
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                "接口发布状态更新失败，请刷新后重试");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.PUBLISHING.getValue());
        publishContext.setInterfaceInfo(interfaceInfo);
        return publishContext;
    }

    /**
     * 将发布中的接口切换为上线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePublishing(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        updatePublishingStatus(interfaceInfoId,
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.ONLINE.getValue(),
                "接口发布状态已变化，请刷新后重试");
    }

    /**
     * 将发布中的接口恢复为下线状态。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackPublishing(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        updatePublishingStatus(interfaceInfoId,
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口发布验证失败后回滚状态失败");
    }

    /**
     * 条件更新接口发布状态和更新时间。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param expectedStatus  期望状态
     * @param targetStatus    目标状态
     * @param errorMessage    更新失败提示
     */
    private void updatePublishingStatus(Long interfaceInfoId,
                                        int expectedStatus,
                                        int targetStatus,
                                        String errorMessage) {
        boolean result = interfaceInfoService.lambdaUpdate()
                .eq(InterfaceInfo::getId, interfaceInfoId)
                .eq(InterfaceInfo::getStatus, expectedStatus)
                .set(InterfaceInfo::getStatus, targetStatus)
                .set(InterfaceInfo::getUpdateTime, new Date())
                .update();
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 在持有接口行锁时恢复超时的发布中状态。
     *
     * @param interfaceInfo 接口信息
     */
    private void recoverExpiredPublishingStatus(InterfaceInfo interfaceInfo) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.PUBLISHING.getValue())) {
            return;
        }
        Date updateTime = interfaceInfo.getUpdateTime();
        if (updateTime == null || System.currentTimeMillis() - updateTime.getTime() <= PUBLISHING_TIMEOUT_MILLIS) {
            return;
        }
        updatePublishingStatus(interfaceInfo.getId(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口发布验证状态恢复失败，请刷新后重试");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
    }

    /**
     * 将接口信息转换为文档域使用的定义快照。
     *
     * @param interfaceInfo 接口信息
     * @return 接口定义快照
     */
    private InterfaceDefinitionSnapshot toDefinitionSnapshot(InterfaceInfo interfaceInfo) {
        return InterfaceDefinitionSnapshot.builder()
                .interfaceInfoId(interfaceInfo.getId())
                .name(interfaceInfo.getName())
                .sdkMethodName(interfaceInfo.getSdkMethodName())
                .description(interfaceInfo.getDescription())
                .url(interfaceInfo.getUrl())
                .path(interfaceInfo.getPath())
                .targetHost(interfaceInfo.getTargetHost())
                .requestParams(interfaceInfo.getRequestParams())
                .requestHeader(interfaceInfo.getRequestHeader())
                .responseHeader(interfaceInfo.getResponseHeader())
                .status(interfaceInfo.getStatus())
                .method(interfaceInfo.getMethod())
                .quotaType(interfaceInfo.getQuotaType())
                .userId(interfaceInfo.getUserId())
                .createTime(interfaceInfo.getCreateTime())
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

    /**
     * 校验接口是否处于下线状态。
     *
     * @param interfaceInfo 接口信息
     * @param errorMessage  状态不匹配时的错误提示
     */
    private void assertOffline(InterfaceInfo interfaceInfo, String errorMessage) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 校验接口是否允许删除。
     *
     * @param interfaceInfo 接口信息
     */
    private void assertDeletableOffline(InterfaceInfo interfaceInfo) {
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

}
