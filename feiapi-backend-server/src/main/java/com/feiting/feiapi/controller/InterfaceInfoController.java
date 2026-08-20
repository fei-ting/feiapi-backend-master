package com.feiting.feiapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.common.*;
import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.SdkContractSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.model.vo.SdkMethodOptionVO;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionReader;
import com.feiting.feiapi.interfaceplatform.facade.service.api.InterfaceInfoApplicationService;
import com.feiting.feiapi.interfaceplatform.invocation.model.vo.InterfaceInvokeResultVO;
import com.feiting.feiapi.interfaceplatform.invocation.service.api.InterfaceInvokeService;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishCheckVO;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.constant.CommonConstant;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfaceInfoPublishingService;
import com.feiting.feiapi.interfaceplatform.publishing.service.api.InterfacePublishCheckService;
import com.feiting.feiapi.interfaceplatform.documentation.service.api.InterfaceDocQueryService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.utils.SortFieldUtils;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接口管理
 *
 */
@RestController
@RequestMapping("/interfaceInfo")
public class InterfaceInfoController {

    /** 调用总数字段名，用于触发聚合排序查询 */
    private static final String TOTAL_NUM_SORT_FIELD = "totalNum";

    private static final Set<String> ALLOWED_SORT_FIELDS = SortFieldUtils.allowedFields(
            "id", "name", "sdkMethodName", "description", "url", "path", "targetHost", "requestParams", "requestHeader",
            "responseHeader", "status", "method", "quotaType", "userId", "createTime", "updateTime"
    );

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private InterfaceInfoApplicationService interfaceInfoApplicationService;

    /**
     * 接口定义只读服务。
     */
    @Resource
    private InterfaceDefinitionReader interfaceDefinitionReader;

    /**
     * 接口发布编排服务。
     */
    @Resource
    private InterfaceInfoPublishingService interfaceInfoPublishingService;

    /**
     * 接口发布前静态检查服务。
     */
    @Resource
    private InterfacePublishCheckService interfacePublishCheckService;

    /**
     * 接口文档服务。
     */
    @Resource
    private InterfaceDocQueryService interfaceDocQueryService;

    @Resource
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    @Resource
    private UserService userService;

    @Resource
    private UserSessionManager userSessionManager;

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private InterfaceInvokeService interfaceInvokeService;

    // region 增删改查

    /**
     * 查询管理员新增接口时可选择的 SDK 方法。
     *
     * @return 已注册 SDK 方法选项
     */
    @GetMapping("/sdk-method/list")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<List<SdkMethodOptionVO>> listSdkMethodOptions() {
        List<SdkMethodOptionVO> options = interfaceDefinitionReader.listSdkContracts().stream()
                .map(this::toSdkMethodOptionVO)
                .collect(Collectors.toList());
        return ResultUtils.success(options);
    }

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
        normalizeInterfaceInfo(interfaceInfo, true);
        // 校验
        interfaceInfoService.validInterfaceInfo(interfaceInfo, true);
        User loginUser = getCurrentLoginUser(request);
        interfaceInfo.setUserId(loginUser.getId());
        long newInterfaceInfoId = interfaceInfoApplicationService.addInterfaceInfoWithDoc(interfaceInfo);
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
        return ResultUtils.success(interfaceInfoApplicationService.deleteOfflineInterfaceInfo(id));
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
        normalizeInterfaceInfo(interfaceInfo, false);
        boolean result = interfaceInfoApplicationService.updateInterfaceInfoWithDoc(interfaceInfo);
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
        InterfaceInfoVO interfaceInfoVO = toInterfaceInfoVO(interfaceInfo);
        completeDocStatus(Collections.singletonList(interfaceInfoVO));
        return ResultUtils.success(interfaceInfoVO);
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
        completeDocStatus(interfaceInfoVOPage.getRecords());
        return ResultUtils.success(interfaceInfoVOPage);
    }

    // endregion


    /**
     * 发布接口
     * @param idRequest 请求参数
     * @param request   HTTP 请求
     * @return
     */
    @PostMapping("/online")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> onlineInterfaceInfo(@Valid @RequestBody IdRequest idRequest,
                                                     HttpServletRequest request) {
        //参数校验
        if(idRequest == null || idRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = getCurrentLoginUser(request);
        return ResultUtils.success(interfaceInfoPublishingService.publish(idRequest.getId(), loginUser.getId()));
    }

    /**
     * 发布前只读检查接口。
     *
     * @param id 接口信息 ID
     * @return 发布前检查结果
     */
    @GetMapping("/publish/check")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<InterfacePublishCheckVO> checkPublish(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(interfacePublishCheckService.check(id));
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

        return ResultUtils.success(interfaceInfoApplicationService.offlineInterfaceInfo(idRequest.getId()));
    }


    /**
     * 在线调用接口
     * @param interfaceInfoInvokeRequest
     * @param request
     * @return
     */
    @PostMapping("/invoke")
    public BaseResponse<InterfaceInvokeResultVO> invokeInterfaceInfo(
            @Valid @RequestBody InterfaceInfoInvokeRequest interfaceInfoInvokeRequest,
            HttpServletRequest request) {
        //参数校验
        if(interfaceInfoInvokeRequest == null || interfaceInfoInvokeRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = getCurrentLoginUser(request);
        return ResultUtils.success(interfaceInvokeService.invoke(
                interfaceInfoInvokeRequest.getId(),
                interfaceInfoInvokeRequest.getUserRequestParams(),
                loginUser));
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
        completeDocStatus(interfaceInfoVOPage.getRecords());
        return interfaceInfoVOPage;
    }

    /**
     * 批量补齐接口视图中的文档状态。
     *
     * @param records 当前页接口视图列表
     */
    private void completeDocStatus(List<InterfaceInfoVO> records) {
        List<Long> interfaceInfoIds = Optional.ofNullable(records)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .map(InterfaceInfoVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, String> docStatusMap = interfaceDocQueryService.listDocStatusByInterfaceInfoIds(interfaceInfoIds);
        Optional.ofNullable(records)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .forEach(record -> record.setDocStatus(docStatusMap.get(record.getId())));
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
     * @param interfaceInfo  接口信息
     * @param applyDefaults 是否应用新增接口默认值
     */
    private void normalizeInterfaceInfo(InterfaceInfo interfaceInfo, boolean applyDefaults) {
        if (interfaceInfo == null) {
            return;
        }
        if (StringUtils.isNotBlank(interfaceInfo.getMethod())) {
            interfaceInfo.setMethod(InterfaceInfoMethodEnum.normalize(interfaceInfo.getMethod()));
        }
        if (StringUtils.isNotBlank(interfaceInfo.getSdkMethodName())) {
            interfaceInfo.setSdkMethodName(interfaceInfo.getSdkMethodName().trim());
        }
        if (applyDefaults && StringUtils.isBlank(interfaceInfo.getQuotaType())) {
            interfaceInfo.setQuotaType(InterfaceQuotaTypeEnum.BASIC_QUOTA.getValue());
        } else if (StringUtils.isNotBlank(interfaceInfo.getQuotaType())) {
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
     * 将 SDK 方法契约快照转换为前端选项。
     *
     * @param snapshot SDK 方法契约快照
     * @return SDK 方法选项
     */
    private SdkMethodOptionVO toSdkMethodOptionVO(SdkContractSnapshot snapshot) {
        SdkMethodOptionVO optionVO = new SdkMethodOptionVO();
        optionVO.setSdkMethodName(snapshot.getSdkMethodName());
        optionVO.setNeedParams(snapshot.isNeedParams());
        return optionVO;
    }
}
