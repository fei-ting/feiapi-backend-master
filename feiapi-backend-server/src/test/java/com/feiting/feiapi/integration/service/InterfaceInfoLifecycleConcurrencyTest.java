package com.feiting.feiapi.integration.service;

import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.facade.service.api.InterfaceInfoApplicationService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishingLifecycleService;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口信息生命周期并发集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("接口信息生命周期并发集成测试")
class InterfaceInfoLifecycleConcurrencyTest {

    /** 测试配置中的发布探测管理员账号。 */
    private static final String TEST_PROBE_ADMIN_ACCOUNT = "probeadmin";

    /** 测试配置中的发布探测管理员 AccessKey。 */
    private static final String TEST_PROBE_ACCESS_KEY = "test-access-key";

    /** 测试配置中的发布探测管理员 SecretKey。 */
    private static final String TEST_PROBE_SECRET_KEY = "test-secret-key";

    /** 接口信息服务。 */
    @Resource
    private InterfaceInfoService interfaceInfoService;

    /** 接口信息应用协调服务。 */
    @Resource
    private InterfaceInfoApplicationService interfaceInfoApplicationService;

    /** 接口发布生命周期协作服务。 */
    @Resource
    private InterfacePublishingLifecycleService interfacePublishingLifecycleService;

    /** 接口文档服务。 */
    @Resource
    private InterfaceDocService interfaceDocService;

    /** 接口文档参数服务。 */
    @Resource
    private InterfaceDocParamService interfaceDocParamService;

    /** 接口文档错误码服务。 */
    @Resource
    private InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /** JDBC 操作工具。 */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 事务管理器。 */
    @Resource
    private PlatformTransactionManager transactionManager;

    /** 当前测试创建的接口 ID。 */
    private final List<Long> createdInterfaceIds = new ArrayList<>();

    /**
     * 准备发布门禁所需的探测管理员。
     */
    @BeforeEach
    void setUpProbeAdmin() {
        jdbcTemplate.update("DELETE FROM `user` WHERE user_account = ? OR access_key = ?",
                TEST_PROBE_ADMIN_ACCOUNT, TEST_PROBE_ACCESS_KEY);
        jdbcTemplate.update("""
                        INSERT INTO `user` (user_name, user_account, user_role, user_password, access_key, secret_key, is_delete)
                        VALUES (?, ?, 'admin', 'encoded-password', ?, ?, 0)
                        """,
                "发布探测管理员", TEST_PROBE_ADMIN_ACCOUNT, TEST_PROBE_ACCESS_KEY, TEST_PROBE_SECRET_KEY);
    }

    /**
     * 清理测试持久化数据。
     */
    @AfterEach
    void cleanUp() {
        createdInterfaceIds.stream()
                .forEach(interfaceInfoId -> {
                    interfaceDocErrorCodeService.lambdaUpdate()
                            .eq(com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode::getInterfaceInfoId,
                                    interfaceInfoId)
                            .remove();
                    interfaceDocParamService.lambdaUpdate()
                            .eq(com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam::getInterfaceInfoId,
                                    interfaceInfoId)
                            .remove();
                    interfaceDocService.lambdaUpdate()
                            .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                            .remove();
                    interfaceInfoService.removeById(interfaceInfoId);
                });
        jdbcTemplate.update("DELETE FROM `user` WHERE user_account = ? OR access_key = ?",
                TEST_PROBE_ADMIN_ACCOUNT, TEST_PROBE_ACCESS_KEY);
    }

