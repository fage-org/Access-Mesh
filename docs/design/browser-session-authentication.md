---
doc_type: design
title: 浏览器 HttpOnly 会话认证改造
status: adopted
domain: auth
last_reviewed: 2026-10-18
---

# 浏览器 HttpOnly 会话认证改造

## 状态与已确认范围

2026-10-17 用户选择 P2：启动独立改造，先确定覆盖范围、Cookie/CSRF 方案及现有调用兼容，再实施。此决定取代 Q-068 中 HttpOnly 事项的延期安排，不改变强制下线 K1、验证码替代通道 L1 的继续暂缓。

**覆盖范围（2026-10-17 用户选择 R1）**：自带租户管理台与独立平台运营网页登录一起改造；两类 Cookie/会话及退出行为继续隔离，不将一个身份的登录或退出传播为另一身份的登录或退出。此范围决定不等于把 SDK、服务凭证或第三方 OAuth2 调用统一改为 Cookie，具体调用兼容边界见下文 S1。

**通道兼容（2026-10-17 用户选择 S1）**：新增租户与平台网页登录专用 Cookie 入口，复用现有账号、密码、验证码及门禁校验；网页登录通过 HttpOnly Cookie 交付凭据，只返回必要的会话/用户元数据，不在 JSON 或其他脚本可读位置返回同一认证凭据。原 `/api/access/auth/login`、`/api/access/platform-auth/login` 保留 Bearer 令牌交付，供现有接口工具及测试使用，原有凭据校验不降低。不提供凭网页登录 Cookie 换取明文 Bearer 令牌的接口。标准 OAuth2 令牌接口、服务凭证及其 Bearer 调用语义保持。

**双凭据冲突（2026-10-17 用户选择 T1）**：受保护业务请求同时携带当前业务身份域的认证 Cookie 与显式 Bearer 时直接拒绝，即使二者属于同一账号也不接受；不设凭据优先级，不在一种凭据失败后回退另一种。租户与平台 Cookie 可以共存，按请求所属身份域识别对应 Cookie，不将另一身份域 Cookie 的存在视为混用。接口工具使用 Bearer 时须避免同时携带对应认证 Cookie。

**退出体验（2026-10-17 用户选择 U2）**：租户与平台网页登录均保留立即清空旧业务页面和弹窗、进入登录页并允许重新登录的体验，不等待服务器注销确认。服务端注销失败或超时时，必须明确提示“服务器尚未确认退出”，不能以本地页面清理替代服务端确认。实现须隔离待退出旧会话与新登录会话：旧注销请求及迟到响应不得注销或删除新登录凭据；刷新和其他标签页不得恢复使用待退出旧会话；两类身份互不影响。此项是目标行为，须完成相应协议与并发/失败验收，不能直接沿用当前脚本删除令牌的实现。

**升级切换（2026-10-17 用户选择 V1）**：新版网页加载时清理本浏览器旧版认证存储，租户与平台分别重新填写账号、密码及图片验证码，以建立新的 HttpOnly 会话；不新增旧 Bearer 会话自动迁移接口。此清理不批量撤销接口工具的 Bearer 会话，原 Bearer 登录继续按 S1 保留。尚未刷新、仍运行旧代码的标签页不因发布新版这一动作而被服务器强制下线；旧凭据仍受既有失效、门禁与过期规则约束。

**简化注销（2026-10-17 用户确认，取代 W1 并取消 X1/X2）**：保留 U2 的立即清空旧页面与重新登录。退出只尝试注销请求所对应的原会话，失败明确提示“服务器尚未确认退出”；不持久化补注销队列，不自动追踪或跨账号补注销，不签发独立注销凭据。未成功注销的旧服务端会话按现有过期规则结束。退出响应不删除固定名认证 Cookie，避免迟到响应删除新登录的 Cookie；服务端成功注销后残留 Cookie 值已失效，下一次登录覆盖该值，或由浏览器按期限清理。

