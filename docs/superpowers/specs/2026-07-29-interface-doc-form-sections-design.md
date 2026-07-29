# FeiAPI 后台文档表单拆分与 Java SDK 示例设计

## 1. 背景与目标

阶段 2 的 2.1 至 2.3 已形成独立维护入口、文档状态和草稿/完成维护校验。当前维护页也已按稳定业务区块拆分为 Vue 子组件，但 2.4 仍有以下缺口：

- 接口摘要未展示接口描述。
- 维护页未展示由请求格式派生的只读 `Content-Type`。
- 后端和数据库仍保留接口级 `authDescription`，详情页仍展示该字段。
- 聚合详情只生成 curl 示例，没有 Java SDK 示例。
- 详情页没有以 Java SDK 为首选、curl 为备用的示例切换能力。

本轮目标是补齐上述缺口，不改变 2.1 至 2.3 已确认的保存、状态、权限和事务语义，不提前实现 2.6 的响应字段子树删除与提升交互。

## 2. 业务规则与验收条件

1. 接口摘要只读展示名称、描述、请求方法、网关路径、配额类型、SDK 方法名、接口状态和文档状态。
2. 文档主信息只维护版本、请求格式、响应格式和公开备注，不接收接口级鉴权说明。
3. 维护页只读展示一个系统 Header：名称为 `Content-Type`，值实时跟随当前表单的 `requestContentType`。
4. 管理员不能新增、修改或删除业务 Header，聚合保存继续拒绝 `HEADER` 场景参数。
5. 聚合详情不再返回 `authDescription`，最新 MySQL 和测试 H2 表结构不再包含 `auth_description`。
6. 聚合详情新增非空 `javaSdkExample`。系统根据已注册 SDK 方法和结构化请求参数动态生成，不允许管理员保存或修改该示例。
7. Java 示例使用 `System.getenv("FEIAPI_ACCESS_KEY")` 和 `System.getenv("FEIAPI_SECRET_KEY")`，不得出现真实访问凭据。
8. 无参 SDK 方法生成直接调用；需要参数的方法生成 JSON 请求字符串并调用对应 SDK 方法。
9. 请求示例优先使用结构化参数的 `exampleValue`，其次使用 `defaultValue`，最后按类型生成安全占位值。
10. Java 字符串和 JSON 内容必须通过结构化序列化与 Java 字面量转义生成，不能直接拼接未经处理的文档输入。
11. SDK 方法不存在、没有合法 `@SdkInvoke` 契约或签名不受支持时，示例生成失败，并返回明确业务错误，不能静默生成误导代码。
12. 接口详情页的调用示例使用标签切换，默认展示 Java SDK，curl 作为备用；两者均支持复制和空状态。

## 3. 后端设计

### 3.1 Java SDK 示例生成器

新增 `InterfaceDocJavaSdkExampleGenerator`，职责仅限于把接口配置和结构化请求参数转换为可公开展示的 Java 示例。

生成器依赖 `SdkMethodRegistry` 获取已注册方法及其 `@SdkInvoke` 契约，不重复扫描 `FeiApiClient`。支持两类当前真实 SDK 签名：

- `needParams=false` 且方法无参数：生成 `client.methodName()`。
- `needParams=true` 且方法接收一个 `String`：生成结构化 JSON 请求字符串，再生成 `client.methodName(requestParam)`。

其他签名按不支持处理。示例固定使用 `FeiApiClient`，通过环境变量读取占位凭据，不接受用户凭据作为生成参数。

请求 JSON 按请求参数的 `sortOrder` 和稳定次序构建。值转换规则如下：

| 参数类型 | 文档值存在 | 文档值缺失 |
| --- | --- | --- |
| `string` | 保持字符串 | `"示例文本"` |
| `number` | 合法数字转换为 JSON 数字 | `0` |
| `boolean` | `true`/`false` 转换为 JSON 布尔值 | `false` |
| `object` | 合法 JSON 对象保留 | `{}` |
| `array` | 合法 JSON 数组保留 | `[]` |

类型与示例值不匹配时不猜测转换，按参数错误拒绝生成，避免文档进入 `READY` 后仍展示不可执行示例。

### 3.2 聚合查询

`InterfaceDocDetailVO` 增加 `javaSdkExample`。`InterfaceDocServiceImpl.getDocDetail` 在结构化参数解析完成后调用 Java SDK 示例生成器，并继续调用现有 curl 生成器。

示例生成只读取接口配置和结构化请求参数，不访问用户凭据，不增加数据库查询。

### 3.3 鉴权说明收敛

移除以下接口级字段：

- `InterfaceDocSaveRequest.authDescription`
- `InterfaceDocVO.authDescription`
- `InterfaceDoc.authDescription`
- MySQL `interface_doc.auth_description`
- H2 测试表 `interface_doc.auth_description`

同时删除初始化默认值、内容安全校验输入、保存映射和聚合映射。平台鉴权由 SDK 和网关统一处理，不再以接口文档正文形式维护。

## 4. 前端设计

### 4.1 维护页

`InterfaceDocSummary` 增加接口描述只读展示。

新增 `SystemRequestHeaderSummary` 子组件，位于文档主信息和请求参数之间。组件只接收 `requestContentType`，展示：

- 名称：`Content-Type`
- 类型：`string`
- 必填：是
- 值：当前请求格式
- 说明：由系统根据请求内容类型自动生成

