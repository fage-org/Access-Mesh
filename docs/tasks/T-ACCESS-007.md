---
doc_type: task
id: T-ACCESS-007
title: 合并系统配置与操作审计并落实日志事务分级
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#52-表合并边界
  - docs/design/access-service-architecture.md#82-审计事务分级
  - docs/design/project-rules.md
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "sys_config 与原 system_config 收敛为唯一 system_config，配置键使用 admin./permission./access. 命名空间"
  - "sys_audit_log 与原 operation_log 收敛为唯一 operation_log，target_id 支持字符串且 module 可区分三个边界"
  - "原 system_config 配置 API 路径、DTO、响应和错误码保持兼容；原 sys_audit_log 的 /audit-log/page 接口经确认前端与代码零引用后删除，日志查询统一走新接口 /api/perm/log/*，不再保留旧路径"
  - "operation_log、sys_login_log、sys_job_log 使用独立短事务；permission_change_log 的强事务写链路唯一归 T-ACCESS-005，本任务不重复修改权限写编排"
  - "请求体、响应和身份信息按规范脱敏、限长，密码、Token、密钥不会入库"
  - "独立日志异步执行使用有界线程池，队列满和写入失败具有同步降级或明确监控告警"
  - "测试覆盖主事务回滚、独立日志失败和敏感字段脱敏"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-19
---

# T-ACCESS-007 合并系统配置与操作审计并落实日志事务分级

## 背景

两服务存在真实重复的配置与普通操作日志，但安全登录、权限变更和任务日志具有不同的一致性与查询语义。

## 范围

- 合并配置和普通操作日志持久层及服务入口。
- 实现普通操作、登录和任务日志的独立事务；核对但不重复实现 T-ACCESS-005 所有的强事务权限审计。
- 建立脱敏、限长、线程池与失败监控测试。

## 当前口径

- **配置键**：`system_config.config_key` 唯一合法前缀 `admin.` / `permission.` / `access.`；存量种子键为 `admin.*`；`upsertSystemConfig` 在权限校验后、触达数据前 fail-closed（`CONFIG_KEY_NAMESPACE_INVALID`）。
- **operation_log**：`target_id` 为 `VARCHAR(256)`；`module` 为 `ADMIN` / `PERMISSION` / `ACCESS`（按事务边界判定）；含 `(tenant_id, operator_id, created_at DESC)` 操作者索引与 `(tenant_id, module, created_at DESC)` 模块分页索引。
- **入口级日志**：写入口统一 `@OperationLog`（必填 `module` / `action` / `targetType` / `targetId` / `summary`）。`action` 为大写事件码 `{业务对象}_{动作}`；`targetType` 为小写物理表名，批量操作用对应业务表名且 `targetId=""`；逻辑对象码例外仅登记 `oauth2_token`。`summary` 必须为合法 SpEL，纯文本用单引号包裹。注解与运行时上下文（`OperationLogRuntimeContext`）在 `infrastructure.aop`，切面在 `permission.aop`。`OperationLogAspect` 为 `@Order(LOWEST_PRECEDENCE - 1)`，位于事务切面外层。`operatorName` 从登录会话读取，无会话为 null。`targetId` / `summary` / `operatorName` 对齐列上限截断（256 / 512 / 256），防止超长 SpEL 结果或会话名触发插入失败丢失整条日志。
- **租户解析**：`OperationLogAspect` 解析租户优先级为 ① `OperationLogRuntimeContext.setTenantId`（方法体内显式登记，覆盖匿名认证派生端点——OAuth2 token/refresh/revoke 从授权码/刷新令牌/JWT 载荷解析租户后登记）；② 方法参数 `tenantId`（如登录失败自动锁定 `lockUser`）；③ `TenantContextHolder`。三者皆空的纯匿名端点该条操作日志跳过并告警（不写 `tenant_id = null` 违反 NOT NULL）。OAuth2 签发/刷新/撤销均有审计：登录成功/失败由 `sys_login_log`（loginType=OAUTH2）承载，`@OperationLog`（`OAUTH2_TOKEN_ISSUE` / `OAUTH2_TOKEN_REFRESH` / `OAUTH2_TOKEN_REVOKE`，targetType=`oauth2_token`）经 runtime override 正常落库。
- **独立短事务**：`operation_log` 经 `AuditDomainService.asyncRecordLog`（`@Async` + `REQUIRES_NEW`）；`sys_login_log` / `sys_job_log` 经 `LoginLogDomainService` / `JobLogDomainService` 同步 `REQUIRES_NEW`。方法体不吞异常：`asyncRecordLog` 写入失败异常传播至 `AsyncUncaughtExceptionHandler` 统一告警，`AuthServiceImpl.safeRecordLoginLog` / `JobServiceImpl.executeJob` finally 兜底，日志失败只告警。
- **登录日志字段**：`LoginLogEntry` 含 tenantId / userId / username / loginType / clientId / ipAddress / userAgent / status / failReason。IP / UA / 请求 ID 由 `HttpRequestUtils` 提取（`X-Forwarded-For` 取代理链首地址，对齐列上限）。SMS 成功或命中用户时 username 为实际用户名。`loginType` 为 `PASSWORD` / `SMS` / `OAUTH2`（OAuth2 令牌签发/刷新成功写 `OAUTH2` 登录日志，匿名端点审计承载）。
- **脱敏限长**：`SensitiveDataUtils` 采用 **Jackson 递归树遍历**：按 JSON 字段名匹配（password / pwd / secret / token / smscode / captchacode / apikey / authorization / phone / mobile / email / idcard / idcardno / certificate / privatekey / privatekeypem）脱敏所有值形态——标量字符串/数字/布尔、嵌套对象、数组元素、以及值为内嵌 JSON 字符串（如 `configValue`）的内层敏感键值，命中即整体替换为掩码；SMS 登录失败的操作人 username 侧记手机号时预脱敏（maskPhone 保留前3后4）。`REQUEST_BODY_MAX_LEN=4000`，截断后总长不超过列上限。切面按参数名包装后序列化；大对象（`MultipartFile` / `Part` / `byte[]` / 流 / `File` / `Resource` / Servlet 请求响应会话）替换为类型/名称/大小元数据。Token 等敏感值不写入 `targetId`。
- **线程池**：容量只来自 `application.yml` `spring.task.execution.pool`；`accessAsyncExecutor` 为容器 Bean。队列满且未停机时调用者线程执行并打告警；停机中丢弃。不引入 Micrometer。
- **原 `/audit-log/page`**：已删除，查询走 `/api/perm/log/*`。
- **测试边界**：不补 Spring 集成事务测试。本任务覆盖注解契约、REQUIRES_NEW 写入失败异常传播、调用方兜底代码。
- **permission 覆盖**：`AppServiceOperationLogCoverageTest` 包扫描 `permission.service.impl` 的 public `@Transactional` 非 readOnly 方法必须有 `@OperationLog`；已标注方法校验 module / action / targetType / SpEL。admin/application 强制全覆盖见 [T-ACCESS-014](T-ACCESS-014.md)。
- **审计豁免**：`NoticeServiceImpl.markNoticeAsRead` 不标注 `@OperationLog`（已读状态经 `read_at` 追踪）。

