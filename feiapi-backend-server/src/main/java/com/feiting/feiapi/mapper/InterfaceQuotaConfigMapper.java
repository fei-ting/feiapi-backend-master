package com.feiting.feiapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import org.apache.ibatis.annotations.Param;

/**
 * 接口配额类型配置 Mapper。
 */
public interface InterfaceQuotaConfigMapper extends BaseMapper<InterfaceQuotaConfig> {

    /**
     * 幂等插入默认配额配置，已存在时不覆盖管理员修改后的初始额度。
     *
     * @param quotaType    配额类型
     * @param initialQuota 初始额度
     * @param description  配置说明
     * @return 影响行数
     */
    int insertDefaultConfigIgnore(@Param("quotaType") String quotaType,
                                  @Param("initialQuota") int initialQuota,
                                  @Param("description") String description);
}
