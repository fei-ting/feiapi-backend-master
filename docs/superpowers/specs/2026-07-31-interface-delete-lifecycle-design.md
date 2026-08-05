# 接口删除生命周期设计

## 1. 背景

阶段 2.1 至 2.8 已完成接口文档维护入口、文档状态、完整性校验、表单拆分、请求参数所有权、响应字段树、聚合全量替换以及数量和报文大小边界。当前删除入口已经具备管理员权限校验、接口行锁和 `OFFLINE` 状态门禁，但只逻辑删除 `interface_info` 主记录，尚未形成完整生命周期：

- 接口文档主记录、文档参数和接口级错误码没有随接口一并删除。
- `interface_info.is_delete` 仍使用固定值 `1`，相同路径和请求方法无法稳定地反复创建、删除。
- 当前排行榜先选取额度关系前三名再过滤已删除接口，可能被历史接口占用名额。
- 在途请求失败时，额度补偿依赖只能查询未删除接口的配额类型，接口删除后可能无法返还预扣额度。
- 前端删除确认只展示接口名称，没有同时展示请求方法和路径。

本设计只落实落地计划 2.9，不增加回收站、恢复接口、物理删除、历史数据迁移或阶段 2.10 的严格发布门禁。

## 2. 目标与非目标

### 2.1 目标

- 只有管理员可以删除接口，且数据库最新状态必须为 `OFFLINE`。
- 在同一事务中逻辑删除接口主记录、文档主记录、文档参数和接口级错误码。
- 删除标识保存当前记录 ID，使同一路径和请求方法可以反复创建、删除。
- 文档关联记录不存在时按空集合处理，不阻止接口删除。
- 保留用户额度关系和调用日志，不重置历史额度、调用次数或日志。
- 已删除接口不出现在接口列表、接口广场、个人额度页、当前排行榜和当前接口统计中。
- 平台级历史调用总量继续包含已删除接口的历史日志。
- 删除前已经通过网关并完成预扣的请求，即使接口随后被删除，失败时仍能返还额度。
- 前端删除确认展示接口名称、请求方法、路径和不可恢复提示。

### 2.2 非目标

- 不提供回收站、恢复按钮、恢复接口或管理员绕过删除状态的查询入口。
- 不删除或重置 `user_interface_info`。
- 不删除 `interface_invoke_log`，不改变平台统一日志保留策略。
- 不让重新创建的接口继承旧接口 ID、文档、额度关系或调用统计。
- 不修改网关路由、签名、nonce、限流或预扣流程。
- 不把重复删除改为幂等成功；首次删除后再次删除同一 ID 返回接口不存在。
- 不编写历史数据库迁移脚本；开发环境继续通过空数据库执行最新完整初始化 SQL。

## 3. 删除状态与接口契约

删除入口继续使用 `POST /interfaceInfo/delete` 和现有 ID 请求结构，不新增独立删除协议。

删除流程必须按数据库最新记录判断：

1. 校验接口 ID 为正数。
2. 使用 `SELECT ... FOR UPDATE` 锁定 `is_delete = 0` 的接口主记录。
3. 记录不存在时返回数据不存在，包括从未存在和已经删除两种场景。
4. 状态为 `ONLINE` 时返回“请先下线接口后再删除”。
5. 状态为 `PUBLISHING` 时返回“接口正在发布验证中，不能删除”。
6. 只有状态精确等于 `OFFLINE` 时进入聚合删除。

Controller 只负责管理员权限、请求校验和响应包装。状态判断、事务边界和全部删除操作继续位于 `InterfaceInfoLifecycleService`。

## 4. 删除标识设计

### 4.1 接口主记录

`interface_info.is_delete` 从 `TINYINT` 改为 `BIGINT`，Java 实体字段从 `Integer` 改为 `Long`，并使用：

```java
@TableLogic(value = "0", delval = "id")
private Long isDelete;
```

