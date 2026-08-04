# 严格发布门禁设计

## 1. 背景

阶段 2.1 至 2.9 已完成接口文档维护入口、文档状态、完整性校验、表单拆分、请求参数所有权、响应字段树、聚合全量替换、数量与报文边界以及接口删除生命周期。当前发布链路已经具备以下基础能力：

- 接口发布使用 `OFFLINE -> PUBLISHING -> ONLINE` 状态流转。
- 发布开始、完成和失败恢复均使用数据库条件更新。
- 同一接口的并发发布只有一个请求能够进入 `PUBLISHING`。
- 发布探测能够调用真实 SDK 方法，经过普通签名、内部探测签名、正式网关和真实下游。
- 发布探测不进入普通配额预扣、调用次数和调用日志流程。
- 进程异常造成的超时 `PUBLISHING` 状态已有恢复机制。
- SDK 探测响应体已经限制为 1 MiB。

现有发布校验仍只覆盖 SDK 方法存在和文档 `READY` 等部分条件，并且遇到首个错误即停止。探测阶段仅判断返回值非空，没有校验媒体类型、JSON 根类型和结构化响应字段，也没有为 SDK 方法声明安全探测策略。因此，新增或编辑时已经执行过的校验仍可能被历史脏数据、配置变化或不完整 SDK 契约绕过。

本设计只落实落地计划 2.10“严格发布门禁”。不修改普通用户调用协议，不新增业务 Header，不实现阶段 3 在线调试增强，也不为当前不存在的有副作用接口新增专用下游探测端点。

## 2. 目标与非目标

### 2.1 目标

- 提供管理员只读的发布前检查，一次返回按接口配置、SDK、运行时模板、文档和调用示例分类的全部静态问题。
- 只读检查不得修改接口、文档或发布状态。
- 正式发布必须重新读取数据库最新完整快照并运行同一套静态检查，不能信任前端状态或此前检查结果。
- 正式发布只有在静态检查全部通过后才能从 `OFFLINE` 条件更新为 `PUBLISHING`。
- 每个可发布 SDK 方法必须声明 `SAFE_REAL_CALL` 或 `DEDICATED_PROBE` 安全探测策略。
- 当前三个 SDK 方法统一声明为 `SAFE_REAL_CALL`。
- 探测参数优先使用结构化文档示例值，缺失时使用运行时模板值，并按声明类型严格标准化。
- 探测完整经过真实 SDK、普通签名、内部探测签名、正式网关、接口路由和真实下游。
- 探测执行 3 秒连接超时、10 秒响应超时和 15 秒总超时，不自动重试。
- 探测响应校验覆盖 HTTP 状态、媒体类型、响应体上限、JSON 语法、根类型和响应字段树。
- 探测失败按明确阶段返回安全错误，并仅在状态仍为 `PUBLISHING` 时恢复为 `OFFLINE`。
- 管理页能够独立执行发布前检查并按分类展示全部问题，发布期间阻止重复操作。

### 2.2 非目标

- 不把新增、编辑、文档保存和发布全部重构为通用规则引擎。
- 不改变接口新增、编辑和文档保存的现有业务校验语义。
- 不允许管理员绕过静态问题强制发布。
- 不缓存或持久化发布前检查结果。
- 不增加发布审批、多人会签、异步任务表或发布历史表。
- 不自动下线在线接口，也不允许 `ONLINE` 接口直接重新发布。
- 不为当前三个无副作用接口增加 `DEDICATED_PROBE` 下游分支。
- 不允许 SDK、前端或普通调用方自行声明可信探测身份。
- 不修改普通调用的配额、限流、日志、签名和响应协议。
- 不增加数据库表或历史数据迁移脚本。

## 3. 方案选择

### 3.1 采用方案

采用“统一发布检查器 + 独立探测组件”的方案：

- `InterfacePublishCheckService` 负责读取发布快照、执行全部静态规则并返回结构化问题。
- 只读检查接口和正式发布共同使用该检查服务。
- 正式发布在接口主记录行锁内复检，通过后才进入 `PUBLISHING`。
- `InterfaceProbeRequestBuilder` 负责构造真实 SDK 探测参数。
- `InterfacePublishProbeService` 负责超时控制和真实 SDK 调用。
- `InterfaceProbeResponseValidator` 负责响应契约校验。
- `InterfaceInfoPublishingService` 继续统一编排开始发布、执行探测、完成发布和失败恢复。

