package com.feiting.feiapi.unit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceQuotaConfigMapper;
import com.feiting.feiapi.model.vo.InterfaceQuotaConfigVO;
import com.feiting.feiapi.service.impl.InterfaceQuotaConfigServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceQuotaConfig;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口配额类型配置服务单元测试。
 */
@DisplayName("InterfaceQuotaConfigServiceImpl 单元测试")
class InterfaceQuotaConfigServiceImplTest {

    /**
     * 创建被测服务并注入 Mapper。
     *
     * @param mapper 配额配置 Mapper
     * @return 被测服务
     */
    private InterfaceQuotaConfigServiceImpl createService(InterfaceQuotaConfigMapper mapper) {
        InterfaceQuotaConfigServiceImpl service = new InterfaceQuotaConfigServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    /**
     * 创建配额配置实体。
     *
     * @param id           主键
     * @param quotaType    配额类型
     * @param initialQuota 初始额度
     * @return 配额配置实体
     */
    private InterfaceQuotaConfig buildConfig(Long id, String quotaType, Integer initialQuota) {
        InterfaceQuotaConfig config = new InterfaceQuotaConfig();
        config.setId(id);
        config.setQuotaType(quotaType);
        config.setInitialQuota(initialQuota);
        config.setDescription(quotaType);
        config.setIsDelete(0);
        return config;
    }

    /**
     * 校验查询配置视图时不会触发默认配置初始化。
     */
    @Test
    @DisplayName("查询配置视图时不执行默认配置初始化")
    void shouldListConfigVOWithoutInitializingDefaults() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                buildConfig(1L, InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 200)
        ));
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        List<InterfaceQuotaConfigVO> result = service.listConfigVO();

        assertThat(result).hasSize(3);
        assertThat(result)
                .filteredOn(item -> InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue().equals(item.getQuotaType()))
                .singleElement()
                .extracting(InterfaceQuotaConfigVO::getInitialQuota)
                .isEqualTo(200);
        assertThat(result)
                .filteredOn(item -> InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getValue().equals(item.getQuotaType()))
                .singleElement()
                .extracting(InterfaceQuotaConfigVO::getInitialQuota)
                .isEqualTo(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getDefaultInitialQuota());
        verify(mapper, never()).insertDefaultConfigIgnore(any(), anyInt(), any());
    }

    /**
     * 校验初始额度映射会用数据库配置覆盖枚举默认值。
     */
    @Test
    @DisplayName("获取初始额度映射时数据库配置优先")
    void shouldGetInitialQuotaMapWithDatabaseConfigFirst() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                buildConfig(1L, InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 120)
        ));
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        Map<String, Integer> result = service.getInitialQuotaMap();

        assertThat(result).containsEntry(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 120);
        assertThat(result).containsEntry(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getValue(),
                InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getDefaultInitialQuota());
        assertThat(result).containsEntry(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue(),
                InterfaceQuotaTypeEnum.FREE_UNLIMITED.getDefaultInitialQuota());
    }

    /**
     * 校验单个初始额度查询在配置缺失时使用枚举默认值。
     */
    @Test
    @DisplayName("配置缺失时获取初始额度使用枚举默认值")
    void shouldUseEnumDefaultWhenConfigAbsent() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        int result = service.getInitialQuota(InterfaceQuotaTypeEnum.ADVANCED_TRIAL);

        assertThat(result).isEqualTo(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getDefaultInitialQuota());
    }

    /**
     * 校验管理员更新有限额度类型配置成功。
     */
    @Test
    @DisplayName("更新有限额度类型初始额度成功")
    void shouldUpdateLimitedInitialQuotaSuccessfully() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        InterfaceQuotaConfig config = buildConfig(2L, InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 100);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(config));
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(config);
        when(mapper.selectOne(any(QueryWrapper.class), eq(false))).thenReturn(config);
        when(mapper.updateById(any(InterfaceQuotaConfig.class))).thenReturn(1);
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        boolean result = service.updateInitialQuota(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 300);

        assertThat(result).isTrue();
        assertThat(config.getInitialQuota()).isEqualTo(300);
        verify(mapper).insertDefaultConfigIgnore(eq(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue()), eq(100), eq("基础额度接口"));
        verify(mapper).updateById(config);
    }

    /**
     * 校验不允许更新免费无限类型初始额度。
     */
    @Test
    @DisplayName("更新免费无限类型初始额度时抛出参数错误")
    void shouldThrowWhenUpdateFreeUnlimitedQuota() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateInitialQuota(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue(), 1));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(mapper, never()).updateById(any(InterfaceQuotaConfig.class));
    }

    /**
     * 校验有限额度类型初始额度必须大于 0。
     */
    @Test
    @DisplayName("更新有限额度类型为非正数时抛出参数错误")
    void shouldThrowWhenUpdateLimitedQuotaWithInvalidInitialQuota() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateInitialQuota(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 0));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(mapper, never()).updateById(any(InterfaceQuotaConfig.class));
    }

    /**
     * 校验默认配置初始化使用 Mapper 幂等插入。
     */
    @Test
    @DisplayName("默认配置初始化使用幂等插入")
    void shouldInitializeDefaultsWithIdempotentInsert() {
        InterfaceQuotaConfigMapper mapper = mock(InterfaceQuotaConfigMapper.class);
        InterfaceQuotaConfigServiceImpl service = createService(mapper);

        service.initDefaultConfigsIfAbsent();
        service.initDefaultConfigsIfAbsent();

        verify(mapper, times(2)).insertDefaultConfigIgnore(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue(), 0, "免费无限接口");
        verify(mapper, times(2)).insertDefaultConfigIgnore(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue(), 100, "基础额度接口");
        verify(mapper, times(2)).insertDefaultConfigIgnore(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getValue(), 3, "高级体验接口");
    }
}
