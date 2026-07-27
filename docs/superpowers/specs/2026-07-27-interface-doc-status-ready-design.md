# FeiAPI 接口文档状态与完成维护设计

## 1. 背景与目标

当前接口文档已经具备独立维护页面、聚合保存、结构化参数、错误码、内容安全校验和下线编辑门禁，但缺少显式的维护状态。管理员无法区分“已经保存但仍待完善”和“已经确认可公开使用”的文档，发布接口时也无法阻止不完整文档进入公开状态。

本次实现落地计划 2.2，并同步实现 2.3 中支撑 `READY` 状态的完整性校验，形成以下闭环：

- 文档状态只使用 `DRAFT` 和 `READY`。
- 新增接口及历史数据均从 `DRAFT` 开始。
- 管理员可以保存草稿或完成维护。
- `READY` 必须通过结构、安全和完整性校验。
- 下线接口的受控配置发生有效变化后，文档自动降为 `DRAFT`。
- 下线接口只有在文档为 `READY` 时才能发布。
- 已上线历史接口初始化为 `DRAFT` 后保持上线，不影响已有调用。

本次不增加文档废弃状态、并发编辑控制、审计变更表、Java SDK 示例或导出能力，也不修改网关、SDK 和接口提供方的主调用链路。

## 2. 验收条件

1. 新增接口时同步创建的文档主记录状态为 `DRAFT`。
2. 历史文档迁移后统一为 `DRAFT`，历史在线接口不自动下线。
3. 聚合详情和后台接口列表始终返回非空的 `docStatus`；文档主记录缺失时返回 `DRAFT`。
4. `structuredDocMissing == true` 时 `docStatus` 必须为 `DRAFT`；`docStatus == READY` 时 `structuredDocMissing` 必须为 `false`。
5. 保存草稿执行现有权限、状态、枚举、数量、长度、重复项、父子关系、运行时参数所有权、JSON 语法和内容安全校验，但允许内容暂不完整。
6. 完成维护在草稿校验基础上执行完整性校验，通过后才将文档状态保存为 `READY`。
7. 后台列表对 `DRAFT` 显示“文档待完善”，并禁用发布操作。
8. 后端发布入口必须拒绝 `DRAFT` 或缺少文档主记录的接口，不能通过绕过前端完成发布。
9. 接口下线不改变文档状态；发布失败恢复为下线状态时也不改变文档状态。
10. 下线接口的受控配置发生有效变化时，接口信息更新、请求参数文档同步和状态降级位于同一事务中。
11. 标准化后的提交与数据库完全一致时，不触发状态降级。
12. 前后端相关单元测试、集成测试、类型检查和构建全部通过。

## 3. 方案选择

沿用 `POST /interfaceDoc/save` 聚合保存接口，在 `InterfaceDocSaveRequest` 中增加必填的 `docStatus`。前端“保存草稿”提交 `DRAFT`，“完成维护”提交 `READY`。

该方案使文档内容替换和状态转换处于同一事务。相比拆分保存和状态转换接口，它不会产生“正文已更新但状态未更新”的部分成功状态，也不需要维护重复的聚合保存逻辑。

后端把客户端提交的状态视为维护意图，而不是无条件赋值：状态必须属于枚举；`READY` 必须额外通过完整性校验；`DRAFT` 也不能绕过现有结构和安全校验。

## 4. 数据模型与迁移

### 4.1 表结构

在 `interface_doc` 增加：

```sql
doc_status varchar(16) default 'DRAFT' not null comment '文档状态 DRAFT-草稿 READY-已完成'
```

`sql/interface_doc.sql` 更新为最新的完整建表定义。另新增一次性迁移脚本 `sql/interface_doc_status_migration.sql`，用于已有环境增加字段并显式将历史记录统一为 `DRAFT`。

迁移脚本不修改 `interface_info.status`，因此历史在线接口保持在线。部署时必须先执行数据库迁移，再发布读取 `doc_status` 的后端版本。

### 4.2 状态枚举

新增 `InterfaceDocStatusEnum`：

- `DRAFT`：文档已保存但尚未由管理员确认完成。
- `READY`：文档结构和公开内容已由管理员确认，可作为发布前置条件。

状态仅表达文档维护完整度，不承载接口上线、下线、发布中或废弃语义。

## 5. 后端设计

### 5.1 聚合查询

`InterfaceDocDetailVO` 增加顶层 `docStatus`：

- 文档主记录存在时返回持久化状态。
- 文档主记录不存在、状态为空或状态非法时，安全降级为 `DRAFT`。
- 不通过参数记录或错误码记录推断状态。

`InterfaceInfoVO` 增加 `docStatus`，供后台接口列表显示。列表查询取得当前页接口后，由文档服务按接口 ID 集合批量查询状态并补齐，避免逐行查询。按调用总数排序的自定义分页结果也复用同一批量补齐流程，不修改分页和排序语义。