该方案保持现有 Controller、Service、Mapper 分层，不把业务校验放入 Controller，也不让探测逻辑进入接口信息数据访问层。

### 3.2 未采用方案

- 仅在现有 `startPublishing` 追加校验：无法稳定聚合全部问题，容易形成只读检查和正式发布两套规则。
- 重构全平台校验为规则引擎：长期一致性较好，但会扩大到已完成的 2.1 至 2.9，回归范围超出本阶段。

## 4. 发布前检查协议

新增管理员只读接口：

```http
GET /interfaceInfo/publish/check?id={interfaceInfoId}
```

响应数据使用专用视图对象，不向前端暴露实体：

```text
InterfacePublishCheckVO
|- passed: boolean
`- issues: InterfacePublishIssueVO[]
   |- category: string
   |- ruleCode: string
   |- field: string | null
   `- message: string
```

`category` 仅允许以下值：

- `INTERFACE_CONFIG`：接口配置。
- `SDK`：SDK 方法和探测契约。
- `RUNTIME_TEMPLATE`：运行时参数模板。
- `DOCUMENT`：结构化文档。
- `CALL_EXAMPLE`：Java SDK 和 curl 调用示例。

`ruleCode` 使用稳定的大写下划线编码，例如 `INTERFACE_NAME_REQUIRED`、`SDK_PROBE_STRATEGY_REQUIRED`。前端只使用服务端中文 `message` 展示，不自行翻译业务规则；稳定编码用于测试断言、日志定位和后续统计。

`field` 使用公开字段路径，例如 `interfaceInfo.path`、`doc.successExample`、`params[username].description`。不能返回数据库列名、服务器地址、堆栈或其他内部实现信息。

检查结果满足：

- `issues` 为空时 `passed=true`。
- `issues` 非空时 `passed=false`。
- 问题按分类、字段和规则编码稳定排序，并按三者组合去重。
- 合法无问题场景返回空数组，不返回 `null` 或省略字段。
- 接口 ID 非法、接口不存在、权限不足和数据库故障使用现有统一异常协议，不伪装成静态问题。

## 5. 数据快照与事务边界

### 5.1 只读检查

只读检查在只读事务中读取：

- 当前有效的 `interface_info`。
- 当前有效的 `interface_doc`。
- 全部当前有效的 `interface_doc_param`。
- 全部当前有效的 `interface_doc_error_code`。
- 当前有效的配额配置。
- 进程内 SDK 注册元数据。

只读事务使用数据库一致性快照，检查期间不执行状态恢复、状态更新、文档补建或任何自动修复。历史脏数据必须作为问题返回，不能在检查过程中静默标准化并写回数据库。

### 5.2 正式发布

正式发布保持现有短事务和固定锁顺序：

1. 校验接口 ID。
2. 使用 `SELECT ... FOR UPDATE` 锁定有效接口主记录。
3. 在持有行锁时恢复超时的 `PUBLISHING` 状态。
4. 确认数据库最新状态精确等于 `OFFLINE`。
5. 读取接口、文档、参数、错误码、配额和 SDK 元数据完整快照。
6. 使用统一检查器重新执行全部静态规则。
7. 存在问题时抛出发布检查异常，事务结束且状态保持 `OFFLINE`。
8. 全部通过后条件更新 `OFFLINE -> PUBLISHING` 并提交事务。

文档保存、接口编辑和删除都先锁定同一接口主记录，并在 `PUBLISHING` 状态下拒绝写入。因此，进入 `PUBLISHING` 后，静态检查使用的数据库配置在真实探测期间不会被业务入口修改。

锁内构造内部 `InterfacePublishContext`，包含接口快照、文档快照、结构化参数、错误码、SDK 契约和探测请求参数。该对象只在 Service 内部传递，不作为 Controller 响应，也不使用数据库实体直接返回前端。

真实网络探测必须在事务外执行，避免持有数据库事务和行锁等待最长 15 秒。

## 6. 静态检查规则

检查器不得因普通规则失败提前结束。每条独立规则捕获可预期业务异常并转换为一个问题；对参数、响应字段、错误码和公开文本逐项检查，使多个记录的问题能够一次返回。数据库连接失败、反射初始化失败等无法继续安全判断的系统异常直接中止检查并返回系统错误。

### 6.1 接口配置

