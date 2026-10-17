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

目前仍执行既有 Bearer 认证。本文未定案部分不作为直接开启 Cookie 读取的授权或实现契约；正式实现与验收前，不把草案表述为已交付。

## 当前行为与依据

- 租户管理台：`/login`，租户原生登录返回 accessToken，前端保存在 JavaScript 可读 Cookie，显式加入 Authorization 请求头。
- 独立平台运营：`/platform/login`，平台令牌存于 localStorage 的 platform-operator-session，显式加入 Authorization 请求头。平台与租户身份独立，可以同时登录。
- 网关 AuthTokenFilter 仅提取 Bearer，Sa-Token Cookie 读取关闭；现行安全边界见 [security-standards §3](../../.claude/rules/security-standards.md#3-csrf-保护)。
- 登录接口令牌响应也被非浏览器测试/调用链消费，不能只删除前端存储而不处理响应和调用契约；E2eTenantSupport 从 accessToken 响应构造 Bearer。
- 原生登录、独立平台、OAuth2 委托与服务凭证的现有入口及边界以[API 契约](access-service-api-contract.md)、[服务认证](service-authentication.md)为准。

## 必须保留的既有保证

两类身份互不串用、互不清理或注销；已有租户门禁、密码代际、强制改密与授权判断不因承载方式变更而放宽。跨标签保护的行为边界继续按[登录设计](frontend/login.md#cross-tab-session-status)与[平台边界](frontend/login.md#platform-cross-tab-session)执行：旧界面不能使用新身份提交，迟到响应不污染新身份，输入和一次性密码按已确认规则处置。

HttpOnly 不等于全部脚本注入风险已解决，也不允许保留同一浏览器令牌的其他脚本可读出口却宣称已经不可读。浏览器自动携带 Cookie 的认证方案必须同时明确 CSRF 防护，不能以现有 Bearer 假设直接关闭相关保护。

## 待用户决定

1. 覆盖租户管理台、独立平台运营，还是两者。
2. 浏览器登录与既有 Bearer/API 调用的兼容边界，以及令牌响应如何交付。
3. 支持的部署形态及浏览器访问边界，再确定 Cookie 属性、路径与 CSRF 方案。
4. 影响现有用户行为或升级支持边界的取舍。纯实现细节在已确认范围内解决，不重复提问。

## 实施与验收入口

由 [T-ACCESS-109](../tasks/T-ACCESS-109.md) 承接。方案确认后补齐接口、网关/服务端信任边界、前端状态恢复与跨标签机制，以及失败/退出/CSRF/双身份隔离的验证要求，再推进实现。本文转为 adopted 前须明确区分目标方案与现行行为。
