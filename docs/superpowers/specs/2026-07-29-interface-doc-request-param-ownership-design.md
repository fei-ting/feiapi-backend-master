# FeiAPI 请求参数所有权边界设计

## 1. 背景与目标

阶段 2.1 至 2.4 已完成独立文档维护页、文档状态、完整性校验和表单拆分。当前第 2.5 项已有运行时参数同步和文档页只读字段，但仍需要把“请求参数由接口运行时配置拥有”的规则收敛为一致、可验证的后端契约。

本轮目标：

- 以 `interface_info.request_params` 作为请求参数名称、位置、类型和必填性的唯一来源。
- 运行时模板参数名为空、全空白或带首尾空白时，在接口配置入口直接拒绝。
- 文档同步和聚合保存使用原始参数名进行大小写敏感的完全匹配，不裁剪、不改名、不相似匹配。
- 保留已有说明性字段的同步语义，并补齐改名、方法变化、非法输入和前端阻断测试。
- 删除初始化 SQL 中依赖旧数据的 `HEADER` 修复语句，保持阶段 2 的空库初始化边界。

## 2. 已确认的业务规则

### 2.1 名称与所有权

“请求参数名”专指运行时请求参数模板 JSON 对象的键，以及文档保存请求中请求参数的 `name`。它不包括 `paramScene`、`docStatus`、响应字段名称或前端会话内的 `paramKey`。

例如模板：

```json
{
  "userId": 1001,
  "keyword": "手机"
}
```

其中 `userId` 和 `keyword` 是请求参数名。文档保存时必须提交完全相同的原始名称：

- `userId` 可以匹配。
- `UserId`、`user_id`、`userId2` 均不能匹配。
- ` userId` 和 `userId ` 属于非法输入，不能通过裁剪变成合法名称。

接口配置入口校验参数名时只使用去除首尾空白后的结果识别非法输入，不使用该结果保存、匹配或同步。合法名称在所有后续链路中保持原值。

### 2.2 运行时模板同步

- 新增接口后，在同一事务内创建文档主记录并按模板顺序生成请求参数。
- 更新接口时，请求参数模板或请求方法发生变化即触发对账同步。
- 名称完全一致且区分大小写时视为同一参数。
- 新增参数的名称、位置、类型、必填性来自模板；示例值来自模板，说明使用待完善占位文案，排序使用模板顺序。
- 模板删除的参数执行逻辑删除。
- 名称改动按删除旧参数并新增新参数处理，不迁移旧说明、默认值、示例值、校验规则和排序。
- 请求方法变化只更新参数位置，保留说明性字段。
- 名称和类型未变化时保留说明性字段；必填性始终覆盖为模板值。
- 类型变化时保留说明和排序，清空默认值和校验规则，示例值重置为新模板值。
- 当前模板生成的请求参数均为必填参数，可选参数协议不在本轮范围内。

### 2.3 文档聚合保存

- 文档页不得新增、删除、改名或修改请求参数的名称、位置、类型、必填性。
- 后端保存前重新读取接口最新配置，并校验提交请求参数集合与运行时模板完整一致。
- 请求参数集合只允许 `QUERY` 或 `BODY`；出现 `HEADER` 直接拒绝。
- `paramScene` 和 `docStatus` 继续执行原始枚举值校验，不通过裁剪或大小写转换接受非法输入。
- 说明、默认值、示例值、校验规则和排序允许由文档页维护，且不得反向影响真实调用校验。

## 3. 后端设计

### 3.1 运行时模板校验器

新增 `RuntimeRequestParamTemplateValidator`，职责仅限于校验运行时模板的结构和参数名边界：

- 空值或空白模板表示无请求参数。
- 非空模板必须是合法 JSON 对象。
- 每个键必须非空、非全空白且不包含首尾空白。
- 校验失败返回参数错误，并包含具体键名或位置。