`value = "0"` 表示有效记录。`delval = "id"` 中的 `id` 是数据库字段表达式，逻辑删除 SQL 的效果为：

```sql
UPDATE interface_info
SET is_delete = id
WHERE id = ?
  AND is_delete = 0;
```

删除标识必须使用 `BIGINT` 和 `Long`，因为其保存的是 `BIGINT` 类型主键，不能使用 `TINYINT` 或 `Integer` 缩小取值范围。

### 4.2 唯一索引

现有唯一索引 `uk_interface_info_path_method_delete(path, method, is_delete)` 保持不变。有效记录的 `is_delete` 固定为 `0`，因此同一路径和请求方法最多只有一条有效记录；历史记录的删除标识分别为各自主键，不会在第二次或后续删除时与旧历史记录冲突。

例如相同 `POST /api/user` 反复创建、删除后允许形成：

| id | path | method | is_delete | 状态 |
| ---: | --- | --- | ---: | --- |
| 101 | `/api/user` | `POST` | 101 | 已删除 |
| 102 | `/api/user` | `POST` | 102 | 已删除 |
| 103 | `/api/user` | `POST` | 0 | 当前有效 |

### 4.3 文档关联记录

`interface_doc`、`interface_doc_param` 和 `interface_doc_error_code` 已使用 `BIGINT is_delete` 以及 `@TableLogic(value = "0", delval = "id")`，本轮沿用该语义，不改变字段结构。

MyBatis-Plus 管理的逻辑删除操作使用实体注解生成删除表达式。手写 XML 查询不能假设注解会自动追加过滤条件，所有当前数据查询必须显式限制 `is_delete = 0`；只有在途额度补偿的历史配额查询允许有意读取已删除接口。

## 5. 聚合删除事务

生命周期 Service 在持有接口主记录行锁后，按以下顺序执行：

1. 逻辑删除该接口的全部有效文档参数。
2. 逻辑删除该接口的全部有效接口级错误码。
3. 逻辑删除该接口的有效文档主记录。
4. 使用接口 ID、`OFFLINE` 状态和 `is_delete = 0` 条件逻辑删除接口主记录。

文档参数、错误码或文档主记录不存在时，受影响行数为零属于合法空集合，不抛出异常。接口主记录在已持有行锁后必须且只能成功删除一行；条件删除失败表示状态或数据发生异常，抛出业务异常。

方法使用 `@Transactional(rollbackFor = Exception.class)`。任一数据库语句抛出异常或接口主记录条件删除失败时，前面已经执行的文档逻辑删除全部回滚。删除顺序与现有生命周期固定锁顺序保持一致：先锁接口主记录，再访问文档记录，避免与文档保存、接口更新和发布流程形成反向锁顺序。

删除操作不触碰 `user_interface_info` 和 `interface_invoke_log`。

## 6. 在途请求与额度补偿

接口只能在下线后删除，但下线前已经通过网关鉴权、路由和预扣的请求可能仍在下游执行。该请求失败时，网关继续使用原接口 ID 调用 `rollbackInvokeCount`。

正常调用相关方法保持现有有效数据边界：

- `leftNumIsEnough` 和 `invokeCount` 只能根据未删除接口判断配额类型。
- 已删除接口不能发起新的调用或产生新的预扣。

仅额度补偿路径增加 Mapper 历史查询，按接口 ID 读取 `quota_type`，不增加 `is_delete = 0` 条件。`rollbackInvokeCount` 使用该查询区分有限额度和无限额度：

- 有限额度接口：`left_num + 1`，`total_num - 1`。
- 无限额度接口：只执行 `total_num - 1`。

额度关系仍要求 `user_interface_info.is_delete = 0`，且 `total_num > 0`，避免重复补偿和负数。历史接口记录实际不存在或配额类型非法时明确失败并记录错误，不使用可能导致错误额度变化的静默兜底。

