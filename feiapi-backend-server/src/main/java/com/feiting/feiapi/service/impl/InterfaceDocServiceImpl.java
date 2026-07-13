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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        detailVO.setRequestHeaders(resolveParams(interfaceInfo, docParams, InterfaceDocParamSceneEnum.HEADER, hasStructuredDoc));
        detailVO.setRequestParams(resolveRequestParams(interfaceInfo, docParams, hasStructuredDoc));
        detailVO.setResponseParams(resolveParams(interfaceInfo, docParams, InterfaceDocParamSceneEnum.RESPONSE, hasStructuredDoc));
        detailVO.setErrorCodes(errorCodes.stream()
                .map(this::toErrorCodeVO)
                .collect(Collectors.toList()));
        detailVO.setCurlExample(buildCurlExample(interfaceInfo, detailVO));
        return detailVO;
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
            docVO.setRequestContentType(firstText(docVO.getRequestContentType(), inferRequestContentType(interfaceInfo)));
            docVO.setResponseContentType(firstText(docVO.getResponseContentType(), DEFAULT_RESPONSE_CONTENT_TYPE));
            return docVO;
        }
        docVO.setInterfaceInfoId(interfaceInfo.getId());
        docVO.setDocVersion("v1");
        docVO.setRequestContentType(inferRequestContentType(interfaceInfo));
        docVO.setResponseContentType(DEFAULT_RESPONSE_CONTENT_TYPE);
        docVO.setAuthDescription("通过平台 AccessKey/SecretKey 签名鉴权，由网关统一校验。");
        return docVO;
    }

    /**
     * 根据场景解析参数列表。
     *
     * @param interfaceInfo    接口信息
     * @param docParams        结构化参数列表
     * @param sceneEnum        参数场景
     * @param hasStructuredDoc 是否存在结构化文档
     * @return 参数视图列表
     */
    private List<InterfaceDocParamVO> resolveParams(InterfaceInfo interfaceInfo,
                                                    List<InterfaceDocParam> docParams,
                                                    InterfaceDocParamSceneEnum sceneEnum,
                                                    boolean hasStructuredDoc) {
        List<InterfaceDocParamVO> structuredParams = docParams.stream()
                .filter(param -> sceneEnum.getValue().equals(param.getParamScene()))
                .map(this::toParamVO)
                .collect(Collectors.toList());
        if (hasStructuredDoc || !structuredParams.isEmpty()) {
            return structuredParams;
        }
        if (InterfaceDocParamSceneEnum.HEADER.equals(sceneEnum)) {
            return parseHeaderText(interfaceInfo.getRequestHeader(), interfaceInfo.getId());
        }
        if (InterfaceDocParamSceneEnum.RESPONSE.equals(sceneEnum)) {
            return parseRawText(interfaceInfo.getResponseHeader(), interfaceInfo.getId(), sceneEnum, "响应说明");
        }
        return Collections.emptyList();
    }

    /**
     * 解析请求参数列表。
     *
     * @param interfaceInfo    接口信息
     * @param docParams        结构化参数列表
     * @param hasStructuredDoc 是否存在结构化文档
     * @return 请求参数视图列表
     */
    private List<InterfaceDocParamVO> resolveRequestParams(InterfaceInfo interfaceInfo,
                                                           List<InterfaceDocParam> docParams,
                                                           boolean hasStructuredDoc) {
        List<InterfaceDocParamVO> structuredParams = docParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .map(this::toParamVO)
                .collect(Collectors.toList());
        if (hasStructuredDoc || !structuredParams.isEmpty()) {
            return structuredParams;
        }
        return parseRequestParamText(interfaceInfo.getRequestParams(), interfaceInfo.getId(), interfaceInfo.getMethod());
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
     * 解析旧请求 Header 文本。
     *
     * @param headerText      旧请求 Header 文本
     * @param interfaceInfoId 接口信息 ID
     * @return Header 参数视图列表
     */
    private List<InterfaceDocParamVO> parseHeaderText(String headerText, Long interfaceInfoId) {
        if (StringUtils.isBlank(headerText)) {
            return Collections.emptyList();
        }
        String content = headerText.trim();
        Optional<List<InterfaceDocParamVO>> jsonParams = parseJsonObjectText(content, interfaceInfoId,
                InterfaceDocParamSceneEnum.HEADER, false);
        if (jsonParams.isPresent()) {
            return jsonParams.get();
        }
        return Stream.of(content.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(line -> toHeaderParam(line, interfaceInfoId))
                .collect(Collectors.toList());
    }

    /**
     * 将单行 Header 文本转换为参数视图。
     *
     * @param line            Header 文本行
     * @param interfaceInfoId 接口信息 ID
     * @return 参数视图
     */
    private InterfaceDocParamVO toHeaderParam(String line, Long interfaceInfoId) {
        String[] pieces = line.split(":", 2);
        String name = pieces[0].trim();
        String exampleValue = pieces.length > 1 ? pieces[1].trim() : "";
        return buildParamVO(interfaceInfoId, InterfaceDocParamSceneEnum.HEADER, name, "string", false,
                "", exampleValue, "旧请求头字段自动转换", "", 0);
    }

    /**
     * 解析旧请求参数文本。
     *
     * @param requestParamText 旧请求参数文本
     * @param interfaceInfoId  接口信息 ID
     * @param method           请求方法
     * @return 请求参数视图列表
     */
    private List<InterfaceDocParamVO> parseRequestParamText(String requestParamText, Long interfaceInfoId, String method) {
        if (StringUtils.isBlank(requestParamText)) {
            return Collections.emptyList();
        }
        InterfaceDocParamSceneEnum sceneEnum = "GET".equalsIgnoreCase(method)
                ? InterfaceDocParamSceneEnum.QUERY
                : InterfaceDocParamSceneEnum.BODY;
        String content = requestParamText.trim();
        Optional<List<InterfaceDocParamVO>> jsonParams = parseJsonObjectText(content, interfaceInfoId, sceneEnum, true);
        if (jsonParams.isPresent()) {
            return jsonParams.get();
        }
        return parseRawText(content, interfaceInfoId, sceneEnum, "请求参数");
    }

    /**
     * 解析 JSON 对象文本。
     *
     * @param content         JSON 文本
     * @param interfaceInfoId 接口信息 ID
     * @param sceneEnum       参数场景
     * @param required        是否必填
     * @return 参数视图列表
     */
    private Optional<List<InterfaceDocParamVO>> parseJsonObjectText(String content,
                                                                    Long interfaceInfoId,
                                                                    InterfaceDocParamSceneEnum sceneEnum,
                                                                    boolean required) {
        try {
            JsonElement jsonElement = JsonParser.parseString(content);
            if (!jsonElement.isJsonObject()) {
                return Optional.empty();
            }
            List<InterfaceDocParamVO> params = jsonElement.getAsJsonObject().entrySet().stream()
                    .map(entry -> buildParamVO(interfaceInfoId, sceneEnum, entry.getKey(), inferJsonType(entry.getValue()),
                            required, "", toJsonExampleValue(entry.getValue()), "旧字段自动转换", "", 0))
                    .collect(Collectors.toList());
            return Optional.of(params);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 解析原始文本为单个参数行。
     *
     * @param text            原始文本
     * @param interfaceInfoId 接口信息 ID
     * @param sceneEnum       参数场景
     * @param name            参数名称
     * @return 参数视图列表
     */
    private List<InterfaceDocParamVO> parseRawText(String text,
                                                   Long interfaceInfoId,
                                                   InterfaceDocParamSceneEnum sceneEnum,
                                                   String name) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(buildParamVO(interfaceInfoId, sceneEnum, name, "text", false,
                "", text.trim(), "旧字段原文展示", "", 0));
    }

    /**
     * 构造参数视图。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param sceneEnum       参数场景
     * @param name            参数名称
     * @param type            参数类型
     * @param required        是否必填
     * @param defaultValue    默认值
     * @param exampleValue    示例值
     * @param description     参数说明
     * @param validationRule  校验规则
     * @param sortOrder       排序值
     * @return 参数视图
     */
    private InterfaceDocParamVO buildParamVO(Long interfaceInfoId,
                                             InterfaceDocParamSceneEnum sceneEnum,
                                             String name,
                                             String type,
                                             boolean required,
                                             String defaultValue,
                                             String exampleValue,
                                             String description,
                                             String validationRule,
                                             Integer sortOrder) {
        InterfaceDocParamVO paramVO = new InterfaceDocParamVO();
        paramVO.setInterfaceInfoId(interfaceInfoId);
        paramVO.setParamScene(sceneEnum.getValue());
        paramVO.setName(name);
        paramVO.setType(type);
        paramVO.setRequired(required);
        paramVO.setDefaultValue(defaultValue);
        paramVO.setExampleValue(exampleValue);
        paramVO.setDescription(description);
        paramVO.setValidationRule(validationRule);
        paramVO.setSortOrder(sortOrder);
        return paramVO;
    }

    /**
     * 推断 JSON 元素类型。
     *
     * @param jsonElement JSON 元素
     * @return 类型名称
     */
    private String inferJsonType(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return "null";
        }
        if (jsonElement.isJsonObject()) {
            return "object";
        }
        if (jsonElement.isJsonArray()) {
            return "array";
        }
        if (jsonElement.getAsJsonPrimitive().isBoolean()) {
            return "boolean";
        }
        if (jsonElement.getAsJsonPrimitive().isNumber()) {
            return "number";
        }
        return "string";
    }

    /**
     * 将 JSON 元素转换为示例值。
     *
     * @param jsonElement JSON 元素
     * @return 示例值
     */
    private String toJsonExampleValue(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return "";
        }
        if (jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()) {
            return jsonElement.getAsString();
        }
        return jsonElement.toString();
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
     * 推断请求内容类型。
     *
     * @param interfaceInfo 接口信息
     * @return 请求内容类型
     */
    private String inferRequestContentType(InterfaceInfo interfaceInfo) {
        List<InterfaceDocParamVO> headers = parseHeaderText(interfaceInfo.getRequestHeader(), interfaceInfo.getId());
        return headers.stream()
                .filter(param -> "content-type".equalsIgnoreCase(param.getName()))
                .map(InterfaceDocParamVO::getExampleValue)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(DEFAULT_REQUEST_CONTENT_TYPE);
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
