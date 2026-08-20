package com.feiting.feiapi.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.AdminBootstrapService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理员一次性初始化服务集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("管理员一次性初始化服务集成测试")
class AdminBootstrapServiceImplTest {

    /**
     * 测试管理员账号。
     */
    private static final String ADMIN_ACCOUNT = "bootadmin";

    /**
     * 测试管理员初始密码。
     */
    private static final String ADMIN_PASSWORD = "admin1234";

    /**
     * 管理员初始化服务。
     */
    @Resource
    private AdminBootstrapService adminBootstrapService;

    /**
     * 用户服务。
     */
    @Resource
    private UserService userService;

    /**
     * 接口信息服务。
     */
    @Resource
    private InterfaceInfoService interfaceInfoService;

    /**
     * 用户接口额度服务。
     */
    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    /**
     * 首次执行应创建管理员、演示接口和有限额度接口关系。
     */
    @Test
    @DisplayName("首次执行创建管理员及初始化数据")
    void shouldCreateAdminAndBootstrapDataOnFirstRun() {
        InterfaceInfo limitedInterface = insertLimitedInterface();

        boolean created = adminBootstrapService.initialize(
                ADMIN_ACCOUNT,
                ADMIN_PASSWORD,
                "管理员");

        User admin = getUserByAccount(ADMIN_ACCOUNT);
        assertThat(created).isTrue();
        assertThat(admin).isNotNull();
        assertThat(admin.getUserRole()).isEqualTo(UserRoleEnum.ADMIN.getCode());
        assertThat(admin.getUserName()).isEqualTo("管理员");
        assertThat(admin.getAccessKey()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(admin.getSecretKey()).hasSize(64).matches("[A-Za-z0-9_-]+");
        assertThat(new BCryptPasswordEncoder().matches(ADMIN_PASSWORD, admin.getUserPassword())).isTrue();

        InterfaceInfo demoInterface = getDemoInterface();
        assertThat(demoInterface).isNotNull();
        assertThat(demoInterface.getUserId()).isEqualTo(admin.getId());
        assertThat(demoInterface.getStatus()).isEqualTo(InterfaceInfoStatusEnum.ONLINE.getValue());

        UserInterfaceInfo quotaRelation = getQuotaRelation(admin.getId(), limitedInterface.getId());
        assertThat(quotaRelation).isNotNull();
        assertThat(quotaRelation.getLeftNum()).isEqualTo(100);
    }

    /**
     * 已存在管理员时应安全跳过，不能覆盖原有凭据。
     */
    @Test
    @DisplayName("重复执行不覆盖现有管理员")
    void shouldSkipWithoutOverwritingExistingAdmin() {
        User existingAdmin = userService.createBootstrapAdmin(
                "admin001",
                ADMIN_PASSWORD,
                "原管理员");
        String accessKey = existingAdmin.getAccessKey();
        String secretKey = existingAdmin.getSecretKey();

        boolean created = adminBootstrapService.initialize(
                "admin002",
                "password5678",
                "新管理员");

        User unchangedAdmin = userService.getById(existingAdmin.getId());
        assertThat(created).isFalse();
        assertThat(unchangedAdmin.getAccessKey()).isEqualTo(accessKey);
        assertThat(unchangedAdmin.getSecretKey()).isEqualTo(secretKey);
        assertThat(getUserByAccount("admin002")).isNull();
        assertThat(getDemoInterface().getUserId()).isEqualTo(existingAdmin.getId());
    }

    /**
     * 普通用户占用初始化账号时应拒绝自动提权。
     */
    @Test
    @DisplayName("普通用户占用初始化账号时拒绝提权")
    void shouldRejectWhenBootstrapAccountBelongsToNormalUser() {
        userService.userRegister(ADMIN_ACCOUNT, ADMIN_PASSWORD, ADMIN_PASSWORD);

        assertThatThrownBy(() -> adminBootstrapService.initialize(
                ADMIN_ACCOUNT,
                ADMIN_PASSWORD,
                "管理员"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝自动提权");

        assertThat(countAdmins()).isZero();
    }

    /**
     * 已存在管理员时，即使初始化配置为空也应安全跳过。
     */
    @Test
    @DisplayName("已有管理员时允许缺少初始化配置")
    void shouldSkipWhenBootstrapCredentialsAreMissingForExistingAdmin() {
        User existingAdmin = userService.createBootstrapAdmin(
                ADMIN_ACCOUNT, ADMIN_PASSWORD, "管理员");

        boolean created = adminBootstrapService.initialize(null, null, null);

        assertThat(created).isFalse();
        assertThat(getUserByAccount(ADMIN_ACCOUNT).getId()).isEqualTo(existingAdmin.getId());
    }

    /**
     * 插入用于验证初始额度发放的有限额度接口。
     *
     * @return 已插入的接口信息
     */
    private InterfaceInfo insertLimitedInterface() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("初始化额度测试接口");
        interfaceInfo.setDescription("用于验证管理员初始化后的额度发放");
        interfaceInfo.setUrl("http://feiapi-interface:8123/api/bootstrap/quota");
        interfaceInfo.setPath("/api/bootstrap/quota");
        interfaceInfo.setTargetHost("http://feiapi-interface:8123");
        interfaceInfo.setRequestParams("");
        interfaceInfo.setRequestHeader("");
        interfaceInfo.setResponseHeader("");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
        interfaceInfo.setMethod("GET");
        interfaceInfo.setQuotaType(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
        interfaceInfo.setUserId(999L);
        interfaceInfoService.save(interfaceInfo);
        return interfaceInfo;
    }

    /**
     * 按账号查询有效用户。
     *
     * @param account 用户账号
     * @return 用户信息，不存在时返回空
     */
    private User getUserByAccount(String account) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", account);
        return userService.getOne(queryWrapper);
    }

    /**
     * 查询内置演示接口。
     *
     * @return 演示接口，不存在时返回空
     */
    private InterfaceInfo getDemoInterface() {
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("path", "/api/name/user")
                .eq("method", "POST");
        return interfaceInfoService.getOne(queryWrapper);
    }

    /**
     * 查询指定用户和接口的额度关系。
     *
     * @param userId          用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 额度关系，不存在时返回空
     */
    private UserInterfaceInfo getQuotaRelation(Long userId, Long interfaceInfoId) {
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("interface_info_id", interfaceInfoId);
        return userInterfaceInfoService.getOne(queryWrapper);
    }

    /**
     * 统计有效管理员数量。
     *
     * @return 管理员数量
     */
    private long countAdmins() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_role", UserRoleEnum.ADMIN.getCode());
        return userService.count(queryWrapper);
    }
}
