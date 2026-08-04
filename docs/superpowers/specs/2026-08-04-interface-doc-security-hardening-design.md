# 阶段 2 接口文档安全补强设计

日期：2026-08-04

## 1. 背景

《Feiapi平台接口文档能力落地计划》的阶段 2.1 至 2.10 已完成。阶段 2.11 要求与后台结构化文档维护能力同时交付管理员权限、CSRF、协议 Header 收敛、错误码去重、内容类型白名单、示例数据安全、纯文本渲染和内部字段隔离等安全能力。

本轮不以计划条目是否存在判断完成状态，而是逐项核对当前后端、前端、数据库和部署代码。核对结果表明，大部分安全能力已经由前置整改或阶段 2 前序功能实现；本轮应修复实际缺口，并为既有能力补齐阶段 2.11 的专项验收证据，避免重复改造已经稳定运行的安全链路。

## 2. 目标

本轮完成以下目标：

1. 聚合保存接口在持久化前按大小写不敏感规则拒绝重复错误码，并保留管理员输入的原始大小写用于展示。
2. 前端在发送保存请求前执行相同的错误码重复检查，提前给出明确提示。
3. 公开备注、参数说明、校验规则、错误说明和解决建议统一拒绝高置信度内部实现信息。
4. 前端保存前校验并格式化非空 JSON 示例，所有字节和聚合载荷边界以格式化后的最终请求为准。
5. 使用专项回归测试证明管理员权限、CSRF、Cookie、Header、内容类型、内容安全、纯文本渲染和内部字段隔离继续有效。
6. 更新实施进度和接口文档，形成阶段 2.11 可追溯的代码、测试和文档证据。

## 3. 非目标

本轮不包含：

- 重新设计或替换现有 Spring Security CSRF 双提交令牌链路。
- 修改网关普通调用、SDK 签名或发布探测协议。
- 开放自定义业务 Header 或新增 Header 数据模型。
- 放宽手机号、邮箱、身份证号、凭据及内部实现信息检测规则。
- 新增数据库迁移脚本、历史数据修复脚本或旧数据兼容逻辑。
- 完成真实部署环境中贯穿 SDK、网关和真实下游的发布探测验证；该事项继续作为独立部署验收任务。
- 引入富文本、HTML 净化器或允许管理员保存 HTML 文档内容。

## 4. 现状核对

| 安全项 | 当前代码证据 | 本轮处理 |
| --- | --- | --- |
| 文档写接口管理员权限 | `InterfaceDocController.saveInterfaceDoc` 已使用 `@AuthCheck(mustRole = ADMIN)` | 保留实现，补权限回归证据 |
| 所有后台写请求 CSRF | `SecurityConfig` 已全局启用 `CookieCsrfTokenRepository`；前端 `http.ts` 已初始化并携带令牌 | 不重构，补接口文档写请求专项验证 |
| 生产 Cookie 属性 | Session Cookie 已配置 `HttpOnly`、`SameSite`、`Secure`；CSRF Cookie 按双提交协议使用 `HttpOnly=false` 且生产 `Secure=true` | 不改配置，执行配置和集成回归 |
| 自定义 Header 禁入 | `InterfaceDocParamSceneEnum` 不再接受 `HEADER`，Service 对非法场景直接拒绝 | 保留实现和现有测试 |
| 系统 Header 控制字符 | curl 生成器已拒绝 URL、路径和 Content-Type 中的控制字符 | 保留实现和生成器回归测试 |
| 错误码去重 | 保存 Service 当前只按区分大小写的字符串去重；发布检查已经按 `trim + Locale.ROOT 小写` 去重 | 修复保存链路，补前后端测试 |
| 内容类型白名单 | 前端使用固定下拉选项，后端使用 `SUPPORTED_CONTENT_TYPES` 最终校验 | 保留实现，补回归证据 |
| JSON 示例安全 | 前端能手动格式化且保存前校验语法；后端执行语法、64 层深度和敏感内容扫描 | 增加保存前自动格式化及最终载荷校验 |
| 模拟数据提示 | `JsonExampleEditor` 已提示“示例只能使用模拟数据或固定脱敏占位符” | 保留提示并测试 |
| 公开文案安全 | 后端已对所有公开文本执行敏感信息校验，但内部实现信息目前只在解决建议中校验 | 统一扩展高置信度内部信息规则，补充所有公开文本字段回归 |
| 纯文本渲染 | 生产前端未发现 `v-html`、`innerHTML` 等危险渲染，文档内容使用 Vue 文本插值 | 增加恶意标签不执行的组件测试 |
| 内部字段隔离 | Service 仅为管理员设置 `url`、`targetHost`，VO 使用 `NON_NULL` 省略空字段 | 增加有文档状态下的普通用户响应回归 |

