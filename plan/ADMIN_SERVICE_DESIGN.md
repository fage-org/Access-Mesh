# 管理服务 (admin-service) - 详细设计文档

本文档定义管理服务的所有模块、接口清单及核心业务规则。与 `ARCHITECTURE_DESIGN.md`（整体架构）、`GATEWAY_DESIGN.md`（网关）和 `DESIGN.md`（权限中心）配套使用。

---

## 目录

1. [认证模块（Auth）](#1-认证模块)
2. [用户管理](#2-用户管理)
3. [组织管理](#3-组织管理)
4. [菜单管理](#4-菜单管理)
5. [角色管理（复用权限中心）](#5-角色管理)
6. [字典管理](#6-字典管理)
7. [通知管理](#7-通知管理)
8. [文件管理](#8-文件管理)
9. [审计日志](#9-审计日志)
10. [任务调度](#10-任务调度)
11. [系统设置](#11-系统设置)

---

## 通用约定

与权限中心一致：

- **所有接口 POST + JSON Body**（除 OAuth2 标准端点外）
- **多租户**：所有表带 `tenant_id`
- **软删除**：`delete_flag`（0=未删除，删除时填本行 id）
- **审计字段**：`created_by`、`updated_by`、`deleted_by`、`created_at`、`updated_at`、`deleted_at`
- **统一响应**：`{ "code": 200, "message": "success", "data": {} }`
- **分页规范**：`pageNum/pageSize/rows/total`

### 请求头

| 头部          | 必填 | 说明                                      |
| ------------- | ---- | ----------------------------------------- |
| X-Tenant-Id   | 是   | 由 Gateway 注入（登录后）；登录时由前端传 |
| X-User-Id     | 否   | 由 Gateway 注入（登录后的请求）           |
| X-Request-Id  | 否   | 由 Gateway 注入                           |
| Authorization | 是   | Bearer Token（登录/验证码等公开接口除外） |

---

## 1. 认证模块

### 1.1 功能描述

基于 Sa-Token + OAuth2 的认证中心。支持多种 OAuth2 授权模式，统一管理 Token 生命周期。

### 1.2 核心概念

| 概念               | 说明                                                             |
| ------------------ | ---------------------------------------------------------------- |
| OAuth2 Client      | 客户端应用配置（client_id, client_secret, 授权模式, 回调地址等） |
| Access Token       | JWT 格式访问令牌，短有效期（默认 2 小时），自含 userId/tenantId  |
| Refresh Token      | UUID 格式刷新令牌，长有效期（默认 30 天），存储在 Redis          |
| 授权码 (Auth Code) | 授权码模式中间态，极短有效期（5 分钟）                           |
| 登录会话           | Sa-Token Session，存储用户登录状态和附加信息                     |
| Token 黑名单       | Redis Set，存储已失效的 JWT Token（登出/踢下线时加入）           |

### 1.3 支持的 OAuth2 授权模式

| 模式          | grant_type         | 场景                             |
| ------------- | ------------------ | -------------------------------- |
| 授权码 + PKCE | authorization_code | SPA 前端应用（管理端/演示端）    |
| 密码模式      | password           | 受信客户端（内部管理端简化登录） |
| 客户端凭证    | client_credentials | 服务间调用（M2M）                |
| 刷新令牌      | refresh_token      | 刷新 Access Token                |

### 1.4 认证场景矩阵

| #   | 场景                     | 认证方式                                              | 接口/流程                  |
| --- | ------------------------ | ----------------------------------------------------- | -------------------------- |
| S1  | 用户名密码登录           | 简化接口，内部走 Sa-Token + JWT 签发                  | POST /auth/login           |
| S2  | 短信验证码登录           | 独立接口，不走 OAuth2 标准流程                        | POST /auth/login/sms       |
| S3  | 外部系统接口认证（M2M）  | OAuth2 client_credentials，分配独立 Client            | POST /auth/oauth2/token    |
| S4  | 业务系统 SSO             | OAuth2 authorization_code + PKCE，已登录用户静默发码  | GET /auth/oauth2/authorize |
| S5  | 微服务间调用             | 预置 internal-service Client (client_credentials)     | Feign 拦截器自动注入 Token |
| S6  | 门户内同域子系统         | 共享 Cookie/localStorage 中的 JWT Token，无需额外登录 | 前端直接读取已有 Token     |
| S7  | 门户内跨域/iframe 子系统 | OAuth2 静默授权（已登录 → 自动发码 → 无跳转感知）     | GET /auth/oauth2/authorize |

> **首期不做但预留扩展点**：第三方社交登录（微信/钉钉/GitHub）、移动端/小程序、LDAP/AD 集成。
> 预留方式：LoginHandler 接口抽象，按 login_type 分发到不同实现。

### 1.5 数据库表

#### 1.4.1 sys_oauth2_client（OAuth2 客户端配置）

| 字段              | 类型          | 说明                                             |
| ----------------- | ------------- | ------------------------------------------------ |
| id                | BIGSERIAL     | 主键                                             |
| tenant_id         | BIGINT        | 租户ID                                           |
| client_id         | VARCHAR(128)  | 客户端标识，全局唯一                             |
| client_secret     | VARCHAR(256)  | 客户端密钥（BCrypt 加密存储）                    |
| client_name       | VARCHAR(128)  | 客户端名称                                       |
| grant_types       | VARCHAR(256)  | 允许的授权模式（逗号分隔）                       |
| redirect_uris     | VARCHAR(1024) | 允许的回调地址（逗号分隔）                       |
| scopes            | VARCHAR(512)  | 允许的权限范围（逗号分隔）                       |
| access_token_ttl  | INT           | Access Token 有效期（秒），默认 7200             |
| refresh_token_ttl | INT           | Refresh Token 有效期（秒），默认 2592000（30天） |
| status            | SMALLINT      | 状态（0=停用/1=启用）                            |
| 审计字段          | -             | created_by, updated_by, ..., delete_flag         |

#### 1.4.2 sys_login_log（登录日志）

| 字段        | 类型         | 说明                          |
| ----------- | ------------ | ----------------------------- |
| id          | BIGSERIAL    | 主键                          |
| tenant_id   | BIGINT       | 租户ID                        |
| user_id     | BIGINT       | 用户ID（登录失败时可为空）    |
| username    | VARCHAR(64)  | 登录账号                      |
| login_type  | VARCHAR(32)  | 登录方式：PASSWORD/SMS/OAUTH2 |
| client_id   | VARCHAR(128) | OAuth2 客户端ID               |
| ip_address  | VARCHAR(64)  | 登录IP                        |
| user_agent  | VARCHAR(512) | 浏览器/设备信息               |
| location    | VARCHAR(256) | 登录地点（IP 解析）           |
| status      | SMALLINT     | 结果（0=失败/1=成功）         |
| fail_reason | VARCHAR(256) | 失败原因                      |
| login_at    | TIMESTAMPTZ  | 登录时间                      |

> 登录日志表不做软删除，永久保留，无 delete_flag。

### 1.5 接口列表

#### 1.5.1 获取图形验证码

```
POST /auth/captcha/image
```

**请求体**

无（或空 `{}`）

**响应 data**

```json
{
  "captchaId": "uuid-xxx",
  "image": "data:image/png;base64,..."
}
```

**业务规则**

- 生成随机验证码图片，答案存 Redis（key=`captcha:{captchaId}`，TTL=5分钟）
- captchaId 返回给前端，登录时回传
- 可通过系统配置开关启停验证码

#### 1.5.2 发送短信验证码

```
POST /auth/captcha/sms
```

**请求体**

| 字段     | 类型   | 必填 | 说明   |
| -------- | ------ | ---- | ------ |
| phone    | string | 是   | 手机号 |
| tenantId | long   | 是   | 租户ID |

**响应 data**

```json
{
  "sent": true,
  "expireSeconds": 300
}
```

**业务规则**

- 验证手机号格式
- 发送频率限制：同一手机号 60 秒内不可重复发送
- 验证码存 Redis（key=`sms:{tenantId}:{phone}`，TTL=5分钟）
- 短信发送通过可配置的 SMS 通道（预留接口，首期可不实现）
- 可通过系统配置开关启停短信验证码

#### 1.5.3 密码登录

```
POST /auth/login
```

**请求体**

| 字段        | 类型   | 必填 | 说明                           |
| ----------- | ------ | ---- | ------------------------------ |
| tenantId    | long   | 是   | 租户ID                         |
| username    | string | 是   | 登录账号                       |
| password    | string | 是   | 密码（前端 SHA256 摘要后传输） |
| captchaId   | string | 条件 | 验证码ID（开启验证码时必填）   |
| captchaCode | string | 条件 | 验证码答案（开启验证码时必填） |
| clientId    | string | 是   | OAuth2 客户端ID                |

**响应 data**

```json
{
  "accessToken": "xxx",
  "refreshToken": "yyy",
  "expiresIn": 7200,
  "tokenType": "Bearer",
  "userId": 1,
  "username": "admin",
  "tenantId": 1,
  "forceResetPwd": false
}
```

**业务规则**

1. 校验验证码（如开启）：从 Redis 取 `captcha:{captchaId}`，校验后删除
2. 校验 clientId：必须存在且 status=1，且 grant_types 包含 `password`
3. 校验租户：tenantId 有效
4. 校验用户：按 `(tenant_id, username)` 查 sys_user
   - 用户不存在 → 登录失败（统一提示"账号或密码错误"）
   - 用户已锁定 → 返回"账号已锁定，请N分钟后重试"
   - 用户已停用 → 返回"账号已停用"
5. 校验密码：BCrypt 比对
   - 失败 → 记录错误次数（Redis `login:fail:{tenantId}:{username}`，TTL=N分钟）
   - 错误次数达到阈值 → 锁定账号（Redis `login:lock:{tenantId}:{username}`，TTL=锁定时长）
6. 登录成功：
   - 创建 Sa-Token 会话，存储 userId、tenantId、username、userType
   - 单设备登录检查（如开启）：踢下线同账号其他会话（Sa-Token `kickout`）
   - 异地登录检查：对比最近登录 IP，不同地区则记录告警（登录日志标记 `is_remote=true`）
   - 生成 access_token + refresh_token
   - 写入 sys_login_log（成功）
7. 登录失败：写入 sys_login_log（失败）

#### 1.5.4 短信登录

```
POST /auth/login/sms
```

**请求体**

| 字段     | 类型   | 必填 | 说明            |
| -------- | ------ | ---- | --------------- |
| tenantId | long   | 是   | 租户ID          |
| phone    | string | 是   | 手机号          |
| smsCode  | string | 是   | 短信验证码      |
| clientId | string | 是   | OAuth2 客户端ID |

**响应 data**：同密码登录

**业务规则**

1. 校验短信验证码：从 Redis 取 `sms:{tenantId}:{phone}`，校验后删除
2. 按 `(tenant_id, phone)` 查 sys_user
3. 其余同密码登录（跳过密码校验步骤）

#### 1.5.5 OAuth2 授权端点

```
GET /auth/oauth2/authorize
```

**请求参数（Query）**

| 参数                  | 类型   | 必填 | 说明                    |
| --------------------- | ------ | ---- | ----------------------- |
| response_type         | string | 是   | 固定 `code`             |
| client_id             | string | 是   | 客户端ID                |
| redirect_uri          | string | 是   | 回调地址                |
| scope                 | string | 否   | 请求的权限范围          |
| state                 | string | 推荐 | 防 CSRF 状态参数        |
| code_challenge        | string | 条件 | PKCE 挑战码（SPA 必填） |
| code_challenge_method | string | 条件 | PKCE 方法（S256）       |

**业务规则**

- 校验 client_id 存在且 grant_types 包含 `authorization_code`
- 校验 redirect_uri 在客户端允许的回调列表中
- 用户未登录 → 重定向到登录页
- 用户已登录 → 生成 authorization_code，重定向到 `redirect_uri?code=xxx&state=xxx`
- Authorization Code 有效期 5 分钟，使用后立即失效

#### 1.5.6 OAuth2 Token 端点

```
POST /auth/oauth2/token
```

**请求体（form-urlencoded 或 JSON）**

| 参数          | 类型   | 必填 | 说明                                     |
| ------------- | ------ | ---- | ---------------------------------------- |
| grant_type    | string | 是   | 授权模式                                 |
| client_id     | string | 是   | 客户端ID                                 |
| client_secret | string | 条件 | 客户端密钥（机密客户端必填）             |
| code          | string | 条件 | 授权码（authorization_code）             |
| redirect_uri  | string | 条件 | 回调地址（authorization_code）           |
| code_verifier | string | 条件 | PKCE 验证码（authorization_code + PKCE） |
| username      | string | 条件 | 用户名（password）                       |
| password      | string | 条件 | 密码（password）                         |
| refresh_token | string | 条件 | 刷新令牌（refresh_token）                |
| scope         | string | 否   | 请求范围                                 |
| tenant_id     | long   | 是   | 租户ID                                   |

**响应 data**

```json
{
  "access_token": "xxx",
  "token_type": "Bearer",
  "expires_in": 7200,
  "refresh_token": "yyy",
  "scope": "all"
}
```

**业务规则**

按 grant_type 分别处理：

**authorization_code**：

1. 校验 client_id + authorization_code
2. 校验 redirect_uri 一致
3. PKCE：校验 code_verifier 与 code_challenge 匹配
4. 生成 access_token + refresh_token

**password**：

1. 校验 client_id + client_secret
2. 校验 grant_types 包含 `password`
3. 执行密码登录逻辑（同 1.5.3）
4. 生成 access_token + refresh_token

**client_credentials**：

1. 校验 client_id + client_secret
2. 校验 grant_types 包含 `client_credentials`
3. 生成 access_token（无 refresh_token，无用户会话）

**refresh_token**：

1. 校验 client_id + refresh_token 有效性
2. 生成新 access_token（可配置是否同时刷新 refresh_token）

#### 1.5.7 登出

```
POST /auth/logout
```

**请求体**

无（从 Token 中获取当前用户）

**响应 data**

```json
{ "loggedOut": true }
```

**业务规则**

- 注销当前 Sa-Token 会话
- 清除关联的 access_token 和 refresh_token
- 写入 sys_login_log（action=LOGOUT）

#### 1.5.8 获取当前用户信息

```
POST /auth/user-info
```

**请求体**

无

**响应 data**

```json
{
  "userId": 1,
  "tenantId": 1,
  "username": "admin",
  "name": "管理员",
  "phone": "13800000000",
  "email": "admin@example.com",
  "avatar": "https://...",
  "roles": [{ "roleId": 1, "roleName": "超级管理员" }],
  "permissions": ["sys:user:list", "sys:user:create"],
  "orgs": [
    { "orgId": 1, "orgName": "技术部", "orgType": "DEPT", "isPrimary": true }
  ]
}
```

**业务规则**

- 从 Token 中获取 userId + tenantId
- 查询 sys_user 基础信息
- 调用权限中心获取用户角色列表和权限标识列表（菜单/按钮权限码）
- 查询用户所在组织列表
- 首次登录后前端用此接口获取完整用户信息

### 1.6 OAuth2 客户端管理接口

#### 1.6.1 查询客户端列表

```
POST /api/admin/oauth2-clients/list
```

**请求体**

| 字段     | 类型   | 必填 | 说明           |
| -------- | ------ | ---- | -------------- |
| keyword  | string | 否   | 按名称模糊搜索 |
| status   | int    | 否   | 状态过滤       |
| pageNum  | int    | 否   | 页码           |
| pageSize | int    | 否   | 每页条数       |

#### 1.6.2 创建客户端

```
POST /api/admin/oauth2-clients/create
```

**请求体**

| 字段            | 类型   | 必填 | 说明                                  |
| --------------- | ------ | ---- | ------------------------------------- |
| clientId        | string | 是   | 客户端标识，全局唯一                  |
| clientSecret    | string | 是   | 客户端密钥（明文传入，后端加密存储）  |
| clientName      | string | 是   | 客户端名称                            |
| grantTypes      | string | 是   | 授权模式，逗号分隔                    |
| redirectUris    | string | 否   | 回调地址，逗号分隔                    |
| scopes          | string | 否   | 权限范围，逗号分隔                    |
| accessTokenTtl  | int    | 否   | Access Token TTL（秒），默认 7200     |
| refreshTokenTtl | int    | 否   | Refresh Token TTL（秒），默认 2592000 |

**业务规则**

- clientId 全局唯一（跨租户）
- clientSecret 使用 BCrypt 加密存储
- grantTypes 只允许：authorization_code, password, client_credentials, refresh_token

#### 1.6.3 更新客户端

```
POST /api/admin/oauth2-clients/update
```

**业务规则**

- clientId 不可修改
- 可修改名称、授权模式、回调地址、范围、TTL、状态
- 重置 clientSecret 单独接口

#### 1.6.4 删除客户端

```
POST /api/admin/oauth2-clients/remove
```

**业务规则**

- 软删除
- 删除后该 client 签发的所有 Token 不再可用（Sa-Token 按 client 维度清理）

#### 1.6.5 重置客户端密钥

```
POST /api/admin/oauth2-clients/reset-secret
```

**请求体**

| 字段      | 类型   | 必填 | 说明     |
| --------- | ------ | ---- | -------- |
| id        | long   | 是   | 客户端ID |
| newSecret | string | 是   | 新密钥   |

### 1.7 登录安全配置

以下安全策略通过 `sys_config` 系统配置表管理，支持租户级配置：

| config_key              | 默认值 | 说明                         |
| ----------------------- | ------ | ---------------------------- |
| LOGIN_CAPTCHA_ENABLED   | true   | 是否开启图形验证码           |
| LOGIN_SMS_ENABLED       | false  | 是否开启短信验证码           |
| LOGIN_FAIL_LOCK_COUNT   | 5      | 密码错误锁定次数             |
| LOGIN_FAIL_LOCK_MINUTES | 30     | 锁定时长（分钟）             |
| LOGIN_SINGLE_DEVICE     | false  | 是否开启单设备登录（踢下线） |
| LOGIN_REMOTE_ALERT      | false  | 是否开启异地登录提醒         |

### 1.8 JWT Token 方案

**Token 格式**：Sa-Token 集成 `sa-token-jwt` 插件，使用 **Mixin 模式**（JWT 存部分信息 + Redis 存会话）。

**JWT Payload**：

```json
{
  "loginId": "1",
  "loginType": "login",
  "tenantId": 1,
  "userId": 1,
  "username": "admin",
  "clientId": "admin-web",
  "iat": 1700000000,
  "exp": 1700007200
}
```

**黑名单机制**：

- 登出/踢下线时，将 Token 的 `jti`（JWT ID）加入 Redis 黑名单
- 黑名单 Key：`token:blacklist:{jti}`，TTL = Token 剩余有效期
- Gateway 校验流程：解析 JWT → 检查黑名单 → 校验有效期 → 通过
- 优势：正常请求无需查 Redis（JWT 自校验），仅黑名单 Token 需查 Redis

**预置 OAuth2 Client**：

| client_id        | client_name | grant_types                                 | 用途              |
| ---------------- | ----------- | ------------------------------------------- | ----------------- |
| admin-web        | 管理端前端  | authorization_code, password, refresh_token | 管理后台 SPA 登录 |
| example-web      | 演示端前端  | authorization_code, password, refresh_token | 演示系统 SPA 登录 |
| internal-service | 服务间调用  | client_credentials                          | 微服务 M2M 通信   |

### 1.9 登录日志查询

#### 1.9.1 查询登录日志

```
POST /api/admin/login-logs/list
```

**请求体**

| 字段      | 类型     | 必填 | 说明                  |
| --------- | -------- | ---- | --------------------- |
| username  | string   | 否   | 登录账号              |
| status    | int      | 否   | 结果（0=失败/1=成功） |
| ipAddress | string   | 否   | 登录IP                |
| startTime | datetime | 否   | 开始时间              |
| endTime   | datetime | 否   | 结束时间              |
| pageNum   | int      | 否   | 页码                  |
| pageSize  | int      | 否   | 每页条数              |

---

## 2. 用户管理

### 2.1 功能描述

管理系统用户的完整生命周期。admin-service 是用户数据的事实源，用户变更时同步到权限中心 abstract_user。

### 2.2 数据库表

#### 2.2.1 sys_user

| 字段            | 类型         | 说明                                            |
| --------------- | ------------ | ----------------------------------------------- |
| id              | BIGSERIAL    | 主键                                            |
| tenant_id       | BIGINT       | 租户ID                                          |
| username        | VARCHAR(64)  | 登录账号，租户内唯一                            |
| password        | VARCHAR(256) | 密码（BCrypt）                                  |
| name            | VARCHAR(128) | 用户姓名                                        |
| phone           | VARCHAR(32)  | 手机号（用于短信登录）                          |
| email           | VARCHAR(128) | 邮箱                                            |
| avatar          | VARCHAR(512) | 头像URL                                         |
| gender          | SMALLINT     | 性别（0=未知/1=男/2=女）                        |
| status          | SMALLINT     | 状态（0=停用/1=启用）                           |
| user_type       | INT          | 用户类型（对应权限中心 user_type），默认 1=人员 |
| perm_user_id    | BIGINT       | 权限中心 abstract_user.id（同步后回填）         |
| force_reset_pwd | BOOLEAN      | 是否需要强制修改密码（首次登录/管理员重置后）   |
| 审计字段        | -            | created_by, ..., delete_flag                    |

**唯一约束**：`(tenant_id, username) WHERE delete_flag = 0`、`(tenant_id, phone) WHERE delete_flag = 0 AND phone IS NOT NULL`

### 2.3 与权限中心同步

用户创建/更新/删除时，通过 API + RocketMQ 双通道同步：

| admin-service 事件 | 同步内容                                                                                         |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| 创建用户           | → 权限中心 /api/perm/users/create（user_type, external_id=sys_user.id, name）→ 回填 perm_user_id |
| 更新用户状态       | → 权限中心 /api/perm/users/update（enabled = sys_user.status == 1）                              |
| 更新用户名称       | → 权限中心 /api/perm/users/update（name）                                                        |
| 删除用户           | → 权限中心 /api/perm/users/remove（ids）                                                         |

**同步策略**：

- **主路径 API 同步**：用户写操作时同步调用权限中心（OpenFeign），失败则记录到重试队列
- **备路径 MQ 异步**：同时发送 RocketMQ 消息（USER_SYNC topic），权限中心消费端幂等处理
- **持久化重试队列**：API 和 MQ 均失败时，写入 `sys_sync_retry` 表（action/payload/retry_count/next_retry_at），定时任务每分钟扫描重试，最多 3 次，超限后标记为 FAILED 告警
- **幂等保证**：权限中心按 `(tenant_id, user_type, external_id)` 去重
- **手动重试**：提供管理接口查看/重试失败记录

#### 2.3.1 sys_sync_retry（同步重试队列）

| 字段          | 类型        | 说明                                            |
| ------------- | ----------- | ----------------------------------------------- |
| id            | BIGSERIAL   | 主键                                            |
| tenant_id     | BIGINT      | 租户ID                                          |
| action        | VARCHAR(64) | 同步动作（USER_CREATE/USER_UPDATE/USER_DELETE） |
| payload       | JSONB       | 同步数据（JSON 序列化的请求体）                 |
| retry_count   | INT         | 已重试次数，默认 0                              |
| max_retries   | INT         | 最大重试次数，默认 3                            |
| next_retry_at | TIMESTAMPTZ | 下次重试时间                                    |
| status        | SMALLINT    | 状态（0=待重试/1=成功/2=失败）                  |
| fail_reason   | TEXT        | 失败原因                                        |
| created_at    | TIMESTAMPTZ | 创建时间                                        |
| updated_at    | TIMESTAMPTZ | 更新时间                                        |

> sys_sync_retry 不做软删除，成功记录可定期清理。

### 2.4 接口列表

#### 2.4.1 查询用户列表

```
POST /api/admin/users/list
```

**请求体**

| 字段     | 类型   | 必填 | 说明                         |
| -------- | ------ | ---- | ---------------------------- |
| keyword  | string | 否   | 按账号/姓名/手机号模糊搜索   |
| status   | int    | 否   | 状态过滤                     |
| orgId    | long   | 否   | 所属组织ID（查该组织下用户） |
| pageNum  | int    | 否   | 页码                         |
| pageSize | int    | 否   | 每页条数                     |

**响应 data.rows[]**

```json
{
  "id": 1,
  "username": "admin",
  "name": "管理员",
  "phone": "138****0000",
  "email": "admin@example.com",
  "status": 1,
  "orgs": [
    { "orgId": 1, "orgName": "技术部", "orgType": "DEPT", "isPrimary": true }
  ],
  "createdAt": "2026-01-01T00:00:00Z"
}
```

**业务规则**

- 手机号脱敏展示（中间 4 位 \*）
- 支持按组织筛选（查 sys_user_org 关联）
- 不返回 password 字段

#### 2.4.2 查询用户详情

```
POST /api/admin/users/detail
```

**请求体**

| 字段 | 类型 | 必填 | 说明   |
| ---- | ---- | ---- | ------ |
| id   | long | 是   | 用户ID |

**响应 data**：完整用户信息 + 所在组织列表 + 角色列表（来自权限中心）

#### 2.4.3 创建用户

```
POST /api/admin/users/create
```

**请求体**

| 字段     | 类型   | 必填 | 说明                         |
| -------- | ------ | ---- | ---------------------------- |
| username | string | 是   | 登录账号                     |
| password | string | 是   | 初始密码（SHA256 摘要）      |
| name     | string | 是   | 用户姓名                     |
| phone    | string | 否   | 手机号                       |
| email    | string | 否   | 邮箱                         |
| orgIds   | long[] | 否   | 关联的组织ID列表             |
| roleIds  | long[] | 否   | 关联的角色ID列表（权限中心） |

**业务规则**

1. 校验 username 租户内唯一
2. 密码 SHA256 → BCrypt 加密存储
3. 写入 sys_user（force_reset_pwd = true）
4. 写入 sys_user_org 关联
5. 同步到权限中心 → 回填 perm_user_id
6. 若有 roleIds → 调用权限中心写入 user_role
7. 若有 orgIds → 组织对应的 ORG 角色也同步到权限中心 user_role
8. 写入审计日志

#### 2.4.4 更新用户

```
POST /api/admin/users/update
```

**请求体**

| 字段    | 类型   | 必填 | 说明               |
| ------- | ------ | ---- | ------------------ |
| id      | long   | 是   | 用户ID             |
| name    | string | 否   | 用户姓名           |
| phone   | string | 否   | 手机号             |
| email   | string | 否   | 邮箱               |
| orgIds  | long[] | 否   | 组织ID列表（全量） |
| roleIds | long[] | 否   | 角色ID列表（全量） |

**业务规则**

- username 不可修改
- password 通过专门的重置密码接口修改
- orgIds/roleIds 传入时为全量替换（先删后增）
- 名称变更 → 同步到权限中心
- 组织变更 → 同步到权限中心（ORG 角色关联变更）
- 角色变更 → 同步到权限中心（user_role 变更）

#### 2.4.5 删除用户

```
POST /api/admin/users/remove
```

**请求体**

```json
{ "ids": [1, 2, 3] }
```

**业务规则**

- 软删除 sys_user
- 级联软删 sys_user_org
- 同步到权限中心删除 abstract_user（级联清理权限关联）
- 不可删除当前登录用户

#### 2.4.6 重置密码

```
POST /api/admin/users/reset-password
```

**请求体**

| 字段        | 类型   | 必填 | 说明                  |
| ----------- | ------ | ---- | --------------------- |
| id          | long   | 是   | 用户ID                |
| newPassword | string | 是   | 新密码（SHA256 摘要） |

**业务规则**

- 管理员操作，重置后 force_reset_pwd = true
- 用户下次登录时前端检测 forceResetPwd=true → 强制跳转修改密码页

#### 2.4.7 修改个人密码

```
POST /api/admin/users/change-password
```

**请求体**

| 字段        | 类型   | 必填 | 说明                  |
| ----------- | ------ | ---- | --------------------- |
| oldPassword | string | 是   | 旧密码（SHA256 摘要） |
| newPassword | string | 是   | 新密码（SHA256 摘要） |

**业务规则**

- 从 Token 中获取当前 userId
- 校验旧密码正确
- 新密码不可与旧密码相同
- 修改成功后 force_reset_pwd = false

#### 2.4.8 启停用户

```
POST /api/admin/users/change-status
```

**请求体**

| 字段   | 类型 | 必填 | 说明                  |
| ------ | ---- | ---- | --------------------- |
| id     | long | 是   | 用户ID                |
| status | int  | 是   | 状态（0=停用/1=启用） |

**业务规则**

- 同步到权限中心（enabled = status == 1）
- 停用时强制下线该用户所有会话（Sa-Token `kickout`）

---

## 3. 组织管理

### 3.1 功能描述

统一组织模型，所有组织节点（部门/团队/项目组等）同表存储，org_type 作为可选标签。支持多棵独立组织树，每棵树可独立配置单/多关联、是否默认树。职位树特殊处理：关联职位不影响组织树的单关联约束。

### 3.2 数据库表

#### 3.2.1 sys_org

| 字段         | 类型         | 说明                                          |
| ------------ | ------------ | --------------------------------------------- |
| id           | BIGSERIAL    | 主键                                          |
| tenant_id    | BIGINT       | 租户ID                                        |
| parent_id    | BIGINT       | 父节点ID，NULL=根节点                         |
| org_type     | VARCHAR(32)  | 组织类型标签（字典管理），仅分类不影响逻辑    |
| code         | VARCHAR(64)  | 组织编码                                      |
| name         | VARCHAR(128) | 组织名称                                      |
| path         | VARCHAR(512) | 物化路径（如 /1/3/7/），加速树查询            |
| level        | INT          | 层级深度（根节点=1），最大 10 层              |
| sort_order   | INT          | 排序，默认 0                                  |
| leader_id    | BIGINT       | 负责人 sys_user.id                            |
| status       | SMALLINT     | 状态（0=停用/1=启用）                         |
| perm_role_id | BIGINT       | 权限中心对应的 abstract_role.id（同步后回填） |
| 审计字段     | -            | created_by, ..., delete_flag                  |

**唯一约束**：`(tenant_id, code) WHERE delete_flag = 0`

> org_type 仅作为标签（字典管理），不再作为唯一约束的一部分。编码全局唯一（租户内）。

#### 3.2.2 sys_org_tree_config（组织树配置）

| 字段         | 类型         | 说明                                              |
| ------------ | ------------ | ------------------------------------------------- |
| id           | BIGSERIAL    | 主键                                              |
| tenant_id    | BIGINT       | 租户ID                                            |
| root_org_id  | BIGINT       | 根组织节点ID（sys_org.id，parent_id=NULL 的节点） |
| tree_name    | VARCHAR(128) | 树名称（如"总部组织架构"、"岗位体系"）            |
| tree_type    | VARCHAR(32)  | 树类型：ORG=组织树 / POSITION=职位树              |
| is_default   | BOOLEAN      | 是否默认组织树（每租户最多一棵）                  |
| single_assoc | BOOLEAN      | 是否单关联（用户在该树下只能属于一个节点）        |
| 审计字段     | -            | created_by, ..., delete_flag                      |

**约束**：

- `(tenant_id, root_org_id) WHERE delete_flag = 0` 唯一
- `(tenant_id) WHERE is_default = true AND delete_flag = 0` 唯一（每租户最多一棵默认树）

**规则**：

- **组织树 (ORG)**：可配置 single_assoc=true（用户只能属于该树的一个节点）
- **职位树 (POSITION)**：始终 single_assoc=false，关联职位不占用组织树的单关联配额
- **默认树**：标记 is_default=true 的组织树，创建用户时必须关联到该树的某个节点

#### 3.2.3 sys_user_org（用户-组织关联）

| 字段       | 类型      | 说明                                     |
| ---------- | --------- | ---------------------------------------- |
| id         | BIGSERIAL | 主键                                     |
| tenant_id  | BIGINT    | 租户ID                                   |
| user_id    | BIGINT    | 用户ID                                   |
| org_id     | BIGINT    | 组织ID                                   |
| is_primary | BOOLEAN   | 是否主组织（用户在默认组织树下的主归属） |
| 审计字段   | -         | created_by, ..., delete_flag             |

**唯一约束**：`(tenant_id, user_id, org_id) WHERE delete_flag = 0`

### 3.3 与权限中心同步

| admin-service 事件 | 权限中心操作                                                            |
| ------------------ | ----------------------------------------------------------------------- |
| 创建组织           | → 创建 abstract_role（role_type=ORG, name=org.name）→ 回填 perm_role_id |
| 更新组织名称       | → 更新 abstract_role.name                                               |
| 启停组织           | → 更新 abstract_role.status                                             |
| 删除组织           | → 删除 abstract_role                                                    |
| 用户加入组织       | → 权限中心 user_role(ROLE, perm_role_id) 写入                           |
| 用户移出组织       | → 权限中心 user_role 移除                                               |

### 3.4 接口列表

#### 3.4.1 查询组织树

```
POST /api/admin/orgs/tree
```

**请求体**

| 字段     | 类型   | 必填 | 说明                                        |
| -------- | ------ | ---- | ------------------------------------------- |
| treeType | string | 否   | 树类型过滤（ORG/POSITION），NULL=返回所有树 |
| keyword  | string | 否   | 按名称/编码搜索（搜索时返回平铺列表）       |
| status   | int    | 否   | 状态过滤                                    |

**响应 data**

```json
[
  {
    "id": 1,
    "parentId": null,
    "orgType": "DEPT",
    "code": "root_dept",
    "name": "总公司",
    "level": 1,
    "sortOrder": 1,
    "leaderName": "张总",
    "status": 1,
    "children": [
      {
        "id": 2,
        "parentId": 1,
        "orgType": "DEPT",
        "code": "tech_dept",
        "name": "技术部",
        "level": 2,
        "children": []
      }
    ]
  }
]
```

**业务规则**

- 同一根节点下的节点形成一棵独立的组织树
- 不同树（不同根节点）在顶层并列返回
- keyword 搜索时返回平铺列表（非树形），展示完整路径
- 支持按 tree_type（ORG/POSITION）筛选

#### 3.4.2 查询组织详情

```
POST /api/admin/orgs/detail
```

**请求体**

| 字段 | 类型 | 必填 | 说明   |
| ---- | ---- | ---- | ------ |
| id   | long | 是   | 组织ID |

**响应 data**：组织基础信息 + 直接子节点列表 + 关联用户数 + 负责人信息

#### 3.4.3 创建组织

```
POST /api/admin/orgs/create
```

**请求体**

| 字段      | 类型   | 必填 | 说明                  |
| --------- | ------ | ---- | --------------------- |
| parentId  | long   | 否   | 父节点ID，NULL=根节点 |
| orgType   | string | 否   | 组织类型标签          |
| code      | string | 是   | 组织编码              |
| name      | string | 是   | 组织名称              |
| sortOrder | int    | 否   | 排序                  |
| leaderId  | long   | 否   | 负责人ID              |

**业务规则**

1. `(tenant_id, code)` 唯一
2. parentId 若非空，须存在且在同一棵树内（共享同一根节点）
3. 自动计算 path 和 level（level 不可超过 10）
4. 若 parentId=NULL（根节点），自动创建 sys_org_tree_config 记录
5. 同步到权限中心创建 abstract_role(ORG)

#### 3.4.4 更新组织

```
POST /api/admin/orgs/update
```

**业务规则**

- org_type 和 code 不可修改
- 可修改 name、sortOrder、leaderId、status
- 名称/状态变更同步到权限中心
- 根节点可额外修改树配置（tree_name / single_assoc / is_default）

#### 3.4.5 移动组织

```
POST /api/admin/orgs/move
```

**请求体**

| 字段        | 类型 | 必填 | 说明       |
| ----------- | ---- | ---- | ---------- |
| id          | long | 是   | 组织ID     |
| newParentId | long | 否   | 新父节点ID |
| sortOrder   | int  | 否   | 新排序     |

**业务规则**

- 不能移动为自己的子节点（防环）
- 目标父节点须在同一棵树内（同根节点）
- 不能跨树移动
- 更新 path 和 level（含所有子节点递归更新）
- 移动后 level 不可超过 10

#### 3.4.6 删除组织

```
POST /api/admin/orgs/remove
```

**业务规则**

- 有子节点时拒绝删除（需先删子节点或移走）
- 级联软删 sys_user_org 关联
- 同步到权限中心删除 abstract_role
- 权限中心级联清理 user_role、role_resource_permission

#### 3.4.7 查询组织成员

```
POST /api/admin/orgs/users/list
```

**请求体**

| 字段            | 类型    | 必填 | 说明                           |
| --------------- | ------- | ---- | ------------------------------ |
| orgId           | long    | 是   | 组织ID                         |
| includeChildren | boolean | 否   | 是否包含子组织成员，默认 false |
| pageNum         | int     | 否   | 页码                           |
| pageSize        | int     | 否   | 每页条数                       |

#### 3.4.8 管理组织成员

```
POST /api/admin/orgs/users/update
```

**请求体**

| 字段          | 类型   | 必填 | 说明                 |
| ------------- | ------ | ---- | -------------------- |
| orgId         | long   | 是   | 组织ID               |
| addUserIds    | long[] | 否   | 新增关联的用户ID列表 |
| removeUserIds | long[] | 否   | 移除关联的用户ID列表 |

**业务规则**

- 新增关联时，校验该树的 single_assoc 配置：若为 true 且用户已关联该树其他节点 → 拒绝（需先移除旧关联）
- 职位树（tree_type=POSITION）不受单关联限制
- 新增关联时，同步到权限中心写入 user_role(ROLE, perm_role_id)
- 移除关联时，同步到权限中心删除 user_role

---

## 4. 菜单管理

### 4.1 功能描述

管理系统菜单树。admin-service 是菜单数据的事实源，菜单变更时同步到权限中心作为 resource_entity（MENU/BUTTON 类型）。

### 4.2 数据库表

#### 4.2.1 sys_menu

| 字段             | 类型         | 说明                                             |
| ---------------- | ------------ | ------------------------------------------------ |
| id               | BIGSERIAL    | 主键                                             |
| tenant_id        | BIGINT       | 租户ID                                           |
| parent_id        | BIGINT       | 父菜单ID，NULL=根                                |
| menu_type        | VARCHAR(16)  | 类型：DIR(目录)/MENU(菜单)/BUTTON(按钮)          |
| service_code     | VARCHAR(64)  | 所属服务标识（admin-service/example-service 等） |
| name             | VARCHAR(64)  | 菜单名称                                         |
| path             | VARCHAR(256) | 路由路径                                         |
| component        | VARCHAR(256) | 前端组件路径                                     |
| icon             | VARCHAR(64)  | 图标                                             |
| perm_code        | VARCHAR(128) | 权限标识（同步到权限中心 resource_entity.code）  |
| sort_order       | INT          | 排序                                             |
| visible          | BOOLEAN      | 是否在菜单中可见（隐藏路由仍可访问）             |
| status           | SMALLINT     | 状态（0=停用/1=启用）                            |
| is_external      | BOOLEAN      | 是否外链（新窗口打开）                           |
| is_frame         | BOOLEAN      | 是否 iframe 嵌入（门户归集外部系统页面）         |
| is_cache         | BOOLEAN      | 是否缓存（keep-alive）                           |
| extra            | JSONB        | 路由元信息（query 参数等）                       |
| perm_resource_id | BIGINT       | 权限中心 resource_entity.id（同步后回填）        |
| 审计字段         | -            | created_by, ..., delete_flag                     |

**菜单树深度**：默认限制 7 层，可通过 sys_config 配置 `MENU_MAX_DEPTH` 调整或去除限制。

### 4.3 与权限中心同步

| admin-service 事件 | 权限中心操作                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------ |
| 创建菜单/按钮      | → 创建 resource_entity（resource_type=MENU/BUTTON, code=perm_code）→ 回填 perm_resource_id |
| 更新菜单名称/编码  | → 更新 resource_entity                                                                     |
| 启停菜单           | → 更新 resource_entity.status                                                              |
| 删除菜单           | → 删除 resource_entity                                                                     |

**同步细节**：

- menu_type=DIR 不同步（纯 UI 目录，无权限意义）
- menu_type=MENU → resource_type=MENU
- menu_type=BUTTON → resource_type=BUTTON
- 树形父子关系也同步到权限中心（resource_entity.parent_id）

### 4.4 接口列表

#### 4.4.1 查询菜单树

```
POST /api/admin/menus/tree
```

**请求体**

| 字段     | 类型   | 必填 | 说明                |
| -------- | ------ | ---- | ------------------- |
| menuType | string | 否   | 类型过滤            |
| keyword  | string | 否   | 按名称/权限标识搜索 |
| status   | int    | 否   | 状态过滤            |

#### 4.4.2 查询用户可用菜单（前端动态路由）

```
POST /api/admin/menus/user-menus
```

**请求体**

无（从 Token 中获取 userId）

**响应 data**

```json
[
  {
    "id": 1,
    "parentId": null,
    "menuType": "DIR",
    "name": "系统管理",
    "path": "/system",
    "icon": "setting",
    "sortOrder": 1,
    "children": [
      {
        "id": 2,
        "parentId": 1,
        "menuType": "MENU",
        "name": "用户管理",
        "path": "/system/user",
        "component": "system/user/index",
        "icon": "user",
        "permCode": "sys:user:list",
        "visible": true,
        "isCache": true,
        "children": [
          {
            "id": 3,
            "menuType": "BUTTON",
            "name": "新增用户",
            "permCode": "sys:user:create"
          }
        ]
      }
    ]
  }
]
```

**业务规则**

1. 查询用户有权访问的菜单列表：
   - 从权限中心获取用户所有有效的 MENU/BUTTON 类型资源的 perm_code 列表
   - 用 perm_code 匹配 sys_menu 表
   - 过滤 status=1 的菜单
2. 补全父级 DIR 节点（确保树结构完整）
3. 按 sort_order 排序
4. 此接口是前端动态路由的数据源

#### 4.4.3 创建菜单

```
POST /api/admin/menus/create
```

**请求体**

| 字段        | 类型    | 必填 | 说明                         |
| ----------- | ------- | ---- | ---------------------------- |
| parentId    | long    | 否   | 父菜单ID                     |
| menuType    | string  | 是   | DIR/MENU/BUTTON              |
| serviceCode | string  | 否   | 所属服务，默认 admin-service |
| name        | string  | 是   | 菜单名称                     |
| path        | string  | 条件 | 路由路径（DIR/MENU 必填）    |
| component   | string  | 条件 | 组件路径（MENU 必填）        |
| icon        | string  | 否   | 图标                         |
| permCode    | string  | 条件 | 权限标识（MENU/BUTTON 必填） |
| sortOrder   | int     | 否   | 排序                         |
| visible     | boolean | 否   | 是否可见，默认 true          |
| isExternal  | boolean | 否   | 是否外链                     |
| isFrame     | boolean | 否   | 是否 iframe 嵌入             |
| isCache     | boolean | 否   | 是否缓存                     |

**业务规则**

- BUTTON 必须挂在 MENU 下
- permCode 在租户内唯一
- 菜单树深度不超过 MENU_MAX_DEPTH 配置（默认 7 层）
- MENU/BUTTON 创建后自动同步到权限中心

#### 4.4.4 更新菜单

```
POST /api/admin/menus/update
```

**业务规则**

- menuType 不可修改
- permCode 变更时同步更新权限中心 resource_entity.code
- 名称变更同步到权限中心

#### 4.4.5 删除菜单

```
POST /api/admin/menus/remove
```

**业务规则**

- 有子节点时拒绝删除
- 级联同步删除权限中心的 resource_entity
- 权限中心级联清理 role_resource_permission

#### 4.4.6 获取权限标识列表

```
POST /api/admin/menus/perm-codes
```

**响应 data**

```json
["sys:user:list", "sys:user:create", "sys:user:update", "sys:user:delete"]
```

**业务规则**

- 返回当前租户所有菜单的 permCode 列表（扁平）
- 用于角色权限配置时的选择下拉

---

## 5. 角色管理（复用权限中心）

### 5.1 功能描述

管理服务不自建角色表，直接封装权限中心的角色相关接口，为管理端提供统一的角色管理入口。

**分工原则**：

- **角色 CRUD**：admin-service 代理调用权限中心接口
- **角色权限配置**（角色-资源-操作）：前端经 Gateway 直接调权限中心 API，不走 admin-service 代理
- **角色-用户关联**：admin-service 代理，因为需要合并 admin 侧用户数据
- **角色与组织**：组织已同步为 ORG 角色，不做额外角色-组织绑定

### 5.2 接口列表

#### 5.2.1 查询角色列表

```
POST /api/admin/roles/list
```

**业务规则**：代理调用权限中心 `/api/perm/roles/list`

#### 5.2.2 查询角色详情

```
POST /api/admin/roles/detail
```

**业务规则**：代理调用权限中心 + 查询该角色下的用户数量

#### 5.2.3 创建角色

```
POST /api/admin/roles/create
```

**业务规则**：代理调用权限中心 `/api/perm/roles/create`

#### 5.2.4 更新角色

```
POST /api/admin/roles/update
```

**业务规则**：代理调用权限中心 `/api/perm/roles/update`

#### 5.2.5 删除角色

```
POST /api/admin/roles/remove
```

**业务规则**：代理调用权限中心 `/api/perm/roles/remove`

#### 5.2.6 查询角色下的用户

```
POST /api/admin/roles/users/list
```

**请求体**

| 字段     | 类型   | 必填 | 说明              |
| -------- | ------ | ---- | ----------------- |
| roleId   | long   | 是   | 角色ID            |
| keyword  | string | 否   | 按用户名/姓名搜索 |
| pageNum  | int    | 否   | 页码              |
| pageSize | int    | 否   | 每页条数          |

**业务规则**：

1. 调用权限中心获取该角色关联的 user_id 列表
2. 批量查询 admin-service 的 sys_user 信息返回

#### 5.2.7 管理角色下的用户

```
POST /api/admin/roles/users/update
```

**请求体**

| 字段          | 类型   | 必填 | 说明                 |
| ------------- | ------ | ---- | -------------------- |
| roleId        | long   | 是   | 角色ID               |
| addUserIds    | long[] | 否   | 新增关联的用户ID列表 |
| removeUserIds | long[] | 否   | 移除关联的用户ID列表 |

**业务规则**：

- 将 sys_user.id 转换为 perm_user_id，调用权限中心写入/删除 user_role

> 注：角色权限配置（角色-资源-操作）由前端直接通过 Gateway 调用权限中心接口，不经 admin-service 代理。

---

## 6. 字典管理

### 6.1 功能描述

管理系统字典类型和字典数据。字典用于下拉选择、状态展示等场景，支持 Redis 缓存。

### 6.2 数据库表

#### 6.2.1 sys_dict_type

| 字段      | 类型         | 说明               |
| --------- | ------------ | ------------------ |
| id        | BIGSERIAL    | 主键               |
| tenant_id | BIGINT       | 租户ID             |
| dict_type | VARCHAR(64)  | 字典类型编码，唯一 |
| dict_name | VARCHAR(128) | 字典名称           |
| status    | SMALLINT     | 状态               |
| remark    | VARCHAR(512) | 备注               |
| 审计字段  | -            | ...                |

#### 6.2.2 sys_dict_data

| 字段       | 类型         | 说明               |
| ---------- | ------------ | ------------------ |
| id         | BIGSERIAL    | 主键               |
| tenant_id  | BIGINT       | 租户ID             |
| dict_type  | VARCHAR(64)  | 关联的字典类型编码 |
| dict_label | VARCHAR(128) | 字典标签（显示值） |
| dict_value | VARCHAR(128) | 字典值（存储值）   |
| sort_order | INT          | 排序               |
| css_class  | VARCHAR(128) | 样式类名           |
| list_class | VARCHAR(128) | 表格回显样式       |
| is_default | BOOLEAN      | 是否默认值         |
| status     | SMALLINT     | 状态               |
| remark     | VARCHAR(512) | 备注               |
| 审计字段   | -            | ...                |

### 6.3 接口列表

| #   | 接口                              | 说明                       |
| --- | --------------------------------- | -------------------------- |
| 1   | POST /api/admin/dict-types/list   | 查询字典类型列表（分页）   |
| 2   | POST /api/admin/dict-types/create | 创建字典类型               |
| 3   | POST /api/admin/dict-types/update | 更新字典类型               |
| 4   | POST /api/admin/dict-types/remove | 删除字典类型（含级联数据） |
| 5   | POST /api/admin/dict-data/list    | 查询字典数据列表           |
| 6   | POST /api/admin/dict-data/create  | 创建字典数据               |
| 7   | POST /api/admin/dict-data/update  | 更新字典数据               |
| 8   | POST /api/admin/dict-data/remove  | 删除字典数据               |

**缓存策略**：

- Redis 缓存 Key：`dict:{tenantId}:{dictType}`
- TTL：30 分钟
- 字典数据变更时清除对应缓存

---

## 7. 通知管理

### 7.1 功能描述

系统公告 + 站内信。公告面向全体用户或指定用户组，站内信为一对一消息。

### 7.2 数据库表

#### 7.2.1 sys_notice

| 字段         | 类型         | 说明                             |
| ------------ | ------------ | -------------------------------- |
| id           | BIGSERIAL    | 主键                             |
| tenant_id    | BIGINT       | 租户ID                           |
| notice_type  | VARCHAR(32)  | 类型：ANNOUNCEMENT/NOTIFICATION  |
| title        | VARCHAR(256) | 标题                             |
| content      | TEXT         | 内容（支持富文本）               |
| target_type  | VARCHAR(32)  | 目标类型：ALL/ORG/USER           |
| target_ids   | JSONB        | 目标ID列表（ORG/USER 时有值）    |
| status       | SMALLINT     | 状态（0=草稿/1=已发布/2=已撤回） |
| published_at | TIMESTAMPTZ  | 发布时间                         |
| 审计字段     | -            | ...                              |

#### 7.2.2 sys_user_notice（用户通知状态）

| 字段      | 类型        | 说明     |
| --------- | ----------- | -------- |
| id        | BIGSERIAL   | 主键     |
| tenant_id | BIGINT      | 租户ID   |
| notice_id | BIGINT      | 通知ID   |
| user_id   | BIGINT      | 用户ID   |
| is_read   | BOOLEAN     | 是否已读 |
| read_at   | TIMESTAMPTZ | 阅读时间 |

### 7.3 接口列表

| #   | 接口                                 | 说明           |
| --- | ------------------------------------ | -------------- |
| 1   | POST /api/admin/notices/list         | 管理端通知列表 |
| 2   | POST /api/admin/notices/create       | 创建通知       |
| 3   | POST /api/admin/notices/update       | 更新通知       |
| 4   | POST /api/admin/notices/remove       | 删除通知       |
| 5   | POST /api/admin/notices/publish      | 发布通知       |
| 6   | POST /api/admin/notices/my-list      | 我的通知列表   |
| 7   | POST /api/admin/notices/read         | 标记已读       |
| 8   | POST /api/admin/notices/unread-count | 未读数量       |

---

## 8. 文件管理

### 8.1 功能描述

基于 S3 标准协议的文件上传下载服务。支持 MinIO、阿里云 OSS 等 S3 兼容存储。

### 8.2 数据库表

#### 8.2.1 sys_file

| 字段          | 类型          | 说明                    |
| ------------- | ------------- | ----------------------- |
| id            | BIGSERIAL     | 主键                    |
| tenant_id     | BIGINT        | 租户ID                  |
| original_name | VARCHAR(256)  | 原始文件名              |
| file_name     | VARCHAR(256)  | 存储文件名（UUID）      |
| file_path     | VARCHAR(512)  | 存储路径（bucket/path） |
| file_url      | VARCHAR(1024) | 访问URL                 |
| file_size     | BIGINT        | 文件大小（字节）        |
| file_type     | VARCHAR(128)  | MIME 类型               |
| bucket_name   | VARCHAR(128)  | 存储桶名称              |
| 审计字段      | -             | ...                     |

### 8.3 接口列表

| #   | 接口                               | 说明                            |
| --- | ---------------------------------- | ------------------------------- |
| 1   | POST /api/admin/files/upload       | 文件上传（multipart/form-data） |
| 2   | POST /api/admin/files/list         | 文件列表查询                    |
| 3   | POST /api/admin/files/remove       | 删除文件                        |
| 4   | GET /api/admin/files/download/{id} | 文件下载（例外：GET 请求）      |

**注**：文件上传接口使用 `multipart/form-data`，是唯一不遵循 POST+JSON 约定的接口。文件下载使用 GET 方法。

**权限管控**（待定）：

- 文件上传/删除需要对应权限控制
- 文件下载可能需要按权限限制可见范围（如部门/项目组级别）
- 具体对接方式待后续细化

---

## 9. 审计日志

### 9.1 功能描述

记录用户在管理服务中的操作行为。通过 AOP 切面自动采集，与权限中心的 operation_log 独立。

### 9.2 数据库表

#### 9.2.1 sys_audit_log

| 字段          | 类型         | 说明                             |
| ------------- | ------------ | -------------------------------- |
| id            | BIGSERIAL    | 主键                             |
| tenant_id     | BIGINT       | 租户ID                           |
| user_id       | BIGINT       | 操作者ID                         |
| username      | VARCHAR(64)  | 操作者账号                       |
| module        | VARCHAR(64)  | 模块名（用户管理/组织管理等）    |
| action        | VARCHAR(64)  | 操作类型（CREATE/UPDATE/DELETE） |
| target_type   | VARCHAR(64)  | 目标类型（USER/ORG/MENU 等）     |
| target_id     | VARCHAR(64)  | 目标ID                           |
| summary       | VARCHAR(512) | 操作摘要                         |
| ip_address    | VARCHAR(64)  | 操作者IP                         |
| request_id    | VARCHAR(64)  | 请求追踪ID                       |
| request_url   | VARCHAR(256) | 请求路径                         |
| request_body  | TEXT         | 请求体（脱敏后）                 |
| response_code | INT          | 响应码                           |
| cost_time     | INT          | 耗时（毫秒）                     |
| created_at    | TIMESTAMPTZ  | 操作时间                         |

> 审计日志表不做软删除，永久保留。

### 9.3 接口列表

| #   | 接口                              | 说明                 |
| --- | --------------------------------- | -------------------- |
| 1   | POST /api/admin/audit-logs/list   | 查询审计日志（分页） |
| 2   | POST /api/admin/audit-logs/detail | 查询日志详情         |
| 3   | POST /api/admin/audit-logs/export | 导出审计日志         |

### 9.4 采集方式

- 使用自定义注解 `@AuditLog(module="用户管理", action="CREATE")` 标记需要记录的接口
- AOP 切面自动采集请求/响应信息
- 异步写入（避免影响主流程性能）
- 请求体中的密码等敏感字段自动脱敏

---

## 10. 任务调度

### 10.1 功能描述

基于 Spring Scheduler 的轻量级定时任务管理。支持任务注册、Cron 表达式配置、执行日志记录。兼演示定时任务中的权限控制。

**外部任务归集**：

- 支持外部系统通过 API 注册定时任务到本系统统一管理
- 任务创建者需要具备 `sys:job:create` 权限
- 定时任务执行时需指定执行身份（run_as_user_id），决定任务运行的权限上下文
- 无显式指定时，以任务创建者身份执行

### 10.2 数据库表

#### 10.2.1 sys_job

| 字段            | 类型         | 说明                              |
| --------------- | ------------ | --------------------------------- |
| id              | BIGSERIAL    | 主键                              |
| tenant_id       | BIGINT       | 租户ID                            |
| job_name        | VARCHAR(128) | 任务名称                          |
| job_group       | VARCHAR(64)  | 任务分组                          |
| invoke_target   | VARCHAR(256) | 调用目标（Bean 名称 + 方法）      |
| cron_expression | VARCHAR(128) | Cron 表达式                       |
| misfire_policy  | SMALLINT     | 错过策略（0=忽略/1=立即执行一次） |
| run_as_user_id  | BIGINT       | 执行身份用户ID（NULL=任务创建者） |
| status          | SMALLINT     | 状态（0=停用/1=启用）             |
| remark          | VARCHAR(512) | 备注                              |
| 审计字段        | -            | ...                               |

#### 10.2.2 sys_job_log

| 字段          | 类型         | 说明                      |
| ------------- | ------------ | ------------------------- |
| id            | BIGSERIAL    | 主键                      |
| tenant_id     | BIGINT       | 租户ID                    |
| job_id        | BIGINT       | 任务ID                    |
| job_name      | VARCHAR(128) | 任务名称                  |
| invoke_target | VARCHAR(256) | 调用目标                  |
| status        | SMALLINT     | 执行结果（0=失败/1=成功） |
| message       | TEXT         | 执行信息/异常             |
| cost_time     | INT          | 耗时（毫秒）              |
| created_at    | TIMESTAMPTZ  | 执行时间                  |

### 10.3 接口列表

| #   | 接口                               | 说明         |
| --- | ---------------------------------- | ------------ |
| 1   | POST /api/admin/jobs/list          | 查询任务列表 |
| 2   | POST /api/admin/jobs/create        | 创建定时任务 |
| 3   | POST /api/admin/jobs/update        | 更新定时任务 |
| 4   | POST /api/admin/jobs/remove        | 删除定时任务 |
| 5   | POST /api/admin/jobs/change-status | 启停定时任务 |
| 6   | POST /api/admin/jobs/run-once      | 立即执行一次 |
| 7   | POST /api/admin/jobs/logs/list     | 查询执行日志 |

---

## 11. 系统设置

### 11.1 功能描述

租户级系统配置管理。存储认证安全策略、系统参数等配置项。

### 11.2 数据库表

#### 11.2.1 sys_config

| 字段         | 类型         | 说明                     |
| ------------ | ------------ | ------------------------ |
| id           | BIGSERIAL    | 主键                     |
| tenant_id    | BIGINT       | 租户ID                   |
| config_key   | VARCHAR(128) | 配置键                   |
| config_value | JSONB        | 配置值（JSON）           |
| config_name  | VARCHAR(256) | 配置名称（展示用）       |
| remark       | VARCHAR(512) | 备注                     |
| is_system    | BOOLEAN      | 是否系统内置（不可删除） |
| 审计字段     | -            | ...                      |

**唯一约束**：`(tenant_id, config_key) WHERE delete_flag = 0`

### 11.3 预置配置项

| config_key              | 默认值   | 说明                     |
| ----------------------- | -------- | ------------------------ |
| LOGIN_CAPTCHA_ENABLED   | true     | 是否开启图形验证码       |
| LOGIN_SMS_ENABLED       | false    | 是否开启短信验证码       |
| LOGIN_FAIL_LOCK_COUNT   | 5        | 密码错误锁定次数         |
| LOGIN_FAIL_LOCK_MINUTES | 30       | 锁定时长（分钟）         |
| LOGIN_SINGLE_DEVICE     | false    | 单设备登录               |
| LOGIN_REMOTE_ALERT      | false    | 异地登录提醒             |
| MENU_MAX_DEPTH          | 7        | 菜单树最大深度           |
| FILE_UPLOAD_MAX_SIZE    | 10485760 | 文件上传大小限制（字节） |
| FILE_ALLOWED_TYPES      | [...]    | 允许的文件类型列表       |

### 11.4 接口列表

| #   | 接口                               | 说明            |
| --- | ---------------------------------- | --------------- |
| 1   | POST /api/admin/configs/list       | 查询配置列表    |
| 2   | POST /api/admin/configs/update     | 更新配置值      |
| 3   | POST /api/admin/configs/get-by-key | 按 key 查询配置 |

**业务规则**

- is_system=true 的配置不可删除
- config_value 变更时清除相关缓存
- 配置项 Redis 缓存 Key：`config:{tenantId}:{configKey}`，TTL=10分钟

---

## 模块汇总

| #   | 模块     | 接口数 | 数据库表                                   |
| --- | -------- | ------ | ------------------------------------------ |
| 1   | 认证     | 13     | sys_oauth2_client, sys_login_log           |
| 2   | 用户管理 | 8      | sys_user, sys_sync_retry                   |
| 3   | 组织管理 | 8      | sys_org, sys_org_tree_config, sys_user_org |
| 4   | 菜单管理 | 6      | sys_menu                                   |
| 5   | 角色管理 | 7      | -（复用权限中心）                          |
| 6   | 字典管理 | 8      | sys_dict_type, sys_dict_data               |
| 7   | 通知管理 | 8      | sys_notice, sys_user_notice                |
| 8   | 文件管理 | 4      | sys_file                                   |
| 9   | 审计日志 | 3      | sys_audit_log                              |
| 10  | 任务调度 | 7      | sys_job, sys_job_log                       |
| 11  | 系统设置 | 3      | sys_config                                 |

**总计：75 个接口，17 张表**
