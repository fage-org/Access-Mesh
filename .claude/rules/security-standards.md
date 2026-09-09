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

```yaml
# GitHub Actions OWASP 扫描
- name: OWASP Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: 'AccessMesh'
    path: '.'
    format: 'HTML'
    out: 'reports'
  env:
    JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17/64

- name: Upload Report
  uses: actions/upload-artifact@v4
  with:
    name: dependency-check-report
    path: reports/
```

要求：
- **高危漏洞阻断**: CVE ≥ 7.0 必须修复后方可合并
- **定期扫描**: 每周全量扫描，新漏洞及时告警
- **禁止黑名单依赖**: 即使扫描通过也禁止使用团队规定的黑名单依赖（如 FastJSON）

---

**记住**: 环境变量管理密钥 → HTTPS 强制 → CSRF 按类型配置 → Gateway 限流 → 安全响应头 → CI 依赖扫描。