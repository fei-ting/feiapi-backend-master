package com.feiting.feiapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.common.*;
import com.feiting.feiapi.component.InterfaceRequestParamValidator;
import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.constant.CommonConstant;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.utils.SortFieldUtils;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口管理
 *
 */
@RestController
@RequestMapping("/interfaceInfo")
@Slf4j
public class InterfaceInfoController {

    /** 调用总数字段名，用于触发聚合排序查询 */
    private static final String TOTAL_NUM_SORT_FIELD = "totalNum";

    private static final Set<String> ALLOWED_SORT_FIELDS = SortFieldUtils.allowedFields(
            "id", "name", "sdkMethodName", "description", "url", "path", "targetHost", "requestParams", "requestHeader",
            "responseHeader", "status", "method", "quotaType", "userId", "createTime", "updateTime"
    );

    /** 发布验证超时时间（毫秒），超过此时间的 PUBLISHING 状态将被视为超时并自动恢复为 OFFLINE */
    private static final long PUBLISHING_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private InterfaceInfoLifecycleService interfaceInfoLifecycleService;

    @Resource
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    @Resource
    private UserService userService;

    @Resource
    private UserSessionManager userSessionManager;

    @Resource
    private FeiApiClient feiApiClient;

    @Resource
    private SdkMethodRegistry sdkMethodRegistry;

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private InterfaceRequestParamValidator interfaceRequestParamValidator;

    @Value("${feiapi.client.gateway-host}")
    private String gatewayHost;

    // region 增删改查