## 5. 方案取舍

### 5.1 采用方案：聚焦缺口并补齐验收证据

只修改错误码保存去重、公开文本内部信息校验和前端保存前 JSON 处理，其他已经实现的安全能力保持不变，通过定向测试和全量回归证明其仍满足阶段 2.11。

优点：改动范围小，能够直接对应真实缺口，降低对既有认证、调用和发布链路的回归风险。

### 5.2 不采用方案：重构统一安全框架

重新抽象所有内容校验、渲染和权限组件可以提高形式上的集中度，但现有职责已经分别位于 Spring Security、AOP、Service 校验器和 Vue 默认文本渲染边界中。此时重构会扩大影响范围，且不能为阶段 2.11 带来等比例收益。

### 5.3 不采用方案：只补测试和文档

该方案无法解决保存 Service 与数据库、发布检查之间的错误码大小写规则不一致，也不能满足保存前自动格式化 JSON 示例的要求。

## 6. 错误码去重设计

### 6.1 比较规则

每个错误码先去除首尾空白，得到用于持久化和展示的值；再使用 `Locale.ROOT` 转为小写，得到仅用于重复判断的比较键：

```text
管理员输入        持久化与展示值      比较键
 A001             A001                a001
a001              a001                a001
 User_Not_Found   User_Not_Found      user_not_found
```

同一聚合保存请求中两个非空比较键相同，后端返回参数错误“同一接口的错误码不能重复”，不执行文档主记录、参数或错误码替换。

MySQL 部署使用 `utf8mb4_unicode_ci`，数据库唯一索引会对常见 ASCII 错误码执行大小写不敏感比较。应用层提前检查可避免把 `A001` 与 `a001` 的冲突推迟为底层唯一索引异常。数据库唯一索引继续作为最终一致性兜底。

`Locale.ROOT` 小写并不声称完整模拟 `utf8mb4_unicode_ci` 对所有 Unicode 字符的排序等价关系。本阶段计划只明确要求大小写不敏感；不额外收紧错误码为 ASCII，也不引入数据库专用批量比较查询。

### 6.2 后端保存流程

`InterfaceDocServiceImpl.buildErrorCodes` 保持现有验证顺序：

1. 校验错误码对象、必填项、长度和内容安全。
2. 对修剪后的错误码生成小写比较键。
3. 使用集合判断比较键是否重复。
4. 实体仍保存修剪后的原始大小写，不保存比较键，不新增数据库字段。
5. 全部错误码验证通过后才进入现有聚合全量替换事务。

严格发布检查已经使用相同规则，本轮不修改其生产实现，只补充或保留大小写变体回归测试，证明保存和发布门禁语义一致。

### 6.3 前端预校验

文档维护页在必填校验之后、构造 HTTP 请求之前，对所有错误码执行 `trim().toLowerCase()`，得到仅用于比较的稳定小写键。平台错误码比较不使用依赖浏览器当前语言环境的 `toLocaleLowerCase()`。

发现重复时显示“同一接口的错误码不能重复”，不调用保存接口。前端不改写管理员输入的大小写，后端仍是最终权威校验。

## 7. JSON 保存前处理设计

### 7.1 处理顺序

管理员点击“保存草稿”或“完成维护”时，前端按以下顺序处理：

