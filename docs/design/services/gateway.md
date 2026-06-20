# Gateway 服务设计

本文档是 Gateway 服务的精简设计入口。旧版完整设计已归档到 `../../archive/2026-04-28/gateway-design.full.md`，仅用于追溯。

## 职责边界

- 作为系统唯一流量入口，负责路由、Token 校验、白名单、请求上下文注入和接口级鉴权。
- 不直接读取业务库或权限中心数据库。
- 鉴权时调用权限中心运行时接口，默认使用 `POST /api/perm/auth/check-interface`。
- 只处理入口安全和路由职责，不承载业务权限管理页面或授权配置。

## 核心链路

1. 接收客户端请求并匹配白名单。
2. 解析 Sa-Token / OAuth2 Token，得到主体信息。
3. 清洗客户端伪造的安全 Header，再注入可信 `X-Tenant-Id`、`X-Request-Id`、`traceId`、主体标识等上下文。
4. 按 `serviceCode + httpMethod + 原始 path` 调用权限中心接口级鉴权。
5. 允许时转发到目标服务，拒绝时返回统一 403 错误响应。

> **平台超管跨租户（2026-06-20 审计 S-017）**：v3.5 **不支持**平台超级管理员跨租户操作。超管必须分别登录每个租户实例，`X-Tenant-Id` 始终对应当前登录租户。不支持双 Header（`X-Tenant-Id` + `X-Target-Tenant-Id`）跨租户切换；如未来需支持，作为 v3.5.1+ platform-admin 增量设计。`TenantManager.ignore()`（见 project-rules.md）仅用于内部测试/迁移场景，**非超管跨租户能力**，禁止用于生产跨租户访问。

## 与权限中心的约定

- 接口级鉴权契约以 `../permission-center/api-contract.md` 为准。
- CORS、限流、请求体大小、安全响应头按 `../project-rules.md` 和网关配置实现。

## 实现参考

- 整体架构见 `../architecture.md`。
- 权限中心流程见 `../permission-center/core-flows.md`。
- 旧版详细过滤器链和配置样例见归档文档。
