package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserInterfaceInfoMapper;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoQueryRequest;
import com.feiting.feiapi.model.vo.UserInterfaceInfoVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户接口调用关系服务实现。
 */
@Service
public class UserInterfaceInfoServiceImpl extends ServiceImpl<UserInterfaceInfoMapper, UserInterfaceInfo>
        implements UserInterfaceInfoService {

    @Resource
    private UserInterfaceInfoMapper userInterfaceInfoMapper;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    @Override
    public void validUserInterfaceInfo(UserInterfaceInfo userInterfaceInfo, boolean add) {
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (add && (userInterfaceInfo.getUserId() <= 0 || userInterfaceInfo.getInterfaceInfoId() <= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户或接口不存在");
        }
        if (userInterfaceInfo.getLeftNum() != null && userInterfaceInfo.getLeftNum() < 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "剩余调用次数不能小于0");
        }
    }

    @Override
    public boolean invokeCount(long userId, long interfaceInfoId) {
        checkUserAndInterfaceId(userId, interfaceInfoId);
        InterfaceQuotaTypeEnum quotaTypeEnum = getQuotaTypeEnum(interfaceInfoId);
        if (!quotaTypeEnum.isLimited()) {
            return userInterfaceInfoMapper.insertOrIncreaseTotalNum(userId, interfaceInfoId) > 0;
        }

        UpdateWrapper<UserInterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", userId);
        updateWrapper.eq("interface_info_id", interfaceInfoId);
        updateWrapper.eq("is_delete", 0);
        updateWrapper.gt("left_num", 0);
        updateWrapper.setSql("left_num = left_num - 1, total_num = total_num + 1");
        return this.update(updateWrapper);
    }

    @Override
    public boolean rollbackInvokeCount(long userId, long interfaceInfoId) {
        checkUserAndInterfaceId(userId, interfaceInfoId);
        InterfaceQuotaTypeEnum quotaTypeEnum = getQuotaTypeEnum(interfaceInfoId);
        if (!quotaTypeEnum.isLimited()) {
            return userInterfaceInfoMapper.decreaseTotalNumOnly(userId, interfaceInfoId) > 0;
        }

        UpdateWrapper<UserInterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("user_id", userId);
        updateWrapper.eq("interface_info_id", interfaceInfoId);
        updateWrapper.eq("is_delete", 0);
        updateWrapper.gt("total_num", 0);
        updateWrapper.setSql("left_num = left_num + 1, total_num = total_num - 1");
        return this.update(updateWrapper);
    }

    @Override
    public boolean leftNumIsEnough(long userId, long interfaceInfoId) {
        checkUserAndInterfaceId(userId, interfaceInfoId);
        InterfaceInfo interfaceInfo = getInterfaceInfoOrThrow(interfaceInfoId);
        InterfaceQuotaTypeEnum quotaTypeEnum = resolveQuotaTypeEnum(interfaceInfo);
        if (!quotaTypeEnum.isLimited()) {
            return true;
        }

        UserInterfaceInfo userInterfaceInfo = initLimitedQuotaIfAbsent(userId, interfaceInfo, quotaTypeEnum);
        if (userInterfaceInfo.getLeftNum() == null || userInterfaceInfo.getLeftNum() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口调用次数不足，请充值");
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantInitialQuotaForNewUser(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        Map<String, Integer> initialQuotaMap = interfaceQuotaConfigService.getInitialQuotaMap();
        List<UserInterfaceInfo> initRelations = listNotDeletedInterfaces().stream()
                .filter(interfaceInfo -> resolveQuotaTypeEnum(interfaceInfo).isLimited())
                .map(interfaceInfo -> buildInitRelation(userId, interfaceInfo, initialQuotaMap, false))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        batchInsertIgnoreIfAbsent(initRelations);
    }

    @Override
    public Page<UserInterfaceInfoVO> listMyQuotaPage(UserInterfaceInfoQueryRequest queryRequest, long userId) {
        if (queryRequest == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper<InterfaceInfo> interfaceQueryWrapper = new QueryWrapper<>();
        interfaceQueryWrapper.eq(queryRequest.getInterfaceInfoId() != null, "id", queryRequest.getInterfaceInfoId());
        interfaceQueryWrapper.eq(queryRequest.getStatus() != null, "status", queryRequest.getStatus());
        interfaceQueryWrapper.orderByAsc("id");
        Page<InterfaceInfo> interfaceInfoPage = interfaceInfoService.page(new Page<>(current, size), interfaceQueryWrapper);
        List<InterfaceInfo> interfaceInfos = interfaceInfoPage.getRecords();

        Map<Long, UserInterfaceInfo> existingRelationMap = listRelations(userId, interfaceInfos).stream()
                .collect(Collectors.toMap(UserInterfaceInfo::getInterfaceInfoId, Function.identity(), (oldValue, newValue) -> oldValue));
        Map<String, Integer> initialQuotaMap = interfaceQuotaConfigService.getInitialQuotaMap();
        List<UserInterfaceInfo> missingInitRelations = interfaceInfos.stream()
                .filter(interfaceInfo -> !existingRelationMap.containsKey(interfaceInfo.getId()))
                .filter(interfaceInfo -> resolveQuotaTypeEnum(interfaceInfo).isLimited())
                .map(interfaceInfo -> buildInitRelation(userId, interfaceInfo, initialQuotaMap, true))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        batchInsertIgnoreIfAbsent(missingInitRelations);
        Map<Long, UserInterfaceInfo> finalRelationMap = existingRelationMap;
        if (!missingInitRelations.isEmpty()) {
            finalRelationMap = listRelations(userId, interfaceInfos).stream()
                    .collect(Collectors.toMap(UserInterfaceInfo::getInterfaceInfoId, Function.identity(), (oldValue, newValue) -> oldValue));
        }
        Map<Long, UserInterfaceInfo> quotaRelationMap = finalRelationMap;
        List<UserInterfaceInfoVO> records = interfaceInfos.stream()
                .map(interfaceInfo -> toUserInterfaceInfoVO(userId, interfaceInfo, quotaRelationMap.get(interfaceInfo.getId()), initialQuotaMap))
                .collect(Collectors.toList());

        Page<UserInterfaceInfoVO> resultPage = new Page<>(interfaceInfoPage.getCurrent(), interfaceInfoPage.getSize(), interfaceInfoPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public List<UserInterfaceInfo> listTopInvokeInterfaceInfo(int limit) {
        return userInterfaceInfoMapper.listTopInvokeInterfaceInfo(limit);
    }

    @Override
    public Map<Long, Integer> listTotalNumByInterfaceInfoIds(List<Long> interfaceInfoIds) {
        if (interfaceInfoIds == null || interfaceInfoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> validInterfaceInfoIds = interfaceInfoIds.stream()
                .filter(Objects::nonNull)
                .filter(interfaceInfoId -> interfaceInfoId > 0)
                .distinct()
                .collect(Collectors.toList());
        if (validInterfaceInfoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInterfaceInfoMapper.listTotalNumByInterfaceInfoIds(validInterfaceInfoIds).stream()
                .collect(Collectors.toMap(
                        UserInterfaceInfo::getInterfaceInfoId,
                        userInterfaceInfo -> userInterfaceInfo.getTotalNum() == null ? 0 : userInterfaceInfo.getTotalNum(),
                        Integer::sum
                ));
    }

    /**
     * 构建用户接口初始化关系。
     *
     * @param userId          用户 ID
     * @param interfaceInfo   接口信息
     * @param initialQuotaMap 配额类型与初始额度映射
     * @param errorOnInvalid  初始额度非法时是否抛出异常
     * @return 用户接口关系，跳过时返回 null
     */
    private UserInterfaceInfo buildInitRelation(long userId,
                                                InterfaceInfo interfaceInfo,
                                                Map<String, Integer> initialQuotaMap,
                                                boolean errorOnInvalid) {
        InterfaceQuotaTypeEnum quotaTypeEnum = resolveQuotaTypeEnum(interfaceInfo);
        int initialQuota = initialQuotaMap.getOrDefault(quotaTypeEnum.getValue(), quotaTypeEnum.getDefaultInitialQuota());
        if (initialQuota <= 0) {
            if (errorOnInvalid) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "有限额度接口初始额度配置错误");
            }
            return null;
        }
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        userInterfaceInfo.setUserId(userId);
        userInterfaceInfo.setInterfaceInfoId(interfaceInfo.getId());
        userInterfaceInfo.setLeftNum(initialQuota);
        userInterfaceInfo.setTotalNum(0);
        userInterfaceInfo.setStatus(0);
        return userInterfaceInfo;
    }

    /**
     * 批量幂等初始化用户接口关系。
     *
     * @param initRelations 待初始化的用户接口关系列表
     */
    private void batchInsertIgnoreIfAbsent(List<UserInterfaceInfo> initRelations) {
        if (initRelations == null || initRelations.isEmpty()) {
            return;
        }
        userInterfaceInfoMapper.batchInsertIgnoreIfAbsent(initRelations);
    }

    /**
     * 初始化有限额度接口关系，已存在时不重置额度。
     *
     * @param userId        用户 ID
     * @param interfaceInfo 接口信息
     * @param quotaTypeEnum 配额类型
     * @return 用户接口关系
     */
    private UserInterfaceInfo initLimitedQuotaIfAbsent(long userId, InterfaceInfo interfaceInfo, InterfaceQuotaTypeEnum quotaTypeEnum) {
        UserInterfaceInfo userInterfaceInfo = getUserInterfaceInfo(userId, interfaceInfo.getId());
        if (userInterfaceInfo != null) {
            return userInterfaceInfo;
        }
        int initialQuota = interfaceQuotaConfigService.getInitialQuota(quotaTypeEnum);
        if (initialQuota <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "有限额度接口初始额度配置错误");
        }
        userInterfaceInfoMapper.insertIgnoreIfAbsent(userId, interfaceInfo.getId(), initialQuota, 0);
        userInterfaceInfo = getUserInterfaceInfo(userId, interfaceInfo.getId());
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化接口调用关系失败");
        }
        return userInterfaceInfo;
    }

    /**
     * 查询未删除接口列表。
     *
     * @return 未删除接口列表
     */
    private List<InterfaceInfo> listNotDeletedInterfaces() {
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("id");
        return interfaceInfoService.list(queryWrapper);
    }

    /**
     * 批量查询用户与接口的关系。
     *
     * @param userId         用户 ID
     * @param interfaceInfos 接口列表
     * @return 用户接口关系列表
     */
    private List<UserInterfaceInfo> listRelations(long userId, List<InterfaceInfo> interfaceInfos) {
        List<Long> interfaceInfoIds = interfaceInfos.stream()
                .map(InterfaceInfo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (interfaceInfoIds.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.in("interface_info_id", interfaceInfoIds);
        queryWrapper.eq("is_delete", 0);
        return list(queryWrapper);
    }

    /**
     * 转换为用户接口额度视图。
     *
     * @param userId            用户 ID
     * @param interfaceInfo     接口信息
     * @param userInterfaceInfo 用户接口关系
     * @param initialQuotaMap    配额类型与初始额度映射
     * @return 用户接口额度视图
     */
    private UserInterfaceInfoVO toUserInterfaceInfoVO(long userId,
                                                      InterfaceInfo interfaceInfo,
                                                      UserInterfaceInfo userInterfaceInfo,
                                                      Map<String, Integer> initialQuotaMap) {
        InterfaceQuotaTypeEnum quotaTypeEnum = resolveQuotaTypeEnum(interfaceInfo);
        UserInterfaceInfoVO userInterfaceInfoVO = new UserInterfaceInfoVO();
        userInterfaceInfoVO.setId(userInterfaceInfo == null ? null : userInterfaceInfo.getId());
        userInterfaceInfoVO.setUserId(userId);
        userInterfaceInfoVO.setInterfaceInfoId(interfaceInfo.getId());
        userInterfaceInfoVO.setInterfaceName(interfaceInfo.getName());
        userInterfaceInfoVO.setInterfacePath(interfaceInfo.getPath());
        userInterfaceInfoVO.setMethod(interfaceInfo.getMethod());
        userInterfaceInfoVO.setInterfaceStatus(interfaceInfo.getStatus());
        userInterfaceInfoVO.setQuotaType(quotaTypeEnum.getValue());
        userInterfaceInfoVO.setQuotaTypeText(quotaTypeEnum.getText());
        userInterfaceInfoVO.setInitialQuota(initialQuotaMap.getOrDefault(quotaTypeEnum.getValue(), quotaTypeEnum.getDefaultInitialQuota()));
        userInterfaceInfoVO.setTotalNum(userInterfaceInfo == null ? 0 : userInterfaceInfo.getTotalNum());
        userInterfaceInfoVO.setLeftNum(userInterfaceInfo == null ? 0 : userInterfaceInfo.getLeftNum());
        userInterfaceInfoVO.setStatus(userInterfaceInfo == null ? 0 : userInterfaceInfo.getStatus());
        userInterfaceInfoVO.setUpdateTime(userInterfaceInfo == null ? null : userInterfaceInfo.getUpdateTime());
        return userInterfaceInfoVO;
    }

    /**
     * 校验用户和接口 ID。
     *
     * @param userId          用户 ID
     * @param interfaceInfoId 接口 ID
     */
    private void checkUserAndInterfaceId(long userId, long interfaceInfoId) {
        if (userId <= 0 || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口或用户不存在");
        }
    }

    /**
     * 查询接口信息，不存在时抛出业务异常。
     *
     * @param interfaceInfoId 接口 ID
     * @return 接口信息
     */
    private InterfaceInfo getInterfaceInfoOrThrow(long interfaceInfoId) {
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "接口不存在");
        }
        return interfaceInfo;
    }

    /**
     * 获取接口配额类型。
     *
     * @param interfaceInfoId 接口 ID
     * @return 接口配额类型
     */
    private InterfaceQuotaTypeEnum getQuotaTypeEnum(long interfaceInfoId) {
        return resolveQuotaTypeEnum(getInterfaceInfoOrThrow(interfaceInfoId));
    }

    /**
     * 解析接口配额类型，缺失时使用基础额度兜底。
     *
     * @param interfaceInfo 接口信息
     * @return 接口配额类型
     */
    private InterfaceQuotaTypeEnum resolveQuotaTypeEnum(InterfaceInfo interfaceInfo) {
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(interfaceInfo.getQuotaType());
        return quotaTypeEnum == null ? InterfaceQuotaTypeEnum.BASIC_QUOTA : quotaTypeEnum;
    }

    /**
     * 查询用户接口关系。
     *
     * @param userId          用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 用户接口关系
     */
    private UserInterfaceInfo getUserInterfaceInfo(long userId, long interfaceInfoId) {
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("interface_info_id", interfaceInfoId);
        queryWrapper.eq("is_delete", 0);
        return userInterfaceInfoMapper.selectOne(queryWrapper);
    }
}
