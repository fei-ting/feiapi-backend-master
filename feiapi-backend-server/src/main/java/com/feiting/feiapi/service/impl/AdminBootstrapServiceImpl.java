package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.AdminBootstrapService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统管理员一次性初始化服务实现。
 */
@Slf4j
@Service
public class AdminBootstrapServiceImpl implements AdminBootstrapService {

    /**
     * 内置演示接口路径。
     */
    private static final String DEMO_INTERFACE_PATH = "/api/name/user";

    /**
     * 内置演示接口请求方法。
     */
    private static final String DEMO_INTERFACE_METHOD = "POST";

    /**
     * 用户服务。
     */
    private final UserService userService;

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 用户接口额度服务。
     */
    private final UserInterfaceInfoService userInterfaceInfoService;

    /**
     * 创建管理员初始化服务。
     *
     * @param userService              用户服务
     * @param interfaceInfoService     接口信息服务
     * @param userInterfaceInfoService 用户接口额度服务
     */
    public AdminBootstrapServiceImpl(UserService userService,
                                     InterfaceInfoService interfaceInfoService,
                                     UserInterfaceInfoService userInterfaceInfoService) {
        this.userService = userService;
        this.interfaceInfoService = interfaceInfoService;
        this.userInterfaceInfoService = userInterfaceInfoService;
    }

    /**
     * 初始化系统管理员及演示数据。
     *
     * @param account         管理员账号
     * @param initialPassword 管理员初始密码
     * @param displayName     管理员显示名称
     * @return 是否创建了新的管理员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean initialize(String account, String initialPassword, String displayName) {
        User admin = findExistingAdmin();
        boolean created = false;
        if (admin == null) {
            assertBootstrapAccountAvailable(account);
            admin = userService.createBootstrapAdmin(account, initialPassword, displayName);
            created = true;
            log.info("系统管理员一次性初始化完成，adminId={}", admin.getId());
        } else {
            log.info("系统已存在管理员，跳过管理员凭据初始化，adminId={}", admin.getId());
        }

        ensureDemoInterface(admin.getId());
        userInterfaceInfoService.grantInitialQuotaForNewUser(admin.getId());
        return created;
    }

    /**
     * 查询现有有效管理员。
     *
     * @return 最早创建的有效管理员，不存在时返回空
     */
    private User findExistingAdmin() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_role", UserRoleEnum.ADMIN.getCode())
                .orderByAsc("id")
                .last("LIMIT 1");
        return userService.getOne(queryWrapper);
    }

    /**
     * 确认待初始化账号没有被普通用户占用。
     *
     * @param account 管理员账号
     */
    private void assertBootstrapAccountAvailable(String account) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", account);
        if (userService.count(queryWrapper) > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "管理员初始化账号已被普通用户占用，拒绝自动提权");
        }
    }

    /**
     * 创建管理员拥有的内置演示接口。
     *
     * @param adminId 管理员用户 ID
     */
    private void ensureDemoInterface(Long adminId) {
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("path", DEMO_INTERFACE_PATH)
                .eq("method", DEMO_INTERFACE_METHOD);
        if (interfaceInfoService.count(queryWrapper) > 0) {
            return;
        }

        InterfaceInfo interfaceInfo = buildDemoInterface(adminId);
        interfaceInfoService.validInterfaceInfo(interfaceInfo, true);
        try {
            if (!interfaceInfoService.save(interfaceInfo)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化演示接口失败");
            }
        } catch (DuplicateKeyException exception) {
            log.info("演示接口已由其他初始化任务创建，跳过重复写入");
        }
    }

    /**
     * 构造内置演示接口。
     *
     * @param adminId 管理员用户 ID
     * @return 内置演示接口实体
     */
    private InterfaceInfo buildDemoInterface(Long adminId) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("测试接口");
        interfaceInfo.setSdkMethodName("getUsernameByPost");
        interfaceInfo.setDescription("根据用户对象获取用户名（测试接口）");
        interfaceInfo.setUrl("http://feiapi-interface:8123/api/name/user");
        interfaceInfo.setPath(DEMO_INTERFACE_PATH);
        interfaceInfo.setTargetHost("http://feiapi-interface:8123");
        interfaceInfo.setRequestParams("{\"username\":\"string\"}");
        interfaceInfo.setRequestHeader("Content-Type: application/json");
        interfaceInfo.setResponseHeader("");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
        interfaceInfo.setMethod(DEMO_INTERFACE_METHOD);
        interfaceInfo.setQuotaType(InterfaceQuotaTypeEnum.FREE_UNLIMITED.getValue());
        interfaceInfo.setUserId(adminId);
        return interfaceInfo;
    }
}
