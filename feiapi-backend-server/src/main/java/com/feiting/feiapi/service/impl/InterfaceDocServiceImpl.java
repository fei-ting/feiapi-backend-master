package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceDocMapper;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.model.vo.InterfaceDocErrorCodeVO;
import com.feiting.feiapi.model.vo.InterfaceDocParamVO;
import com.feiting.feiapi.model.vo.InterfaceDocVO;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 接口文档主信息服务实现。
 */
@Service
public class InterfaceDocServiceImpl extends ServiceImpl<InterfaceDocMapper, InterfaceDoc>
        implements InterfaceDocService {

    /**
     * 默认请求内容类型。
     */
    private static final String DEFAULT_REQUEST_CONTENT_TYPE = "application/json";

    /**
     * 默认响应内容类型。
     */
    private static final String DEFAULT_RESPONSE_CONTENT_TYPE = "application/json";

    /**
     * 支持的参数类型标记。
     */
    private static final Set<String> SUPPORTED_TYPE_MARKERS = new HashSet<>(
            Arrays.asList("string", "number", "boolean", "object", "array"));

    /**
     * 当前网关调用地址前缀。
     */
    @Value("${feiapi.client.gateway-host}")
    private String gatewayHost;

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 文档参数服务。
     */
    private final InterfaceDocParamService interfaceDocParamService;

    /**
     * 文档错误码服务。
     */
    private final InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * 接口配额配置服务。
     */
    private final InterfaceQuotaConfigService interfaceQuotaConfigService;

    /**
     * 用户接口调用关系服务。
     */
    private final UserInterfaceInfoService userInterfaceInfoService;

    public InterfaceDocServiceImpl(InterfaceInfoService interfaceInfoService,
                                   InterfaceDocParamService interfaceDocParamService,
                                   InterfaceDocErrorCodeService interfaceDocErrorCodeService,
                                   InterfaceQuotaConfigService interfaceQuotaConfigService,
                                   UserInterfaceInfoService userInterfaceInfoService) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceDocParamService = interfaceDocParamService;
        this.interfaceDocErrorCodeService = interfaceDocErrorCodeService;
        this.interfaceQuotaConfigService = interfaceQuotaConfigService;
        this.userInterfaceInfoService = userInterfaceInfoService;
    }

    /**
     * 获取接口文档聚合详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param admin           当前用户是否为管理员
     * @return 接口文档聚合详情
     */
    @Override
    public InterfaceDocDetailVO getDocDetail(Long interfaceInfoId, boolean admin) {
        if (interfaceInfoId == null || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(interfaceInfoId);
        if (interfaceInfo == null || !canView(interfaceInfo, admin)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        InterfaceDoc doc = lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                .one();
        List<InterfaceDocParam> docParams = listDocParams(interfaceInfoId);
        List<InterfaceDocErrorCode> errorCodes = listErrorCodes(interfaceInfoId);
        boolean hasStructuredDoc = doc != null || !docParams.isEmpty() || !errorCodes.isEmpty();

        InterfaceDocDetailVO detailVO = new InterfaceDocDetailVO();
        detailVO.setInterfaceInfo(toInterfaceInfoVO(interfaceInfo, admin));
        detailVO.setDoc(toInterfaceDocVO(doc, interfaceInfo));
        detailVO.setGatewayUrl(buildGatewayUrl(interfaceInfo));
        detailVO.setLegacyFallback(!hasStructuredDoc);
        detailVO.setRequestHeaders(resolveParams(docParams, InterfaceDocParamSceneEnum.HEADER));
        detailVO.setRequestParams(resolveRequestParams(docParams));
        detailVO.setResponseParams(resolveParams(docParams, InterfaceDocParamSceneEnum.RESPONSE));
        detailVO.setErrorCodes(errorCodes.stream()
                .map(this::toErrorCodeVO)
                .collect(Collectors.toList()));
        detailVO.setCurlExample(buildCurlExample(interfaceInfo, detailVO));
        return detailVO;
    }

    /**
     * 根据接口运行时参数模板同步结构化请求参数文档。
     *
     * @param interfaceInfo 接口信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncRequestDocFromInterfaceInfo(InterfaceInfo interfaceInfo) {
        if (interfaceInfo == null || interfaceInfo.getId() == null || interfaceInfo.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ensureInterfaceDoc(interfaceInfo);
        removeRequestDocParams(interfaceInfo.getId());
        List<InterfaceDocParam> requestParams = buildRequestDocParams(interfaceInfo);
        if (!requestParams.isEmpty()) {
            boolean result = interfaceDocParamService.saveBatch(requestParams);
            if (!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "结构化请求参数保存失败");
            }
        }
    }

    /**
     * 确保接口文档主信息存在。
     *
     * @param interfaceInfo 接口信息
     */
    private void ensureInterfaceDoc(InterfaceInfo interfaceInfo) {
        InterfaceDoc doc = lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfo.getId())
                .one();
        if (doc != null) {
            return;
        }
        InterfaceDoc newDoc = new InterfaceDoc();
        newDoc.setInterfaceInfoId(interfaceInfo.getId());
        newDoc.setDocVersion("v1");
        newDoc.setRequestContentType(DEFAULT_REQUEST_CONTENT_TYPE);
        newDoc.setResponseContentType(DEFAULT_RESPONSE_CONTENT_TYPE);
        newDoc.setAuthDescription("通过平台 AccessKey/SecretKey 签名鉴权，由网关统一校验。");
        boolean result = save(newDoc);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口文档主信息保存失败");
        }
    }

    /**
     * 删除已有结构化请求参数。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void removeRequestDocParams(Long interfaceInfoId) {
        interfaceDocParamService.lambdaUpdate()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .in(InterfaceDocParam::getParamScene,
                        InterfaceDocParamSceneEnum.QUERY.getValue(),
                        InterfaceDocParamSceneEnum.BODY.getValue())
                .remove();
    }

    /**
     * 根据接口运行时参数模板构建结构化请求参数。
     *
     * @param interfaceInfo 接口信息
     * @return 结构化请求参数列表
     */
    private List<InterfaceDocParam> buildRequestDocParams(InterfaceInfo interfaceInfo) {
        String requestParams = interfaceInfo.getRequestParams();
        if (StringUtils.isBlank(requestParams)) {
            return Collections.emptyList();
        }
        JsonObject requestParamObject = parseRequestParamObject(requestParams);
        InterfaceDocParamSceneEnum sceneEnum = resolveRequestParamScene(interfaceInfo.getMethod());
        int[] sortOrder = {1};
        return requestParamObject.entrySet().stream()
                .map(entry -> buildRequestDocParam(interfaceInfo.getId(), sceneEnum, entry.getKey(), entry.getValue(), sortOrder[0]++))
                .collect(Collectors.toList());
    }

    /**
     * 解析接口运行时参数模板。
     *
     * @param requestParams 请求参数模板
     * @return JSON 对象
     */
    private JsonObject parseRequestParamObject(String requestParams) {
        try {
            JsonElement jsonElement = JsonParser.parseString(requestParams);
            if (!jsonElement.isJsonObject()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数模板必须是 JSON 对象");
            }
            return jsonElement.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数模板必须是合法 JSON");
        }
    }

    /**
     * 解析请求参数场景。
     *
     * @param method 请求方法
     * @return 请求参数场景
     */
    private InterfaceDocParamSceneEnum resolveRequestParamScene(String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return InterfaceDocParamSceneEnum.QUERY;
        }
        return InterfaceDocParamSceneEnum.BODY;
    }

    /**
     * 构建单个结构化请求参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param sceneEnum       参数场景
     * @param name            参数名称
     * @param templateValue   参数模板值
     * @param sortOrder       排序值
     * @return 结构化请求参数
     */
    private InterfaceDocParam buildRequestDocParam(Long interfaceInfoId,
                                                   InterfaceDocParamSceneEnum sceneEnum,
                                                   String name,
                                                   JsonElement templateValue,
                                                   Integer sortOrder) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene(sceneEnum.getValue());
        param.setName(name);
        param.setType(resolveTemplateType(templateValue));
        param.setRequired(1);
        param.setDefaultValue("");
        param.setExampleValue(resolveTemplateExampleValue(templateValue));
        param.setDescription("由接口运行时参数模板自动生成");
        param.setValidationRule("");
        param.setSortOrder(sortOrder);
        return param;
    }

    /**
     * 解析参数类型。
     *
     * @param templateValue 参数模板值
     * @return 参数类型
     */
    private String resolveTemplateType(JsonElement templateValue) {
        if (templateValue == null || templateValue.isJsonNull()) {
            return "string";
        }
        if (templateValue.isJsonObject()) {
            return "object";
        }
        if (templateValue.isJsonArray()) {
            return "array";
        }
        if (!templateValue.isJsonPrimitive()) {
            return "string";
        }
        JsonPrimitive primitive = templateValue.getAsJsonPrimitive();
        if (primitive.isString()) {
            String marker = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
            return SUPPORTED_TYPE_MARKERS.contains(marker) ? marker : "string";
        }
        if (primitive.isNumber()) {
            return "number";
        }
        if (primitive.isBoolean()) {
            return "boolean";
        }
        return "string";
    }

    /**
     * 解析参数示例值。
     *
     * @param templateValue 参数模板值
     * @return 示例值
     */
    private String resolveTemplateExampleValue(JsonElement templateValue) {
        if (templateValue == null || templateValue.isJsonNull()) {
            return "";
        }
        if (templateValue.isJsonPrimitive()) {
            JsonPrimitive primitive = templateValue.getAsJsonPrimitive();
            if (primitive.isString()) {
                String value = primitive.getAsString();
                String marker = value.trim().toLowerCase(Locale.ROOT);
                return SUPPORTED_TYPE_MARKERS.contains(marker) ? "" : value;
            }
            return primitive.toString();
        }
        return templateValue.toString();
    }

    /**
     * 判断当前用户是否可以查看接口文档。
     *
     * @param interfaceInfo 接口信息
     * @param admin         是否为管理员
     * @return 是否可以查看
     */
    private boolean canView(InterfaceInfo interfaceInfo, boolean admin) {
        return admin || interfaceInfo.getStatus() == InterfaceInfoStatusEnum.ONLINE.getValue();
    }

    /**
     * 查询文档参数列表。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 文档参数列表
     */
    private List<InterfaceDocParam> listDocParams(Long interfaceInfoId) {
        return interfaceDocParamService.lambdaQuery()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .orderByAsc(InterfaceDocParam::getSortOrder)
                .orderByAsc(InterfaceDocParam::getId)
                .list();
    }

    /**
     * 查询文档错误码列表。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 文档错误码列表
     */
    private List<InterfaceDocErrorCode> listErrorCodes(Long interfaceInfoId) {
        return interfaceDocErrorCodeService.lambdaQuery()
                .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                .orderByAsc(InterfaceDocErrorCode::getSortOrder)
                .orderByAsc(InterfaceDocErrorCode::getId)
                .list();
    }

    /**
     * 转换接口基础视图。
     *
     * @param interfaceInfo 接口信息
     * @param admin         是否为管理员
     * @return 接口基础视图
     */
    private InterfaceInfoVO toInterfaceInfoVO(InterfaceInfo interfaceInfo, boolean admin) {
        InterfaceInfoVO interfaceInfoVO = new InterfaceInfoVO();
        BeanUtils.copyProperties(interfaceInfo, interfaceInfoVO);
        if (!admin) {
            interfaceInfoVO.setUrl(null);
            interfaceInfoVO.setTargetHost(null);
        }
        InterfaceQuotaTypeEnum quotaTypeEnum = InterfaceQuotaTypeEnum.getEnumByValue(interfaceInfo.getQuotaType());
        if (quotaTypeEnum != null) {
            interfaceInfoVO.setQuotaTypeText(quotaTypeEnum.getText());
            interfaceInfoVO.setInitialQuota(interfaceQuotaConfigService.getInitialQuota(quotaTypeEnum));
        }
        Map<Long, Integer> totalNumMap = userInterfaceInfoService.listTotalNumByInterfaceInfoIds(
                Collections.singletonList(interfaceInfo.getId())
        );
        interfaceInfoVO.setTotalNum(totalNumMap.getOrDefault(interfaceInfo.getId(), 0));
        return interfaceInfoVO;
    }

    /**
     * 转换文档主信息视图。
     *
     * @param doc           文档实体
     * @param interfaceInfo 接口信息
     * @return 文档主信息视图
     */
    private InterfaceDocVO toInterfaceDocVO(InterfaceDoc doc, InterfaceInfo interfaceInfo) {
        InterfaceDocVO docVO = new InterfaceDocVO();
        if (doc != null) {
            BeanUtils.copyProperties(doc, docVO);
            docVO.setRequestContentType(firstText(docVO.getRequestContentType(), DEFAULT_REQUEST_CONTENT_TYPE));
            docVO.setResponseContentType(firstText(docVO.getResponseContentType(), DEFAULT_RESPONSE_CONTENT_TYPE));
            return docVO;
        }
        docVO.setInterfaceInfoId(interfaceInfo.getId());
        docVO.setDocVersion("v1");
        docVO.setRequestContentType(DEFAULT_REQUEST_CONTENT_TYPE);
        docVO.setResponseContentType(DEFAULT_RESPONSE_CONTENT_TYPE);
        docVO.setAuthDescription("通过平台 AccessKey/SecretKey 签名鉴权，由网关统一校验。");
        return docVO;
    }

    /**
     * 根据场景解析参数列表。
     *
     * @param docParams 结构化参数列表
     * @param sceneEnum 参数场景
     * @return 参数视图列表
     */
    private List<InterfaceDocParamVO> resolveParams(List<InterfaceDocParam> docParams,
                                                    InterfaceDocParamSceneEnum sceneEnum) {
        return docParams.stream()
                .filter(param -> sceneEnum.getValue().equals(param.getParamScene()))
                .map(this::toParamVO)
                .collect(Collectors.toList());
    }

    /**
     * 解析请求参数列表。
     *
     * @param docParams 结构化参数列表
     * @return 请求参数视图列表
     */
    private List<InterfaceDocParamVO> resolveRequestParams(List<InterfaceDocParam> docParams) {
        return docParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .map(this::toParamVO)
                .collect(Collectors.toList());
    }

    /**
     * 转换参数视图。
     *
     * @param param 参数实体
     * @return 参数视图
     */
    private InterfaceDocParamVO toParamVO(InterfaceDocParam param) {
        InterfaceDocParamVO paramVO = new InterfaceDocParamVO();
        BeanUtils.copyProperties(param, paramVO);
        paramVO.setRequired(Objects.equals(param.getRequired(), 1));
        return paramVO;
    }

    /**
     * 转换错误码视图。
     *
     * @param errorCode 错误码实体
     * @return 错误码视图
     */
    private InterfaceDocErrorCodeVO toErrorCodeVO(InterfaceDocErrorCode errorCode) {
        InterfaceDocErrorCodeVO errorCodeVO = new InterfaceDocErrorCodeVO();
        BeanUtils.copyProperties(errorCode, errorCodeVO);
        return errorCodeVO;
    }

    /**
     * 构建 curl 调用示例。
     *
     * @param interfaceInfo 接口信息
     * @param detailVO      文档聚合视图
     * @return curl 示例
     */
    private String buildCurlExample(InterfaceInfo interfaceInfo, InterfaceDocDetailVO detailVO) {
        String method = firstText(interfaceInfo.getMethod(), "GET").toUpperCase();
        String url = appendQueryParamsIfNecessary(detailVO.getGatewayUrl(), method, detailVO.getRequestParams());
        String headerOptions = detailVO.getRequestHeaders().stream()
                .map(param -> "  -H '" + param.getName() + ": " + firstText(param.getExampleValue(), param.getDefaultValue()) + "'")
                .collect(Collectors.joining(" \\\n"));
        String contentTypeHeader = "  -H 'Content-Type: " + firstText(detailVO.getDoc().getRequestContentType(), DEFAULT_REQUEST_CONTENT_TYPE) + "'";
        String bodyOption = buildBodyOption(method, detailVO.getRequestParams());
        return Stream.of("curl -X " + method + " '" + url + "'", headerOptions, contentTypeHeader, bodyOption)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining(" \\\n"));
    }

    /**
     * 在 GET 请求地址后追加 Query 示例参数。
     *
     * @param url           网关调用地址
     * @param method        请求方法
     * @param requestParams 请求参数列表
     * @return 处理后的地址
     */
    private String appendQueryParamsIfNecessary(String url, String method, List<InterfaceDocParamVO> requestParams) {
        if (!"GET".equalsIgnoreCase(method)) {
            return url;
        }
        String query = requestParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene()))
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .map(param -> encode(param.getName()) + "=" + encode(firstText(param.getExampleValue(), param.getDefaultValue())))
                .collect(Collectors.joining("&"));
        if (StringUtils.isBlank(query)) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    /**
     * 构造请求体 curl 选项。
     *
     * @param method        请求方法
     * @param requestParams 请求参数列表
     * @return 请求体 curl 选项
     */
    private String buildBodyOption(String method, List<InterfaceDocParamVO> requestParams) {
        if ("GET".equalsIgnoreCase(method)) {
            return "";
        }
        List<InterfaceDocParamVO> bodyParams = requestParams.stream()
                .filter(param -> !InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene()))
                .collect(Collectors.toList());
        if (bodyParams.isEmpty()) {
            return "";
        }
        JsonObject bodyJson = new JsonObject();
        bodyParams.stream()
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .sorted(Comparator.comparing(param -> Optional.ofNullable(param.getSortOrder()).orElse(0)))
                .forEach(param -> bodyJson.addProperty(param.getName(), firstText(param.getExampleValue(), param.getDefaultValue())));
        return "  -d '" + bodyJson + "'";
    }

    /**
     * 构建网关调用地址。
     *
     * @param interfaceInfo 接口信息
     * @return 网关调用地址
     */
    private String buildGatewayUrl(InterfaceInfo interfaceInfo) {
        if (StringUtils.isBlank(interfaceInfo.getPath())) {
            return firstText(interfaceInfo.getUrl(), "");
        }
        String normalizedGatewayHost = firstText(gatewayHost, "").trim();
        while (normalizedGatewayHost.endsWith("/")) {
            normalizedGatewayHost = normalizedGatewayHost.substring(0, normalizedGatewayHost.length() - 1);
        }
        String path = interfaceInfo.getPath().trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return normalizedGatewayHost + path;
    }

    /**
     * URL 编码。
     *
     * @param value 原始文本
     * @return 编码后的文本
     */
    private String encode(String value) {
        return URLEncoder.encode(firstText(value, ""), StandardCharsets.UTF_8);
    }

    /**
     * 返回第一个非空文本。
     *
     * @param values 候选文本
     * @return 第一个非空文本
     */
    private String firstText(String... values) {
        return Stream.of(values)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("");
    }
}
