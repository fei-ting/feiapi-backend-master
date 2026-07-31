# 接口文档数量、长度与报文大小边界设计

## 1. 背景

阶段 2.1 至 2.7 已完成接口文档维护入口、文档状态、草稿与完成维护校验、后台表单拆分、请求参数所有权、响应字段树和聚合全量替换。当前代码已经具备部分数量限制和 HTML `maxlength`，但各链路的计量口径仍不完整：

- Java `@Size` 和浏览器 `maxlength` 按 UTF-16 代码单元计数，不符合 Unicode 码点语义。
- JSON 示例仍按字符串长度校验，没有按 UTF-8 实际字节校验。
- 聚合保存没有在 JSON 解析前限制实际读取的 1 MiB 请求体。
- 在线调用、SDK、网关和发布探测没有形成一致的请求体及响应体大小边界。
- 前端缺少集合、字符、字节和聚合载荷的剩余量反馈。
- 发布前只检查文档状态，没有重新检查已经持久化的 2.8 边界。

本设计只落实落地计划 2.8，不提前实现后续严格发布完整性检查、接口删除生命周期、大文件上传、流式下载或超大响应能力。

## 2. 目标与非目标

### 2.1 目标

- 请求参数最多 100 条，响应字段最多 200 条，请求参数与响应字段合计最多 200 条，错误码最多 100 条。
- 普通文本去除首尾 Unicode 空白后按 Unicode 码点计数。
- 成功、失败 JSON 示例分别限制为 65,535 个 UTF-8 字节。
- 聚合文档保存 HTTP 请求体按实际读取字节限制为 1 MiB，并兼容分块传输。
- 在线调用参数、SDK 和网关签名请求体、发布探测请求体限制为 65,535 个 UTF-8 字节。
- 发布探测响应体限制为 1 MiB；文本按解压、解码后重新编码为 UTF-8 的字节数计量，二进制按解压后的原始字节计量。
- 保存草稿、完成维护、运行时参数同步和发布前检查使用一致的边界。
- 前端提供同口径提前校验和剩余量反馈，后端、SDK 与网关保留最终权威校验。
- 所有超限场景均明确失败，不静默截断、裁剪或保留部分数据。

### 2.2 非目标

- 不把固定限制改为环境变量或管理员配置。
- 不使用全局 1 MiB 容器限制影响头像上传或其他无关接口。
- 不改变文档全量替换协议、请求参数所有权或响应字段树规则。
- 不增加大文件上传、流式签名、流式下载和超大响应接口。
- 不重构所有网关错误响应，只补充本次 413 JSON 错误契约。
- 不改变普通 SDK 调用的返回类型或业务接口协议。

## 3. 计量口径

### 3.1 固定限制

| 边界 | 上限 |
| --- | ---: |
| 请求参数数量 | 100 |
| 响应字段数量，包含全部层级节点 | 200 |
| 请求参数与响应字段合计数量 | 200 |
| 错误码数量 | 100 |
| 响应字段树深度 | 8 |
| 文档版本号 | 64 个 ASCII 字符 |
| 参数名称 | 128 个 Unicode 码点 |
| 参数默认值 | 512 个 Unicode 码点 |
| 参数单字段示例值 | 1024 个 Unicode 码点 |
| 参数说明、校验规则、公开备注、错误说明、解决建议 | 512 个 Unicode 码点 |
| 错误码 | 64 个 Unicode 码点 |
| 错误信息 | 256 个 Unicode 码点 |
| 单个成功或失败 JSON 示例 | 65,535 个 UTF-8 字节 |
| 聚合文档保存 HTTP 请求体 | 1,048,576 字节 |
| 在线调用参数和签名请求体 | 65,535 个 UTF-8 字节 |
| 发布探测响应体 | 1,048,576 字节 |

所有边界均包含上限值：恰好达到上限时允许，超过一个计量单位时拒绝。

### 3.2 Unicode 字符

普通文本先去除首尾 Unicode 空白，再按 Unicode 码点计数。Java 使用 `codePointCount`，前端使用按码点迭代的等价实现。一个普通表情计为一个码点；由多个码点组成的组合表情按实际码点数计数。

运行时请求参数名称继续遵循阶段 2.5 的严格规则：名称不允许首尾 Unicode 空白，因此不通过裁剪改变权威名称；其长度按原始名称码点数校验。响应字段名和其他会被持久化标准化的普通文本按去除首尾空白后的值校验。

文档版本号继续使用现有 ASCII 白名单 `[A-Za-z0-9._-]`，不接受 Unicode 字符或空白。

### 3.3 UTF-8 字节

JSON 示例和请求体使用 `UTF-8` 编码后的实际字节数。前端使用 `TextEncoder`，Java 使用 `StandardCharsets.UTF_8`。成功与失败示例独立计数，不能以二者合计替代单字段限制。