网关已经持有接口快照并继续使用旧接口 ID、路径和方法记录调用日志，因此接口删除后，在途请求仍允许写入旧接口 ID 的成功或失败日志。

## 7. 当前视图与历史统计

### 7.1 当前接口视图

接口列表、接口广场和个人额度页继续以未删除 `interface_info` 为驱动表。MyBatis-Plus 实体查询自动过滤逻辑删除记录，自定义 SQL 必须显式添加 `interface_info.is_delete = 0`。

重新创建相同路径和方法时会生成新接口 ID。个人额度页只为新 ID 展示或初始化额度关系，不读取旧 ID 的历史关系。

### 7.2 当前排行榜

排行榜查询必须在排序和 `LIMIT` 之前关联有效接口：

```sql
SELECT uii.interface_info_id, SUM(uii.total_num) AS total_num
FROM user_interface_info uii
JOIN interface_info ii
  ON ii.id = uii.interface_info_id
 AND ii.is_delete = 0
WHERE uii.is_delete = 0
GROUP BY uii.interface_info_id
HAVING SUM(uii.total_num) > 0
ORDER BY total_num DESC
LIMIT ?;
```

这样已删除的高调用量接口不会占用前三名名额，接口存在性过滤也不再推迟到 Controller 查询后处理。

### 7.3 历史调用统计

`interface_invoke_log` 不随接口删除。平台级历史调用总量、成功率和响应时间统计继续按日志口径计算，可以包含已删除接口的历史调用。

当前接口数量只能统计 `is_delete = 0` 的接口。需要展示当前接口名称、排行榜或当前接口维度统计的查询必须关联有效接口；纯历史日志统计可以继续按旧接口 ID、路径和请求方法聚合。

## 8. 前端交互

接口管理页继续只允许对 `OFFLINE` 接口启用删除操作。`ONLINE` 和 `PUBLISHING` 的前端禁用或提示仅用于提升体验，后端状态门禁保持最终权威。

删除确认内容必须同时包含：

- 接口名称。
- 请求方法。
- 网关路径。
- “删除后不可恢复”的明确提示。

确认后只提交接口 ID。删除期间沿用页面现有请求状态处理；后端拒绝状态已变化、接口已删除或数据库操作失败时，展示后端具体消息并重新加载列表。成功后提示“接口已删除”并刷新当前页。

阶段 2 不增加回收站入口、恢复按钮、历史接口详情页或批量删除。

## 9. 错误处理与安全

- 删除接口继续要求后端 `@AuthCheck` 管理员权限和 CSRF 校验，不能依赖前端隐藏按钮。
- ID 非法返回参数错误，不进入数据库删除事务。
- 接口不存在或已删除返回数据不存在，不把重复删除伪装为成功。
- `ONLINE` 和 `PUBLISHING` 返回不同的可操作提示，不自动下线或等待发布完成。
- 删除接口不接收名称、方法、路径或状态等客户端快照作为可信条件，所有判断读取数据库最新记录。
- 错误日志只记录接口 ID、删除阶段和异常，不输出接口正文、凭据或内部地址。
- 不提供通过通用业务查询读取已删除接口的能力；补偿专用 Mapper 方法只返回所需配额类型。

## 10. 测试设计

### 10.1 删除状态和权限

- 普通用户删除返回无权限，数据保持不变。
- `OFFLINE` 接口删除成功。
- `ONLINE` 接口删除失败并提示先下线。
- `PUBLISHING` 接口删除失败并提示正在发布验证。
- 非法 ID 返回参数错误。
- 删除不存在或已删除 ID 返回数据不存在。

### 10.2 聚合删除与事务

- 删除具有文档主记录、多个请求/响应参数和多个错误码的接口，四类有效数据均不可通过业务查询读取。
- 使用原生 SQL 验证四类历史记录的 `is_delete` 分别等于各自主键。
- 文档主记录、参数或错误码部分缺失以及全部缺失时，接口仍可删除。
- 模拟文档删除阶段抛出异常，验证先前已经执行的逻辑删除与接口主记录全部回滚。
- 删除时不修改或删除用户额度关系和调用日志。