### 5.2 聚合保存

`InterfaceDocSaveRequest.docStatus` 使用 `@NotBlank` 和枚举业务校验。保存顺序为：

1. 校验接口存在且为 `OFFLINE`。
2. 校验全量 `params`、`errorCodes` 已显式提交。
3. 执行文档主信息、结构化参数、错误码和内容安全校验。
4. 当目标状态为 `READY` 时执行完整性校验。
5. 保存文档主信息及目标状态。
6. 全量替换参数和错误码。

以上步骤继续由 `InterfaceDocService.saveDoc` 的事务统一控制，任一步骤失败均回滚。

### 5.3 完整性校验

进入 `READY` 时增加以下规则：

- 运行时模板存在请求参数时，保存请求中的请求参数必须与最新模板完整对应；该规则已经由现有运行时参数所有权校验保证。
- 所有已经存在的请求参数必须填写非空公开说明。
- 请求参数说明不能等于系统占位文案“由接口运行时参数模板自动生成”。比较前去除首尾空白。
- 所有已经存在的响应字段必须填写非空公开说明。
- 响应内容类型为 `application/json` 时，成功响应示例必须非空；其 JSON 语法、深度、敏感数据和内容安全继续由现有校验器负责。
- 非 JSON 响应允许成功响应示例为空。
- 失败响应示例始终可选；非空时仍必须通过现有校验。
- 请求参数、响应字段和接口错误码列表均允许为空。
- 错误码存在时继续要求 `errorCode` 和 `errorMessage`，说明与解决建议可选。
- 默认值和单字段示例值不作为完成维护的强制条件。

草稿保存不执行上述完整性要求，但仍执行所有结构和安全校验。

### 5.4 新增接口

`syncRequestDocFromInterfaceInfo` 当前会确保文档主记录存在。新建主记录时显式写入 `DRAFT`，随后同步运行时请求参数文档。接口信息新增、文档创建和参数同步继续位于 `InterfaceInfoLifecycleService.addInterfaceInfoWithDoc` 的同一事务。

### 5.5 配置变更降级

`InterfaceInfoLifecycleService.updateInterfaceInfoWithDoc` 在条件更新成功后读取最新数据库记录，并比较更新前后的以下受控字段：

- 接口名称
- 接口描述
- 请求方法
- 网关路径
- 真实后端地址
- 展示地址
- 配额类型
- SDK 方法名
- 运行时请求参数模板

只有数据库中的最终值发生变化才视为有效修改。有效修改时：

1. 如果请求方法或运行时请求参数模板变化，先按现有逻辑同步请求参数文档。
2. 将文档状态更新为 `DRAFT`。

接口更新、参数同步和状态降级共享现有事务。调用次数、更新时间和发布状态等非管理员维护字段不参与比较。文档已经是 `DRAFT` 时允许幂等更新，不额外创建记录。

### 5.6 发布门禁

在接口由 `OFFLINE` 转为 `PUBLISHING` 之前调用文档服务进行发布资格校验：

- 文档主记录必须存在。
- `doc_status` 必须为 `READY`。

不满足条件时抛出统一业务异常，提示“接口文档待完善，请先完成文档维护”。校验发生在任何发布状态更新和探测调用之前，因此失败请求不会进入 `PUBLISHING`。

已处于 `ONLINE` 的历史接口不会主动经过该门禁，也不会因迁移为 `DRAFT` 自动下线。其后若先下线，再次发布时必须满足 `READY`。

## 6. 前端设计

### 6.1 文档维护页

维护页读取聚合详情顶层 `docStatus`，在接口摘要中展示“草稿”或“已完成”。顶部和底部操作区将原“保存文档”替换为：

- “保存草稿”：提交完整表单快照和 `docStatus: DRAFT`。
- “完成维护”：提交完整表单快照和 `docStatus: READY`。

按钮规则：

- 加载失败、接口不可编辑或正在保存时，两种操作都禁用。
- 保存草稿要求表单存在未保存修改，避免无意义重复提交。
- 当前状态为 `DRAFT` 时，即使表单正文未变化，也允许执行完成维护。
- 当前状态为 `READY` 且表单有变化时，管理员可以直接重新完成维护，也可以保存为草稿。

前端保留基础必填和 JSON 格式即时提示，并为 `READY` 镜像请求参数说明、响应字段说明和 JSON 成功示例的完整性检查，提供更快反馈。后端校验始终是最终权威。

保存成功后重新加载文档详情，使用服务端返回状态刷新基线快照，并分别提示“草稿已保存”或“文档维护已完成”。保存失败时保留编辑状态并展示后端业务错误。

