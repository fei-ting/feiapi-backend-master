package com.feiting.feiapi.integration.service;

import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceInvokeLogService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口删除生命周期集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("接口删除生命周期集成测试")
class InterfaceInfoDeleteLifecycleTest {

    /**
     * 接口生命周期服务。
     */
    @Resource
    private InterfaceInfoLifecycleService interfaceInfoLifecycleService;

    /**
     * 接口信息服务。
     */
    @Resource
    private InterfaceInfoService interfaceInfoService;

    /**
     * 接口文档参数服务。
     */
    @Resource
    private InterfaceDocParamService interfaceDocParamService;

    /**
     * 接口文档错误码服务。
     */
    @Resource
    private InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * 用户接口额度关系服务。
     */
    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    /**
     * 接口调用日志服务。
     */
    @Resource
    private InterfaceInvokeLogService interfaceInvokeLogService;

    /**
     * 数据库操作模板。
     */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 当前测试创建的接口 ID。
     */
    private final List<Long> createdInterfaceInfoIds = new ArrayList<>();

    /**
     * 清理当前测试持久化的数据。
     */
    @AfterEach
    void cleanUp() {
        createdInterfaceInfoIds.forEach(interfaceInfoId -> {
            jdbcTemplate.update("delete from interface_invoke_log where interface_info_id = ?", interfaceInfoId);
            jdbcTemplate.update("delete from user_interface_info where interface_info_id = ?", interfaceInfoId);
            jdbcTemplate.update("delete from interface_doc_error_code where interface_info_id = ?", interfaceInfoId);
            jdbcTemplate.update("delete from interface_doc_param where interface_info_id = ?", interfaceInfoId);
            jdbcTemplate.update("delete from interface_doc where interface_info_id = ?", interfaceInfoId);
            jdbcTemplate.update("delete from interface_info where id = ?", interfaceInfoId);
        });
        createdInterfaceInfoIds.clear();
    }

    /**
     * 验证接口没有任何文档关联记录时仍可正常删除。
     */
    @Test
    @DisplayName("文档关联记录全部缺失时仍可删除接口")
    void shouldDeleteWhenAllDocumentRecordsAreMissing() {
        InterfaceInfo interfaceInfo = createOfflineInterface("all_doc_missing");

        Boolean result = interfaceInfoLifecycleService.deleteOfflineInterfaceInfo(interfaceInfo.getId());

        assertThat(result).isTrue();
        assertThat(queryDeleteFlag("interface_info", interfaceInfo.getId())).isEqualTo(interfaceInfo.getId());
    }

    /**
     * 验证接口只有部分文档关联记录时仍可正常删除。
     */
    @Test
    @DisplayName("文档关联记录部分缺失时仍可删除接口")
    void shouldDeleteWhenDocumentRecordsArePartiallyMissing() {
        InterfaceInfo interfaceInfo = createOfflineInterface("partial_doc_missing");
        InterfaceDocParam param = createDocumentParam(interfaceInfo.getId(), "partialField");

        Boolean result = interfaceInfoLifecycleService.deleteOfflineInterfaceInfo(interfaceInfo.getId());

        assertThat(result).isTrue();
        assertThat(queryDeleteFlag("interface_info", interfaceInfo.getId())).isEqualTo(interfaceInfo.getId());
        assertThat(queryDeleteFlag("interface_doc_param", param.getId())).isEqualTo(param.getId());
        assertThat(countByInterfaceInfoId("interface_doc", interfaceInfo.getId())).isZero();
        assertThat(countByInterfaceInfoId("interface_doc_error_code", interfaceInfo.getId())).isZero();
    }

    /**
     * 验证删除接口不会修改或删除用户额度关系和调用日志。
     */
    @Test
    @DisplayName("删除接口保留额度关系和调用日志")
    void shouldRetainQuotaRelationAndInvokeLog() {
        InterfaceInfo interfaceInfo = createOfflineInterface("retain_history");
        UserInterfaceInfo relation = createQuotaRelation(interfaceInfo.getId());
        assertThat(interfaceInvokeLogService.recordInvoke(
                1L,
                interfaceInfo.getId(),
                interfaceInfo.getPath(),
                interfaceInfo.getMethod(),
                200,
                true,
                120L
        )).isTrue();
        Long invokeLogId = jdbcTemplate.queryForObject(
                "select id from interface_invoke_log where interface_info_id = ? and is_delete = 0",
                Long.class,
                interfaceInfo.getId()
        );

        interfaceInfoLifecycleService.deleteOfflineInterfaceInfo(interfaceInfo.getId());

        assertThat(queryDeleteFlag("user_interface_info", relation.getId())).isZero();
        assertThat(queryInteger("select left_num from user_interface_info where id = ?", relation.getId())).isEqualTo(9);
        assertThat(queryInteger("select total_num from user_interface_info where id = ?", relation.getId())).isEqualTo(1);
        assertThat(invokeLogId).isNotNull();
        assertThat(queryDeleteFlag("interface_invoke_log", invokeLogId)).isZero();
    }