1. 对成功、失败示例分别读取原始文本；空白示例保持为空。
2. 非空示例使用 `JSON.parse` 解析，解析失败时保留原文、显示具体字段错误并停止保存。
3. 解析成功后使用两空格缩进的 `JSON.stringify` 生成格式化文本，并回写当前编辑模型，让管理员看到实际提交内容。
4. 使用格式化后的示例重新执行单字段 65,535 UTF-8 字节限制。
5. 使用包含格式化示例的完整保存快照执行 1 MiB 聚合请求体限制。
6. 继续执行数量、普通文本、树结构、必填项和 `READY` 完整性校验。
7. 全部通过后才调用聚合保存接口。

格式化可能增加空白字符和最终字节数，因此不能先按原始文本通过边界校验，再发送更大的格式化报文。任何超限场景均明确失败，不截断、不压缩、不发送部分内容。

### 7.2 手动格式化兼容

现有两个“格式化”按钮继续保留，便于管理员在保存前主动检查示例。保存动作调用相同的格式化逻辑，避免手动格式化与保存前格式化产生不同结果。

保存成功后仍按现有流程重新读取后端权威数据。保存失败或其他表单规则失败时，已经成功格式化的 JSON 保留在编辑模型中，属于可见且可继续修改的未保存变更。

## 8. 权限、CSRF 与 Cookie

`POST /interfaceDoc/save` 继续由两层边界保护：

