package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.config.InterfaceTargetHostProperties;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoMethodEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import com.feiting.feiapicommon.utils.InterfaceTargetHostValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author asus
* @description 针对表【interface_info(接口信息)】的数据库操作Service实现
* @createDate 2023-02-20 21:59:30
*/
@Service
public class InterfaceInfoServiceImpl extends ServiceImpl<InterfaceInfoMapper, InterfaceInfo>
    implements InterfaceInfoService {

    /**
     * 真实后端地址白名单配置。
     */
    private final InterfaceTargetHostProperties interfaceTargetHostProperties;

    /**
     * 运行时请求参数模板校验器。
     */
    private final RuntimeRequestParamTemplateValidator runtimeRequestParamTemplateValidator;

    /**
     * 创建接口信息服务实现。
     *
     * @param interfaceTargetHostProperties        真实后端地址白名单配置
     * @param runtimeRequestParamTemplateValidator 运行时请求参数模板校验器
     */
    public InterfaceInfoServiceImpl(InterfaceTargetHostProperties interfaceTargetHostProperties,
                                    RuntimeRequestParamTemplateValidator runtimeRequestParamTemplateValidator) {
        this.interfaceTargetHostProperties = interfaceTargetHostProperties;
        this.runtimeRequestParamTemplateValidator = runtimeRequestParamTemplateValidator;
    }

    /**
     * 校验接口运行时配置。
     *
     * @param interfaceInfo 接口信息
     * @param add           是否为新增场景
     */
    @Override
    public void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean add) {
        // 空对象校验
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String name = interfaceInfo.getName();
        String sdkMethodName = interfaceInfo.getSdkMethodName();
        String url = interfaceInfo.getUrl();
        String path = interfaceInfo.getPath();
        String targetHost = interfaceInfo.getTargetHost();
        String requestParams = interfaceInfo.getRequestParams();
        String method = interfaceInfo.getMethod();
        String quotaType = interfaceInfo.getQuotaType();

        // 创建时，必填字段强制校验：name、path、targetHost、method 不能为空。
        if (add) {
            if (StringUtils.isAnyBlank(name, path, targetHost, method)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称、接口路径、真实后端服务地址、请求方法不能为空");
            }
        } else {
            // 更新时，只校验传入的核心字段不能是空白
            // name 如果传了，不能是空白
            if (name != null && name.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口名称不能为空白");
            }
            // url 如果传了，不能是空白
            if (url != null && url.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口URL不能为空白");
            }
            // path 如果传了，不能是空白
            if (path != null && path.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口路径不能为空白");
            }
            // targetHost 如果传了，不能是空白
            if (targetHost != null && targetHost.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "真实后端服务地址不能为空白");
            }
            // method 如果传了，不能是空白
            if (method != null && method.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求方法不能为空白");
            }
        }

        // name 长度校验（创建和更新都适用）
        if (StringUtils.isNotBlank(name) && name.length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "名称过长");
        }
        if (sdkMethodName != null && sdkMethodName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SDK 方法名不能为空白");
        }
        if (StringUtils.isNotBlank(sdkMethodName) && sdkMethodName.length() > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SDK 方法名过长");
        }
        if (StringUtils.isNotBlank(method) && !InterfaceInfoMethodEnum.isValid(method)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求方法不合法");
        }
        if (StringUtils.isNotBlank(quotaType) && !InterfaceQuotaTypeEnum.isValid(quotaType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "配额类型不合法");
        }
        if (StringUtils.isNotBlank(path) && !path.trim().startsWith("/")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口路径必须以 / 开头");
        }
        if (StringUtils.isNotBlank(targetHost)
                && !InterfaceTargetHostValidator.isSafeTargetHost(targetHost, interfaceTargetHostProperties.getAllowedHostnames())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "真实后端服务地址不允许访问");
        }
        runtimeRequestParamTemplateValidator.validate(requestParams);
    }

    /**
     * 按接口调用总数分页查询接口视图。
     *
     * @param queryRequest 查询条件
     * @param status       接口状态过滤值
     * @param asc          是否按调用总数升序排序
     * @return 接口视图分页结果
     */
    @Override
    public Page<InterfaceInfoVO> listPageOrderByTotalNum(InterfaceInfoQueryRequest queryRequest, Integer status, boolean asc) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String method = InterfaceInfoMethodEnum.normalize(queryRequest.getMethod());
        Page<InterfaceInfoVO> page = new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
        return baseMapper.selectPageOrderByTotalNum(page, queryRequest, status, method, asc);
    }
}




