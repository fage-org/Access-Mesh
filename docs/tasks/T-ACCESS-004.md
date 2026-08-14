---
doc_type: task
id: T-ACCESS-004
title: 实现可信请求上下文和统一安全策略矩阵
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-003
blocks: []
acceptance:
  - "实现 access-service 唯一可信请求上下文，统一 tenantId、operatorId、callerType 和 verifiedServiceCode"
  - "业务代码只能读取上下文；外部请求头必须经过会话、签名或服务凭证验证后才能绑定"
  - "按公开认证、用户管理、Gateway 权限查询、外部 sync/full-sync 和权限管理建立可测试的安全策略矩阵"
  - "/auth/** 是唯一平台用户会话签发入口；平台用户 Token 固定 2 小时绝对有效期和 30 分钟无操作有效期，Gateway 与 access-service 对同一 Token 的登录、续期、解析、租户/主体提取、注销和失效结果一致；OAuth2 客户端令牌继续使用各客户端配置的有效期"
  - "perm-sdk、外部 sync/full-sync 与注册业务服务使用服务签名或内部凭证建立 SERVICE 上下文，不复用或伪装平台用户 Sa-Token 会话"
  - "sourceService 必须与已验证服务身份一致；内部凭证不能隐式获得 /api/perm/** 全权限"
  - "本地跨域调用不模拟 HTTP 请求头；异步和定时任务显式建立/传递上下文"
  - "正常、异常和异步路径均能清理上下文；测试覆盖租户串扰、伪造头和服务身份冒充"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-14
---

# T-ACCESS-004 实现可信请求上下文和统一安全策略矩阵

## 背景

合并前两套租户与安全拦截链互不相同，单进程内必须先统一身份建立顺序和可信边界。

## 范围

- 实现请求上下文、入口认证策略和上下文生命周期。
- 对现有接口建立调用方类型与操作权限矩阵。
- 增加负向安全测试。

## 非目标

- 不改变现有请求/响应 DTO 或错误码分段。
- 不做服务注册表白名单（T-ACCESS-005/010 范围）。
- 不改签名协议（payload 不含 serviceCode；重放防御登记 Gateway 侧收紧）。

## 完成记录

**实施日期**：2026-08-14。**用户决策 4 项**（全部采纳推荐项）：

1. **上下文载体**：新建 `AccessRequestContext`（infrastructure，ThreadLocal 承载 RequestContext 四要素：tenantId/operatorId/callerType/serviceCode + 快照/恢复 API）；`TenantContextHolder` 保留为兼容门面（set/get/clear 委托新上下文，~60 引用点零改动，T-ACCESS-012 扁平化）。
2. **operatorId 绑定（修复 G1）**：`X-User-Id` 头仅在签名验证通过后绑定为操作者；内部凭证路径携带 X-User-Id 但无有效签名 → 403（伪造头立即暴露）。纯凭证调用（perm-sdk 同步）→ SERVICE 上下文 operatorId=null。
3. **X-Service-Code 可信化（修复 G2）**：内部凭证验证通过后 `X-Service-Code` 视为凭证持有者声明的服务身份并绑定上下文；`SyncAuthVerifier` 一致性比对改为从上下文取 serviceCode（不再读裸请求头）。凭证持有者互冒充登记为已知限制（T-ACCESS-005/010 服务白名单收敛）。
4. **admin 域门禁（修复 G3）**：会话权威 + 拦截器显式门禁——未登录非公开路径 → 401（不依赖服务层隐式 NotLoginException）；X-Tenant-Id/X-User-Id 头存在必须与会话一致（不一致 403）。

**统一安全链**（`SecurityWebMvcConfig` 唯一 WebMvcConfigurer，替换双链）：