1. Spring Security CSRF 过滤器先校验 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` Header。
2. 请求通过 CSRF 后，`@AuthCheck` 再校验当前 Session 用户必须为管理员。

缺少、空值、伪造或不匹配的令牌必须在 Controller 和 Service 执行前返回 HTTP 403、业务码 `40300`。携带有效令牌的普通用户仍必须被管理员权限校验拒绝。

Session Cookie 继续使用 `HttpOnly=true`；CSRF Cookie 因双提交协议需要由 Axios 读取，继续使用 `HttpOnly=false`。两者在生产环境使用 `Secure=true`、`SameSite=Lax` 和 Host-only 边界。阶段 2.11 所称生产 Cookie 的 `HttpOnly` 要求不能错误地应用到需要前端读取的 CSRF Cookie。

本轮不修改 `SecurityConfig`、`http.ts` 或部署环境变量。测试发现现有实现与上述契约不符时，暂停当前实现并重新确认设计范围，不在本轮内临时扩张安全基础设施改动。

## 9. Header 与内容类型

- 聚合保存只接受 `QUERY`、`BODY`、`RESPONSE` 参数场景，任何 `HEADER` 或未知枚举均由后端拒绝。
- 前端保存快照不构造 Header 参数。
- `Content-Type` 只由文档主记录中的请求内容类型动态生成，不写入 `interface_doc_param`。
- 请求和响应内容类型继续由前端固定下拉选项选择，后端白名单执行最终校验。
- curl 示例继续拒绝 URL、路径和 Content-Type 中的 NUL、CR、LF 及其他受控字符。
- `accessKey`、`nonce`、`timestamp` 和 `sign` 继续由平台调用链动态生成，文档维护请求不能提供这些系统 Header。

本轮不修改网关、SDK 或 Header 生成生产代码，现有生成器测试作为验收证据的一部分执行。

## 10. 内容安全与纯文本渲染

### 10.1 后端最终校验

现有 `InterfaceDocContentSecurityValidator` 已覆盖：

- JSON 语法和 64 层扫描深度。
- Unicode NFKC 标准化后的敏感字段名。
- 手机号、邮箱、身份证号、凭据和认证方案。
- 固定脱敏占位符。
- 校验规则中的脚本内容。
- 解决建议中的部分数据库、中间件和服务器路径信息。

本轮将内部实现信息校验收敛到普通公开文本校验路径，使文档备注、参数默认值、示例值、参数说明、校验规则、错误码、错误信息、错误说明和解决建议均执行相同的高置信度规则。至少拒绝：

- 数据库和中间件名称、JDBC 等基础设施标记。
- Windows、Linux 常见服务器绝对路径。
- “内部路由”“内网地址”“真实后端地址”、`targetHost`、`upstream` 等明确内部路由标记。
- “异常堆栈”“stack trace”“SQLSTATE”等明确内部排查信息。

规则保持高置信度，不因普通的“异常”“路径”“缓存”等泛化业务词直接拒绝公开文案。`validateSolution` 保留现有公开方法，但复用统一公开文本校验，避免解决建议与其他公开字段产生不同安全语义。

前端提示只改善录入体验，不能替代后端安全门禁。

### 10.2 前端渲染

管理员维护页和公开接口文档继续使用 Vue 文本插值、输入框值绑定及 `<pre>` 文本内容展示，不使用 `v-html`、`innerHTML`、`outerHTML` 或等价未净化 HTML 注入 API。

组件测试使用包含 `<script>`、`<img onerror>` 等文本的模拟响应，断言页面显示字面文本，同时 DOM 中不存在由输入内容创建的脚本或图片元素。测试验证渲染边界，不代表后端允许保存这些内容；生产保存仍会先被内容安全校验拒绝。

## 11. 普通用户字段隔离

聚合详情继续使用专用 `InterfaceDocInterfaceInfoVO`。Service 只有在管理员上下文中才设置 `url` 和 `targetHost`，字段级 `JsonInclude.NON_NULL` 负责让普通用户响应完全省略这两个属性，而不是返回 `null`、空字符串或脱敏字符串。

专项集成测试使用已经存在结构化文档且状态为 `READY` 的在线接口，验证普通用户响应包含公开 `docStatus` 和文档内容，但 `interfaceInfo` 中不存在 `url`、`targetHost` 以及旧运行时 Header 字段。这样可以证明新增文档状态不会绕过已有字段隔离。

## 12. 错误处理与事务

- 前端重复错误码、非法 JSON 或格式化后超限：页面显示明确错误，不发送请求。
- 后端重复错误码：返回业务参数错误，不暴露索引名、SQL 或数据库异常。
- 后端权限失败：沿用统一未登录或无权限响应。
- CSRF 失败：沿用 HTTP 403、业务码 `40300`，不自动重放原写请求。
- 内容安全失败：沿用明确且不包含敏感原文的参数错误。
- 聚合保存任一校验或持久化步骤失败：现有 Service 事务整体回滚，旧文档、参数和错误码保持不变。

不捕获并吞掉数据库唯一索引异常，也不把数据库异常转换为“保存部分成功”。应用层预校验和数据库唯一索引共同构成防线。

## 13. 测试设计

### 13.1 后端定向测试

- `A001` 与 `a001`、带首尾空白的大小写变体在同一请求中保存失败。
- 单个混合大小写错误码保存成功，回读保持修剪后的原始大小写。
- 重复错误码失败时已有文档和错误码不被清空。
- 发布检查对大小写变体返回 `ERROR_CODE_DUPLICATED`，与保存门禁一致。
- 未携带 CSRF 令牌的接口文档保存请求在业务执行前返回 `40300`。
- 有效 CSRF 令牌不能替代管理员权限，普通用户保存仍失败。
- 普通用户读取已有结构化文档时完全省略 `url`、`targetHost` 和旧 Header 字段。
- 备注、参数说明、校验规则、错误说明和解决建议中的高置信度内部实现信息均保存失败。
- “异常处理建议”“请求路径说明”等不包含内部细节的正常公开文案仍可保存，防止规则过宽。
- 继续执行自定义 `HEADER`、内容类型白名单、内容安全、64 层深度和 curl 控制字符现有测试。

### 13.2 前端定向测试

- 错误码完全相同、仅大小写不同或仅首尾空白不同均阻止保存。
- 重复提示后管理员原始大小写不被改写。
- 点击保存时成功、失败 JSON 均自动格式化后提交。
- 任一 JSON 非法时保留原值且不发送保存请求。
- 格式化后单字段或聚合载荷超限时不发送请求。
- JSON 示例区域继续显示模拟数据与固定脱敏占位符提示。
- 内容类型只能通过固定下拉选项选择。
- 恶意标签和事件属性以文本显示，不生成可执行 DOM。
- 继续执行统一 Axios CSRF、接口文档维护页和浏览器 E2E 现有测试。

### 13.3 全量验证

- 后端定向 JUnit 5 测试和 Maven 聚合全量测试。
- 核心 Service 测试继续使用 AssertJ，并核对核心分支覆盖率。
- 前端定向 Vitest、全量 Vitest、TypeScript 类型检查、ESLint 和生产构建。
- 接口管理与文档维护 Playwright E2E。
- 后端、前端分别执行 `git diff --check` 和工作区状态检查。

真实 Compose 环境的 CSRF/Cookie 验证已有前置整改证据，且本轮不修改相应生产配置，因此本轮不重复执行完整 Compose 安全验证。

## 14. 预计改动文件

### 14.1 后端仓库

生产代码预计修改：

- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceDocServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/component/InterfaceDocContentSecurityValidator.java`

