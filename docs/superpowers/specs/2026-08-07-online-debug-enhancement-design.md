# 在线调试增强设计

## 1. 背景与边界

在线调试页面只负责收集根据接口运行时模板生成的请求参数，真实调用必须继续经过现有链路：

```text
在线调试页面
  -> /interfaceInfo/invoke
  -> 平台运行时模板基础校验
  -> SdkMethodRegistry
  -> FeiApiClient
  -> feiapi-gateway
  -> feiapi-interface
```

直接 SDK 调用从 `FeiApiClient` 开始，和在线调试共享网关、路由、鉴权、计费、统计及接口服务校验链路。

本阶段不新增平台规则引擎，不解析 `validationRule`，不把接口提供方的业务校验复制到前端或平台后端。`validationRule` 继续作为接口文档中的公开说明；`required`、`type` 继续用于生成字段表单和构造调用参数。

## 2. 当前问题

1. “填充示例”按钮存在，但页面加载时已经自动填充参数；当示例值为空时按钮仍可能显示，用户感知为按钮无效。
2. 在线调用返回值只有下游响应正文，未提供 HTTP 状态、响应媒体类型和响应耗时。
3. `FeiApiClient` 遇到下游非 2xx 会保留状态和正文构造异常，但反射注册器将其包装为通用 SDK 调用失败，在线页面无法展示真实下游响应。
4. 在线文档 compact 模式固定展示 curl，未展示 Java SDK 示例，也没有把文档示例复制事件传递到页面。
5. 请求参数已经自动序列化为 `userRequestParams`，页面需要增加只读 Body 预览，避免把字段输入误认为实际请求报文展示。

## 3. 设计方案

### 3.1 SDK 调用结果捕获

在 `feiapi-client-sdk` 增加在线调试专用的调用捕获上下文和结果模型，结果至少包含：

- HTTP 状态码。
- 响应媒体类型。
- 响应正文。
- 响应耗时（毫秒）。

在线调试模式只改变 SDK 对响应的读取和返回方式，不改变请求 URL、方法、签名、鉴权 Header、请求 Body、网关路由、计费和统计行为。普通 SDK 调用继续保持现有返回类型和非 2xx 异常行为。

`FeiApiClient` 在在线调试模式下读取一次完整响应并记录元数据；网络异常或 SDK 参数转换异常没有 HTTP 响应时，结果由平台按无状态错误处理。捕获上下文使用线程隔离，并在调用结束后清理，避免 Spring 单例客户端或线程池复用造成数据串扰。

### 3.2 平台后端在线调用结果

保留 `POST /interfaceInfo/invoke` 的 URL、请求方法和请求 DTO（`id`、`userRequestParams`）。Controller 只负责参数绑定、登录上下文传递和结果返回，实际编排下沉到在线调用 Service。

在线调用 Service 创建临时 `FeiApiClient`，开启在线调试捕获模式，通过现有 `SdkMethodRegistry` 调用绑定的 SDK 方法，然后返回专用结果 VO。下游 2xx、下游非 2xx 和平台执行异常分别处理：

- 下游 2xx：返回状态、耗时、媒体类型和正文，页面展示成功结果。
- 下游非 2xx：返回真实状态、耗时、媒体类型和下游公开正文，页面展示失败结果；不再包装成“SDK 方法调用失败”。
- 未产生 HTTP 响应的 SDK、连接或平台异常：返回安全错误信息和可空状态，不能泄露密钥、真实后端地址、堆栈或内部路径。

在线调用结果 VO 不暴露实体对象，不返回 SecretKey、真实目标地址或未脱敏的敏感 Header。

### 3.3 前端参数表单

保留当前根据结构化请求参数生成字段表单的方式：

- `required` 控制空值提示。
- `type` 控制输入控件和最终 JSON 类型转换。
- `object`、`array` 字段仍使用字段级 JSON 输入并校验对应根类型。
- `validationRule` 只作为字段说明展示，不解析为前端执行规则。
- 系统根据字段值自动生成 `userRequestParams`，用户不手写完整请求 JSON。

示例填充调整为显式操作：

- 页面加载时不使用示例值覆盖用户输入；默认值仍可作为初始字段值。
- 只有存在有效 `exampleValue` 或 `defaultValue` 的字段时才显示可用的“填充示例”按钮。
- 点击后按示例值优先、默认值兜底的规则填充全部字段，并重新生成请求 Body。
- 示例值为空、仅为类型占位标记或无法转换为声明类型时，按钮禁用并给出明确提示。

请求区域增加只读 Body 预览，内容来自当前字段序列化结果；请求地址、方法和协议 Header 继续使用现有文档/接口定义展示，不开放用户编辑。

### 3.4 前端响应与文档面板

请求结果面板增加响应元信息区域：状态码、响应耗时、响应媒体类型和正文。正文继续支持复制；正文为空时显示明确空状态。

接口文档二级页改为复用详情文档的调用示例切换能力，展示 Java SDK 与 curl 两个示例，并通过 `copy-text` 事件由在线调用页统一调用剪贴板和 Toast。不得在页面保存真实 `accessKey` 或 `secretKey`。

### 3.5 错误与安全处理

- 平台模板基础校验失败仍返回现有业务错误，不进入 SDK 调用。
- 下游业务校验错误通过在线调试结果展示真实公开状态和正文；不改变直接 SDK 的异常语义。
- 连接失败、超时、SDK 反射失败等没有下游响应的错误使用固定安全文案。
- 在线响应正文必须遵守现有响应大小和敏感内容边界；不能因为展示响应而泄露密钥、签名、内部地址或异常堆栈。
- 捕获元数据和线程上下文必须在 finally 中清理。

## 4. 文件范围

后端：

- `feiapi-client-sdk/src/main/java/com/feiting/feiapiclientsdk/client/FeiApiClient.java`
- `feiapi-client-sdk/src/main/java/com/feiting/feiapiclientsdk/model/` 下新增在线调用结果模型
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/interfaceplatform/definition/component/SdkMethodRegistry.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/controller/InterfaceInfoController.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/` 下新增在线调用 Service 及实现
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/vo/` 下新增在线调用结果 VO
- 对应 SDK、Service、Controller 和调用链测试

前端：

- `src/composables/useInterfaceInvoke.ts`
- `src/components/invoke/RequestParameterForm.vue`
- `src/components/invoke/InvokeResultPanel.vue`
- `src/views/InterfaceInvokeView.vue`
- `src/types/invoke.ts`
- `src/services/interfaceInfo.ts`
- `src/styles/pages/invoke.css`
- 对应在线调用、表单、结果面板和文档组件测试

文档：

- `doc/Feiapi平台接口文档能力落地计划.md`：将 `validationRule` 的前端职责修正为说明展示，不作为平台执行规则。
- `doc/Feiapi平台接口文档能力实施进度.md`：实现与验证完成后补充证据。

## 5. 验收标准

- 有示例值的接口点击“填充示例”后所有字段恢复为可调用示例；无示例值时按钮不会产生无效操作。
- 在线调用结果能展示真实下游 HTTP 状态、响应耗时、响应媒体类型和正文，覆盖 2xx、非 2xx、空正文和无 HTTP 响应异常。
- 直接 SDK 调用的公开方法签名和非 2xx 异常行为保持兼容。
- 在线文档二级页能切换 Java SDK/curl，并分别复制成功。
- 请求 Body 预览与最终提交的 `userRequestParams` 一致。
- 前端、后端和 SDK 定向测试通过，类型检查、Lint、生产构建和 `git diff --check` 通过。
- 不新增自定义业务 Header，不修改网关主调用链路、计费、配额和统计规则。