- 接口名称、描述、请求方法、网关路径、真实后端地址、SDK 方法名和配额类型不能为空。
- `url` 不作为独立必填字段；展示地址按 `targetHost + path` 生成规则复核一致性。
- 请求方法必须命中后端白名单。
- 同一路径和请求方法只能存在一个未删除接口；当前接口自身不计为冲突。
- 路径必须以 `/` 开头，不允许空白、反斜杠、查询串、片段、控制字符和 `.`、`..` 路径穿越片段。
- 真实后端地址必须是合法 HTTP/HTTPS 地址，命中允许主机白名单，并通过本机、内网、链路本地和特殊地址防护。
- 配额类型必须命中枚举，并且数据库存在当前有效配置。发布检查不使用枚举默认额度兜底。
- 所有接口文本重新执行 Unicode 字符长度、格式、控制字符和内容安全校验。

### 6.2 SDK 契约

- `sdkMethodName` 必须存在于 `SdkMethodRegistry`。
- 注册方法必须具有 `@SdkInvoke`，方法签名与 `needParams` 一致，返回类型必须为 `String`。
- `@SdkInvoke.probeStrategy` 必须显式声明为 `SAFE_REAL_CALL` 或 `DEDICATED_PROBE`，默认值使用禁止发布的 `UNSPECIFIED`，防止新增方法遗漏安全评估。
- 当前 `getLoveWords`、`getUsernameByPost`、`generateQrCode` 均声明为 `SAFE_REAL_CALL`。
- 服务端管理员 AK、SK 和 `probeSecret` 必须配置为非空值；AK 必须属于当前有效管理员，配置的 AK、SK 必须与该管理员记录一致。检查响应只能说明配置缺失或不匹配，不能回显任何凭据。
- `DEDICATED_PROBE` 只表示协议受支持；没有对应可信下游专用逻辑的具体方法不得声明该策略。下游专用逻辑无法通过反射自动证明，必须与策略声明在同一功能中接受代码审查，并通过“可信标记进入专用分支且不产生正式业务数据”的集成测试。

### 6.3 运行时模板

- `needParams=true` 时模板不得为空，`needParams=false` 时模板必须为空。
- 非空模板必须是合法 JSON 对象，不允许数组、标量、`null` 或解析失败后降级放行。
- 参数名称不能为空或重复，并继续区分大小写。
- 参数值必须能够识别为 `string`、`number`、`boolean`、`object` 或 `array`。
- 参数数量、名称长度、单值大小和最终序列化请求体必须满足阶段 2.8 边界。
- 模板参数与结构化请求参数的名称、位置、类型和必填性必须完整一致。

### 6.4 结构化文档

- 必须存在文档主记录，`docStatus=READY`，且结构化文档不能完全缺失。
- 文档版本、请求内容类型和响应内容类型必须合法。
- 请求参数和响应字段必须具有有效公开说明；自动生成的待完善说明不能通过。
- 不接受 `HEADER` 场景记录；协议 Header 继续根据请求内容类型动态生成。
- 响应字段树必须无循环、无孤儿、不超过 8 层、同级名称不重复，且只有 `object`、`array` 可以拥有子字段。
- JSON 响应必须具有合法成功示例；非 JSON 响应不强制成功 JSON 示例。
- 非空成功示例、失败示例、错误码及全部公开文案重新执行语法、数量、长度、敏感数据、内部实现信息和内容安全校验。
- 错误码按大小写不敏感规则去重，同时保留原始展示值。

### 6.5 调用示例

- Java SDK 示例必须能够根据 SDK 契约和结构化请求参数生成。
- curl 示例必须能够根据请求方法、路径、内容类型和参数生成。
- 两类示例不得包含服务端管理员 AK/SK、`probeSecret`、真实用户凭据或其他疑似密钥。
- 示例 Header 和 Shell 内容继续拒绝 NUL、CR、LF 等控制字符。

## 7. 安全探测策略

SDK 新增枚举：

```text
UNSPECIFIED
SAFE_REAL_CALL
DEDICATED_PROBE
```

- `UNSPECIFIED` 只用于注解默认值和兼容编译，发布检查必须拒绝。
- `SAFE_REAL_CALL` 使用模拟数据执行正常业务逻辑，只能用于查询、计算、转换或内容生成等无正式业务副作用的方法。
- `DEDICATED_PROBE` 仍经过真实 SDK、网关、路由和下游，但下游执行经过平台验证的无副作用专用逻辑。