    /**
     * 创建
     *
     * @param interfaceInfoAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Long> addInterfaceInfo(@Valid @RequestBody InterfaceInfoAddRequest interfaceInfoAddRequest, HttpServletRequest request) {
        if (interfaceInfoAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        BeanUtils.copyProperties(interfaceInfoAddRequest, interfaceInfo);
        normalizeInterfaceInfo(interfaceInfo);
        // 校验
        interfaceInfoService.validInterfaceInfo(interfaceInfo, true);
        User loginUser = getCurrentLoginUser(request);
        interfaceInfo.setUserId(loginUser.getId());
        long newInterfaceInfoId = interfaceInfoLifecycleService.addInterfaceInfoWithDoc(interfaceInfo);
        return ResultUtils.success(newInterfaceInfoId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> deleteInterfaceInfo(@Valid @RequestBody IdRequest idRequest, HttpServletRequest request) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = idRequest.getId();
        return ResultUtils.success(interfaceInfoLifecycleService.deleteOfflineInterfaceInfo(id));
    }

    /**
     * 更新
     *
     * @param interfaceInfoUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateInterfaceInfo(@Valid @RequestBody InterfaceInfoUpdateRequest interfaceInfoUpdateRequest,
                                            HttpServletRequest request) {
        if (interfaceInfoUpdateRequest == null || interfaceInfoUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        // 接口状态和归属人只能由平台内部流程维护，禁止通过通用更新接口写入。
        BeanUtils.copyProperties(interfaceInfoUpdateRequest, interfaceInfo, "status", "userId");
        // 参数校验
        interfaceInfoService.validInterfaceInfo(interfaceInfo, false);
        long id = interfaceInfoUpdateRequest.getId();
        // 判断是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        completeUpdateDisplayUrl(interfaceInfo, oldInterfaceInfo);
        normalizeInterfaceInfo(interfaceInfo);
        boolean result = interfaceInfoLifecycleService.updateInterfaceInfoWithDoc(interfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<InterfaceInfoVO> getInterfaceInfoById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        if (interfaceInfo == null || !isVisibleToCurrentUser(interfaceInfo, request)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(toInterfaceInfoVO(interfaceInfo));
    }

    /**
     * 分页获取列表
     *
     * @param interfaceInfoQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<InterfaceInfoVO>> listInterfaceInfoByPage(@Valid InterfaceInfoQueryRequest interfaceInfoQueryRequest, HttpServletRequest request) {
        if (interfaceInfoQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = interfaceInfoQueryRequest.getCurrent();
        long size = interfaceInfoQueryRequest.getPageSize();
        String sortField = toDatabaseSortField(interfaceInfoQueryRequest.getSortField());
        String sortOrder = interfaceInfoQueryRequest.getSortOrder();
        // 限制爬虫
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<InterfaceInfo> queryWrapper = new QueryWrapper<>();
        Integer status = interfaceInfoQueryRequest.getStatus();
        if (!isCurrentUserAdmin(request)) {
            status = InterfaceInfoStatusEnum.ONLINE.getValue();
        }
        if (isTotalNumSortField(interfaceInfoQueryRequest.getSortField())) {
            return ResultUtils.success(listInterfaceInfoByTotalNumPage(interfaceInfoQueryRequest, status, sortOrder));
        }
        String descriptionKeyword = interfaceInfoQueryRequest.getDescription();
        queryWrapper.eq(interfaceInfoQueryRequest.getId() != null, "id", interfaceInfoQueryRequest.getId());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getName()), "name", interfaceInfoQueryRequest.getName());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getSdkMethodName()),
                "sdk_method_name", interfaceInfoQueryRequest.getSdkMethodName());
        queryWrapper.like(StringUtils.isNotBlank(descriptionKeyword), "description", descriptionKeyword);
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getUrl()), "url", interfaceInfoQueryRequest.getUrl());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getPath()), "path", interfaceInfoQueryRequest.getPath());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getTargetHost()), "target_host", interfaceInfoQueryRequest.getTargetHost());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getRequestParams()), "request_params", interfaceInfoQueryRequest.getRequestParams());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getRequestHeader()), "request_header", interfaceInfoQueryRequest.getRequestHeader());
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getResponseHeader()), "response_header", interfaceInfoQueryRequest.getResponseHeader());
        queryWrapper.eq(status != null, "status", status);
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getMethod()),
                "method", InterfaceInfoMethodEnum.normalize(interfaceInfoQueryRequest.getMethod()));
        queryWrapper.eq(StringUtils.isNotBlank(interfaceInfoQueryRequest.getQuotaType()),
                "quota_type", interfaceInfoQueryRequest.getQuotaType());
        queryWrapper.eq(interfaceInfoQueryRequest.getUserId() != null, "user_id", interfaceInfoQueryRequest.getUserId());
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);
        Page<InterfaceInfo> interfaceInfoPage = interfaceInfoService.page(new Page<>(current, size), queryWrapper);
        Page<InterfaceInfoVO> interfaceInfoVOPage = new Page<>(interfaceInfoPage.getCurrent(), interfaceInfoPage.getSize(), interfaceInfoPage.getTotal());
        List<Long> interfaceInfoIds = interfaceInfoPage.getRecords().stream()
                .map(InterfaceInfo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, Integer> totalNumMap = userInterfaceInfoService.listTotalNumByInterfaceInfoIds(interfaceInfoIds);
        interfaceInfoVOPage.setRecords(interfaceInfoPage.getRecords().stream()
                .map(interfaceInfo -> toInterfaceInfoVO(interfaceInfo, totalNumMap))
                .collect(Collectors.toList()));
        return ResultUtils.success(interfaceInfoVOPage);
    }

    // endregion


    /**
     * 发布接口
     * @param idRequest
     * @return
     */
    @PostMapping("/online")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> onlineInterfaceInfo(@Valid @RequestBody IdRequest idRequest) {
        //参数校验
        if(idRequest == null || idRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long id = idRequest.getId();

        //检查接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 懒恢复：如果接口处于 PUBLISHING 状态且已超时，先恢复为 OFFLINE，避免残留状态阻塞发布。
        recoverExpiredPublishingStatus(oldInterfaceInfo);

        // 前置检查：只允许 OFFLINE -> PUBLISHING
        if (oldInterfaceInfo.getStatus() != InterfaceInfoStatusEnum.OFFLINE.getValue()) {
            if (oldInterfaceInfo.getStatus() == InterfaceInfoStatusEnum.PUBLISHING.getValue()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口正在发布验证中，请稍后重试");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅支持从下线状态发布");
        }
        String sdkMethodName = getRequiredSdkMethodName(oldInterfaceInfo);

        // 条件更新：只在当前状态为 OFFLINE 时才更新为 PUBLISHING
        updateInterfaceStatus(id,
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                "接口发布状态更新失败，请刷新后重试");

        try {
            feiApiClient.enableProbeMode();
            Object invoke = sdkMethodRegistry.invoke(feiApiClient, sdkMethodName, oldInterfaceInfo.getRequestParams());
            if(invoke == null){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"接口验证失败");
            }
            // 成功后：只在当前状态为 PUBLISHING 时才更新为 ONLINE
            updateInterfaceStatus(id,
                    InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                    InterfaceInfoStatusEnum.ONLINE.getValue(),
                    "接口发布状态已变化，请刷新后重试");
            return ResultUtils.success(true);
        } catch (Exception e) {
            // 回滚时：只在当前状态为 PUBLISHING 时才回滚为 OFFLINE
            rollbackPublishingStatus(id);
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口验证失败：" + e.getMessage());
        } finally {
            feiApiClient.disableProbeMode();
        }
    }