    /**
     * 验证文档错误码删除失败时，已执行的文档参数删除会随事务回滚。
     */
    @Test
    @DisplayName("聚合删除中途失败时回滚全部已执行操作")
    void shouldRollbackAggregateDeleteWhenDocumentDeleteFails() {
        InterfaceInfo interfaceInfo = createOfflineInterface("rollback_delete");
        InterfaceDocParam param = createDocumentParam(interfaceInfo.getId(), "rollbackField");
        InterfaceDocErrorCode activeErrorCode = createErrorCode(interfaceInfo.getId(), "ROLLBACK_ERROR");
        long historicalErrorCodeId = activeErrorCode.getId() + 1_000_000_000L;
        jdbcTemplate.update(
                "insert into interface_doc_error_code "
                        + "(id, interface_info_id, error_code, error_message, sort_order, is_delete) "
                        + "values (?, ?, ?, ?, ?, ?)",
                historicalErrorCodeId,
                interfaceInfo.getId(),
                activeErrorCode.getErrorCode(),
                "历史错误信息",
                2,
                activeErrorCode.getId()
        );

        assertThatThrownBy(() -> interfaceInfoLifecycleService.deleteOfflineInterfaceInfo(interfaceInfo.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(queryDeleteFlag("interface_info", interfaceInfo.getId())).isZero();
        assertThat(queryDeleteFlag("interface_doc_param", param.getId())).isZero();
        assertThat(queryDeleteFlag("interface_doc_error_code", activeErrorCode.getId())).isZero();
    }

    /**
     * 创建下线状态的测试接口。
     *
     * @param scene 测试场景标识
     * @return 已保存的接口信息
     */
    private InterfaceInfo createOfflineInterface(String scene) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String path = "/api/delete_lifecycle/" + scene + "/" + suffix;
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("删除生命周期测试接口");
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setDescription("删除生命周期集成测试接口");
        interfaceInfo.setUrl("http://feiapi-interface:8123" + path);
        interfaceInfo.setPath(path);
        interfaceInfo.setTargetHost("http://feiapi-interface:8123");
        interfaceInfo.setRequestParams("");
        interfaceInfo.setRequestHeader("");
        interfaceInfo.setResponseHeader("");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
        interfaceInfo.setMethod("GET");
        interfaceInfo.setQuotaType(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
        interfaceInfo.setUserId(1L);
        assertThat(interfaceInfoService.save(interfaceInfo)).isTrue();
        createdInterfaceInfoIds.add(interfaceInfo.getId());
        return interfaceInfo;
    }

    /**
     * 创建测试文档参数。
     *
     * @param interfaceInfoId 接口 ID
     * @param name            参数名称
     * @return 已保存的文档参数
     */
    private InterfaceDocParam createDocumentParam(Long interfaceInfoId, String name) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene("RESPONSE");
        param.setName(name);
        param.setType("string");
        param.setRequired(0);
        param.setNullable(0);
        param.setDescription("公开说明");
        param.setSortOrder(1);
        assertThat(interfaceDocParamService.save(param)).isTrue();
        return param;
    }

    /**
     * 创建测试错误码。
     *
     * @param interfaceInfoId 接口 ID
     * @param errorCode       错误码
     * @return 已保存的错误码
     */
    private InterfaceDocErrorCode createErrorCode(Long interfaceInfoId, String errorCode) {
        InterfaceDocErrorCode item = new InterfaceDocErrorCode();
        item.setInterfaceInfoId(interfaceInfoId);
        item.setErrorCode(errorCode);
        item.setErrorMessage("错误信息");
        item.setSortOrder(1);
        assertThat(interfaceDocErrorCodeService.save(item)).isTrue();
        return item;
    }

    /**
     * 创建用户额度关系。
     *
     * @param interfaceInfoId 接口 ID
     * @return 已保存的额度关系
     */
    private UserInterfaceInfo createQuotaRelation(Long interfaceInfoId) {
        UserInterfaceInfo relation = new UserInterfaceInfo();
        relation.setUserId(1L);
        relation.setInterfaceInfoId(interfaceInfoId);
        relation.setLeftNum(9);
        relation.setTotalNum(1);
        relation.setStatus(0);
        relation.setIsDelete(0);
        assertThat(userInterfaceInfoService.save(relation)).isTrue();
        return relation;
    }

    /**
     * 查询指定记录的逻辑删除标识。
     *
     * @param tableName 表名
     * @param id        记录 ID
     * @return 逻辑删除标识
     */
    private Long queryDeleteFlag(String tableName, Long id) {
        return jdbcTemplate.queryForObject(
                "select is_delete from " + tableName + " where id = ?",
                Long.class,
                id
        );
    }

    /**
     * 按接口 ID 统计关联记录数量。
     *
     * @param tableName       表名
     * @param interfaceInfoId 接口 ID
     * @return 记录数量
     */
    private Long countByInterfaceInfoId(String tableName, Long interfaceInfoId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where interface_info_id = ?",
                Long.class,
                interfaceInfoId
        );
    }

    /**
     * 查询单个整数结果。
     *
     * @param sql SQL 语句
     * @param id  记录 ID
     * @return 整数结果
     */
    private Integer queryInteger(String sql, Long id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }
}
