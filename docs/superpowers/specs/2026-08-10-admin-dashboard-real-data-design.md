# 管理员工作台真实数据接入设计

## 目标

将管理员后台工作台从前端静态/Mock 状态改为真实数据库驱动，覆盖概览统计、最近 24 小时运行趋势、重点关注接口和最近变更；前端不保留任何工作台业务 Mock 数据。

## 现状与问题

- 前端请求 `/analysis/dashboard/overview`、`/trends`、`/alerts`、`/changes`，后端目前没有对应路由。
- `interface_info` 和 `interface_invoke_log` 已具备概览、趋势和异常计算所需的基础数据。
- 现有接口记录只有创建时间、更新时间和当前状态，不能可靠还原上线、下线、修改历史。
- 前端工作台包含 `dashboardMock.ts` 及 E2E Mock 分支，会掩盖真实接口缺失。

## 方案选择

采用四个独立工作台接口，保持现有前端服务边界。每个区块独立查询、独立失败，不引入大聚合响应；同时新增接口变更审计表，保证最近变更来自真实事件记录。

不采用单一聚合接口，原因是任一查询失败会影响整个页面，且需要重构现有前端契约；不采用前端自行聚合，原因是无法准确计算历史变更并会扩大数据暴露范围。

## API 契约

所有接口位于 `AnalysisController`，使用 `@AuthCheck(mustRole = ADMIN)`。

### 概览

`GET /analysis/dashboard/overview`

- `totalInterfaces`：未删除接口总数。
- `onlineInterfaces`：状态为上线的接口数。
- `offlineInterfaces`：状态为下线的接口数。
- `todayInvocations`：当天零点至当前的调用数。
- `todayErrors`：当天失败调用数。
- `abnormalInterfaces`：最近24小时命中失败率或响应时间规则的不同接口数。

### 趋势

`GET /analysis/dashboard/trends`

最近24小时划分为6个四小时桶，后端补齐无数据桶，返回成功率、调用量、错误率和平均响应时间四组点。百分比保留1位小数，响应时间单位为毫秒；无调用时对应比率为0。

### 重点关注

`GET /analysis/dashboard/alerts`

使用配置化阈值：最近24小时调用不少于5次且失败率不低于5%；平均响应时间不低于1000ms；上线接口连续7天无调用；最近1小时调用量达到前一小时的150%。每个接口只输出优先级最高的一条告警，最多返回10条。

### 最近变更

`GET /analysis/dashboard/changes`

返回接口变更审计表最近10条记录，事件类型为新增、修改、上线、下线，包含接口 ID、名称、事件类型和事件时间。接口名称写入审计快照，避免主记录删除后历史不可读。

## 数据与事务

新增 `interface_change_log` 表，保存接口 ID、接口名称快照、事件类型、事件时间和创建时间，并建立接口 ID、事件时间联合索引。

接口新增、修改、成功上线、下线分别在对应应用服务事务内写入审计记录。发布探测失败或业务事务回滚时不得产生上线记录。审计写入失败抛出异常并回滚原业务事务，避免工作台出现虚假历史。

统计查询集中在 `DashboardAnalysisService` 和 `DashboardAnalysisMapper`，时间窗口由服务层计算，SQL 只负责聚合；查询不直接暴露实体，统一返回工作台 VO。

## 配置

新增 `DashboardProperties`，配置前缀为 `feiapi.dashboard`，默认值为：

- `failure-rate-threshold: 0.05`
- `minimum-invocations: 5`
- `slow-response-threshold-ms: 1000`
- `spike-multiplier: 1.5`
- `unused-days: 7`
- `alert-limit: 10`

## 前端行为

删除 `dashboardMock.ts`、Mock 降级逻辑及 E2E 工作台伪造响应。工作台服务直接请求真实接口；页面使用独立结果归并，成功区块正常展示，失败区块显示错误提示和刷新入口。趋势无数据时显示明确空状态，不渲染误导性的空白图表。告警和变更时间由前端统一格式化为相对时间。其他业务既有的单元测试桩和 E2E Fixture 不在本次清理范围内。

## 错误处理与安全

- 工作台接口仅管理员可访问，沿用现有会话和统一业务错误处理。
- 无数据不是异常，返回零值或空集合。
- 查询异常由全局异常处理器统一记录和返回，前端不再静默使用伪造数据。
- 配置阈值使用校验注解限制为合法非负值，避免错误配置导致统计异常。

## 测试计划

- 后端：Dashboard 服务聚合规则、阈值边界、空数据、审计事务写入和失败回滚的 JUnit 5 + Mockito 测试；AnalysisController 管理员权限及响应契约集成测试。
- 前端：Dashboard 服务真实请求契约、部分失败归并、空趋势状态、相对时间格式化和工作台 Mock 完全移除的 Vitest 测试。
- 验证：执行后端 Maven 测试、前端 `yarn test`、`yarn build`；本地前后端和数据库可用时，使用不拦截工作台接口的 Playwright 冒烟检查真实数据展示。

## 文件范围

后端新增审计模型、统计模型、Mapper、Service、配置类、SQL 初始化脚本及测试；修改 `AnalysisController`、接口生命周期应用服务、发布生命周期服务、`application.yml` 和 H2 建表脚本。

前端修改工作台页面、服务、类型、告警/变更/趋势组件及测试；删除所有工作台 Mock 文件和 E2E Mock 分支。

部署仓库修改 `docker-compose.yml`，挂载审计表初始化脚本。
