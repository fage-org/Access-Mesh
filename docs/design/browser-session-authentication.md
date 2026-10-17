---
doc_type: design
title: 浏览器 HttpOnly 会话认证改造
status: draft
domain: auth
last_reviewed: 2026-10-17
---

# 浏览器 HttpOnly 会话认证改造

## 状态与已确认范围

2026-10-17 用户选择 P2：启动独立改造，先确定覆盖范围、Cookie/CSRF 方案及现有调用兼容，再实施。此决定取代 Q-068 中 HttpOnly 事项的延期安排，不改变强制下线 K1、验证码替代通道 L1 的继续暂缓。

**覆盖范围（2026-10-17 用户选择 R1）**：自带租户管理台与独立平台运营网页登录一起改造；两类 Cookie/会话及退出行为继续隔离，不将一个身份的登录或退出传播为另一身份的登录或退出。此范围决定不等于把 SDK、服务凭证或第三方 OAuth2 调用统一改为 Cookie，其调用兼容边界另行确认。

**通道兼容（2026-10-17 用户选择 S1）**：新增租户与平台网页登录专用 Cookie 入口，复用现有账号、密码、验证码及门禁校验；网页登录通过 HttpOnly Cookie 交付凭据，只返回必要的会话/用户元数据，不在 JSON 或其他脚本可读位置返回同一认证凭据。原 `/api/access/auth/login`、`/api/access/platform-auth/login` 保留 Bearer 令牌交付，供现有接口工具及测试使用，原有凭据校验不降低。不提供凭网页登录 Cookie 换取明文 Bearer 令牌的接口。标准 OAuth2 令牌接口、服务凭证及其 Bearer 调用语义保持。

**双凭据冲突（2026-10-17 用户选择 T1）**：受保护业务请求同时携带当前业务身份域的认证 Cookie 与显式 Bearer 时直接拒绝，即使二者属于同一账号也不接受；不设凭据优先级，不在一种凭据失败后回退另一种。租户与平台 Cookie 可以共存，按请求所属身份域识别对应 Cookie，不将另一身份域 Cookie 的存在视为混用。接口工具使用 Bearer 时须避免同时携带对应认证 Cookie。

**退出体验（2026-10-17 用户选择 U2）**：租户与平台网页登录均保留立即清空旧业务页面和弹窗、进入登录页并允许重新登录的体验，不等待服务器注销确认。服务端注销失败或超时时，必须明确提示“服务器尚未确认退出”，不能以本地页面清理替代服务端确认。实现须隔离待退出旧会话与新登录会话：旧注销请求及迟到响应不得注销或删除新登录凭据；刷新和其他标签页不得恢复使用待退出旧会话；两类身份互不影响。此项是目标行为，须完成相应协议与并发/失败验收，不能直接沿用当前脚本删除令牌的实现。

**部署基线沿用**：现有[生产部署基线](../ops/deployment.md#1-拓扑)已明确前端与 API 同源，开发形态通过 Vite 同源代理访问。本次不据“新增 Cookie”自动扩大到跨源或第三方 Cookie 部署；生产 HTTPS 与本地预览形态继续分别核对，具体属性和配置须体现这些既有边界。

目前仍执行既有 Bearer 认证。本文未定案部分不作为直接开启 Cookie 读取的授权或实现契约；正式实现与验收前，不把草案表述为已交付。

## 当前行为与依据

- 租户管理台：`/login`，租户原生登录返回 accessToken，前端保存在 JavaScript 可读 Cookie，显式加入 Authorization 请求头。
- 独立平台运营：`/platform/login`，平台令牌存于 localStorage 的 platform-operator-session，显式加入 Authorization 请求头。平台与租户身份独立，可以同时登录。
- 网关 AuthTokenFilter 仅提取 Bearer，Sa-Token Cookie 读取关闭；现行安全边界见 [security-standards §3](../../.claude/rules/security-standards.md#3-csrf-保护)。
- 登录接口令牌响应也被非浏览器测试/调用链消费，不能只删除前端存储而不处理响应和调用契约；E2eTenantSupport 从 accessToken 响应构造 Bearer；quickstart 也明确以 `/api/access/auth/login` 的响应令牌调用 example-service。现有登录返回体为 LoginResp/PlatformLoginResp，均暴露 accessToken。
- 原生登录、独立平台、OAuth2 委托与服务凭证的现有入口及边界以[API 契约](access-service-api-contract.md)、[服务认证](service-authentication.md)为准。

## 必须保留的既有保证

两类身份互不串用、互不清理或注销；已有租户门禁、密码代际、强制改密与授权判断不因承载方式变更而放宽。跨标签保护的行为边界继续按[登录设计](frontend/login.md#cross-tab-session-status)与[平台边界](frontend/login.md#platform-cross-tab-session)执行：旧界面不能使用新身份提交，迟到响应不污染新身份，输入和一次性密码按已确认规则处置。

HttpOnly 不等于全部脚本注入风险已解决，也不允许保留同一浏览器令牌的其他脚本可读出口却宣称已经不可读。浏览器自动携带 Cookie 的认证方案必须同时明确 CSRF 防护，不能以现有 Bearer 假设直接关闭相关保护。

## 待用户决定

1. 升级时已有网页 Bearer 会话如何切换为新 Cookie 会话，是否要求用户重新登录。
2. 影响现有用户行为或升级支持边界的取舍；若要求超出现有同源部署支持，须另行确认。纯实现细节在已确认范围内解决，不重复提问。

## 实施与验收入口

由 [T-ACCESS-109](../tasks/T-ACCESS-109.md) 承接。方案确认后补齐接口、网关/服务端信任边界、前端状态恢复与跨标签机制，以及失败/退出/CSRF/双身份隔离的验证要求，再推进实现。本文转为 adopted 前须明确区分目标方案与现行行为。
