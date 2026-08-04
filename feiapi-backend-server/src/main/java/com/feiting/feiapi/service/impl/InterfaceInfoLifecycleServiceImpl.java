package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfacePublishCheckService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Stream;
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
    private final InterfaceDocService interfaceDocService;

    /**
     * 接口文档参数服务。
     */
    private final InterfaceDocParamService interfaceDocParamService;

    /**
     * 接口文档错误码服务。
     */
    private final InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 发布前静态检查服务。
     */
    private final InterfacePublishCheckService interfacePublishCheckService;

    /**
     * 创建接口信息生命周期服务。
     *
     * @param interfaceInfoService 接口信息服务
     * @param interfaceInfoMapper  接口信息数据访问对象
     * @param interfaceDocService          接口文档服务
     * @param interfaceDocParamService     接口文档参数服务
     * @param interfaceDocErrorCodeService 接口文档错误码服务
     * @param sdkMethodRegistry            SDK 方法注册器
     */
    public InterfaceInfoLifecycleServiceImpl(InterfaceInfoService interfaceInfoService,
                                             InterfaceInfoMapper interfaceInfoMapper,
                                             InterfaceDocService interfaceDocService,
                                             InterfaceDocParamService interfaceDocParamService,
                                             InterfaceDocErrorCodeService interfaceDocErrorCodeService,
                                             SdkMethodRegistry sdkMethodRegistry,
                                             InterfacePublishCheckService interfacePublishCheckService) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceInfoMapper = interfaceInfoMapper;
        this.interfaceDocService = interfaceDocService;
        this.interfaceDocParamService = interfaceDocParamService;
        this.interfaceDocErrorCodeService = interfaceDocErrorCodeService;
        this.sdkMethodRegistry = sdkMethodRegistry;
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
        interfaceDocService.syncRequestDocFromInterfaceInfo(interfaceInfo);
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
        boolean controlledConfigChanged = controlledConfigChanged(oldInterfaceInfo, latestInterfaceInfo);
        if (requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)) {
            interfaceDocService.syncRequestDocFromInterfaceInfo(latestInterfaceInfo);
        }
        if (controlledConfigChanged) {
            interfaceDocService.downgradeToDraft(latestInterfaceInfo.getId());
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
        interfaceDocParamService.lambdaUpdate()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .remove();
        interfaceDocErrorCodeService.lambdaUpdate()
                .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                .remove();
        interfaceDocService.lambdaUpdate()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                .remove();
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

    /**
     * 判断运行时请求参数模板是否变化。
     * 方法和请求参数变化在此处负责触发请求文档对账同步，同时也属于受控配置变化并触发文档降级。
     *
     * @param oldInterfaceInfo    更新前接口信息
     * @param latestInterfaceInfo 更新后接口信息
     * @return 请求文档模板是否变化
     */
    private boolean requestDocTemplateChanged(InterfaceInfo oldInterfaceInfo, InterfaceInfo latestInterfaceInfo) {
        return !Objects.equals(oldInterfaceInfo.getRequestParams(), latestInterfaceInfo.getRequestParams())
                || !Objects.equals(oldInterfaceInfo.getMethod(), latestInterfaceInfo.getMethod());
    }

    /**
     * 判断管理员维护的受控接口配置是否发生有效变化。
     * 方法和请求参数与模板变化判断有意重叠，此处只负责决定是否将已维护文档降为草稿。
     *
     * @param oldInterfaceInfo    更新前接口信息
     * @param latestInterfaceInfo 更新后的数据库最终值
     * @return 是否发生有效变化
     */
    private boolean controlledConfigChanged(InterfaceInfo oldInterfaceInfo,
                                            InterfaceInfo latestInterfaceInfo) {
        return Stream.of(
                        !Objects.equals(oldInterfaceInfo.getName(), latestInterfaceInfo.getName()),
                        !Objects.equals(oldInterfaceInfo.getDescription(), latestInterfaceInfo.getDescription()),
                        !Objects.equals(oldInterfaceInfo.getMethod(), latestInterfaceInfo.getMethod()),
                        !Objects.equals(oldInterfaceInfo.getPath(), latestInterfaceInfo.getPath()),
                        !Objects.equals(oldInterfaceInfo.getTargetHost(), latestInterfaceInfo.getTargetHost()),
                        !Objects.equals(oldInterfaceInfo.getUrl(), latestInterfaceInfo.getUrl()),
                        !Objects.equals(oldInterfaceInfo.getQuotaType(), latestInterfaceInfo.getQuotaType()),
                        !Objects.equals(oldInterfaceInfo.getSdkMethodName(), latestInterfaceInfo.getSdkMethodName()),
                        !Objects.equals(oldInterfaceInfo.getRequestParams(), latestInterfaceInfo.getRequestParams()))
                .anyMatch(Boolean.TRUE::equals);
    }
}