| order | 拦截器 | 覆盖 | 职责 |
|---|---|---|---|
| 1 | InternalApiSecretInterceptor | /api/perm/** | 内部凭证（X-Internal-Secret），通过写 INTERNAL_AUTHENTICATED attribute |
| 2 | HeaderSignatureInterceptor | /api/**, /internal/** | X-User-Id 恒需验签（G1：内部凭证不再无条件信任用户头），通过写 SIGNATURE_VERIFIED attribute；/actuator/** 排除（P2-2） |
| 3 | RequestContextInterceptor | /** | 唯一 AccessRequestContext 绑定入口：按矩阵建立上下文 + MDC 注入 + afterCompletion 统一清理 |

**已删除**：admin `TenantInterceptor`/`WebMvcConfig`、permission `PermTenantInterceptor`/`PermWebMvcConfig`（双链、双重执行、语义分叉消除）。

**其他修复**：
- OperatorContext 不再直接读 X-User-Id 头（只读可信上下文；SERVICE/TASK/匿名/未绑定 → SecurityException fail-closed）。
- MDC 注入（traceId=X-Request-Id 或 UUID / userId / tenantId / serviceCode，配合 log4j2 JsonLayout properties=true；外部可控值截断 64）。
- 匿名 actuator 不再强制 X-Tenant-Id（G4，对齐文档"actuator 健康检查匿名放行"）；`management.endpoints.web.exposure.include: health,info` 最小暴露（P2-5）。
- /error ERROR dispatch 跳过身份判定（评审 P1-1，防真实错误被 401 掩蔽）。
- 会话读取竞态 NotLoginException → 401（评审 P2-5，仅收窄捕获竞态异常）。
- X-Tenant-Id/X-User-Id 头格式非法 → 400、一致性与会话不符 → 403（评审 P2-2 语义对齐旧拦截器）。

**契约变更标注（评审 P2-3）**：无会话非公开路径从"服务层 500/异常"改为拦截器显式 401（G3 修复有意为之）；前端/调用方 401 语义=触发 token 过期重登逻辑，Gateway 已拦截场景下无副作用。

**验证**：access-service 默认 `mvn test` **433 测试 0 失败 19 跳过**（408 + 二轮评审修复 6 + IT 接入后 SecurityMatrixIT 10 / SyncEndpointAuthIT 9 随默认构建执行）。新增：`AccessRequestContextTest`(8)、`RequestContextInterceptorTest`(22，含 mockStatic 会话分支与 /auth 拆分/异步清理用例)、`OperatorContextTest`(4)、`SecurityMatrixIT`(10 矩阵用例，全量 Context + 真实链)。适配：`HeaderSignatureInterceptorTest`(9，G1 分支新用例)、`SyncEndpointAuthIT`(9，用例 4 断言 200→403、用例 7 断言 403→200)、6 个 sync service 测试（mock 请求头 → 绑定 SERVICE 上下文）。

**评审**：安全评审（ecc:security-reviewer）+ 代码评审（ecc:java-reviewer）+ 对抗核实（修复复核）。评审结论：无 P0；P1×1（/error 401 掩蔽，已修）；P2 修复 4 项（actuator 签名链排除、会话头格式 400、MDC 截断、actuator 最小暴露）+ 登记 3 项；P3 修复 3 项（冗余工厂、构造校验、注释/测试补全）。

**外部评审二轮（2026-08-14，2 P1 + 1 P2 + 1 P3，全部核实成立并修复）**：

- **P1（/auth/** 统一匿名绑定）**：/auth/** 全匿名导致 userinfo/user-menu/oauth2-authorize 登录后无租户上下文（getUserInfo 以 null 租户查询 → MyBatis-Flex 不过滤 → 跨租户查询风险；OAuth2 授权码写入 null 租户 → JWT tenant_id 降级）。**用户决策**：/auth/** 精确拆分——公开子集 {captcha, login, login/sms, oauth2/token, oauth2/refresh, oauth2/revoke, logout} 匿名（logout 保持未登录 200 幂等语义，无租户需求）；{userinfo, user-menu, oauth2/authorize, oauth2/userinfo} 进入会话 USER 分支（登录时绑定会话租户/操作者）。注：此问题为存量（旧 TenantInterceptor 对 /auth/** 同样不设租户），T-ACCESS-004 验收"租户/主体提取端到端一致"驱动修复。
- **P1（安全矩阵 IT 未接入默认构建）**：Surefire 默认规则排除 `*IT.java`，SecurityMatrixIT/SyncEndpointAuthIT 不随 mvn test 执行。**用户决策**：Surefire includes 显式接入 `**/*IT.java`（无新插件）；默认构建现含全部 IT。
- **P2（Servlet 异步生命周期）**：无异步 MVC Controller（潜在缺陷）；实现 `AsyncHandlerInterceptor.afterConcurrentHandlingStarted` 清理原线程上下文与 MDC（异步线程需上下文时显式 snapshot/restore）。
- **P3（精确 /actuator 根路径）**：`/actuator` 不匹配 `/actuator/` 前缀 → 401；公开判定补充精确根路径。

**范围外登记**：
- serviceCode-tenantId 绑定校验（查 service_config 注册，防凭证持有者任意声明服务身份/租户）→ T-ACCESS-005/010 服务白名单。
- 签名重放防御（300s 窗口内跨端点重放，payload 不含 method/path）→ Gateway 侧收紧（登记）。
- /auth/** 匿名依赖端点自保护（logout/userinfo/user-menu 用 StpUtil 内部鉴权）→ 既有设计，登记观察。
- Feign 自调用（AdminPermissionValidatorImpl → PermissionFeignClient）SERVICE 上下文依赖 operatorId 的管理接口 fail-closed（存量语义未变）→ T-ACCESS-005 统一处理。
