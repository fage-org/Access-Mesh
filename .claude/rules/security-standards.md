---
name: security-standards
description: >-
  安全标准规范。
  Rule type: ALWAYS — applies to all backend services and API endpoints.
  Covers: secrets management, HTTPS enforcement, CSRF protection, rate limiting,
  security headers, dependency scanning.
origin: project
metadata:
  project: AccessMesh
  version: "1.0.0"
---

# 安全标准规范

## 1. 敏感信息管理

**MUST** 使用环境变量或密钥管理系统存储敏感信息，**禁止**硬编码凭证。

```yaml
# ❌ 禁止 — 硬编码在 application.yml
spring:
  datasource:
    password: mySecretPassword123

# ✅ 正确 — 环境变量注入
spring:
  datasource:
    password: ${DB_PASSWORD}

# ✅ 正确 — Nacos 加密配置
spring:
  cloud:
    nacos:
      config:
        encryption-key: ${NACOS_ENCRYPTION_KEY}
```

禁止硬编码：
- 数据库密码
- API 密钥
- OAuth Client Secret
- JWT 签名密钥
- 加密密钥

## 2. HTTPS 强制

**MUST** 对所有对外服务启用 HTTPS。

```yaml
# Gateway HTTPS 配置
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}

# Cookie 安全配置
session:
  cookie:
    secure: true
    http-only: true
    same-site: strict
```

必需配置：
- **SSL/TLS**: 所有 Gateway 和对外端口使用 TLS
- **Secure Cookie**: Session Cookie 设置 `Secure` 属性
- **HSTS Header**: `Strict-Transport-Security: max-age=31536000; includeSubDomains`（上线前确认全部子域均已具备 TLS，否则子域会被钉死强制 HTTPS）
- **SSL Redirect**: Gateway 配置 HTTP → HTTPS 自动重定向

## 3. CSRF 保护

**MUST** 根据认证类型配置 CSRF 保护。

> 示例为通用 Spring Security 参考写法（示意「按认证类型选择策略」的语义）；本项目实际使用 Sa-Token + Gateway 自研过滤器，仓内无 Spring Security 依赖——落地时按本项目过滤器链实现同等语义，照抄示例会引入不存在的框架依赖。
>
> 本仓事实：令牌仅经 `Authorization` 头显式传递（前端 http 层恒发 Bearer 头），Cookie 承载通道已双向关闭——access-service `is-read-cookie: false`（sa-token 默认 true，不显式关闭时 `StpUtil.login` 会在响应种无 SameSite/HttpOnly 标记的 `Authorization` Cookie，Safari/旧浏览器跨站自动携带 = CSRF 面），Gateway `AuthTokenFilter` 只认 Bearer 头且 sa-token 侧同步 `is-read-cookie: false`；双端 `saTokenConfigMatchesAuthority` 断言钉住。若未来要启用 cookie 承载，须先补 CSRF 防护（Origin/Referer 校验、显式 SameSite/HttpOnly/Secure）并重评本节策略。

```java
// ✅ 正确 — 无状态 JWT API 禁用 CSRF（需文档说明理由）
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}

// ✅ 正确 — 有状态 Session API 启用 CSRF
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
    return http.build();
}
```

| API 类型 | CSRF 配置 |
|---------|----------|
| 无状态 JWT | 禁用（令牌仅经 Header 显式传递，浏览器不自动附带；本仓 Cookie 承载通道已关闭，见上注） |
| 有状态 Session | 启用（默认 CSRF Token 校验） |

**MUST** CORS 配置限制 `Access-Control-Allow-Origin` 为可信域名，**禁止**使用 `*`。

## 4. API 限流

**MUST** 在 Gateway 层配置限流策略。

```yaml
# Resilience4j 限流配置
resilience4j:
  ratelimiter:
    instances:
      login:
        limitForPeriod: 10
        limitRefreshPeriod: 1m
        timeoutDuration: 5s
      api:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
```

