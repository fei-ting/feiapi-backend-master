# 修复 CI DTO 校验测试设计

## 背景与根因

后端 CI 在执行 `feiapi-backend-server` 模块测试时，
`InterfaceDocContentSecurityValidatorTest.shouldRejectExampleLongerThanDtoLimit` 失败。
测试期望仅产生“成功响应示例长度不能超过 65535”这一条校验违规，但测试请求未填写
`interfaceInfoId`、`docVersion`、`requestContentType` 和 `responseContentType` 四个必填字段，
因此 Bean Validation 实际返回五条违规。

## 方案选择

采用“构造完整有效请求，再单独制造目标字段越界”的方案：在测试中填写四个合法必填字段，
将 `successExample` 保持为 65536 个字符，并保留违规数量和消息的精确断言。

未采用以下方案：

- `validateProperty`：只能验证单字段，不能覆盖真实请求对象的完整参数校验过程。
- 将断言放宽为 `contains`：会掩盖测试夹具缺少必填字段的问题，降低回归测试精度。

## 改动范围

只修改
`feiapi-backend-server/src/test/java/com/feiting/feiapi/unit/component/InterfaceDocContentSecurityValidatorTest.java`。
不修改 DTO 校验规则、业务代码、数据库结构或 CI 工作流。

## 验证标准

1. 目标测试方法通过，并且只产生一条预期的长度校验违规。
2. `feiapi-backend-server` 及其依赖模块按 CI 参数执行测试通过。
3. Maven 打包通过。
4. 改动通过正确性、可读性、架构、安全性和性能五个维度的合并前审查。

## 交付流程

实现提交先合并到 `dev`，再将 `dev` 合并到 `main`，推送远程 `dev` 和 `main`，
最后删除本地临时 feature 分支。