任何发送真实短信、创建正式订单、扣款、发货、写入正式业务数据或触发不可逆外部操作的方法都不能声明 `SAFE_REAL_CALL`。

本阶段只为 `DEDICATED_PROBE` 建立枚举、检查规则和可信网关标记协议，不新增实际专用下游端点。未来增加有副作用接口时，必须在同一功能中实现并测试下游专用分支，才能将对应 SDK 方法声明为 `DEDICATED_PROBE`。

## 8. 可信探测协议

SDK 发布探测继续携带普通签名 Header 和以下内部探测 Header：

- `X-FeiAPI-Probe`
- `X-FeiAPI-Probe-Nonce`
- `X-FeiAPI-Probe-Timestamp`
- `X-FeiAPI-Probe-Sign`

网关处理规则：

1. 对所有入站请求先移除客户端可能伪造的下游可信标记。
2. 完成普通 AK/SK 签名、普通 nonce 和时间戳校验。
3. 对发布探测完成 `probeSecret`、探测 nonce、探测时间戳和探测签名校验。
4. 只允许命中 `PUBLISHING` 接口，不允许普通用户调用发布中接口。
5. 探测校验通过后，移除对下游无用的探测签名材料，并添加网关生成的内部可信标记。
6. 根据数据库接口快照重写真实目标地址并转发下游。

下游未来的 `DEDICATED_PROBE` 分支只能信任网关生成的内部可信标记，不能信任客户端原始 `X-FeiAPI-Probe`。真实部署继续要求下游服务只通过网关所在受控网络访问，不能将内部服务端口直接暴露给公网。

网关自身产生的探测鉴权、发布接口未命中和目标路由错误使用受控响应 Header 标记失败阶段。该 Header 仅返回有限枚举，不包含内部地址、异常堆栈或密钥，用于 SDK 区分网关错误和下游返回的相同 HTTP 状态。

## 9. 探测参数构造

`InterfaceProbeRequestBuilder` 使用已经通过静态检查的运行时模板和结构化请求参数构造探测 JSON：

1. 按运行时模板顺序遍历参数，结构化文档只提供说明性值，不能新增模板外参数。
2. 首选结构化参数 `exampleValue`。
3. 示例值为空时使用运行时模板原始值。
4. 根据声明类型标准化为 JSON 字符串、数字、布尔、对象或数组。
5. 字符串值保留字符串语义；`number` 使用有限十进制数；`boolean` 只接受 `true`、`false`；对象和数组必须解析后根类型一致。
6. 无法取得有效值时，返回包含参数名称的静态问题或探测准备错误。
7. 最终序列化结果重新校验 65,535 UTF-8 字节上限。

`needParams=false` 的 SDK 方法不构造请求 JSON，并要求运行时模板和结构化请求参数均为空。参数默认值不参与探测值优先级，避免文档默认值被误认为可安全调用的模拟数据。

特殊业务约束仍由具体接口的示例值表达。即使参数示例值不是所有 `READY` 文档的统一必填项，缺少可安全探测值的具体接口仍不能发布。

## 10. 探测执行与超时

`InterfacePublishProbeService` 使用专用有界线程池执行真实 SDK 调用，不使用公共 `ForkJoinPool`。探测模式必须在执行 SDK 调用的同一工作线程中开启，并在 `finally` 中清理，保持现有 `ThreadLocal` 隔离语义。

超时规则：

- TCP 连接超时 3 秒。
- 响应读取超时 10 秒。
- 后端等待单次探测总时间不超过 15 秒。
- 不自动重试，也不在失败后调用第二个探测地址。
- 总超时时取消任务；SDK 的连接和读取超时必须小于总超时，避免长期占用工作线程。
- 线程池满时明确返回探测资源繁忙，不在 HTTP 请求线程无界排队。

SDK 在探测模式下采集以下不可变快照：

- HTTP 状态码。
- 原始 `Content-Type`。
- 受 1 MiB 限制的响应体。
- 网关受控失败阶段。

普通 SDK 调用继续返回现有 `String`，不改变公开方法签名。探测元数据只在当前线程和当前调用生命周期内可见，调用结束后清理，不能跨发布请求复用。

## 11. 探测响应契约

### 11.1 通用规则