组件不提供输入框、增加按钮或删除按钮。请求格式变化后，该区立即更新，但仍只在管理员执行保存时持久化文档主信息。

已有请求参数、响应字段、JSON 示例和错误码组件保持现有职责。本轮不调整响应字段删除策略。

### 4.2 接口详情页

删除基础信息中的“鉴权说明”。

`InterfaceDocumentation` 将单一 curl 区改为调用示例区：

- 标签一：`Java SDK`，默认选中。
- 标签二：`curl`。
- 复制按钮复制当前标签的示例。
- 当前示例为空时展示对应空状态，不允许复制空文本。

在线调试页继续使用公共文档组件的紧凑模式；紧凑模式不新增示例标签，避免扩大在线调试布局变更。Java SDK 首选展示仅在详情模式生效。

## 5. 数据流

```text
interface_info.sdk_method_name
        +
结构化请求参数
        |
        v
InterfaceDocJavaSdkExampleGenerator
        |
        v
InterfaceDocDetailVO.javaSdkExample
        |
        v
InterfaceDocumentation 详情模式
        |
        +--> Java SDK（默认）
        +--> curl（备用）
```

维护页的 Header 数据流独立于保存参数：

```text
DocumentMainInfoForm.requestContentType
        |
        v
SystemRequestHeaderSummary（只读派生）
```

## 6. 异常与安全处理

- SDK 方法不存在或契约非法时，后端返回业务错误，不生成猜测性示例。
- 参数示例不符合声明类型时，后端返回包含参数名称的明确错误。
- JSON 使用 Gson 结构化构建，Java 源码文本执行独立字符串转义。
- 示例只能出现环境变量名和固定占位值，不读取登录用户的 AK/SK。
- 前端使用 Vue 文本插值和 `<pre>` 展示代码，不使用 `v-html`。
- 移除鉴权说明后，不保留兼容读取、隐藏提交或旧字段兜底。

## 7. 测试策略

后端先编写失败测试，再完成最小实现：

- 无参 SDK 方法示例。
- 带参数 SDK 方法示例及各 JSON 类型。
- 示例值、默认值和类型占位值优先级。
- Java 字符串转义和凭据占位符。
- 非法数字、布尔值、对象、数组和不支持 SDK 签名。
- 聚合详情返回 Java 示例且不返回鉴权说明。
- 保存请求不再接受或持久化鉴权说明。
- MySQL/H2 表结构与实体保持一致。

前端测试：

- 摘要展示接口描述。
- Header 区只读展示并随请求格式变化。
- 维护页包含 Header 区且保存载荷不包含 Header。
- 详情页不再展示鉴权说明。
- Java SDK 默认选中，curl 可切换，两种示例复制正确。
- 空示例状态和复制按钮禁用。
- 既有维护页、详情页和在线调试页回归。

验证命令：

```text
.\mvnw.cmd -pl feiapi-backend-server test
npm test
npm run lint
npm run typecheck
npm run build
```

前端完成后使用桌面和移动视口检查维护页与详情页，确认标签、代码块和 Header 区无重叠、溢出或布局跳动。

## 8. 预计变更文件

后端：

```text
feiapi-backend-server/src/main/java/com/feiting/feiapi/component/InterfaceDocJavaSdkExampleGenerator.java
feiapi-backend-server/src/main/java/com/feiting/feiapi/model/dto/interfaceDoc/InterfaceDocSaveRequest.java
feiapi-backend-server/src/main/java/com/feiting/feiapi/model/entity/InterfaceDoc.java
feiapi-backend-server/src/main/java/com/feiting/feiapi/model/vo/InterfaceDocDetailVO.java
feiapi-backend-server/src/main/java/com/feiting/feiapi/model/vo/InterfaceDocVO.java
feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceDocServiceImpl.java
feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/component/InterfaceDocJavaSdkExampleGeneratorTest.java
feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/controller/InterfaceDocControllerTest.java
feiapi-backend-server/src/test/resources/schema-h2.sql
sql/interface_doc.sql
```

前端：

```text
src/components/admin/doc/InterfaceDocSummary.vue
src/components/admin/doc/SystemRequestHeaderSummary.vue
src/components/admin/doc/__tests__/InterfaceDocSummary.test.ts
src/components/admin/doc/__tests__/SystemRequestHeaderSummary.test.ts
src/components/interface/InterfaceDocumentation.vue
src/components/interface/__tests__/InterfaceDocumentation.test.ts
src/types/interfaceDoc.ts
src/views/InterfaceDetailView.vue
src/views/__tests__/InterfaceDetailView.test.ts
src/views/admin/InterfaceDocMaintenanceView.vue
src/views/admin/__tests__/InterfaceDocMaintenanceView.test.ts
src/styles/features/interface-documentation.css
src/styles/pages/admin-tools.css
```

根目录进度文档在功能和验证完成后更新，不提前宣告完成。

## 9. 非目标

- 不实现 2.6 的非叶子响应字段删除整个子树或提升直接子字段交互。
- 不实现发布前检查器、删除生命周期或发布探测补强。
- 不增加 Node、Python 或其他语言示例。
- 不增加示例持久化字段、缓存或管理员自定义代码编辑器。
- 不兼容旧数据库字段；开发环境按项目约定使用最新完整 SQL 初始化空数据库。