`InterfaceInfoServiceImpl.validInterfaceInfo` 在新增、更新入口调用该校验器；`InterfaceDocServiceImpl.buildRuntimeRequestParams` 也调用同一校验器，覆盖绕过 Controller 的 Service 直调路径。

### 3.2 对账与保存匹配

`InterfaceDocServiceImpl` 的对账映射和所有权校验移除名称上的 `trim()`：

- 现有文档参数映射键使用数据库原始 `name`。
- 保存请求映射键使用 DTO 原始 `name`。
- 运行时模板映射键使用 JSON 原始键。
- 任何映射、删除判定和相等比较均使用大小写敏感的原始字符串。

参数实体转换不再对请求参数名称做静默裁剪。响应字段的名称处理和本次请求参数所有权规则解耦，不扩大本轮行为变化。

### 3.3 生命周期与事务

保持现有职责边界：

```text
Controller
  -> InterfaceInfoService.validInterfaceInfo
  -> InterfaceInfoLifecycleService
       -> InterfaceDocService.syncRequestDocFromInterfaceInfo
```

新增、更新、文档同步在同一事务中完成。模板非法、数量超限、同步删除失败或新增失败时整体回滚，不能保留半完成状态。

## 4. 前端设计

`InterfaceConfigModal` 在提交新增或更新请求前解析运行时模板，并阻止包含空参数名、全空白键或首尾空白键的请求发送到后端。该校验只提供即时反馈，后端仍是最终权威。

`RequestParamDescriptionList` 保持现有只读身份展示，仅发出说明、示例值、默认值、校验规则和排序五类更新事件，不新增请求参数编辑入口。

## 5. 异常与安全处理

- 错误信息包含非法参数名称，避免只返回笼统的模板错误。
- 不接受通过首尾空白、大小写变化或相似名称绕过运行时参数所有权的请求。
- 不把文档说明性字段用于真实请求校验，避免文档维护反向改变调用行为。
- 不新增自定义业务 Header；初始化 SQL 不执行旧 `HEADER` 记录修复或迁移。

## 6. 测试策略

后端先编写失败测试，再实现最小改动：

- 模板参数名为空、全空白、带首空白、带尾空白时新增和更新均失败。
- 合法名称保持原值并成功生成结构化请求参数。
- 文档保存提交裁剪形式的名称时失败。
- 名称大小写变化按删除旧参数并新增新参数处理，旧说明不迁移。
- 模板删除和新增参数按完整对账执行。
- 请求方法变化更新参数场景并保留说明性字段。
- 类型变化继续清理失效字段并采用新模板示例值。
- `HEADER` 场景、名称缺失和请求参数伪造均被拒绝。
- 对账或保存任一子操作失败时事务回滚。

前端测试：

- 非法模板不会调用新增或更新接口。
- 合法模板照常提交。
- 请求参数说明组件不暴露名称、位置、类型和必填性的编辑事件。

## 7. 预计变更文件

后端：

- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/RuntimeRequestParamTemplateValidator.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceInfoServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceDocServiceImpl.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/component/RuntimeRequestParamTemplateValidatorTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/service/InterfaceInfoServiceImplValidTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/controller/InterfaceDocControllerTest.java`
- `sql/interface_doc.sql`

前端：

- `src/components/admin/InterfaceConfigModal.vue`
- `src/components/admin/__tests__/InterfaceConfigModal.test.ts`

交付后更新根目录 `doc/Feiapi平台接口文档能力实施进度.md` 的实际完成状态和验证结果。

## 8. 非目标

- 不支持可选运行时请求参数。
- 不引入稳定参数 ID、文档版本号或协同编辑锁。
- 不改变响应字段树规则、发布探测、接口删除生命周期和在线调试增强。
- 不迁移旧运行时 Header 数据，也不恢复自定义业务 Header。
- 不对响应字段名称或前端会话 `paramKey` 引入本轮请求参数所有权规则。