聚合保存 1 MiB 限制作用于服务端实际读取的 HTTP 请求体字节，不只信任 `Content-Length`。`Content-Length` 可用于提前失败，但无声明长度或分块传输仍必须由受限输入流执行最终计数。

### 3.4 探测响应

发布探测根据实际响应 `Content-Type` 分类：

- `text/*`、JSON、XML、`+json` 和 `+xml` 作为文本。
- 其他类型或缺失 `Content-Type` 时按二进制处理。

Hutool 异步响应的 `bodyStream` 已对 `gzip`、`deflate` 解压，因此限制作用于解压后的流。文本使用响应声明字符集解码，未声明时沿用 Hutool 的 UTF-8 默认值，再按 UTF-8 重新编码计数；二进制直接按解压后的原始字节计数。

## 4. 后端服务设计

### 4.1 文档边界校验

新增独立的文档边界校验组件，负责以下规则：

1. 校验请求参数、响应字段、合计参数和错误码数量。
2. 校验文档主信息、参数说明性字段和错误码字段的 Unicode 码点长度。
3. 校验成功、失败 JSON 示例的 UTF-8 字节数。
4. 校验运行时模板生成的参数名称、数量和示例值是否能安全写入结构化文档。
5. 校验持久化文档是否仍满足发布前边界。

`InterfaceDocServiceImpl.saveDoc` 在构建实体、删除旧快照或写入数据库前完成全部校验。DTO 校验负责 Controller 入口的快速反馈，Service 校验负责直接调用和事务内最终权威，不能只依赖注解。

### 4.2 运行时参数同步

`RuntimeRequestParamTemplateValidator` 在解析 JSON 对象后检查：

- 对象键数量不超过 100。
- 每个原始参数名称不超过 128 个 Unicode 码点。
- 模板整体作为发布探测请求参数时不超过 65,535 个 UTF-8 字节。
- 自动生成的单字段示例值不超过 1024 个 Unicode 码点。

接口新增或更新仍在现有事务中创建文档和同步参数。任何分类数量、合计数量或字段长度超限均抛出业务异常，使接口配置更新、文档同步和状态变化整体回滚，不执行截断。

### 4.3 发布前检查

扩展现有 `InterfaceDocService.validateReadyForPublish`：在确认主记录存在且状态为 `READY` 后，重新读取文档主信息、全部请求参数、响应字段和错误码，执行本设计的数量、文本和示例字节边界。同时重新校验运行时参数模板的请求体边界。

发布生命周期继续保持“锁定接口主记录 → 校验文档 → 切换 `PUBLISHING`”顺序。边界失败发生在状态切换前，不执行发布探测。

### 4.4 聚合 HTTP 请求体

新增只支持 `InterfaceDocSaveRequest` 的 `RequestBodyAdvice`：

1. `Content-Length` 已知且超过 1 MiB 时立即抛出超限异常。
2. 其他请求通过受限输入流读取，累计实际字节。
3. 读取第 1,048,577 个字节时立即抛出超限异常，Jackson 停止解析。
4. 全局异常处理器识别直接异常及 `HttpMessageNotReadableException` 包装的超限根因。

该限制不配置为 Tomcat 全局限制，避免影响头像上传和其他请求。无 `Content-Length` 的分块传输与声明长度不可信的请求由实际读取计数兜底。

### 4.5 在线调用参数

`InterfaceInfoInvokeRequest.userRequestParams` 增加 UTF-8 字节约束。约束失败由全局异常处理器识别为请求体过大，返回 HTTP 413。现有 JSON 结构和运行时参数类型校验继续执行，但必须位于字节边界之后。

## 5. SDK 与网关设计

### 5.1 SDK 请求体

在 `FeiApiClient` 生成签名 Header 之前检查最终真实请求体的 UTF-8 字节数：

- 空请求体按 0 字节处理。
- 恰好 65,535 字节允许。
- 超过上限抛出明确参数异常，不生成签名、不执行 HTTP 请求。

检查作用于序列化后的最终正文。例如 `getUsernameByPost` 必须检查转换后真正发送的 JSON，而不是转换前输入。

### 5.2 发布探测响应

普通 SDK 调用保留现有同步读取行为；探测模式使用 `executeAsync` 获取响应流，并交由受限响应读取工具处理。

文本响应最多读取 1,048,577 个解码字符作为快速上界，随后按完整文本的 UTF-8 字节数执行最终校验。由于任何 Java 字符形成的 UTF-8 内容都不会比其 UTF-16 代码单元更少到绕过该上界，这一过程可限制内存并保持字节判断准确。二进制响应最多读取 1,048,577 个解压后原始字节。