### 6.2 接口管理列表

接口列表在接口名称旁显示文档状态：

- `DRAFT`：显示“文档待完善”警示角标。
- `READY`：不增加额外角标，减少列表噪声。

当接口为 `OFFLINE` 且文档状态不是 `READY` 时，发布按钮禁用，并通过按钮标题说明需要先完成文档维护。前端禁用不替代后端发布门禁。

发布请求失败时优先展示服务端业务消息，便于管理员直接识别文档状态问题。

## 7. 异常与安全边界

- 非管理员仍不能保存接口文档或发布接口。
- `ONLINE`、`PUBLISHING` 接口仍不能保存文档。
- 非法文档状态拒绝保存，不进行默认纠正。
- `DRAFT` 不能绕过敏感数据、脚本、内部实现信息、JSON 深度和长度限制。
- 前端提交的 `docStatus` 不能绕过后端完整性校验。
- 批量状态查询只返回请求接口 ID 对应的数据，不暴露额外文档内容。
- 本次不在日志中记录文档正文、密钥或示例中的敏感内容。

## 8. 测试设计

核心业务按 TDD 顺序实现：先增加失败测试，再完成最小实现，最后重构。

### 8.1 后端测试

- DTO：`docStatus` 缺失、空白时校验失败。
- 状态枚举：只接受 `DRAFT`、`READY`。
- 新增接口：自动创建 `DRAFT` 文档。
- 聚合查询：主记录缺失返回 `DRAFT` 且 `structuredDocMissing` 为真；`READY` 文档的缺失标记为假。
- 草稿保存：允许缺少说明、响应字段、示例和错误码；非法 JSON 或不安全内容仍被拒绝。
- 完成维护：拒绝缺少请求参数说明、系统占位说明、缺少响应说明以及 JSON 响应缺少成功示例。
- 完成维护：允许无参数、无响应字段、无错误码；允许非 JSON 响应没有成功示例。
- 发布门禁：缺少文档、`DRAFT` 均拒绝，`READY` 才进入现有发布探测流程。
- 历史在线接口：`DRAFT` 状态不导致自动下线。
- 配置降级：九类受控字段任一有效变化后状态变为 `DRAFT`。
- 无效变化：标准化后相同的提交保持 `READY`。
- 事务：请求参数同步或状态降级失败时，接口信息更新整体回滚。
- 列表：普通分页和调用总数排序分页均返回正确 `docStatus`，缺少主记录时返回 `DRAFT`。

### 8.2 前端测试

- 类型定义包含 `docStatus`，保存请求必须提交状态。
- 维护页正确显示状态和两个操作按钮。
- 保存草稿与完成维护发送不同目标状态。
- `DRAFT` 且无正文变化时仍可完成维护。
- `READY` 完整性错误在前端被拦截，服务端错误仍能展示。
- 列表为 `DRAFT` 显示“文档待完善”并禁用发布。
- `READY` 下线接口允许发布。
- 端到端流程覆盖“新增接口 -> 待完善 -> 完成维护 -> 发布 -> 下线 -> 修改配置 -> 重新待完善”。

## 9. 计划改动文件

后端：

- `sql/interface_doc.sql`
- `sql/interface_doc_status_migration.sql`（新增）
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/entity/InterfaceDoc.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/enums/InterfaceDocStatusEnum.java`（新增）
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceDoc/InterfaceDocSaveRequest.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/vo/InterfaceDocDetailVO.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/model/vo/InterfaceInfoVO.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/InterfaceDocService.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceDocServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceInfoLifecycleServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/controller/InterfaceInfoController.java`
- 对应 DTO、Service 和 Controller 测试文件

前端：

- `src/types/interfaceDoc.ts`
- `src/types/interface.ts`
- `src/views/admin/InterfaceDocMaintenanceView.vue`
- `src/views/admin/InterfaceManagementView.vue`
- `src/components/admin/doc/InterfaceDocSummary.vue`
- `src/styles/components/cards.css`
- `src/styles/pages/admin-tools.css`
- 对应 Vitest、接口模拟和 Playwright 测试文件

## 10. 实施与交付顺序

1. 在后端和前端各自的 `feature/interface-doc-status-ready` 分支开发。
2. 后端先写状态、完整性、发布门禁和事务测试，再实现数据库模型和服务逻辑。
3. 前端先写维护页和列表行为测试，再实现类型、交互和样式。
4. 执行后端定向测试、完整测试，执行前端单元测试、类型检查、构建和相关端到端测试。
5. 使用代码质量评审流程检查安全、事务、兼容性、测试有效性和回归风险。
6. 分别使用中文提交说明提交前后端功能。
7. 分别合并到各自 `dev`，确认合并成功后删除临时 feature 分支。
