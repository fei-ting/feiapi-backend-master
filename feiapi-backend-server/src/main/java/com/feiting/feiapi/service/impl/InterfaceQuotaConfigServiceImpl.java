package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceQuotaConfigMapper;
import com.feiting.feiapi.model.vo.InterfaceQuotaConfigVO;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 接口配额类型配置服务实现。
 */
@Service
public class InterfaceQuotaConfigServiceImpl extends ServiceImpl<InterfaceQuotaConfigMapper, InterfaceQuotaConfig>
        implements InterfaceQuotaConfigService {

    @Override
    public List<InterfaceQuotaConfigVO> listConfigVO() {
        Map<String, InterfaceQuotaConfig> configMap = listActiveConfigs().stream()
                .collect(Collectors.toMap(InterfaceQuotaConfig::getQuotaType, config -> config, (oldValue, newValue) -> oldValue));
        return Arrays.stream(InterfaceQuotaTypeEnum.values())
                .map(quotaTypeEnum -> toConfigVO(quotaTypeEnum, configMap.get(quotaTypeEnum.getValue())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateInitialQuota(String quotaType, Integer initialQuota) {
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(quotaType);
        if (quotaTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配额类型不合法");
        }
        if (!quotaTypeEnum.isLimited()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "免费无限接口不允许修改初始额度");
        }
        if (initialQuota == null || initialQuota <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "有限额度类型的初始额度必须大于 0");
        }
        initDefaultConfigIfAbsent(quotaTypeEnum);
        InterfaceQuotaConfig config = getByQuotaType(quotaTypeEnum.getValue());
        config.setInitialQuota(initialQuota);
        config.setDescription(quotaTypeEnum.getText());
        return updateById(config);
    }

    @Override
    public int getInitialQuota(InterfaceQuotaTypeEnum quotaTypeEnum) {
        if (quotaTypeEnum == null) {
            return InterfaceQuotaTypeEnum.BASIC_QUOTA.getDefaultInitialQuota();
        }
        InterfaceQuotaConfig config = getByQuotaType(quotaTypeEnum.getValue());
        if (config == null || config.getInitialQuota() == null) {
            return quotaTypeEnum.getDefaultInitialQuota();
        }
        return config.getInitialQuota();
    }

    @Override
    public Map<String, Integer> getInitialQuotaMap() {
        Map<String, InterfaceQuotaConfig> configMap = listActiveConfigs().stream()
                .collect(Collectors.toMap(InterfaceQuotaConfig::getQuotaType, config -> config, (oldValue, newValue) -> oldValue));
        return Arrays.stream(InterfaceQuotaTypeEnum.values())
                .collect(Collectors.toMap(InterfaceQuotaTypeEnum::getValue, quotaTypeEnum -> {
                    InterfaceQuotaConfig config = configMap.get(quotaTypeEnum.getValue());
                    if (config == null || config.getInitialQuota() == null) {
                        return quotaTypeEnum.getDefaultInitialQuota();
                    }
                    return config.getInitialQuota();
                }));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultConfigsIfAbsent() {
        Arrays.stream(InterfaceQuotaTypeEnum.values()).forEach(this::initDefaultConfigIfAbsent);
    }

    /**
     * 初始化单个内置配额类型配置。
     *
     * @param quotaTypeEnum 配额类型枚举
     */
    private void initDefaultConfigIfAbsent(InterfaceQuotaTypeEnum quotaTypeEnum) {
        baseMapper.insertDefaultConfigIgnore(quotaTypeEnum.getValue(),
                quotaTypeEnum.getDefaultInitialQuota(),
                quotaTypeEnum.getText());
    }

    /**
     * 根据配额类型查询配置。
     *
     * @param quotaType 配额类型
     * @return 配额配置
     */
    private InterfaceQuotaConfig getByQuotaType(String quotaType) {
        QueryWrapper<InterfaceQuotaConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("quota_type", quotaType);
        return getOne(queryWrapper, false);
    }

    /**
     * 查询未删除的配额配置列表。
     *
     * @return 配额配置列表
     */
    private List<InterfaceQuotaConfig> listActiveConfigs() {
        QueryWrapper<InterfaceQuotaConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_delete", 0);
        return list(queryWrapper);
    }

    /**
     * 将枚举与数据库配置合并为视图对象。
     *
     * @param quotaTypeEnum 配额类型枚举
     * @param config        数据库配置
     * @return 配额配置视图对象
     */
    private InterfaceQuotaConfigVO toConfigVO(InterfaceQuotaTypeEnum quotaTypeEnum, InterfaceQuotaConfig config) {
        InterfaceQuotaConfigVO configVO = new InterfaceQuotaConfigVO();
        if (config != null) {
            BeanUtils.copyProperties(config, configVO);
        } else {
            configVO.setQuotaType(quotaTypeEnum.getValue());
            configVO.setInitialQuota(quotaTypeEnum.getDefaultInitialQuota());
            configVO.setDescription(quotaTypeEnum.getText());
        }
        configVO.setQuotaTypeText(quotaTypeEnum.getText());
        configVO.setLimited(quotaTypeEnum.isLimited());
        return configVO;
    }
}