    /**
     * 文档保存必须等待接口主记录行锁释放，避免基于旧接口状态继续写入。
     */
    @Test
    @DisplayName("文档保存等待接口主记录行锁")
    void shouldBlockDocSaveUntilInterfaceLockReleased() throws Exception {
        long interfaceInfoId = createOfflineInterfaceWithReadyDoc();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<?> lockFuture = executorService.submit(
                    () -> holdInterfaceLock(interfaceInfoId, lockAcquired, releaseLock, false));
            assertThat(lockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            InterfaceDocSaveRequest saveRequest = buildDraftSaveRequest(interfaceInfoId);
            Future<Boolean> saveFuture = executorService.submit(() -> interfaceDocService.saveDoc(saveRequest));

            assertThatThrownBy(() -> saveFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseLock.countDown();

            lockFuture.get(3, TimeUnit.SECONDS);
            assertThat(saveFuture.get(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
        }
    }

    /**
     * 接口配置更新必须等待接口主记录行锁释放，保证与发布开始使用相同互斥点。
     */
    @Test
    @DisplayName("接口配置更新等待接口主记录行锁")
    void shouldBlockInterfaceUpdateUntilInterfaceLockReleased() throws Exception {
        long interfaceInfoId = createOfflineInterfaceWithReadyDoc();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<?> lockFuture = executorService.submit(
                    () -> holdInterfaceLock(interfaceInfoId, lockAcquired, releaseLock, false));
            assertThat(lockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            InterfaceInfo updateRequest = new InterfaceInfo();
            updateRequest.setId(interfaceInfoId);
            updateRequest.setDescription("并发更新后的接口说明");
            Future<Boolean> updateFuture = executorService.submit(
                    () -> interfaceInfoApplicationService.updateInterfaceInfoWithDoc(updateRequest));

            assertThatThrownBy(() -> updateFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseLock.countDown();

            lockFuture.get(3, TimeUnit.SECONDS);
            assertThat(updateFuture.get(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
        }
    }

    /**
     * 发布开始必须在获得接口锁后读取最新文档状态，不能发布并发降级后的草稿。
     */
    @Test
    @DisplayName("发布开始拒绝锁等待期间降级为草稿的文档")
    void shouldRejectPublishingWhenDocDowngradedBeforeLockAcquired() throws Exception {
        long interfaceInfoId = createOfflineInterfaceWithReadyDoc();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<?> lockFuture = executorService.submit(
                    () -> holdInterfaceLock(interfaceInfoId, lockAcquired, releaseLock, true));
            assertThat(lockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            Future<InterfaceInfo> publishingFuture = executorService.submit(
                    () -> startPublishing(interfaceInfoId));

            assertThatThrownBy(() -> publishingFuture.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseLock.countDown();
            lockFuture.get(3, TimeUnit.SECONDS);

            assertThatThrownBy(() -> publishingFuture.get(3, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .rootCause()
                    .hasMessage("接口发布前检查未通过，请先修复检查问题");
            assertThat(interfaceInfoService.getById(interfaceInfoId).getStatus())
                    .isEqualTo(InterfaceInfoStatusEnum.OFFLINE.getValue());
        } finally {
            releaseLock.countDown();
            executorService.shutdownNow();
        }
    }

    /**
     * 同一接口的并发发布开始请求只能有一个成功进入发布中状态。
     */
    @Test
    @DisplayName("并发发布开始只能成功一次")
    void shouldAllowOnlyOneConcurrentPublishingStart() throws Exception {
        long interfaceInfoId = createOfflineInterfaceWithReadyDoc();
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<InterfaceInfo> firstFuture = executorService.submit(
                    () -> startPublishingAfterSignal(interfaceInfoId, startSignal));
            Future<InterfaceInfo> secondFuture = executorService.submit(
                    () -> startPublishingAfterSignal(interfaceInfoId, startSignal));
            startSignal.countDown();

            List<Future<InterfaceInfo>> publishingFutures = List.of(firstFuture, secondFuture);
            long successCount = publishingFutures.stream()
                    .filter(this::publishingStartedSuccessfully)
                    .count();
            long failureCount = publishingFutures.stream()
                    .filter(this::publishingRejectedByBusinessRule)
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(1);
            assertThat(interfaceInfoService.getById(interfaceInfoId).getStatus())
                    .isEqualTo(InterfaceInfoStatusEnum.PUBLISHING.getValue());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 发布开始成功后，后续文档保存必须读取到发布中状态并拒绝写入。
     */
    @Test
    @DisplayName("发布开始后拒绝保存文档")
    void shouldRejectDocSaveAfterPublishingStarted() {
        long interfaceInfoId = createOfflineInterfaceWithReadyDoc();
        startPublishing(interfaceInfoId);

        InterfaceDocSaveRequest saveRequest = buildDraftSaveRequest(interfaceInfoId);

        assertThatThrownBy(() -> interfaceDocService.saveDoc(saveRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("接口仅允许在下线状态维护文档");
        InterfaceDoc interfaceDoc = interfaceDocService.lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                .one();
        assertThat(interfaceDoc.getDocStatus()).isEqualTo("READY");
        assertThat(interfaceInfoService.getById(interfaceInfoId).getStatus())
                .isEqualTo(InterfaceInfoStatusEnum.PUBLISHING.getValue());
    }

    /**
     * 在独立事务中持有接口主记录行锁。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param lockAcquired    行锁获取信号
     * @param releaseLock     行锁释放信号
     * @param downgradeDoc    是否在提交前将文档降为草稿
     */
    private void holdInterfaceLock(long interfaceInfoId,
                                   CountDownLatch lockAcquired,
                                   CountDownLatch releaseLock,
                                   boolean downgradeDoc) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT id FROM interface_info WHERE id = ? AND is_delete = 0 FOR UPDATE",
                    Long.class,
                    interfaceInfoId);
            lockAcquired.countDown();
            awaitRelease(releaseLock);
            if (downgradeDoc) {
                jdbcTemplate.update(
                        "UPDATE interface_doc SET doc_status = 'DRAFT' WHERE interface_info_id = ? AND is_delete = 0",
                        interfaceInfoId);
            }
        });
    }

    /**
     * 等待测试允许释放行锁。
     *
     * @param releaseLock 行锁释放信号
     */
    private void awaitRelease(CountDownLatch releaseLock) {
        try {
            if (!releaseLock.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待释放接口行锁超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待释放接口行锁被中断", e);
        }
    }

    /**
     * 等待并发开始信号后发起发布状态切换。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param startSignal     并发开始信号
     * @return 发布中的接口快照
     * @throws InterruptedException 等待被中断
     */
    private InterfaceInfo startPublishingAfterSignal(long interfaceInfoId,
                                                      CountDownLatch startSignal) throws InterruptedException {
        startSignal.await();
        return startPublishing(interfaceInfoId);
    }

    /**
     * 通过发布域生命周期协作服务开始发布，并返回接口快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布中的接口快照
     */
    private InterfaceInfo startPublishing(long interfaceInfoId) {
        return interfacePublishingLifecycleService.startPublishingWithContext(interfaceInfoId).getInterfaceInfo();
    }

    /**
     * 判断发布任务是否成功。
     *
     * @param publishingFuture 发布任务
     * @return 是否成功
     */
    private boolean publishingStartedSuccessfully(Future<InterfaceInfo> publishingFuture) {
        try {
            return publishingFuture.get(3, TimeUnit.SECONDS) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断发布任务是否因生命周期业务规则被拒绝。
     *
     * @param publishingFuture 发布任务
     * @return 是否因业务规则被拒绝
     */
    private boolean publishingRejectedByBusinessRule(Future<InterfaceInfo> publishingFuture) {
        try {
            publishingFuture.get(3, TimeUnit.SECONDS);
            return false;
        } catch (ExecutionException e) {
            return e.getCause() instanceof BusinessException;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * 创建下线接口及已完成文档。
     *
     * @return 接口信息 ID
     */
    private long createOfflineInterfaceWithReadyDoc() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("并发接口" + suffix);
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setDescription("并发测试接口");
        interfaceInfo.setUrl("http://feiapi-interface:8123/api/concurrency_" + suffix);
        interfaceInfo.setPath("/api/concurrency_" + suffix);
        interfaceInfo.setTargetHost("http://feiapi-interface:8123");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
        interfaceInfo.setMethod("GET");
        interfaceInfo.setQuotaType("BASIC_QUOTA");
        interfaceInfo.setUserId(1L);
        assertThat(interfaceInfoService.save(interfaceInfo)).isTrue();
        createdInterfaceIds.add(interfaceInfo.getId());

        InterfaceDoc interfaceDoc = new InterfaceDoc();
        interfaceDoc.setInterfaceInfoId(interfaceInfo.getId());
        interfaceDoc.setDocStatus("READY");
        interfaceDoc.setDocVersion("v1");
        interfaceDoc.setRequestContentType("application/json");
        interfaceDoc.setResponseContentType("text/plain");
        assertThat(interfaceDocService.save(interfaceDoc)).isTrue();
        return interfaceInfo.getId();
    }

    /**
     * 构建草稿保存请求。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 草稿保存请求
     */
    private InterfaceDocSaveRequest buildDraftSaveRequest(long interfaceInfoId) {
        InterfaceDocSaveRequest request = new InterfaceDocSaveRequest();
        request.setInterfaceInfoId(interfaceInfoId);
        request.setDocStatus("DRAFT");
        request.setDocVersion("v1");
        request.setRequestContentType("application/json");
        request.setResponseContentType("text/plain");
        request.setParams(new ArrayList<>());
        request.setErrorCodes(new ArrayList<>());
        return request;
    }
}