## 验收对照

| 验收 | 结论 |
|---|---|
| system_config 唯一 + 三前缀 | 通过：命名空间校验 + 种子迁移断言 |
| operation_log 收敛 + target_id 字符串 + module 三值 | 通过：列/索引/注释 + 注解改造 |
| 原配置 API 兼容；旧审计日志接口删除、查询统一新接口 | 通过：配置接口路径/DTO/响应/错误码未变；`/audit-log/page` 经确认前端与代码零引用后删除，日志查询走 `/api/perm/log/*`（新接口若后续需补旧字段见遗留） |
| 三类日志独立短事务；permission_change_log 归 T-ACCESS-005 | 通过：REQUIRES_NEW + 调用方兜底与异常传播断言 |
| 脱敏限长、密码/Token/密钥不入库 | 通过：SensitiveDataUtils + 切面脱敏/大对象元数据/请求头截断测试 |
| 有界线程池 + 队列满降级/告警 | 通过：AsyncConfig + 降级测试 |
| 匿名安全写租户解析 + OAuth2 令牌审计 | 通过：runtime override 优先于参数、无租户跳过告警（lockUser 正常落库）；OAuth2 签发/刷新/撤销均写 `OAUTH2` 登录日志，token/refresh/revoke 补 `@OperationLog` 经 runtime override 正常落库 |
| 测试覆盖回滚/独立日志失败/脱敏 | 部分：可测边界为注解契约 + 异常传播（`asyncRecordLog` 写入失败断言向上抛） + 调用方兜底代码；REQUIRES_NEW 事务语义不测第三方框架 |

## 非目标 / 遗留

- `permission_change_log` 强事务写链路不重复修改，唯一归 T-ACCESS-005。
- `sys_menu` DDL-实体漂移留待 T-ACCESS-012。
- admin/application 域 `@OperationLog` 强制全覆盖： [T-ACCESS-014](T-ACCESS-014.md)。