    /**
     * 下线接口
     * @param idRequest
     * @return
     */
    @PostMapping("/offline")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> offlineInterfaceInfo(@Valid @RequestBody IdRequest idRequest) {
        //参数校验
        if(idRequest == null || idRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long id = idRequest.getId();

        //检查接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        if (oldInterfaceInfo.getStatus() != InterfaceInfoStatusEnum.ONLINE.getValue()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口仅支持从上线状态下线");
        }

        updateInterfaceStatus(id,
                InterfaceInfoStatusEnum.ONLINE.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口下线状态已变化，请刷新后重试");
        return ResultUtils.success(true);
    }


    /**
     * 在线调用接口
     * @param interfaceInfoInvokeRequest
     * @param request
     * @return
     */
    @PostMapping("/invoke")
    public BaseResponse<Object> invokeInterfaceInfo(@Valid @RequestBody InterfaceInfoInvokeRequest interfaceInfoInvokeRequest,
                                                     HttpServletRequest request) {
        //参数校验
        if(interfaceInfoInvokeRequest == null || interfaceInfoInvokeRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long id = interfaceInfoInvokeRequest.getId();
        String userRequestParams = interfaceInfoInvokeRequest.getUserRequestParams();

        //检查接口是否存在
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(id);
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if(oldInterfaceInfo.getStatus() != InterfaceInfoStatusEnum.ONLINE.getValue()){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"接口未上线或正在发布验证中");
        }
        interfaceRequestParamValidator.validate(oldInterfaceInfo.getRequestParams(), userRequestParams);

        User loginUser = getCurrentLoginUser(request);
        String accessKey = loginUser.getAccessKey();
        String secretKey = loginUser.getSecretKey();

        //调用模拟接口
        FeiApiClient tempClient = new FeiApiClient(accessKey, secretKey, gatewayHost);
        Object invoke = sdkMethodRegistry.invoke(tempClient, getRequiredSdkMethodName(oldInterfaceInfo), userRequestParams);
        return ResultUtils.success(invoke);
    }

    /**
     * 获取接口绑定的 SDK 方法名。
     *
     * @param interfaceInfo 接口信息
     * @return SDK 方法名
     */
    private String getRequiredSdkMethodName(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null || StringUtils.isBlank(interfaceInfo.getSdkMethodName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口未配置 SDK 方法名");
        }
        return interfaceInfo.getSdkMethodName().trim();
    }

    private String toDatabaseSortField(String sortField) {
        return SortFieldUtils.resolveSortField(sortField, ALLOWED_SORT_FIELDS);
    }

    /**
     * 判断是否按接口调用总数排序。
     *
     * @param sortField 排序字段
     * @return 是否为调用总数字段
     */
    private boolean isTotalNumSortField(String sortField) {
        return TOTAL_NUM_SORT_FIELD.equals(sortField);
    }

    /**
     * 按接口调用总数分页查询接口视图。
     *
     * @param queryRequest 查询请求
     * @param status       接口状态过滤值
     * @param sortOrder    排序方向
     * @return 接口视图分页结果
     */
    private Page<InterfaceInfoVO> listInterfaceInfoByTotalNumPage(InterfaceInfoQueryRequest queryRequest,
                                                                  Integer status,
                                                                  String sortOrder) {
        boolean asc = CommonConstant.SORT_ORDER_ASC.equals(sortOrder);
        Page<InterfaceInfoVO> interfaceInfoVOPage = interfaceInfoService.listPageOrderByTotalNum(queryRequest, status, asc);
        interfaceInfoVOPage.setRecords(interfaceInfoVOPage.getRecords().stream()
                .map(this::completeQuotaInfo)
                .collect(Collectors.toList()));
        return interfaceInfoVOPage;
    }

    /**
     * 补齐接口视图中的配额展示信息。
     *
     * @param interfaceInfoVO 接口视图对象
     * @return 补齐配额展示信息后的接口视图对象
     */
    private InterfaceInfoVO completeQuotaInfo(InterfaceInfoVO interfaceInfoVO) {
        if (interfaceInfoVO == null) {
            return null;
        }
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(interfaceInfoVO.getQuotaType());
        if (quotaTypeEnum != null) {
            interfaceInfoVO.setQuotaTypeText(quotaTypeEnum.getText());
            interfaceInfoVO.setInitialQuota(interfaceQuotaConfigService.getInitialQuota(quotaTypeEnum));
        }
        if (interfaceInfoVO.getTotalNum() == null) {
            interfaceInfoVO.setTotalNum(0);
        }
        return interfaceInfoVO;
    }

    /**
     * 标准化接口信息中的派生字段。
     *
     * <p>请求方法统一转为大写；当接口展示地址为空且接口路径、真实后端服务地址存在时，自动组装展示地址。</p>
     *
     * @param interfaceInfo 接口信息
     */
    private void normalizeInterfaceInfo(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            return;
        }
        if (StringUtils.isNotBlank(interfaceInfo.getMethod())) {
            interfaceInfo.setMethod(InterfaceInfoMethodEnum.normalize(interfaceInfo.getMethod()));
        }
        if (StringUtils.isNotBlank(interfaceInfo.getSdkMethodName())) {
            interfaceInfo.setSdkMethodName(interfaceInfo.getSdkMethodName().trim());
        }
        if (StringUtils.isBlank(interfaceInfo.getQuotaType())) {
            interfaceInfo.setQuotaType(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
        } else {
            interfaceInfo.setQuotaType(interfaceInfo.getQuotaType().trim());
        }
        if (StringUtils.isBlank(interfaceInfo.getUrl())
                && StringUtils.isNotBlank(interfaceInfo.getTargetHost())
                && StringUtils.isNotBlank(interfaceInfo.getPath())) {
            interfaceInfo.setUrl(buildDisplayUrl(interfaceInfo.getTargetHost(), interfaceInfo.getPath()));
        }
    }

    /**
     * 更新接口时补齐展示地址。
     *
     * <p>如果本次更新未显式传入 url，但修改了 path 或 targetHost，则使用新旧字段组合生成新的展示地址。</p>
     *
     * @param interfaceInfo    本次更新的接口信息
     * @param oldInterfaceInfo 原接口信息
     */
    private void completeUpdateDisplayUrl(InterfaceInfo interfaceInfo, InterfaceInfo oldInterfaceInfo) {
        if (interfaceInfo == null || oldInterfaceInfo == null || StringUtils.isNotBlank(interfaceInfo.getUrl())) {
            return;
        }
        boolean pathChanged = StringUtils.isNotBlank(interfaceInfo.getPath());
        boolean targetHostChanged = StringUtils.isNotBlank(interfaceInfo.getTargetHost());
        if (!pathChanged && !targetHostChanged) {
            return;
        }
        String targetHost = targetHostChanged ? interfaceInfo.getTargetHost() : oldInterfaceInfo.getTargetHost();
        String path = pathChanged ? interfaceInfo.getPath() : oldInterfaceInfo.getPath();
        if (StringUtils.isNotBlank(targetHost) && StringUtils.isNotBlank(path)) {
            interfaceInfo.setUrl(buildDisplayUrl(targetHost, path));
        }
    }

    /**
     * 构建接口展示地址。
     *
     * @param targetHost 真实后端服务地址
     * @param path       接口路径
     * @return 接口展示地址
     */
    private String buildDisplayUrl(String targetHost, String path) {
        String normalizedTargetHost = targetHost.trim();
        while (normalizedTargetHost.endsWith("/")) {
            normalizedTargetHost = normalizedTargetHost.substring(0, normalizedTargetHost.length() - 1);
        }
        String normalizedPath = path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedTargetHost + normalizedPath;
    }

    /**
     * 将接口实体转换为接口视图对象。
     *
     * @param interfaceInfo 接口实体
     * @return 接口视图对象
     */
    private InterfaceInfoVO toInterfaceInfoVO(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null) {
            return null;
        }
        Map<Long, Integer> totalNumMap = userInterfaceInfoService.listTotalNumByInterfaceInfoIds(
                Collections.singletonList(interfaceInfo.getId())
        );
        return toInterfaceInfoVO(interfaceInfo, totalNumMap);
    }