超限时立即关闭响应和底层流，并抛出“发布探测响应体超过 1048576 字节”。发布编排捕获异常后沿用现有回滚路径恢复为 `OFFLINE`，不得转换为 JSON 格式错误或下游超时。

### 5.3 网关请求体

网关在签名校验前执行两层检查：

1. `Content-Length` 已知且超过 65,535 字节时直接返回 413。
2. 使用 `DataBufferUtils.join(request.getBody(), 65535)` 限制实际聚合字节，捕获 `DataBufferLimitException` 并返回 413。

超限请求不计算签名、不消费 nonce、不查询接口、不预扣配额、不转发下游。合法请求继续使用聚合后的原始字节恢复请求体，签名内容不变。

## 6. 前端设计

### 6.1 公共计量工具

新增固定限制常量和无副作用文本计量工具：

- 去除首尾 Unicode 空白。
- 计算 Unicode 码点数。
- 计算 UTF-8 字节数。
- 计算 JSON 序列化载荷字节数。
- 生成剩余量和超限状态。

新增紧凑的剩余量提示组件，显示“剩余 N 字符”或“剩余 N 字节”；超限时显示超过数量并使用现有错误色。提示只呈现当前状态，不自动截断输入。

### 6.2 文档维护表单

- 文档主信息、请求参数说明、响应字段和错误码输入框显示 Unicode 剩余字符。
- JSON 示例分别显示 UTF-8 剩余字节。
- 请求参数区显示 `当前数量/100`。
- 响应字段区显示响应数量及参数合计；响应达到 200 或合计达到 200 时禁用“新增字段”，并显示触发的具体限制。
- 错误码达到 100 时禁用“新增错误码”。
- 保存前同时校验全部字段、集合和 `JSON.stringify` 后的聚合请求体；超过 1 MiB 时不发送请求。
- 移除会错误按 UTF-16 提前阻止表情输入的普通文本 `maxlength`，文档版本号等纯 ASCII 字段可以保留原生限制。

### 6.3 运行时配置

接口配置弹窗在解析运行时模板后检查参数数量、参数名码点长度、自动示例值长度及模板 UTF-8 字节数。超限时不调用新增或更新接口，避免等到后端同步阶段才反馈。

### 6.4 在线调用

在线调用组合式函数从当前结构化字段构建实际 `userRequestParams` 预览，并计算其 UTF-8 字节数。请求参数区显示剩余字节；超过 65,535 字节时禁用发送并展示明确错误。发送前仍重新构建和校验一次，避免界面状态与实际载荷不一致。

## 7. 错误契约

新增业务错误码 `41300`，默认消息为“请求体过大”。

后端和网关请求体超限统一返回：

```json
{
  "code": 41300,
  "data": null,
  "message": "请求体不能超过 65535 字节"
}
```

聚合保存使用 1 MiB 对应提示，HTTP 状态均为 `413 Payload Too Large`。数量、普通文本长度、JSON 示例单字段字节数等文档内容错误继续使用业务码 `40000`，因为这些请求已被完整、安全地读取，只是业务数据不满足约束。

前端沿用现有 Axios 错误解析展示后端 `message`。错误内容不得包含正文、凭据、数据库 ID、堆栈或下游响应全文。

## 8. 原子性与安全性

- 所有文档边界必须在全量删除旧参数和错误码之前完成。
- 运行时同步超限必须通过现有 Service 事务回滚接口信息、文档同步和状态变化。
- 发布前边界失败不能进入 `PUBLISHING`；探测响应超限必须从 `PUBLISHING` 恢复为 `OFFLINE`。
- 网关超限不能验签、消费 nonce、预扣次数或转发。
- SDK 超限不能生成签名或建立请求。
- 不记录超限正文或示例内容，只记录接口 ID、链路和限制类型等必要上下文。
- 所有限制禁止静默截断。现有 SDK 异常响应 200 字符截断仅用于构造错误提示，不改变本设计的响应读取上限。

## 9. 测试设计

### 9.1 后端文档与 DTO

- 请求参数 100/101、响应字段 200/201、参数合计 200/201、错误码 100/101。
- 所有普通文本分别覆盖上限和上限加一码点。
- 使用表情验证一个补充平面字符计为一个 Unicode 码点。
- 成功和失败 JSON 示例分别覆盖 65,535/65,536 个 UTF-8 字节及多字节中文。
- Controller 校验与 Service 直接调用得到一致结论。
- 任一校验失败后旧文档快照保持不变。

### 9.2 HTTP 请求读取

- 聚合请求体恰好 1 MiB 可以继续解析。
- 1 MiB 加 1 字节返回 HTTP 413 和业务码 41300。
- `Content-Length` 提前超限返回 413。
- 无 `Content-Length`、多数据块读取超过上限仍返回 413。
- 非接口文档保存端点不受 1 MiB 专用限制影响。