关键接口限流建议：

| 接口类型 | 限流阈值 |
|---------|---------|
| 登录 | ≤ 10 次/分钟/IP |
| 密码重置 | ≤ 5 次/分钟/IP |
| 标准 API | ≤ 100 次/分钟/用户 |
| 内部服务 | ≤ 10000 次/分钟/服务 |

**MUST** 触发限流时返回 HTTP 429，不暴露内部限流配置细节。

## 5. 安全响应头

**MUST** Gateway 与服务响应包含以下安全 Header：

| Header | 值 | 作用 |
|--------|-----|------|
| `Content-Security-Policy` | `default-src 'self'` | 防止 XSS |
| `X-Frame-Options` | `DENY` | 防止点击劫持 |
| `X-XSS-Protection` | `1; mode=block` | 浏览器 XSS 过滤 |
| `X-Content-Type-Options` | `nosniff` | 防止 MIME 嗅探 |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | 强制 HTTPS（与 §2 HSTS 配置同值） |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 控制 Referrer 泄露 |

```java
// Spring Security Header 配置（通用参考写法；本项目实际经 Gateway 过滤器设置安全头，勿照抄引入 Spring Security）
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            .frameOptions(FrameOptionsConfig::deny)
            .xssProtection(Customizer.withDefaults())
            .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
        );
    return http.build();
}
```

## 6. 依赖安全扫描

**MUST** CI 管道集成依赖安全扫描。

现行入口为 `.github/workflows/ci.yml` 的 `dependency-security` job（push / PR / 手动 / 每周一 02:00 UTC）。Maven 先解析并安装 reactor 依赖，再用固定 `org.owasp:dependency-check-maven:12.2.2:aggregate` 扫描，JDK 21，包含 test/provided，`failBuildOnCVSS=7.0`、`failOnError=true`；NVD API key 可通过仓库 secret `NVD_API_KEY` 加速，没有 key 不豁免扫描失败。12.2.2 修复 NVD 参考链接超出旧数据库 URL 字段长度时的导入错误；出现逐条 CVE 导入错误时，即使工具继续运行也不能视为完整数据验收。OSS Index 补充源关闭以避免依赖另一个账户认证，Maven CVE 主检查使用 NVD；前端由 pnpm 单独扫描，不使用 Dependency Check 的 Node 审计适配器。

CI 的 NVD 初始化使用官方年度压缩文件与 modified 文件（2026-10-17 用户批准）：`tools/security/download_nvd_feed.py` 核对官方 metadata、压缩/解压长度及 SHA-256；完整导入后，必须执行 `update-only -DnvdValidForHours=0` 强制官方 API 增量更新，两段成功才进入 aggregate。始终保留自动更新；不以快照时间代替 API 补齐。数据目录位于当前 runner 的临时目录，不发布或共享数据库缓存。下载、导入、增量、扫描各自保存完整日志并流式输出；任一步非零退出或 Maven `[ERROR]` 均阻断，不能把继续执行当作完整数据验收。原始 NVD metadata 清单随报告保存。

前端固定 pnpm 11.5.0，按锁文件安装后执行 `pnpm audit --audit-level high --json`，包括生产与开发依赖；保留原始报告，再经 `tools/security/check-pnpm-audit.mjs` 执行高危门禁和下述唯一例外判断。不忽略 registry 错误或泛化豁免无修复版本。新增 Actions 固定到经标签核实的 commit SHA，报告即使失败也上传并保留 30 天。扫描失败、未执行、漏洞阻断与批准例外须分别报告；存量依赖修复由 T-ACCESS-105 承接，不以关闭扫描、调低门槛或未批准的忽略项获得绿色。

### braces 开发依赖临时例外