    /**
     * 将接口实体转换为接口视图对象，并填充调用总数。
     *
     * @param interfaceInfo 接口实体
     * @param totalNumMap   接口 ID 与调用总数映射
     * @return 接口视图对象
     */
    private InterfaceInfoVO toInterfaceInfoVO(InterfaceInfo interfaceInfo, Map<Long, Integer> totalNumMap) {
        if (interfaceInfo == null) {
            return null;
        }
        InterfaceInfoVO interfaceInfoVO = new InterfaceInfoVO();
        BeanUtils.copyProperties(interfaceInfo, interfaceInfoVO);
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(interfaceInfo.getQuotaType());
        if (quotaTypeEnum != null) {
            interfaceInfoVO.setQuotaTypeText(quotaTypeEnum.getText());
            interfaceInfoVO.setInitialQuota(interfaceQuotaConfigService.getInitialQuota(quotaTypeEnum));
        }
        interfaceInfoVO.setTotalNum(totalNumMap.getOrDefault(interfaceInfo.getId(), 0));
        return interfaceInfoVO;
    }

    /**
     * 判断当前用户是否可以查看接口详情。
     *
     * <p>管理员可以查看全部接口；普通用户和未登录访客只能查看已上线接口，避免暴露待发布或已下线接口信息。</p>
     *
     * @param interfaceInfo 接口信息
     * @param request       HTTP 请求
     * @return 是否可见
     */
    private boolean isVisibleToCurrentUser(InterfaceInfo interfaceInfo, HttpServletRequest request) {
        return isCurrentUserAdmin(request)
                || interfaceInfo.getStatus() == InterfaceInfoStatusEnum.ONLINE.getValue();
    }