### 9.3 同步与发布

- 运行时模板 100/101 个参数。
- 参数名 128/129 个码点，模板示例 1024/1025 个码点，请求模板 65,535/65,536 个 UTF-8 字节。
- 同步超限时接口更新、文档参数和文档状态整体回滚。
- 直接构造超限持久化文档后，发布前检查拒绝并保持 `OFFLINE`。
- 探测请求正文超限时发布失败并恢复 `OFFLINE`。
- 探测响应正文超过 1 MiB 时立即失败并恢复 `OFFLINE`。

### 9.4 SDK 与网关

- SDK 请求体 65,535 字节允许，65,536 字节拒绝且不执行 HTTP 请求。
- 探测文本覆盖 UTF-8、非 UTF-8 字符集、gzip/deflate 和分块响应。
- 探测二进制响应覆盖 1 MiB 与 1 MiB 加 1 字节。
- 网关覆盖已知长度、未知长度、多 `DataBuffer`、空正文和精确上限。
- 网关超限验证不会调用用户、接口、nonce、配额和下游链路。
- 413 响应状态、内容类型与 JSON 结构准确。

### 9.5 前端

- Unicode 码点和 UTF-8 字节工具覆盖 ASCII、中文、普通表情和组合字符。
- 所有输入的剩余量、超限状态和保存前校验。
- 响应字段与错误码达到上限时禁用新增，删除后恢复。
- 请求参数与响应字段合计达到上限时禁用响应字段新增。
- 聚合载荷 1 MiB/1 MiB 加 1 字节。
- 在线调用正文 65,535/65,536 字节及序列化转义后的实际大小。
- 运行时模板数量、名称、示例和字节边界。
- 既有草稿、完成维护、树操作、全量回读、脏状态和调用流程无回归。

## 10. 文件范围

后端服务预计修改或新增：

- `feiapi-backend-server/src/main/java/com/feiting/feiapi/common/ErrorCode.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/exception/GlobalExceptionHandler.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/exception/RequestBodyTooLargeException.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/InterfaceDocBoundaryValidator.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/InterfaceDocRequestBodyAdvice.java`
- 后端 Unicode 码点与 UTF-8 字节 Jakarta Validation 注解及校验器
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/RuntimeRequestParamTemplateValidator.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceDocServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceDoc/InterfaceDocSaveRequest.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceDoc/InterfaceDocParamSaveRequest.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceDoc/InterfaceDocErrorCodeSaveRequest.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceInfo/InterfaceInfoInvokeRequest.java`
- 对应 DTO、组件、异常、Controller、Service、同步和发布测试

SDK 与网关预计修改或新增：

- `feiapi-client-sdk/src/main/java/com/feiting/feiapiclientsdk/client/FeiApiClient.java`
- SDK 请求与探测响应大小常量、受限响应读取工具及测试
- `feiapi-gateway/src/main/java/com/feiting/feiapigateway/CustomGlobalFilter.java`
- `feiapi-gateway/src/test/java/com/feiting/feiapigateway/CustomGlobalFilterTest.java`

前端预计修改或新增：

- `src/constants/interfaceDocLimits.ts`
- `src/utils/textSize.ts`
- 通用剩余量提示组件及测试
- `src/views/admin/InterfaceDocMaintenanceView.vue`
- `src/components/admin/doc/DocumentMainInfoForm.vue`
- `src/components/admin/doc/RequestParamDescriptionList.vue`
- `src/components/admin/doc/ResponseParamEditor.vue`
- `src/components/admin/doc/JsonExampleEditor.vue`
- `src/components/admin/doc/ErrorCodeEditor.vue`
- `src/components/admin/InterfaceConfigModal.vue`
- `src/composables/useInterfaceInvoke.ts`
- `src/components/invoke/RequestParameterForm.vue`
- `src/views/InterfaceInvokeView.vue`
- 对应工具、组件、页面测试和既有样式文件

完成实现与验证后更新工作区根目录 `doc/Feiapi平台接口文档能力实施进度.md`。如实现中发现必须修改本范围之外的生产文件，应先说明原因，不顺带重构无关代码。

## 11. Git 与验证

后端、前端分别从各自 `dev` 创建 `feature/interface-doc-size-boundaries`。根据用户本轮要求，设计、实现、测试和进度文档变更均保留为未提交状态，不执行 Git 提交、合并或功能分支删除。

验证至少包括：

- 后端相关模块定向测试与 Maven 聚合测试。
- 前端相关定向测试、全量单元测试、类型检查、ESLint 和生产构建。
- 相关 Playwright 端到端测试。
- 两个仓库执行 `git diff --check` 和工作区状态核对。