**部署基线沿用**：现有[生产部署基线](../ops/deployment.md#1-拓扑)已明确前端与 API 同源，开发形态通过 Vite 同源代理访问。本次不据“新增 Cookie”自动扩大到跨源或第三方 Cookie 部署；生产 HTTPS 与本地预览形态继续分别核对，具体属性和配置须体现这些既有边界。

本文为已确认的目标与实现边界，T-ACCESS-109 已完成实现与验收；未升级的部署仍执行既有 Bearer 认证。部署时须同批提供 Cookie、CSRF 与会话隔离，不允许只开启 Cookie 读取。

## 改造前基线与兼容依据

- 改造前租户管理台 `/login` 将原生登录 accessToken 存于脚本可读 Cookie，并加入 Authorization 头；本任务改为网页专用入口与 HttpOnly Cookie。
- 改造前平台 `/platform/login` 将令牌存于 platform-operator-session；新版只持久化非认证元数据，平台与租户仍独立。
- 网关 AuthTokenFilter 继续消费 Bearer；排在它之前的浏览器适配先校核 Cookie 并在内部转换，Sa-Token 通用 Cookie 读取保持关闭；安全边界见 [security-standards §3](../../.claude/rules/security-standards.md#3-csrf-保护)。
- 登录接口令牌响应也被非浏览器测试/调用链消费，不能只删除前端存储而不处理响应和调用契约；E2eTenantSupport 从 accessToken 响应构造 Bearer；quickstart 也明确以 `/api/access/auth/login` 的响应令牌调用 example-service。现有登录返回体为 LoginResp/PlatformLoginResp，均暴露 accessToken。
- 原生登录、独立平台、OAuth2 委托与服务凭证的现有入口及边界以[API 契约](access-service-api-contract.md)、[服务认证](service-authentication.md)为准。

## 必须保留的既有保证

两类身份互不串用、互不清理或注销；已有租户门禁、密码代际、强制改密与授权判断不因承载方式变更而放宽。跨标签保护的行为边界继续按[登录设计](frontend/login.md#cross-tab-session-status)与[平台边界](frontend/login.md#platform-cross-tab-session)执行：旧界面不能使用新身份提交，迟到响应不污染新身份，输入和一次性密码按已确认规则处置。

HttpOnly 不等于全部脚本注入风险已解决，也不允许保留同一浏览器令牌的其他脚本可读出口却宣称已经不可读。浏览器自动携带 Cookie 的认证方案必须同时明确 CSRF 防护，不能以现有 Bearer 假设直接关闭相关保护。

## 决策边界

覆盖范围、兼容、冲突处理、立即重新登录、升级切换与上述简化注销均已确认。具体实现沿用已有认证设施，保留安全校核，不重新引入跨会话补偿机制。涉及新的产品行为取舍时再由用户决定。

## Cookie / CSRF 与会话隔离

以下约束已落实于代码；已部署环境须升级后才切换。

- **Cookie 属性与部署**：认证值只经 HttpOnly Cookie 交付；生产 HTTPS 下明确设置 Secure、SameSite，限定当前主机，不扩展到父域。当前回环 HTTP 预览需要与生产属性分别处理，不从未经信任的转发头推断外部协议；公开 Origin 按现有代理拓扑显式配置。Cookie 的期限不延长现有服务端会话期限，也不改变闲置过期规则。
- **CSRF 与页面身份绑定**：网页登录/退出入口检查来源及请求格式；Cookie 业务请求还须携带与该会话绑定的不可预测标记。标记可以交给前端，但单独不能登录、换取认证值或调用业务接口。服务端拒绝缺失、错误或不匹配的标记，不在标记失效时换用浏览器里的另一会话。Origin 优先，必要时严格解析 Referer；来源缺失或不可信时拒绝 Cookie 通道。SameSite 只作为补充防护。
- **保留跨标签保证**：请求创建时固定对应会话标记；发送前和响应回写前仍检查页面代际；服务端再校核请求标记与实际 Cookie 会话。换账号后不能将旧请求的标记替换成最新标记后重发，也不能自动重放业务写请求。登录响应的 Cookie 已由浏览器处理时，迟到响应仍不得把旧用户资料写回新页面。
- **U2 的退出隔离**：租户与平台各用固定名称 Cookie。注销请求固定原页面会话标记，服务端必须核对实际 Cookie 所对应的标记；若浏览器已携带新账号 Cookie，则拒绝旧请求，不回退为注销当前账号。注销响应不写 Set-Cookie；前端立即清除可读会话元数据与旧 UI，刷新只按现有本地活动会话恢复，不凭残留 Cookie 自动恢复旧身份。
- **下游与直连边界**：Gateway 与 access-service 均需处理凭据混用和来源校核，不能只在网页检查。Gateway 需要按目标服务清理无关的浏览器认证 Cookie，尤其不能把平台 Cookie 随租户业务请求传给 example-service。不能把 Gateway 内部适配出的认证信息误判为用户混用，也不能提供客户端可伪造的“已校验”头绕过校核。
- **既有调用兼容**：Cookie 处理不扩大标准 OAuth2、服务凭证或内部接口的白名单；新的网页入口复用账号、验证码、登录失败计数、租户门禁和审计链。继续保持 Sa-Token 的通用 Cookie 自动读取关闭，通过明确的浏览器适配处理新 Cookie，避免接受遗留 `Authorization` Cookie。

浏览器属性依据：[MDN Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)。CSRF 校核依据：[OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)。本项目的接线与浏览器验收见下方任务入口；资料依据不替代实测。

### 协议实现边界

网页登录新建不可预测的会话标记，保存于现有 Sa-Token 的 token-session 中，生命周期随原生会话；不另建“标记到原生令牌”的可反查索引。业务请求与注销均须同时提交 HttpOnly Cookie 和对应标记。标记单独不能登录、获取明文令牌、调用业务或授权注销。校核通过后继续既有原生会话、租户、密码代际与权限门禁。

认证 Cookie 的名字按身份域固定，生产使用 Secure、HttpOnly、SameSite=Strict 和 host-only 属性；HTTP 回环预览使用独立的本地 Cookie 名称。新登录覆盖对应 Cookie，退出及错误响应不删除或覆盖认证 Cookie。前端仅采用仍属于当前页面代际的登录响应；Cookie 与页面会话不一致时阻断并要求重新登录，不自动选择 Cookie 内另一个账号。

可读会话元数据使用新版本存储键，升级清除旧版认证存储。主动退出先创建绑定旧标记的注销请求，再立即清除对应活动元数据；迟到成功响应不修改本地状态，失败只提示原会话退出未确认。清除后不保留补偿队列；其他标签页沿用 N1/O1 的清理与重新载入行为。

## 实施与验收入口

由 [T-ACCESS-109](../archive/2026-10-17/tasks/T-ACCESS-109.md) 完成；接口、部署与前端生命周期已回写，验证见任务验收记录。

候选正式包的回环非默认端口验证见 [T-ACCESS-131 部署证据](../archive/2026-10-18/tasks/evidence/candidate-deployment/verification.md)：平台 browser-login 经 nginx/Gateway 返回 HttpOnly、SameSite=Strict、host-only 的本地 Cookie，JSON 不交付 accessToken；对应会话标记可读取身份，缺标记、缺失/不可信 Origin 和退出后的旧会话均拒绝。公开 Origin 同时配置于 Gateway 与 access-service，并验证预检响应。该次浏览器证据覆盖登录页渲染，认证成功证据来自独立 HTTP 协议检查；完整浏览器表单旅程、租户开通以及生产 Secure Cookie 不由该记录代证。

[T-ACCESS-132 浏览器旅程](../archive/2026-10-18/tasks/evidence/candidate-deployment/T-ACCESS-132/verification.md)补充了同一回环候选上的平台/租户实际登录、用户亲自首次改密、重新登录、主动退出、刷新与直达、跨标签旧页阻断以及平台会话独立性。租户恢复后旧会话请求拒绝，新登录可用。公开 API 的 Bearer 反例与浏览器证据分开记录；未扩展到生产 HTTPS 或故障期间的全部竞态。