    /**
     * 从当前 HTTP 会话中获取登录用户
     *
     * @param request HTTP 请求
     * @return 当前登录用户
     */
    private User getCurrentLoginUser(HttpServletRequest request) {
        return userService.getLoginUser(userSessionManager.getLoginUser(request));
    }

    /**
     * 判断当前 HTTP 会话用户是否为管理员
     *
     * @param request HTTP 请求
     * @return 是否为管理员
     */
    private boolean isCurrentUserAdmin(HttpServletRequest request) {
        User sessionUser = userSessionManager.getLoginUser(request);
        if (sessionUser == null || sessionUser.getId() == null) {
            return false;
        }
        try {
            return userService.isAdmin(userService.getLoginUser(sessionUser));
        } catch (BusinessException e) {
            if (ErrorCode.NOT_LOGIN_ERROR.getCode() == e.getCode()) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 条件更新接口状态。
     *
     * <p>只在当前状态等于 expectedStatus 时才更新为 targetStatus，防止并发操作导致状态错乱。</p>
     *
     * @param id             接口 ID
     * @param expectedStatus 期望的当前状态
     * @param targetStatus   目标状态
     * @param errorMessage   更新失败时的错误提示
     */
    private void updateInterfaceStatus(long id, int expectedStatus, int targetStatus, String errorMessage) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(id);
        interfaceInfo.setStatus(targetStatus);
        UpdateWrapper<InterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.eq("status", expectedStatus);
        boolean result = interfaceInfoService.update(interfaceInfo, updateWrapper);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 回滚发布验证中的接口状态为 OFFLINE。
     *
     * <p>只在当前状态仍为 PUBLISHING 时才回滚，避免覆盖其他并发操作的结果。</p>
     * <p>回滚失败时仅记录日志，不抛出异常，避免掩盖原始错误。</p>
     *
     * @param id 接口 ID
     */
    private void rollbackPublishingStatus(long id) {
        try {
            updateInterfaceStatus(id,
                    InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                    InterfaceInfoStatusEnum.OFFLINE.getValue(),
                    "接口发布验证失败后回滚状态失败");
        } catch (Exception e) {
            log.error("接口发布验证失败后回滚状态失败，interfaceInfoId={}", id, e);
        }
    }

    /**
     * 懒恢复超时的 PUBLISHING 状态为 OFFLINE。
     *
     * <p>如果接口处于 PUBLISHING 状态且距离上次更新已超过 10 分钟，说明发布流程可能因进程崩溃等原因中断，
     * 此时将状态恢复为 OFFLINE，允许管理员重新发布。</p>
     *
     * @param interfaceInfo 接口信息
     */
    private void recoverExpiredPublishingStatus(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null || interfaceInfo.getStatus() != InterfaceInfoStatusEnum.PUBLISHING.getValue()) {
            return;
        }
        Date updateTime = interfaceInfo.getUpdateTime();
        if (updateTime == null || System.currentTimeMillis() - updateTime.getTime() <= PUBLISHING_TIMEOUT_MILLIS) {
            return;
        }

        updateInterfaceStatus(interfaceInfo.getId(),
                InterfaceInfoStatusEnum.PUBLISHING.getValue(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(),
                "接口发布验证状态恢复失败，请刷新后重试");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
    }
}
