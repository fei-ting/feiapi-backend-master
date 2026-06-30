package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapi.model.vo.InterfaceQuotaConfigVO;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * 接口配额类型配置服务。
 */
public interface InterfaceQuotaConfigService extends IService<InterfaceQuotaConfig> {

    /**
     * 查询全部配额类型配置视图。
     *
     * @return 配额类型配置视图列表
     */
    List<InterfaceQuotaConfigVO> listConfigVO();

    /**
     * 更新有限额度类型的初始额度。
     *
     * @param quotaType    配额类型
     * @param initialQuota 初始额度
     * @return 是否更新成功
     */
    boolean updateInitialQuota(String quotaType, Integer initialQuota);

    /**
     * 获取指定配额类型的初始额度，配置缺失时使用枚举默认值兜底。
     *
     * @param quotaTypeEnum 配额类型枚举
     * @return 初始额度
     */
    int getInitialQuota(InterfaceQuotaTypeEnum quotaTypeEnum);

    /**
     * 获取所有配额类型的初始额度映射，配置缺失时使用枚举默认值兜底。
     *
     * @return 配额类型与初始额度映射
     */
    Map<String, Integer> getInitialQuotaMap();

    /**
     * 初始化缺失的内置配额类型配置。
     */
    void initDefaultConfigsIfAbsent();
}