### 10.3 重复创建和删除

- 创建接口 A、删除 A、以相同路径和方法创建接口 B、删除 B、再次创建接口 C。
- 三个接口 ID 必须不同，两条历史记录分别保存自身 ID 作为删除标识，接口 C 保持 `is_delete = 0`。
- A 或 B 的额度关系、文档和日志不被 C 继承。

### 10.4 在途额度补偿

- 有限额度接口预扣后逻辑删除，失败补偿仍恢复 `left_num` 并减少 `total_num`。
- 无限额度接口预扣后逻辑删除，失败补偿只减少 `total_num`。
- 已删除接口不能开始新的额度检查或预扣。
- 重复补偿不产生负数或多返额度。
- 补偿完成后仍可按旧接口 ID 写入调用日志。

### 10.5 当前视图和历史统计

- 已删除接口不出现在管理列表、接口广场和个人额度页。
- 已删除的高调用量接口不进入排行榜，也不占用前三名名额。
- 重新创建的接口按新 ID 进入个人额度页和排行榜。
- 当前接口数量排除已删除接口。
- 平台历史调用总量继续包含删除前及在途请求产生的旧接口日志。

### 10.6 前端

- `OFFLINE` 接口可以打开删除确认。
- 确认文本包含名称、请求方法、路径和不可恢复提示。
- 取消确认不发送请求。
- 确认后只提交 ID，成功刷新列表。
- 后端返回状态变化、数据不存在或删除失败时展示具体错误。
- `ONLINE`、`PUBLISHING` 不会绕过前端状态提示发起删除。

## 11. 文件范围

后端预计修改：

- `feiapi-common/src/main/java/com/feiting/feiapicommon/model/entity/InterfaceInfo.java`
- `sql/interface_info.sql`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/mapper/InterfaceInfoMapper.java`
- `feiapi-backend-server/src/main/resources/mapper/InterfaceInfoMapper.xml`
- `feiapi-backend-server/src/main/resources/mapper/UserInterfaceInfoMapper.xml`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/InterfaceInfoLifecycleServiceImpl.java`
- `feiapi-backend-server/src/main/java/com/feiting/feiapi/service/impl/UserInterfaceInfoServiceImpl.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/controller/InterfaceInfoControllerTest.java`
- `feiapi-backend-server/src/test/java/com/feiting/feiapi/integration/controller/AnalysisControllerTest.java`
- 用户额度补偿和删除事务相关测试类

前端预计修改：

- `src/views/admin/InterfaceManagementView.vue`
- `src/views/admin/__tests__/InterfaceManagementView.test.ts`

实现完成后同步更新工作区根目录：

- `doc/Feiapi平台接口文档能力实施进度.md`
- `doc/后端接口文档.md`
- `doc/后端开发文档.md`

如实现中发现必须修改本范围之外的生产文件，应先说明原因，不顺带重构无关代码。

## 12. Git 与验证

后端和前端分别从各自 `dev` 创建 `feature/interface-delete-lifecycle`。设计文档先在后端功能分支使用中文 `docs:` 提交；用户复核设计文档后再进入实现。

实现与测试完成后：

1. 后端和前端分别使用中文 `feat:` 或 `fix:` 前缀提交功能代码。
2. 分别将功能分支合并回各自 `dev`。
3. 验证合并结果后删除两个功能分支。
4. 不修改长期维护的 `main` 分支。

验证至少包括：

- 后端删除、额度补偿、排行榜、当前统计相关定向测试。
- 后端 Maven 聚合测试及核心模块覆盖率核对。
- 前端管理页定向测试、全量单元测试、类型检查、ESLint 和生产构建。
- 两个仓库执行 `git diff --check` 和工作区状态核对。