测试预计修改：

- `feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/controller/InterfaceDocControllerTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/security/CsrfSecurityIntegrationTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/service/InterfacePublishCheckServiceImplTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/component/InterfaceDocContentSecurityValidatorTest.java`

- `feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/service/InterfaceDocServiceImplTest.java`

明确不预计修改：

- `sql/interface_doc.sql`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/config/SecurityConfig.java`
- `feiapi-client-sdk`
- `feiapi-gateway`

### 14.2 前端仓库

生产代码预计修改：

- `src/views/admin/InterfaceDocMaintenanceView.vue`

测试预计修改：

- `src/views/admin/__tests__/InterfaceDocMaintenanceView.test.ts`
- `src/components/admin/doc/__tests__/JsonExampleEditor.test.ts`
- `src/components/interface/__tests__/InterfaceDocumentation.test.ts`

- `tests/e2e/fixtures/apiMock.ts`
- `tests/e2e/interface-management.spec.ts`

### 14.3 工作区文档

实现完成并取得测试证据后更新：

- `doc/Feiapi平台接口文档能力实施进度.md`
- `doc/后端接口文档.md`
- `doc/后端开发文档.md`

落地计划中的业务规则不变，因此不预计修改 `doc/Feiapi平台接口文档能力落地计划.md`。若实现核对发现必须改变规则，应先更新计划并重新取得确认。

## 15. Git 与交付

本设计文档在后端 `dev` 派生的临时 feature 分支中完成，使用中文提交说明并按仓库规范合并回 `dev` 后删除设计分支。

用户审核本设计文档通过后，业务实现阶段分别执行：

1. 后端从最新 `dev` 创建 `feature/interface-doc-security-hardening`。
2. 前端从最新 `dev` 创建同名 feature 分支。
3. 部署仓库预计不修改，不预先创建分支。
4. 前后端各自在 feature 分支完成代码和测试。
5. 实现提交使用中文说明及 `feat:`、`fix:`、`test:` 或 `docs:` 前缀。
6. 验证通过后合并到各自 `dev`，删除本地 feature 分支，不自动推送远端。

任何超出第 14 节的生产文件改动都必须先说明安全原因和影响范围，不顺带重构无关代码。

## 16. 验收标准

阶段 2.11 在以下条件全部满足后判定完成：

- 保存和发布检查均按大小写不敏感方式识别重复错误码，原始大小写正常展示。
- 前端在请求前拒绝重复错误码、非法 JSON 和格式化后超限报文。
- 成功、失败 JSON 示例在实际保存请求中均为格式化后的合法 JSON。
- 文档保存继续同时受有效 CSRF 令牌和管理员权限保护。
- 自定义 Header、非法内容类型、敏感内容、内部实现信息和控制字符继续被后端拒绝。
- 恶意 HTML 内容不会在管理员页面或公开文档页面中作为 HTML 执行。
- 普通用户聚合详情完全省略 `url`、`targetHost` 等管理员专用字段。
- 后端、前端定向测试和全量质量门禁通过，进度文档记录实际测试数量和验证结果。
