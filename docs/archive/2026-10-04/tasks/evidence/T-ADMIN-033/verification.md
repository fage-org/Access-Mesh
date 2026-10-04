# T-ADMIN-033 验证证据

2026-10-04：`mvn test -pl access-service -DskipTestcontainers=true -Dtest=BusinessFieldValidationTest,ClearFieldProtocolValidationTest`，26 项通过，零失败/错误。容器 execution 按命令跳过，不计作通过；批次完整回归已通过，见[完整证据](../pending-problems-clearance/batch-2026-10-04.md)。

`BusinessFieldValidationTest` 修复前 18 项中 17 项失败，分别为列宽溢出及组织更新空白被错误放行；null 不更新的既有允许行为通过。修复后全部通过。边界按当前 schema 的 sys_user、sys_org、sys_notice 列宽逐字段覆盖，无新测试上下文或数据库复制夹具。HTTP 400 接线依据现有 Controller 的 `@Valid` 与统一校验异常处理，定向测试只证明 DTO 验证，不声称执行了超长输入的旧版 HTTP 500 复现。

组织表单 `parentOrgDisplay` 仍对空父名回退为「根组织」；新校验闭合空名更新，历史空名按确认边界走开发库重建，不假称自动清理。user 的现有格式、Clear 协议及字段缺省语义保持。
