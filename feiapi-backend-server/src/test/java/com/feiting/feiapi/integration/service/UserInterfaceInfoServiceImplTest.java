package com.feiting.feiapi.integration.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserInterfaceInfoMapper;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserInterfaceInfoServiceImpl 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserInterfaceInfoServiceImpl 集成测试")
class UserInterfaceInfoServiceImplTest {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserInterfaceInfoMapper userInterfaceInfoMapper;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    private long insertInterfaceInfo(String quotaType) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        long uniqueId = System.nanoTime();
        interfaceInfo.setName("testInterface" + uniqueId);
        interfaceInfo.setDescription("测试接口");
        interfaceInfo.setUrl("http://feiapi-interface:8123/api/test/" + uniqueId);
        interfaceInfo.setPath("/api/test/" + uniqueId);
        interfaceInfo.setTargetHost("http://feiapi-interface:8123");
        interfaceInfo.setRequestParams("");
        interfaceInfo.setRequestHeader("");
        interfaceInfo.setResponseHeader("");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
        interfaceInfo.setMethod("GET");
        interfaceInfo.setQuotaType(quotaType);
        interfaceInfo.setUserId(1L);
        interfaceInfoService.save(interfaceInfo);
        return interfaceInfo.getId();
    }

    private void insertUserInterfaceInfo(long userId, long interfaceInfoId, int leftNum, int totalNum) {
        UserInterfaceInfo info = new UserInterfaceInfo();
        info.setUserId(userId);
        info.setInterfaceInfoId(interfaceInfoId);
        info.setLeftNum(leftNum);
        info.setTotalNum(totalNum);
        info.setStatus(0);
        info.setIsDelete(0);
        userInterfaceInfoService.save(info);
    }

    @Nested
    @DisplayName("invokeCount 调用计数")
    class InvokeCountTests {

        @Test
        @DisplayName("正常扣减成功，leftNum 减 1，totalNum 加 1")
        void shouldDecreaseLeftNumAndIncreaseTotalNum() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 10, 0);

            boolean result = userInterfaceInfoService.invokeCount(1L, interfaceInfoId);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(9, info.getLeftNum());
            assertEquals(1, info.getTotalNum());
        }

        @Test
        @DisplayName("userId 为 0 时抛出异常")
        void shouldThrowWhenUserIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.invokeCount(0L, 1L));
        }

        @Test
        @DisplayName("interfaceInfoId 为 0 时抛出异常")
        void shouldThrowWhenInterfaceInfoIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.invokeCount(1L, 0L));
        }

        @Test
        @DisplayName("leftNum 为 0 时扣减失败（SQL 条件 left_num > 0）")
        void shouldFailWhenLeftNumIsZero() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 0, 10);

            boolean result = userInterfaceInfoService.invokeCount(1L, interfaceInfoId);

            assertFalse(result);
        }

        @Test
        @DisplayName("不存在的记录扣减失败")
        void shouldFailWhenRecordNotFound() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());

            boolean result = userInterfaceInfoService.invokeCount(999L, interfaceInfoId);

            assertFalse(result);
        }

        @Test
        @DisplayName("已预扣后返还成功，leftNum 加 1，totalNum 减 1")
        void shouldRollbackInvokeCountSuccessfully() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 9, 1);

            boolean result = userInterfaceInfoService.rollbackInvokeCount(1L, interfaceInfoId);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(10, info.getLeftNum());
            assertEquals(0, info.getTotalNum());
        }

        @Test
        @DisplayName("首次调用预扣后失败补偿成功，额度恢复到初始状态")
        void shouldRollbackSuccessfullyAfterFirstInvokePrecharged() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 100, 0);

            boolean invokeResult = userInterfaceInfoService.invokeCount(1L, interfaceInfoId);
            boolean rollbackResult = userInterfaceInfoService.rollbackInvokeCount(1L, interfaceInfoId);

            assertTrue(invokeResult);
            assertTrue(rollbackResult);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(100, info.getLeftNum());
            assertEquals(0, info.getTotalNum());
        }

        @Test
        @DisplayName("没有预扣时返还失败，避免总调用次数变为负数或误加额度")
        void shouldFailRollbackWhenNotPrecharged() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 10, 0);

            boolean result = userInterfaceInfoService.rollbackInvokeCount(1L, interfaceInfoId);

            assertFalse(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(10, info.getLeftNum());
            assertEquals(0, info.getTotalNum());
        }

        @Test
        @DisplayName("免费无限接口调用只累计 totalNum，不扣减 leftNum")
        void shouldOnlyIncreaseTotalNumForFreeUnlimitedInterface() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue());

            boolean result = userInterfaceInfoService.invokeCount(1L, interfaceInfoId);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(0, info.getLeftNum());
            assertEquals(1, info.getTotalNum());
        }

        @Test
        @DisplayName("免费无限接口回滚只回退 totalNum，不返还 leftNum")
        void shouldNotRollbackLeftNumForFreeUnlimitedInterface() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue());
            userInterfaceInfoService.invokeCount(1L, interfaceInfoId);

            boolean result = userInterfaceInfoService.rollbackInvokeCount(1L, interfaceInfoId);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(0, info.getLeftNum());
            assertEquals(0, info.getTotalNum());
        }
    }

    @Nested
    @DisplayName("leftNumIsEnough 剩余次数检查")
    class LeftNumIsEnoughTests {

        @Test
        @DisplayName("已有记录且 leftNum > 0 时返回 true")
        void shouldReturnTrueWhenEnough() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 10, 0);

            boolean result = userInterfaceInfoService.leftNumIsEnough(1L, interfaceInfoId);

            assertTrue(result);
        }

        @Test
        @DisplayName("userId 为 0 时抛出异常")
        void shouldThrowWhenUserIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(0L, 1L));
        }

        @Test
        @DisplayName("interfaceInfoId 为 0 时抛出异常")
        void shouldThrowWhenInterfaceInfoIdZero() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(1L, 0L));
        }

        @Test
        @DisplayName("leftNum 为 0 时抛出次数不足异常")
        void shouldThrowWhenLeftNumExhausted() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 0, 10);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.leftNumIsEnough(1L, interfaceInfoId));
        }

        @Test
        @DisplayName("重复初始化 active 记录时不重置剩余次数和总调用次数")
        void shouldNotResetQuotaWhenActiveRecordAlreadyExists() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 3, 20);

            userInterfaceInfoMapper.insertIgnoreIfAbsent(1L, interfaceInfoId, 100, 0);

            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(3, info.getLeftNum());
            assertEquals(20, info.getTotalNum());
            assertEquals(0, info.getIsDelete());
        }

        @Test
        @DisplayName("恢复软删除记录时不重置剩余次数和总调用次数")
        void shouldNotResetQuotaWhenRestoreDeletedRecord() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(1L, interfaceInfoId, 0, 30);
            UserInterfaceInfo savedInfo = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();

            boolean removeResult = userInterfaceInfoService.removeById(savedInfo.getId());
            assertTrue(removeResult);

            userInterfaceInfoMapper.insertIgnoreIfAbsent(1L, interfaceInfoId, 100, 0);

            UserInterfaceInfo restoredInfo = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(restoredInfo);
            assertEquals(0, restoredInfo.getLeftNum());
            assertEquals(30, restoredInfo.getTotalNum());
            assertEquals(0, restoredInfo.getIsDelete());
        }

        @Test
        @DisplayName("有限额度接口缺失关系时按当前配置懒初始化")
        void shouldInitializeLimitedQuotaWithCurrentConfigWhenAbsent() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getValue());
            interfaceQuotaConfigService.updateInitialQuota(InterfaceQuotaTypeEnum.ADVANCED_TRIAL.getValue(), 5);

            boolean result = userInterfaceInfoService.leftNumIsEnough(1L, interfaceInfoId);

            assertTrue(result);
            UserInterfaceInfo info = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNotNull(info);
            assertEquals(5, info.getLeftNum());
            assertEquals(0, info.getTotalNum());
        }

        @Test
        @DisplayName("免费无限接口缺失关系时校验直接通过且不创建关系")
        void shouldPassWithoutRelationForFreeUnlimitedInterface() {
            long interfaceInfoId = insertInterfaceInfo(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue());

            boolean result = userInterfaceInfoService.leftNumIsEnough(1L, interfaceInfoId);

            assertTrue(result);
            long count = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, 1L)
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .count();
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("listTotalNumByInterfaceInfoIds 批量统计调用总数")
    class ListTotalNumByInterfaceInfoIdsTests {

        @Test
        @DisplayName("按接口 ID 批量汇总所有用户调用次数")
        void shouldReturnTotalNumMapByInterfaceInfoIds() {
            long firstInterfaceId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            long secondInterfaceId = insertInterfaceInfo(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
            insertUserInterfaceInfo(11L, firstInterfaceId, 0, 3);
            insertUserInterfaceInfo(12L, firstInterfaceId, 0, 5);
            insertUserInterfaceInfo(13L, secondInterfaceId, 0, 9);

            Map<Long, Integer> totalNumMap = userInterfaceInfoService.listTotalNumByInterfaceInfoIds(
                    Arrays.asList(firstInterfaceId, firstInterfaceId, secondInterfaceId, null, 0L)
            );

            assertEquals(2, totalNumMap.size());
            assertEquals(8, totalNumMap.get(firstInterfaceId));
            assertEquals(9, totalNumMap.get(secondInterfaceId));
        }

        @Test
        @DisplayName("空接口 ID 列表返回空映射")
        void shouldReturnEmptyMapWhenIdsEmpty() {
            Map<Long, Integer> totalNumMap = userInterfaceInfoService.listTotalNumByInterfaceInfoIds(Collections.emptyList());

            assertTrue(totalNumMap.isEmpty());
        }
    }

    @Nested
    @DisplayName("validUserInterfaceInfo 校验")
    class ValidUserInterfaceInfoTests {

        @Test
        @DisplayName("null 对象抛出 PARAMS_ERROR")
        void shouldThrowWhenNull() {
            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(null, true));
        }

        @Test
        @DisplayName("创建时 userId <= 0 抛出异常")
        void shouldThrowWhenUserIdInvalidOnAdd() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(0L);
            info.setInterfaceInfoId(1L);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(info, true));
        }

        @Test
        @DisplayName("leftNum 为负数抛出异常")
        void shouldThrowWhenLeftNumNegative() {
            UserInterfaceInfo info = new UserInterfaceInfo();
            info.setUserId(1L);
            info.setInterfaceInfoId(1L);
            info.setLeftNum(-1);

            assertThrows(BusinessException.class,
                    () -> userInterfaceInfoService.validUserInterfaceInfo(info, true));
        }
    }
}
