# Gateway 服务设计

本文档是 Gateway 服务的精简设计入口。旧版完整设计已归档到 `../archive/2026-04-28/gateway-design.full.md`，仅用于追溯。

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

## 与权限中心的约定

- 接口级鉴权契约以 `../permission-center/api-contract.md` 为准。
- Gateway 传给权限中心的 `path` 必须是客户端看到的原始路径，不是 StripPrefix 后的后端路径。
- 未注册接口默认拒绝，除非系统配置明确调整策略。
- Gateway 可做短 TTL L1 缓存，但不得绕过权限中心契约自行解释权限模型。

## 配置重点

- 路由配置来自 Nacos。
- 每条路由需要能映射到稳定 `serviceCode`。
- 白名单只用于登录、健康检查、静态资源等明确公开接口。
- CORS、限流、请求体大小、安全响应头按 `../project-rules.md` 和网关配置实现。

## 实现参考

- 整体架构见 `../architecture.md`。
- 权限中心流程见 `../permission-center/core-flows.md`。
- 旧版详细过滤器链和配置样例见归档文档。
