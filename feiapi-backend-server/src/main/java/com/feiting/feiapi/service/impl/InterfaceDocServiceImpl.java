package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceDocMapper;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocErrorCodeSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocParamSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.model.vo.InterfaceDocErrorCodeVO;
import com.feiting.feiapi.model.vo.InterfaceDocInterfaceInfoVO;
import com.feiting.feiapi.model.vo.InterfaceDocParamVO;
import com.feiting.feiapi.model.vo.InterfaceDocVO;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.enums.InterfaceQuotaTypeEnum;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
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
     * 参数数量上限。
     */
    private static final int MAX_PARAM_COUNT = 200;

    /**
     * 响应参数最大嵌套深度。
     */
    private static final int MAX_RESPONSE_DEPTH = 8;

    /**
     * 错误码数量上限。
     */
    private static final int MAX_ERROR_CODE_COUNT = 100;

    /**
     * 支持的参数类型标记。
     */
    private static final Set<String> SUPPORTED_TYPE_MARKERS = new HashSet<>(
            Arrays.asList("string", "number", "boolean", "object", "array"));

    /**
     * 允许保存的内容类型。
     */
    private static final Set<String> SUPPORTED_CONTENT_TYPES = new HashSet<>(
            Arrays.asList("application/json", "application/xml", "text/plain", "text/html",
                    "application/x-www-form-urlencoded", "multipart/form-data", "application/octet-stream"));

    /**
     * 文档版本白名单。
     */
    private static final Pattern DOC_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /**
     * 手机号高置信度匹配规则。
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 邮箱高置信度匹配规则。
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * 身份证号高置信度匹配规则。
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    /**
     * 未脱敏密钥字段匹配规则。
     */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(accessKey|secretKey|token|password|密码|密钥)\\s*['\"]?\\s*[:=]\\s*['\"]?(?!\\*{3,}|<[^>]+>|\\$\\{[^}]+\\})[A-Za-z0-9._\\-+/=]{8,}");

    /**
     * 脚本内容匹配规则。
     */
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<\\s*script|javascript\\s*:|\\son[a-z]+\\s*=");

    /**
     * 内部实现信息匹配规则。
     */
    private static final Pattern INTERNAL_INFO_PATTERN = Pattern.compile(
            "(?i)\\b(db|database|mysql|postgresql|oracle|mongodb|redis|dubbo|nacos|jdbc)\\b|数据库|数据表|服务器绝对路径|[A-Za-z]:\\\\|/(var|etc|usr|opt|home|root|data)/");

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
        boolean structuredDocMissing = doc == null && docParams.isEmpty() && errorCodes.isEmpty();

        InterfaceDocDetailVO detailVO = new InterfaceDocDetailVO();
        detailVO.setInterfaceInfo(toInterfaceInfoVO(interfaceInfo, admin));
        detailVO.setDoc(toInterfaceDocVO(doc, interfaceInfo));
        detailVO.setGatewayUrl(buildGatewayUrl(interfaceInfo));
        detailVO.setStructuredDocMissing(structuredDocMissing);
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
        List<RuntimeRequestParam> runtimeParams = buildRuntimeRequestParams(interfaceInfo);
        List<InterfaceDocParam> existingParams = listRequestDocParams(interfaceInfo.getId());
        reconcileRequestDocParams(interfaceInfo.getId(), runtimeParams, existingParams);
    }

    /**
     * 聚合保存接口文档。
     *
     * @param saveRequest 保存请求
     * @return 是否保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDoc(InterfaceDocSaveRequest saveRequest) {
        if (saveRequest == null || saveRequest.getInterfaceInfoId() == null || saveRequest.getInterfaceInfoId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(saveRequest.getInterfaceInfoId());
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        validateDocMain(saveRequest);
        List<InterfaceDocParamSaveRequest> paramRequests = Optional.ofNullable(saveRequest.getParams())
                .orElse(Collections.emptyList());
        List<InterfaceDocErrorCodeSaveRequest> errorCodeRequests = Optional.ofNullable(saveRequest.getErrorCodes())
                .orElse(Collections.emptyList());
        if (paramRequests.size() > MAX_PARAM_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档参数数量不能超过 200");
        }
        if (errorCodeRequests.size() > MAX_ERROR_CODE_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误码数量不能超过 100");
        }

        List<ParamSaveNode> paramNodes = validateAndBuildParamNodes(interfaceInfo, paramRequests);
        List<InterfaceDocErrorCode> errorCodes = buildErrorCodes(interfaceInfo.getId(), errorCodeRequests);
        saveOrUpdateDoc(saveRequest);
        replaceAllParams(interfaceInfo.getId(), paramNodes);
        replaceAllErrorCodes(interfaceInfo.getId(), errorCodes);
        return true;
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
     * 对账同步请求文档参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param runtimeParams   运行时参数模板
     * @param existingParams  已存在的请求文档参数
     */
    private void reconcileRequestDocParams(Long interfaceInfoId,
                                           List<RuntimeRequestParam> runtimeParams,
                                           List<InterfaceDocParam> existingParams) {
        Map<String, InterfaceDocParam> existingParamMap = existingParams.stream()
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .collect(Collectors.toMap(
                        param -> param.getName().trim(),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        Set<String> runtimeNames = runtimeParams.stream()
                .map(RuntimeRequestParam::getName)
                .collect(Collectors.toSet());
        List<Long> removedIds = existingParams.stream()
                .filter(param -> StringUtils.isBlank(param.getName()) || !runtimeNames.contains(param.getName().trim()))
                .map(InterfaceDocParam::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!removedIds.isEmpty()) {
            boolean removeResult = interfaceDocParamService.removeByIds(removedIds);
            if (!removeResult) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "废弃请求参数删除失败");
            }
        }

        runtimeParams.forEach(runtimeParam -> {
            InterfaceDocParam existingParam = existingParamMap.get(runtimeParam.getName());
            if (existingParam == null) {
                InterfaceDocParam newParam = buildRequestDocParam(interfaceInfoId, runtimeParam);
                boolean saveResult = interfaceDocParamService.save(newParam);
                if (!saveResult) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "新增请求参数文档失败");
                }
                return;
            }
            existingParam.setParamScene(runtimeParam.getParamScene());
            existingParam.setParentId(null);
            existingParam.setType(runtimeParam.getType());
            existingParam.setRequired(runtimeParam.getRequired());
            existingParam.setNullable(0);
            boolean updateResult = interfaceDocParamService.updateById(existingParam);
            if (!updateResult) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新请求参数文档失败");
            }
        });
    }

    /**
     * 查询请求文档参数列表。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 请求文档参数列表
     */
    private List<InterfaceDocParam> listRequestDocParams(Long interfaceInfoId) {
        return interfaceDocParamService.lambdaQuery()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .in(InterfaceDocParam::getParamScene,
                        InterfaceDocParamSceneEnum.QUERY.getValue(),
                        InterfaceDocParamSceneEnum.BODY.getValue())
                .orderByAsc(InterfaceDocParam::getSortOrder)
                .orderByAsc(InterfaceDocParam::getId)
                .list();
    }

    /**
     * 根据接口运行时参数模板构建结构化请求参数。
     *
     * @param interfaceInfo 接口信息
     * @return 结构化请求参数列表
     */
    private List<RuntimeRequestParam> buildRuntimeRequestParams(InterfaceInfo interfaceInfo) {
        String requestParams = interfaceInfo.getRequestParams();
        if (StringUtils.isBlank(requestParams)) {
            return Collections.emptyList();
        }
        JsonObject requestParamObject = parseRequestParamObject(requestParams);
        InterfaceDocParamSceneEnum sceneEnum = resolveRequestParamScene(interfaceInfo.getMethod());
        int[] sortOrder = {1};
        return requestParamObject.entrySet().stream()
                .map(entry -> new RuntimeRequestParam(
                        entry.getKey(),
                        sceneEnum.getValue(),
                        resolveTemplateType(entry.getValue()),
                        1,
                        resolveTemplateExampleValue(entry.getValue()),
                        sortOrder[0]++))
                .collect(Collectors.toList());
    }

    /**
     * 构建单个默认请求文档参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param runtimeParam    运行时参数模板
     * @return 请求文档参数
     */
    private InterfaceDocParam buildRequestDocParam(Long interfaceInfoId, RuntimeRequestParam runtimeParam) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene(runtimeParam.getParamScene());
        param.setName(runtimeParam.getName());
        param.setType(runtimeParam.getType());
        param.setRequired(runtimeParam.getRequired());
        param.setNullable(0);
        param.setDefaultValue("");
        param.setExampleValue(runtimeParam.getExampleValue());
        param.setDescription("由接口运行时参数模板自动生成");
        param.setValidationRule("");
        param.setSortOrder(runtimeParam.getSortOrder());
        return param;
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
     * 校验文档主信息。
     *
     * @param saveRequest 保存请求
     */
    private void validateDocMain(InterfaceDocSaveRequest saveRequest) {
        String docVersion = trimToEmpty(saveRequest.getDocVersion());
        if (!DOC_VERSION_PATTERN.matcher(docVersion).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档版本号格式非法");
        }
        assertSupportedContentType(saveRequest.getRequestContentType(), "请求内容类型不支持");
        assertSupportedContentType(saveRequest.getResponseContentType(), "响应内容类型不支持");
        assertJsonExample(saveRequest.getSuccessExample(), "成功响应示例必须是合法 JSON");
        assertJsonExample(saveRequest.getFailExample(), "失败响应示例必须是合法 JSON");
        Stream.of(saveRequest.getAuthDescription(), saveRequest.getSuccessExample(),
                        saveRequest.getFailExample(), saveRequest.getRemark())
                .forEach(this::assertNoSensitiveText);
    }

    /**
     * 校验内容类型是否在白名单内。
     *
     * @param contentType  内容类型
     * @param errorMessage 错误提示
     */
    private void assertSupportedContentType(String contentType, String errorMessage) {
        String normalizedContentType = trimToEmpty(contentType).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 校验 JSON 示例。
     *
     * @param example      JSON 示例
     * @param errorMessage 错误提示
     */
    private void assertJsonExample(String example, String errorMessage) {
        if (StringUtils.isBlank(example)) {
            return;
        }
        try {
            JsonParser.parseString(example);
        } catch (JsonSyntaxException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 校验并构建文档参数保存节点。
     *
     * @param interfaceInfo 接口信息
     * @param paramRequests 参数保存请求
     * @return 参数保存节点列表
     */
    private List<ParamSaveNode> validateAndBuildParamNodes(InterfaceInfo interfaceInfo,
                                                           List<InterfaceDocParamSaveRequest> paramRequests) {
        Map<String, ParamSaveNode> nodeMap = paramRequests.stream()
                .peek(this::validateParamBasic)
                .map(request -> new ParamSaveNode(request, toParamEntity(interfaceInfo.getId(), request)))
                .collect(Collectors.toMap(
                        node -> node.getRequest().getParamKey().trim(),
                        Function.identity(),
                        (first, second) -> {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数键不能重复");
                        },
                        LinkedHashMap::new));
        linkParamNodes(nodeMap);
        assertNoDuplicateSiblingNames(nodeMap);
        validateRuntimeRequestParams(interfaceInfo, nodeMap.values().stream()
                .map(ParamSaveNode::getRequest)
                .collect(Collectors.toList()));
        return nodeMap.values().stream()
                .peek(node -> node.setDepth(resolveParamDepth(node, new HashSet<>())))
                .peek(node -> {
                    if (node.getDepth() > MAX_RESPONSE_DEPTH) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "响应参数嵌套深度不能超过 8");
                    }
                })
                .sorted(Comparator.comparingInt(ParamSaveNode::getDepth)
                        .thenComparing(node -> Optional.ofNullable(node.getRequest().getSortOrder()).orElse(0)))
                .collect(Collectors.toList());
    }

    /**
     * 校验参数基础字段。
     *
     * @param request 参数保存请求
     */
    private void validateParamBasic(InterfaceDocParamSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档参数不能为空");
        }
        String scene = trimToEmpty(request.getParamScene());
        InterfaceDocParamSceneEnum sceneEnum = InterfaceDocParamSceneEnum.getEnumByValue(scene);
        if (sceneEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数场景不支持");
        }
        String type = trimToEmpty(request.getType()).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPE_MARKERS.contains(type)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数类型不支持");
        }
        if (InterfaceDocParamSceneEnum.RESPONSE.equals(sceneEnum)) {
            if (request.getRequired() == null || request.getNullable() == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "响应参数必须明确 required 和 nullable");
            }
        } else if (StringUtils.isNotBlank(request.getParentParamKey())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅响应参数允许配置父级参数");
        } else if (Boolean.TRUE.equals(request.getNullable())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数 nullable 固定为否");
        }
        if (SCRIPT_PATTERN.matcher(trimToEmpty(request.getValidationRule())).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "校验规则不能包含脚本内容");
        }
        Stream.of(request.getDefaultValue(), request.getExampleValue(), request.getDescription(), request.getValidationRule())
                .forEach(this::assertNoSensitiveText);
    }

    /**
     * 转换文档参数实体。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param request         参数保存请求
     * @return 文档参数实体
     */
    private InterfaceDocParam toParamEntity(Long interfaceInfoId, InterfaceDocParamSaveRequest request) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene(trimToEmpty(request.getParamScene()));
        param.setName(trimToEmpty(request.getName()));
        param.setType(trimToEmpty(request.getType()).toLowerCase(Locale.ROOT));
        param.setRequired(Boolean.TRUE.equals(request.getRequired()) ? 1 : 0);
        param.setNullable(Boolean.TRUE.equals(request.getNullable()) ? 1 : 0);
        param.setDefaultValue(trimToNull(request.getDefaultValue()));
        param.setExampleValue(trimToNull(request.getExampleValue()));
        param.setDescription(trimToNull(request.getDescription()));
        param.setValidationRule(trimToNull(request.getValidationRule()));
        param.setSortOrder(request.getSortOrder());
        return param;
    }

    /**
     * 关联参数父子节点。
     *
     * @param nodeMap 参数键与保存节点映射
     */
    private void linkParamNodes(Map<String, ParamSaveNode> nodeMap) {
        nodeMap.values().stream()
                .filter(node -> StringUtils.isNotBlank(node.getRequest().getParentParamKey()))
                .forEach(node -> {
                    String parentKey = node.getRequest().getParentParamKey().trim();
                    ParamSaveNode parentNode = nodeMap.get(parentKey);
                    if (parentNode == null) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "父级参数不存在");
                    }
                    if (!InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(parentNode.getRequest().getParamScene())) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "父级参数必须是响应参数");
                    }
                    if (!Objects.equals(parentNode.getRequest().getParamScene(), node.getRequest().getParamScene())) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "父级参数必须属于同一场景");
                    }
                    node.setParent(parentNode);
                    parentNode.getChildren().add(node);
                });
    }

    /**
     * 校验同级参数名称不重复。
     *
     * @param nodeMap 参数键与保存节点映射
     */
    private void assertNoDuplicateSiblingNames(Map<String, ParamSaveNode> nodeMap) {
        Set<String> siblingNameSet = new HashSet<>();
        nodeMap.values().forEach(node -> {
            String parentKey = Optional.ofNullable(node.getParent())
                    .map(parent -> parent.getRequest().getParamKey().trim())
                    .orElse("");
            String duplicateKey = node.getRequest().getParamScene() + ":" + parentKey + ":" + node.getRequest().getName().trim();
            if (!siblingNameSet.add(duplicateKey)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "同级参数名称不能重复");
            }
        });
    }

    /**
     * 解析参数嵌套深度。
     *
     * @param node     参数保存节点
     * @param visiting 当前递归链路
     * @return 嵌套深度
     */
    private int resolveParamDepth(ParamSaveNode node, Set<String> visiting) {
        String key = node.getRequest().getParamKey().trim();
        if (!visiting.add(key)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数父子关系不能循环引用");
        }
        if (node.getParent() == null) {
            visiting.remove(key);
            return 1;
        }
        int depth = resolveParamDepth(node.getParent(), visiting) + 1;
        visiting.remove(key);
        return depth;
    }

    /**
     * 校验维护请求中的请求参数与运行时模板一致。
     *
     * @param interfaceInfo 接口信息
     * @param paramRequests 参数保存请求
     */
    private void validateRuntimeRequestParams(InterfaceInfo interfaceInfo,
                                              List<InterfaceDocParamSaveRequest> paramRequests) {
        List<RuntimeRequestParam> runtimeParams = buildRuntimeRequestParams(interfaceInfo);
        List<InterfaceDocParamSaveRequest> requestParams = paramRequests.stream()
                .filter(request -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(request.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(request.getParamScene()))
                .collect(Collectors.toList());
        Map<String, RuntimeRequestParam> runtimeParamMap = runtimeParams.stream()
                .collect(Collectors.toMap(RuntimeRequestParam::getName, Function.identity(), (first, second) -> first));
        Map<String, InterfaceDocParamSaveRequest> requestParamMap = requestParams.stream()
                .collect(Collectors.toMap(
                        request -> request.getName().trim(),
                        Function.identity(),
                        (first, second) -> {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数名称不能重复");
                        }));
        if (runtimeParamMap.size() != requestParamMap.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数必须与运行时参数模板一致");
        }
        runtimeParamMap.values().forEach(runtimeParam -> {
            InterfaceDocParamSaveRequest request = requestParamMap.get(runtimeParam.getName());
            if (request == null
                    || !runtimeParam.getParamScene().equals(request.getParamScene())
                    || !runtimeParam.getType().equals(request.getType())
                    || !Objects.equals(Boolean.TRUE, request.getRequired())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数名称、位置、类型和必填属性必须与运行时模板一致");
            }
        });
    }

    /**
     * 构建错误码实体列表。
     *
     * @param interfaceInfoId    接口信息 ID
     * @param errorCodeRequests 错误码保存请求
     * @return 错误码实体列表
     */
    private List<InterfaceDocErrorCode> buildErrorCodes(Long interfaceInfoId,
                                                        List<InterfaceDocErrorCodeSaveRequest> errorCodeRequests) {
        Set<String> errorCodeSet = new HashSet<>();
        return errorCodeRequests.stream()
                .peek(this::validateErrorCode)
                .map(request -> {
                    String errorCodeValue = trimToEmpty(request.getErrorCode());
                    if (!errorCodeSet.add(errorCodeValue)) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "同一接口的错误码不能重复");
                    }
                    InterfaceDocErrorCode errorCode = new InterfaceDocErrorCode();
                    errorCode.setInterfaceInfoId(interfaceInfoId);
                    errorCode.setErrorCode(errorCodeValue);
                    errorCode.setErrorMessage(trimToEmpty(request.getErrorMessage()));
                    errorCode.setDescription(trimToNull(request.getDescription()));
                    errorCode.setSolution(trimToNull(request.getSolution()));
                    errorCode.setSortOrder(request.getSortOrder());
                    return errorCode;
                })
                .collect(Collectors.toList());
    }

    /**
     * 校验错误码保存请求。
     *
     * @param request 错误码保存请求
     */
    private void validateErrorCode(InterfaceDocErrorCodeSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误码不能为空");
        }
        Stream.of(request.getErrorCode(), request.getErrorMessage(), request.getDescription(), request.getSolution())
                .forEach(this::assertNoSensitiveText);
        if (INTERNAL_INFO_PATTERN.matcher(trimToEmpty(request.getSolution())).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误解决建议不能包含内部实现信息");
        }
    }

    /**
     * 保存或更新文档主信息。
     *
     * @param saveRequest 保存请求
     */
    private void saveOrUpdateDoc(InterfaceDocSaveRequest saveRequest) {
        InterfaceDoc doc = lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, saveRequest.getInterfaceInfoId())
                .one();
        if (doc == null) {
            doc = new InterfaceDoc();
            doc.setInterfaceInfoId(saveRequest.getInterfaceInfoId());
        }
        doc.setDocVersion(trimToEmpty(saveRequest.getDocVersion()));
        doc.setRequestContentType(trimToEmpty(saveRequest.getRequestContentType()).toLowerCase(Locale.ROOT));
        doc.setResponseContentType(trimToEmpty(saveRequest.getResponseContentType()).toLowerCase(Locale.ROOT));
        doc.setAuthDescription(trimToNull(saveRequest.getAuthDescription()));
        doc.setSuccessExample(trimToNull(saveRequest.getSuccessExample()));
        doc.setFailExample(trimToNull(saveRequest.getFailExample()));
        doc.setRemark(trimToNull(saveRequest.getRemark()));
        boolean result = doc.getId() == null ? save(doc) : updateById(doc);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口文档主信息保存失败");
        }
    }

    /**
     * 全量替换文档参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param paramNodes      参数保存节点列表
     */
    private void replaceAllParams(Long interfaceInfoId, List<ParamSaveNode> paramNodes) {
        boolean removeResult = interfaceDocParamService.lambdaUpdate()
                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                .remove();
        if (!removeResult && !listDocParams(interfaceInfoId).isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "旧文档参数删除失败");
        }
        paramNodes.forEach(node -> {
            if (node.getParent() != null) {
                node.getEntity().setParentId(node.getParent().getEntity().getId());
            }
            boolean saveResult = interfaceDocParamService.save(node.getEntity());
            if (!saveResult) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档参数保存失败");
            }
        });
    }

    /**
     * 全量替换错误码。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param errorCodes      错误码列表
     */
    private void replaceAllErrorCodes(Long interfaceInfoId, List<InterfaceDocErrorCode> errorCodes) {
        boolean removeResult = interfaceDocErrorCodeService.lambdaUpdate()
                .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                .remove();
        if (!removeResult && !listErrorCodes(interfaceInfoId).isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "旧错误码删除失败");
        }
        if (errorCodes.isEmpty()) {
            return;
        }
        boolean saveResult = interfaceDocErrorCodeService.saveBatch(errorCodes);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "错误码保存失败");
        }
    }

    /**
     * 检查文本是否包含敏感信息。
     *
     * @param value 待检查文本
     */
    private void assertNoSensitiveText(String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        String text = value.trim();
        if (PHONE_PATTERN.matcher(text).find()
                || EMAIL_PATTERN.matcher(text).find()
                || ID_CARD_PATTERN.matcher(text).find()
                || SECRET_PATTERN.matcher(text).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档内容不能包含未脱敏敏感信息");
        }
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
    private InterfaceDocInterfaceInfoVO toInterfaceInfoVO(InterfaceInfo interfaceInfo, boolean admin) {
        InterfaceDocInterfaceInfoVO interfaceInfoVO = new InterfaceDocInterfaceInfoVO();
        interfaceInfoVO.setId(interfaceInfo.getId());
        interfaceInfoVO.setName(interfaceInfo.getName());
        interfaceInfoVO.setDescription(interfaceInfo.getDescription());
        interfaceInfoVO.setPath(interfaceInfo.getPath());
        interfaceInfoVO.setStatus(interfaceInfo.getStatus());
        interfaceInfoVO.setMethod(interfaceInfo.getMethod());
        interfaceInfoVO.setQuotaType(interfaceInfo.getQuotaType());
        interfaceInfoVO.setCreateTime(interfaceInfo.getCreateTime());
        interfaceInfoVO.setUpdateTime(interfaceInfo.getUpdateTime());
        if (admin) {
            interfaceInfoVO.setUrl(interfaceInfo.getUrl());
            interfaceInfoVO.setTargetHost(interfaceInfo.getTargetHost());
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
        paramVO.setNullable(Objects.equals(param.getNullable(), 1));
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
        String method = firstText(interfaceInfo.getMethod(), "GET").toUpperCase(Locale.ROOT);
        String gatewayUrl = appendQueryParamsIfNecessary(detailVO.getGatewayUrl(), method, detailVO.getRequestParams());
        String path = buildSignPath(interfaceInfo);
        String body = "GET".equalsIgnoreCase(method) ? "" : buildBodyJson(detailVO.getRequestParams());
        List<String> curlOptions = buildCurlOptions(detailVO, body);
        String curlCommand = Stream.concat(Stream.of("curl -X \"$METHOD\" \"$URL\""), curlOptions.stream())
                .collect(Collectors.joining(" \\\n"));
        return Stream.of(
                        "#!/usr/bin/env bash",
                        "set -euo pipefail",
                        "",
                        ": \"${ACCESS_KEY:?请先 export ACCESS_KEY='你的 AccessKey'}\"",
                        ": \"${SECRET_KEY:?请先 export SECRET_KEY='你的 SecretKey'}\"",
                        "",
                        "METHOD=" + shellSingleQuote(method),
                        "PATH_VALUE=" + shellSingleQuote(path),
                        "URL=" + shellSingleQuote(gatewayUrl),
                        "BODY=" + shellSingleQuote(body),
                        "NONCE=\"$(openssl rand -hex 16)\"",
                        "TIMESTAMP=\"$(date +%s)\"",
                        "CANONICAL_STRING=\"$(printf 'feiting\\n%s\\n%s\\n%s\\n%s\\n%s' \"$METHOD\" \"$PATH_VALUE\" \"$NONCE\" \"$TIMESTAMP\" \"$BODY\")\"",
                        "SIGN=\"$(printf '%s' \"$CANONICAL_STRING\" | openssl dgst -sha256 -hmac \"$SECRET_KEY\" | awk '{print $2}')\"",
                        "",
                        curlCommand)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建 curl 参数列表。
     *
     * @param detailVO 文档聚合视图
     * @param body     请求体
     * @return curl 参数列表
     */
    private List<String> buildCurlOptions(InterfaceDocDetailVO detailVO, String body) {
        Set<String> headerNames = new HashSet<>(Arrays.asList("accesskey", "nonce", "timestamp", "sign"));
        String contentType = firstText(detailVO.getDoc().getRequestContentType(), DEFAULT_REQUEST_CONTENT_TYPE);
        List<String> authHeaders = Stream.of(
                        "  -H \"accessKey: ${ACCESS_KEY}\"",
                        "  -H \"nonce: ${NONCE}\"",
                        "  -H \"timestamp: ${TIMESTAMP}\"",
                        "  -H \"sign: ${SIGN}\"")
                .collect(Collectors.toCollection(ArrayList::new));
        if (StringUtils.isNotBlank(contentType)) {
            authHeaders.add("  -H " + shellSingleQuote("Content-Type: " + contentType));
            headerNames.add("content-type");
        }
        List<String> customHeaders = detailVO.getRequestHeaders().stream()
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .filter(param -> headerNames.add(param.getName().trim().toLowerCase(Locale.ROOT)))
                .map(param -> "  -H " + shellSingleQuote(param.getName().trim() + ": " + firstText(param.getExampleValue(), param.getDefaultValue())))
                .collect(Collectors.toList());
        List<String> bodyOption = StringUtils.isBlank(body)
                ? Collections.emptyList()
                : Collections.singletonList("  --data \"$BODY\"");
        return Stream.of(authHeaders, customHeaders, bodyOption)
                .flatMap(List::stream)
                .collect(Collectors.toList());
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
                .map(param -> encode(param.getName()) + "=" + encode(resolveSampleValue(param)))
                .collect(Collectors.joining("&"));
        if (StringUtils.isBlank(query)) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    /**
     * 构建请求体 JSON。
     *
     * @param requestParams 请求参数列表
     * @return 请求体 JSON
     */
    private String buildBodyJson(List<InterfaceDocParamVO> requestParams) {
        List<InterfaceDocParamVO> bodyParams = requestParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .sorted(Comparator.comparing(param -> Optional.ofNullable(param.getSortOrder()).orElse(0)))
                .collect(Collectors.toList());
        if (bodyParams.isEmpty()) {
            return "";
        }
        JsonObject bodyJson = new JsonObject();
        bodyParams.forEach(param -> bodyJson.add(param.getName(), toTypedJsonElement(param)));
        return bodyJson.toString();
    }

    /**
     * 转换参数示例为指定 JSON 类型。
     *
     * @param param 参数视图
     * @return JSON 元素
     */
    private JsonElement toTypedJsonElement(InterfaceDocParamVO param) {
        String type = firstText(param.getType(), "string").toLowerCase(Locale.ROOT);
        String rawValue = firstText(param.getExampleValue(), param.getDefaultValue());
        if ("number".equals(type)) {
            return toNumberElement(rawValue);
        }
        if ("boolean".equals(type)) {
            return new JsonPrimitive("true".equalsIgnoreCase(rawValue) || "1".equals(rawValue));
        }
        if ("object".equals(type)) {
            return toContainerElement(rawValue, true);
        }
        if ("array".equals(type)) {
            return toContainerElement(rawValue, false);
        }
        return new JsonPrimitive(rawValue);
    }

    /**
     * 转换数字示例。
     *
     * @param rawValue 原始值
     * @return 数字 JSON 元素
     */
    private JsonElement toNumberElement(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return new JsonPrimitive(0);
        }
        try {
            return new JsonPrimitive(new BigDecimal(rawValue.trim()));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(0);
        }
    }

    /**
     * 转换对象或数组示例。
     *
     * @param rawValue 原始值
     * @param object   是否期望对象
     * @return 容器 JSON 元素
     */
    private JsonElement toContainerElement(String rawValue, boolean object) {
        if (StringUtils.isBlank(rawValue)) {
            return object ? new JsonObject() : new JsonArray();
        }
        try {
            JsonElement element = JsonParser.parseString(rawValue);
            if (object && element.isJsonObject()) {
                return element;
            }
            if (!object && element.isJsonArray()) {
                return element;
            }
            return object ? new JsonObject() : new JsonArray();
        } catch (JsonSyntaxException e) {
            return object ? new JsonObject() : new JsonArray();
        }
    }

    /**
     * 解析参数示例值。
     *
     * @param param 参数视图
     * @return 示例值
     */
    private String resolveSampleValue(InterfaceDocParamVO param) {
        JsonElement jsonElement = toTypedJsonElement(param);
        if (jsonElement == null || jsonElement instanceof JsonNull) {
            return "";
        }
        if (jsonElement.isJsonPrimitive()) {
            return jsonElement.getAsJsonPrimitive().getAsString();
        }
        return jsonElement.toString();
    }

    /**
     * 构建签名路径。
     *
     * @param interfaceInfo 接口信息
     * @return 签名路径
     */
    private String buildSignPath(InterfaceInfo interfaceInfo) {
        String path = firstText(interfaceInfo.getPath(), "");
        if (StringUtils.isBlank(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
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
     * Shell 单引号转义。
     *
     * @param value 原始文本
     * @return 可安全放入 Bash 的单引号文本
     */
    private String shellSingleQuote(String value) {
        return "'" + firstText(value, "").replace("'", "'\"'\"'") + "'";
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

    /**
     * 转换为空安全的去空格文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 转换为可空的去空格文本。
     *
     * @param value 原始文本
     * @return 去空格文本或 null
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 运行时请求参数模板。
     */
    private static class RuntimeRequestParam {

        /**
         * 参数名称。
         */
        private final String name;

        /**
         * 参数场景。
         */
        private final String paramScene;

        /**
         * 参数类型。
         */
        private final String type;

        /**
         * 是否必填。
         */
        private final Integer required;

        /**
         * 示例值。
         */
        private final String exampleValue;

        /**
         * 排序值。
         */
        private final Integer sortOrder;

        RuntimeRequestParam(String name,
                            String paramScene,
                            String type,
                            Integer required,
                            String exampleValue,
                            Integer sortOrder) {
            this.name = name;
            this.paramScene = paramScene;
            this.type = type;
            this.required = required;
            this.exampleValue = exampleValue;
            this.sortOrder = sortOrder;
        }

        /**
         * 获取参数名称。
         *
         * @return 参数名称
         */
        public String getName() {
            return name;
        }

        /**
         * 获取参数场景。
         *
         * @return 参数场景
         */
        public String getParamScene() {
            return paramScene;
        }

        /**
         * 获取参数类型。
         *
         * @return 参数类型
         */
        public String getType() {
            return type;
        }

        /**
         * 获取是否必填。
         *
         * @return 是否必填
         */
        public Integer getRequired() {
            return required;
        }

        /**
         * 获取示例值。
         *
         * @return 示例值
         */
        public String getExampleValue() {
            return exampleValue;
        }

        /**
         * 获取排序值。
         *
         * @return 排序值
         */
        public Integer getSortOrder() {
            return sortOrder;
        }
    }

    /**
     * 参数保存节点。
     */
    private static class ParamSaveNode {

        /**
         * 参数保存请求。
         */
        private final InterfaceDocParamSaveRequest request;

        /**
         * 参数实体。
         */
        private final InterfaceDocParam entity;

        /**
         * 父级节点。
         */
        private ParamSaveNode parent;

        /**
         * 子级节点列表。
         */
        private final List<ParamSaveNode> children = new ArrayList<>();

        /**
         * 节点深度。
         */
        private int depth;

        ParamSaveNode(InterfaceDocParamSaveRequest request, InterfaceDocParam entity) {
            this.request = request;
            this.entity = entity;
        }

        /**
         * 获取参数保存请求。
         *
         * @return 参数保存请求
         */
        public InterfaceDocParamSaveRequest getRequest() {
            return request;
        }

        /**
         * 获取参数实体。
         *
         * @return 参数实体
         */
        public InterfaceDocParam getEntity() {
            return entity;
        }

        /**
         * 获取父级节点。
         *
         * @return 父级节点
         */
        public ParamSaveNode getParent() {
            return parent;
        }

        /**
         * 设置父级节点。
         *
         * @param parent 父级节点
         */
        public void setParent(ParamSaveNode parent) {
            this.parent = parent;
        }

        /**
         * 获取子级节点列表。
         *
         * @return 子级节点列表
         */
        public List<ParamSaveNode> getChildren() {
            return children;
        }

        /**
         * 获取节点深度。
         *
         * @return 节点深度
         */
        public int getDepth() {
            return depth;
        }

        /**
         * 设置节点深度。
         *
         * @param depth 节点深度
         */
        public void setDepth(int depth) {
            this.depth = depth;
        }
    }
}