- HTTP 状态必须为 `2xx`。
- 实际响应必须包含合法 `Content-Type`，并与文档声明兼容；比较时忽略 `charset` 等参数。
- 响应体超过 1 MiB 时立即停止读取并失败，不截断后继续校验。
- 非 JSON 响应不使用 JSON 字段树校验。文档成功示例非空时，实际响应体不能为空；文档成功示例为空且未维护响应字段时，实际响应体允许为空。该规则复用现有字段，不新增“允许空响应体”数据库字段。

### 11.2 JSON 根类型

- 响应体必须解析为合法 JSON。
- 实际根类型必须与成功示例根类型一致。
- 支持对象、数组、字符串、数字、布尔和 `null` 根类型。
- JSON 标量响应合法，但存在结构化响应字段时发布失败，因为对象式字段无法应用到标量根节点。

### 11.3 响应字段树

- 对象根节点：根响应字段匹配对象属性。
- 数组根节点：存在响应字段时，对数组中的每个对象元素应用根响应字段；空数组允许通过。
- `object` 字段的子字段匹配该对象的属性。
- `array` 字段存在子字段时，对数组中的每个对象元素应用子字段；空数组允许通过。
- 数组配置子字段但实际元素不是对象时失败。
- `nullable=false` 的字段必须存在且不能为 JSON `null`。
- `nullable=true` 的字段允许缺失或为 JSON `null`；非空时仍必须校验类型和子字段。
- 字段存在时必须符合 `string`、`number`、`boolean`、`object` 或 `array` 类型。
- 未写入文档的额外字段允许存在，采用向后兼容的开放对象语义。
- 空字符串、空对象和空数组不是 JSON `null`，在类型正确时允许通过。

## 12. 失败分类与状态恢复

探测失败使用以下稳定阶段：

- `SDK_INVOCATION`：SDK 契约、反射或参数调用失败。
- `GATEWAY_AUTH`：普通签名或内部探测签名失败。
- `GATEWAY_ROUTE`：发布中接口未命中或网关目标路由拒绝。
- `CONNECTION_TIMEOUT`：连接下游链路超时。
- `RESPONSE_TIMEOUT`：等待或读取响应超时。
- `TOTAL_TIMEOUT`：单次探测超过 15 秒。
- `DOWNSTREAM_STATUS`：下游返回非 `2xx`。
- `RESPONSE_FORMAT`：媒体类型、JSON 语法或根类型不匹配。
- `RESPONSE_STRUCTURE`：结构化响应字段缺失、空值或类型不匹配。

错误响应只返回阶段和安全裁剪后的公开原因。下游响应正文最多保留现有受控长度，并经过控制字符和敏感信息处理；日志不得输出 AK/SK、`probeSecret`、签名、完整请求正文或内部真实地址。

失败恢复流程：

1. 捕获并保存原始探测异常。
2. 条件更新 `PUBLISHING -> OFFLINE`。
3. 状态恢复成功后向调用方抛出原始分类异常。
4. 状态已经变化或恢复失败时记录接口 ID 和恢复阶段，不覆盖原始探测错误。

发布开始之前的静态检查失败不得执行状态恢复，因为接口从未进入本次请求拥有的 `PUBLISHING` 状态。探测成功只允许条件更新 `PUBLISHING -> ONLINE`，不得覆盖其他状态。

## 13. 前端交互

接口管理页为 `OFFLINE` 接口提供独立“检查”操作。`ONLINE` 和 `PUBLISHING` 不提供可执行的发布前检查入口，因为它们不满足正式发布的起始状态。

检查交互：

- 点击“检查”后调用只读检查接口。
- 请求期间仅禁用当前接口的检查和发布操作，避免重复请求。
- 检查通过时展示明确的“发布条件已通过”，但不自动发布。
- 检查失败时使用独立对话框按五类展示全部问题；没有问题的分类不显示。
- 对话框内容使用文本插值渲染，不使用 `v-html` 或 `innerHTML`。
- 关闭对话框不修改列表数据和接口状态。

正式发布交互：

- 管理员仍需明确点击“发布”。
- 前端不携带或信任此前检查结果，只提交接口 ID。
- 发布期间禁用当前接口的检查、编辑、删除、文档保存动作和重复发布操作；文档页面仍允许进入并按现有规则只读查看。
- 发布成功后重新加载权威列表。
- 正式发布因最新静态问题失败时，前端重新调用只读检查并展示最新问题。
- 探测失败时展示后端返回的失败阶段和公开原因，并重新加载数据库状态，恢复 `OFFLINE` 后重新启用维护操作。