2026-10-17 用户批准（T-ACCESS-105）：仅接受 `GHSA-vfj7-8cjw-p6xm` 在 `braces@3.0.3` 的既有开发工具路径，路径由上述门禁脚本显式限制；不接受生产依赖、新调用路径、其他版本或其他公告。该库在深层花括号匹配规则下可能发生栈耗尽，当前输入来自受开发者控制的文件匹配配置，未发现业务页面输入通路。npm 无已发布修复版本时保留此风险，不声称已修复或漏洞清零；原始报告与例外判定报告同时保存。

复核触发条件：官方发布比 3.0.3 更新的正式版本（门禁实时查询 npm 并重新阻断）、依赖进入生产路径/出现新根路径（门禁阻断），或匹配规则开始接收外部输入（实现变更评审必须撤回例外并重新决策）。未来修复后删除例外和对应专用检查，不推广为默认忽略策略。[公告](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)。

工具参数依据：[Dependency Check Maven](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/configuration.html)、[pnpm audit](https://pnpm.io/cli/audit)。

### Maven 精确误报排除

2026-10-17 用户批准（T-ACCESS-105）：`tools/security/dependency-check-suppressions.xml` 仅排除以下已核实不适用的匹配，原始报告及核实依据见 [Maven 证据](../../docs/tasks/evidence/T-ACCESS-105/maven-remediation.md)。这不是接受可利用漏洞，不按整个 CPE 或严重等级忽略。

| 限定对象 | CVE | 不适用依据 |
|---|---|---|
| `com.mybatis-flex` 的 core、spring、codegen、annotation、processor、spring-boot-starter、spring-boot4-starter 七个 `mybatis-flex-*` 包，固定 1.11.8 | CVE-2023-25330、CVE-2020-26945 | 前者针对 MyBatis-Plus，后者针对 MyBatis <3.5.6；实际 MyBatis 为 3.5.19，不把 Flex 版本当作 MyBatis 版本 |
| `com.alibaba.nacos:logback-adapter:1.1.4` | CVE-2021-29441、CVE-2021-29442、CVE-2021-43116 | 仅日志适配包，不含公告涉及的 Nacos 服务端认证过滤器、控制台或 ConfigOpsController |
| `docker-java-transport-zerodep:3.7.1` JAR 内嵌 HttpClient 的精确路径 | CVE-2026-71290 | Docker 传输使用 Classic 同步客户端；Apache 公告明确其不受该异步客户端漏洞影响。普通 HttpClient 依赖不排除 |
| 同一 Docker JAR 内嵌 httpcore5 / httpcore5-h2 5.3.6 的精确路径 | CVE-2026-54428 | 实际使用 Classic HTTP/1.1，不走该公告涉及的 HTTP/2 HPACK 解码链 |

组件或版本变化时重新核实；Docker 传输调用方式变化时必须重新核实并撤回不再成立的排除。其他 CVE 仍按阈值阻断，保留原始及排除后报告，不能把排除规则扩展到未来版本或其他组件。

### Docker 测试依赖临时风险例外

2026-10-17 用户批准（T-ACCESS-105）：仅接受 `docker-java-transport-zerodep:3.7.1` JAR 内嵌 HttpCore 5.3.6 的 **CVE-2026-54399**，范围为 Access Service / E2E Tests 的 test 依赖、连接可信 Docker 基础设施。此 HTTP/1.1 头资源耗尽漏洞真实存在，不能归为误报；普通 HttpCore 已升级，排除不覆盖普通依赖或生产链路。

XML 规则限定内嵌路径和 CVE，`tools/security/check-maven-audit.mjs` 从原始报告核对版本及全部 projectReferences，缺失或超出上述测试范围即阻断；查询 Maven Central，当出现更新的 docker-java 正式版即阻断并要求复核其修复情况，查询失败也不放行。原始报告与风险判定报告一起保存。上游修复、生产路径或不可信 Docker/代理接入时撤回例外并重新处理；连接可信性须在环境配置变更评审中核实，不由 artifact 版本推断。修复后删除对应规则及专用门禁。

要求：
- **高危漏洞阻断**: CVSS ≥ 7.0 必须修复后方可合并；仅允许本节明确批准、范围可核对的临时例外
- **定期扫描**: 每周全量扫描，新漏洞及时告警
- **禁止黑名单依赖**: 即使扫描通过也禁止使用团队规定的黑名单依赖（如 FastJSON）

## 7. 客户端 IP 信任面（X-Forwarded-For）

**MUST** 网关清洗外部 `X-Forwarded-For` / `X-Real-IP`（头名匹配大小写不敏感，RFC 7230），并以自身观测的 `remoteAddr` 重建 `X-Forwarded-For` 写回下游——客户端可伪造的 IP 头声明一律不采信（T-GW-008，2026-09-10 定案；实现见 `docs/design/services/gateway.md` §请求头清洗与客户端 IP 重建）。`X-Forwarded-Host/Port/Proto/Prefix` 与 `Forwarded` 同在清洗列表（可伪造转发声明）。

- **出站最终重建**：Gateway 5 的框架头清理之后，`ClientIpHeadersFilter` 在出站过滤链末位仅从 socket 对端重建单值 XFF；没有对端地址则移除 XFF。不得为保留 XFF 而启用客户端代理声明信任。入口清洗与出站重建共同维持上述既有来源契约。
- **网关自身条件评估直用 remoteAddr**：`PermissionFilter` 做 IP_WHITELIST / IP_BLACKLIST 评估时直接取 socket 层 remoteAddr，不读任何请求头——即使清洗配置被误删，网关侧评估也不复活伪造面。
- **下游统一消费重建值**：access-service 门禁条件评估与操作/登录日志消费网关重建后的 XFF（值=网关观测的直连对端 IP）；下游服务不得自行采信外部 XFF / X-Real-IP 声明。
- **多层 LB / nginx / CDN 拓扑部署前提**：网关观测到的是**代理出口 IP**，按真实客户端 IP 的黑白名单在此类拓扑下不工作（所有客户端同呈一个代理 IP）。要么 IP 条件改配代理出口网段（粒度变粗），要么把按真实客户端 IP 的过滤上移到最外层可信设施（nginx/WAF）；需要网关层精确采信真实客户端 IP 须重启已弃的 trusted-proxies 设计（另立项，定案见 [历史定案原文](../../docs/archive/2026-09-26/decision-registry-before.md) 2026-09-10）。
- **forward-headers-strategy 启动护栏**：`server.forward-headers-strategy=framework` 时 Spring 的 ForwardedHeaderTransformer 在全部 WebFilter 之前把外部 X-Forwarded-For 解析进 `request.getRemoteAddress()` 并删除转发头——清洗来不及参与、remoteAddr 已被污染（spring-web/spring-boot 字节码实证，codex 外评 P1）。`ForwardHeadersStrategyGuard` 启动校验 framework/native 一律拒绝启动（native 在 WebFlux 下无 transformer 不污染，同拒防语义混淆），允许缺省/none（Spring Boot 默认）。评审勿再建议开启 framework 转发策略或在 Gateway 处理转发头——与「socket 对端=唯一可信 IP 来源」定案冲突。
- **实现约束**：请求头清洗必须走显式 `headers(h -> h.remove(...))` 删除语义，**禁止**「构建干净头副本再 `putAll`」——`ServerHttpRequest.Builder#headers` 的 consumer 收到的是原请求头的可写视图，`putAll` 为叠加语义、清洗项不会被移除（归并起 HeaderCleanFilter 即因此从未真正删除过头，仅靠下游注入 filter 的 set 覆盖兜底；T-GW-008 实测修正并以回归锁钉住）。

---

**记住**: 环境变量管理密钥 → HTTPS 强制 → CSRF 按类型配置 → Gateway 限流 → 安全响应头 → CI 依赖扫描 → XFF 清洗重建不采信外部 IP 头 + forward-headers-strategy 护栏拒启。