不新增发布确认绕过、强制发布或自动重试按钮。

## 14. 安全要求

- 两个发布接口继续使用后端管理员权限校验；正式发布写接口继续受 CSRF 防护。
- 只读检查不接收接口配置、文档或 SDK 元数据的前端快照，只接收接口 ID。
- 正式发布不接收 AK/SK、`probeSecret`、探测参数或此前检查结果。
- 管理员 AK/SK 继续只从服务端 `FEIAPI_CLIENT_ACCESS_KEY`、`FEIAPI_CLIENT_SECRET_KEY` 读取。
- `probeSecret` 继续只从服务端 `FEIAPI_PROBE_SECRET` 读取。
- SDK 和网关启动或发布检查时发现凭据为空，应明确失败，不能使用固定默认密钥。
- 网关必须剥离客户端伪造的下游可信探测标记，并且只在内部探测签名通过后重新添加。
- 前端问题消息和字段路径全部按纯文本渲染。
- 检查结果不返回 `targetHost`、`url`、凭据、内部主机、数据库信息或异常堆栈。

## 15. 测试设计

### 15.1 静态检查单元测试

- 完整合法快照返回 `passed=true` 和空问题列表。
- 同一快照同时存在接口名称缺失、SDK 不存在、模板非法、文档草稿和示例失败时，一次返回五类问题。
- 问题按固定顺序返回并去重。
- 接口路径、目标地址、配额配置和内容安全规则分别失败。
- SDK 方法缺少注解、签名不匹配或策略为 `UNSPECIFIED` 时失败。
- `needParams` 与模板空值规则不一致时失败。
- 模板不是对象、参数重复、类型非法或结构化参数不一致时失败。
- 文档缺失、状态非 `READY`、响应树非法、示例非法和错误码重复时失败。
- Java SDK 或 curl 示例生成失败时归入调用示例分类。
- 只读检查不执行任何更新语句。

### 15.2 发布事务与并发测试

- 正式发布重新调用统一检查器，不复用此前只读检查结果。
- 静态问题存在时保持 `OFFLINE`，不执行 SDK 探测。
- 静态检查通过后才能条件更新为 `PUBLISHING`。
- 两个并发发布只有一个进入 `PUBLISHING`。
- 发布与接口编辑、文档保存或删除并发时，后获得行锁的一方根据最新状态判断。
- 探测成功只执行 `PUBLISHING -> ONLINE`。
- 探测失败只执行 `PUBLISHING -> OFFLINE`。
- 发布开始失败不回滚其他请求拥有的 `PUBLISHING` 状态。
- 超时状态恢复后仍重新执行完整静态检查。

### 15.3 探测参数测试

- 字符串、数字、布尔、对象和数组使用合法示例值构造。
- 示例值为空时使用运行时模板值。
- 不使用文档默认值替代缺失探测值。
- 类型格式不匹配时返回具体参数名称。
- `needParams=false` 返回空探测参数。
- 最终探测请求超过 65,535 UTF-8 字节时失败且不调用 SDK。

### 15.4 SDK 与网关测试

- 当前三个 SDK 方法均声明 `SAFE_REAL_CALL`。
- 普通模式不携带探测 Header，也不保存探测元数据。
- 探测模式同时生成普通签名和内部探测签名。
- 探测元数据包含状态、媒体类型、响应体和网关失败阶段，并在调用后清理。
- 连接、读取和总超时均返回正确分类，不自动重试。
- 网关拒绝伪造、过期、重复或签名错误的探测请求。
- 网关先剥离客户端可信标记，只在验证通过后添加内部可信标记。
- 普通用户不能调用 `PUBLISHING` 接口。
- 探测不扣管理员配额、不增加普通调用次数、不写普通调用日志。
- 网关自身鉴权和路由错误带受控失败阶段，下游同状态码不会被误分类。

### 15.5 响应契约测试

- `2xx` 与非 `2xx`。
- 媒体类型一致、带 `charset`、不兼容和缺失。
- 响应体超过 1 MiB。
- JSON 合法、非法和根类型不一致。
- 对象、数组、字符串、数字、布尔和 `null` 根类型。
- 根对象字段缺失、非空字段为 `null`、允许空字段缺失和字段类型错误。
- 对象子字段和数组元素递归校验。
- 空数组、数组标量、数组对象和数组元素类型不一致。
- JSON 标量配置响应字段时失败。
- 未记录的额外字段允许存在。

### 15.6 Controller 与前端测试

- 未登录用户和普通用户不能执行发布前检查或正式发布。
- 只读检查一次返回全部分类问题且接口状态不变。
- 管理页检查期间阻止重复检查和发布。
- 检查通过只展示结果，不自动发布。
- 检查失败按分类展示全部问题并使用安全文本节点。
- 正式发布重新校验失败后展示最新静态问题。
- 探测失败展示阶段和公开原因，列表回读后恢复可编辑 `OFFLINE` 状态。
- 发布成功后列表展示 `ONLINE`。
- E2E 模拟检查接口、正式复检、探测失败和成功状态流转。

## 16. 文件范围

后端仓库预计新增：

- 发布检查 Service、实现类、问题分类枚举和检查响应 VO。
- Service 内部发布上下文模型。
- 探测参数构造组件。
- 探测执行 Service、专用线程池配置和失败阶段异常。
- 探测响应契约校验组件。
- SDK 安全探测策略枚举和探测响应元数据模型。
- 上述组件对应的 JUnit 5、Mockito 和 AssertJ 测试。

后端仓库预计修改：

- `feiapi-backend-server/.../controller/InterfaceInfoController.java`
- `feiapi-backend-server/.../service/InterfaceInfoPublishingService.java`
- `feiapi-backend-server/.../service/InterfaceInfoLifecycleService.java`
- `feiapi-backend-server/.../service/impl/InterfaceInfoPublishingServiceImpl.java`
- `feiapi-backend-server/.../service/impl/InterfaceInfoLifecycleServiceImpl.java`
- `feiapi-backend-server/.../service/InterfaceDocService.java`
- `feiapi-backend-server/.../service/impl/InterfaceDocServiceImpl.java`
- `feiapi-backend-server/.../service/InterfaceQuotaConfigService.java`
- `feiapi-backend-server/.../component/RuntimeRequestParamTemplateValidator.java`
- `feiapi-backend-server/.../component/SdkMethodRegistry.java`
- `feiapi-backend-server/.../component/InterfaceDocJavaSdkExampleGenerator.java`
- `feiapi-client-sdk/.../annotation/SdkInvoke.java`
- `feiapi-client-sdk/.../client/FeiApiClient.java`
- `feiapi-gateway/.../CustomGlobalFilter.java`
- 对应现有单元、集成、并发、SDK 和网关测试。

`feiapi-interface` 本阶段不新增专用探测接口。实现中如果发现必须修改下游生产代码，应先说明具体安全原因和范围。

前端仓库预计新增：

- `src/types/interfacePublish.ts`
- `src/components/admin/InterfacePublishCheckDialog.vue`
- `src/components/admin/__tests__/InterfacePublishCheckDialog.test.ts`

前端仓库预计修改：

- `src/services/interfaceInfo.ts`
- `src/views/admin/InterfaceManagementView.vue`
- `src/views/admin/__tests__/InterfaceManagementView.test.ts`
- `tests/e2e/fixtures/apiMock.ts`
- `tests/e2e/interface-management.spec.ts`
- 必要的现有管理页样式文件。

实现完成后同步更新工作区根目录：

- `doc/Feiapi平台接口文档能力实施进度.md`
- `doc/后端接口文档.md`
- `doc/后端开发文档.md`

如实现中发现必须修改本范围之外的生产文件，应先说明原因，不顺带重构无关代码。

## 17. Git 与验证

后端和前端已经分别从当前本地 `dev` 创建 `feature/strict-publish-gate`。部署仓库预计无需修改。

按本轮用户要求：

- 先写入并审阅本设计文档。
- 设计批准后再修改业务代码。
- 代码编写和验证完成后先保留未提交状态，不自动执行 Git 提交、合并或删除 feature 分支。
- 后续只有在用户明确要求提交时，才使用中文说明和 `feat:`、`fix:`、`docs:` 等前缀提交，并按规范合并到 `dev`。

验证至少包括：

- 后端发布检查、发布编排、探测参数、响应契约、SDK 和网关定向测试。
- 后端 Maven 聚合测试和核心发布模块覆盖率核对。
- 前端对话框与管理页定向测试、全量 Vitest、类型检查、ESLint 和生产构建。
- 发布管理相关 Playwright E2E。
- 后端、前端分别执行 `git diff --check` 和工作区状态核对。
