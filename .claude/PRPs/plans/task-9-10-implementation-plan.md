# Task 9 & Task 10 实施方案

> 基于后端 OAuth2 实现核查结果制定

---

## 后端接口核查结果

### Token 刷新接口

| 项目 | 前端计划假设 | 后端实际实现 |
|---|---|---|
| **端点** | `/oauth2/token` (grant_type=refresh_token) | `/auth/oauth2/refresh` |
| **格式** | form-urlencoded | JSON |
| **参数** | `{grant_type, refresh_token, client_id}` | `{clientId, clientSecret, refreshToken}` |
| **响应** | `{access_token, refresh_token, expires_in}` | `{accessToken, refreshToken, expiresIn, tokenType}` |
| **特点** | - | RefreshToken **一次性使用**（原子删除），返回新的 refreshToken |

### 登出接口

| 项目 | 前端计划假设 | 后端实际实现 |
|---|---|---|
| **端点** | `/admin/api/auth/logout` | `/auth/logout` |
| **格式** | JSON (data: {}) | 无请求体 |
| **行为** | - | Sa-Token session 终止，清除 Redis session |

### Token 有效期配置

| Token 类型 | 有效期 | 来源 |
|---|---|---|
| accessToken | 24小时 (86400s) | admin-service bootstrap.yml |
| refreshToken | 7天 (604800s) | OAuth2 client 配置 |
| Gateway activity timeout | 30分钟 (1800s) | gateway bootstrap.yml |

---

## Task 9: Token 定时刷新策略

### 方案概述

由于后端 accessToken 有效期 24小时，gateway activity timeout 30分钟，建议：
- **定时刷新**: 在 accessToken 有效期 50% 时刷新（12小时后）
- **拦截器刷新**: 作为应急措施（token 过期时）

### 实现方案

#### 9.1 创建 Token 刷新 API

**文件**: `frontend/src/api/admin/oauth2.ts`

```typescript
/**
 * 刷新 Token
 * 后端端点: POST /auth/oauth2/refresh
 * 注意: refreshToken 为一次性使用,刷新后返回新的 refreshToken
 */
export const refreshToken = async (refreshTokenValue: string) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  
  const response = await Axios.post<OAuth2TokenResponse>(
    `${baseUrl}/auth/oauth2/refresh`,
    {
      clientId: import.meta.env.VITE_OAUTH2_CLIENT_ID,
      clientSecret: import.meta.env.VITE_OAUTH2_CLIENT_SECRET,
      refreshToken: refreshTokenValue
    },
    {
      headers: {
        "Content-Type": "application/json"
      }
    }
  );

  return response.data;
};
```

#### 9.2 创建 Token 刷新调度器

**文件**: `frontend/src/utils/http/tokenRefreshScheduler.ts` (新建)

```typescript
import { getToken, setToken, removeToken } from "@/utils/auth";
import { refreshToken as refreshTokenApi } from "@/api/admin/oauth2";
import { router } from "@/router";

/**
 * Token 定时刷新调度器
 * - 在 accessToken 有效期 50% 时触发刷新
 * - refreshToken 一次性使用,刷新后返回新的 refreshToken
 * - 刷新失败时清除 token 并跳转登录页
 */
class TokenRefreshScheduler {
  private timer: number | null = null;
  private readonly REFRESH_THRESHOLD = 0.5; // Token 有效期一半时刷新

  /**
   * 启动定时刷新
   */
  start() {
    const tokenData = getToken();
    if (!tokenData?.refreshToken) return;

    const expires = tokenData.expires;
    const now = Date.now();
    const remaining = expires - now;

    // Token 已过期,不启动调度器
    if (remaining <= 0) {
      this.stop();
      return;
    }

    // 计算刷新时间点 (有效期的一半)
    const refreshTime = remaining * this.REFRESH_THRESHOLD;

    // 设置定时器
    this.timer = window.setTimeout(async () => {
      await this.doRefresh();
    }, refreshTime);
  }

  /**
   * 执行刷新
   */
  private async doRefresh() {
    const tokenData = getToken();
    if (!tokenData?.refreshToken) {
      this.stop();
      return;
    }

    try {
      const res = await refreshTokenApi(tokenData.refreshToken);

      if (res.code === 200 && res.data) {
        // 计算新的过期时间
        const newExpires = new Date(Date.now() + res.data.expiresIn * 1000);

        // 存储新 Token (包含新的 refreshToken)
        setToken({
          accessToken: res.data.accessToken,
          refreshToken: res.data.refreshToken,
          expires: newExpires,
          isOAuth2: tokenData.isOAuth2,
          username: tokenData.username,
          nickname: tokenData.nickname,
          avatar: tokenData.avatar,
          roles: tokenData.roles,
          permissions: tokenData.permissions,
          tenantId: tokenData.tenantId
        });

        // 递归启动下一次刷新
        this.start();
      } else {
        // 刷新失败
        this.handleRefreshFailure();
      }
    } catch (error) {
      this.handleRefreshFailure();
    }
  }

  /**
   * 处理刷新失败
   */
  private handleRefreshFailure() {
    this.stop();
    removeToken();
    router.push("/login");
  }

  /**
   * 停止调度器
   */
  stop() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}

// 单例导出
export const tokenRefreshScheduler = new TokenRefreshScheduler();

// 便捷函数
export function startTokenRefreshScheduler() {
  tokenRefreshScheduler.start();
}

export function stopTokenRefreshScheduler() {
  tokenRefreshScheduler.stop();
}
```

#### 9.3 改造 HTTP 请求拦截器

**文件**: `frontend/src/utils/http/index.ts`

保留现有拦截器刷新逻辑作为应急措施，但使用正确的刷新接口：

```typescript
// 白名单更新: 添加刷新接口
const whiteList = [
  "/auth/oauth2/refresh",
  "/auth/oauth2/token",
  "/admin/api/auth/captcha",
  "/admin/api/tenant/query"
];

// 拦截器中 Token 过期时的应急刷新
if (expired) {
  if (!PureHttp.isRefreshing) {
    PureHttp.isRefreshing = true;
    refreshTokenApi(tokenData.refreshToken)
      .then(res => {
        if (res.code === 200) {
          const newExpires = new Date(Date.now() + res.data.expiresIn * 1000);
          setToken({
            accessToken: res.data.accessToken,
            refreshToken: res.data.refreshToken,
            expires: newExpires,
            isOAuth2: tokenData.isOAuth2,
            // ...其他字段
          });
          // 重启定时刷新
          startTokenRefreshScheduler();
        }
      })
      .finally(() => {
        PureHttp.isRefreshing = false;
      });
  }
  resolve(PureHttp.retryOriginalRequest(config));
}
```

#### 9.4 登录成功后启动调度器

**文件**: `frontend/src/utils/oauth2.ts`

```typescript
// handleOAuth2Callback 中,Token 存储后启动调度器
import { startTokenRefreshScheduler } from "@/utils/http/tokenRefreshScheduler";

// Token 存储后
setToken({ ... });
oauth2Store.CLEAR();

// 启动定时刷新
startTokenRefreshScheduler();

// 跳转首页
router.push("/");
```

#### 9.5 环境变量配置

**文件**: `frontend/.env.development`

```bash
# OAuth2 客户端凭证
VITE_OAUTH2_CLIENT_ID = accessmesh-web
VITE_OAUTH2_CLIENT_SECRET = your-client-secret-here
```

---

## Task 10: 登出逻辑改造

### 方案概述

登出需调用后端 `/auth/logout` 终止 Sa-Token session，同时清除前端 Token 并停止定时刷新调度器。

### 实现方案

#### 10.1 创建登出 API

**文件**: `frontend/src/api/admin/auth.ts`

```typescript
/**
 * 登出
 * 后端端点: POST /auth/logout
 * 无请求体,使用 Sa-Token session
 */
export const logout = () => {
  return http.request("post", "/auth/logout");
};
```

#### 10.2 改造 User Store 登出方法

**文件**: `frontend/src/store/modules/user.ts`

```typescript
import { logout as logoutApi } from "@/api/admin/auth";
import { removeToken } from "@/utils/auth";
import { stopTokenRefreshScheduler } from "@/utils/http/tokenRefreshScheduler";
import { router } from "@/router";
import { useMultiTagsStoreHook } from "./multiTags";
import { resetRouter, routerArrays } from "@/router";

/** 登出 */
async logOut() {
  // 1. 停止定时刷新调度器
  stopTokenRefreshScheduler();

  // 2. 调用后端登出接口
  try {
    await logoutApi();
  } catch (error) {
    // 后端登出失败不影响前端登出
    console.error("Backend logout failed:", error);
  }

  // 3. 清除前端 Token
  removeToken();

  // 4. 清除状态
  this.username = "";
  this.roles = [];
  this.permissions = [];

  // 5. 重置标签页和路由
  useMultiTagsStoreHook().handleTags("equal", [...routerArrays]);
  resetRouter();

  // 6. 跳转登录页
  router.push("/login");
}
```

#### 10.3 租户切换时的登出处理

**文件**: `frontend/src/layout/components/Header/TenantSwitcher.vue`

```typescript
import { stopTokenRefreshScheduler } from "@/utils/http/tokenRefreshScheduler";

const handleSwitchTenant = async (tenantId: number) => {
  loading.value = true;

  // 1. 停止定时刷新
  stopTokenRefreshScheduler();

  // 2. 调用登出接口 (可选)
  try {
    await logout();
  } catch (error) {
    // 忽略错误
  }

  // 3. 清除 Token
  removeToken();

  // 4. 更新租户 ID
  tenantStore.SET_CURRENT_TENANT(tenantId);

  // 5. 启动新的 OAuth2 流程
  try {
    await startOAuth2Flow(tenantId);
  } catch (error) {
    router.push("/login");
  } finally {
    loading.value = false;
  }
};
```

---

## 实施文件清单

| Task | 文件 | 操作 | 说明 |
|---|---|---|---|
| 9.1 | `frontend/src/api/admin/oauth2.ts` | 修改 | refreshToken API 改为正确端点和格式 |
| 9.2 | `frontend/src/utils/http/tokenRefreshScheduler.ts` | 新建 | Token 定时刷新调度器 |
| 9.3 | `frontend/src/utils/http/index.ts` | 修改 | 拦截器白名单和应急刷新逻辑 |
| 9.4 | `frontend/src/utils/oauth2.ts` | 修改 | 登录成功后启动调度器 |
| 9.5 | `frontend/.env.development` | 修改 | 添加 VITE_OAUTH2_CLIENT_SECRET |
| 10.1 | `frontend/src/api/admin/auth.ts` | 修改 | logout API 端点改为 `/auth/logout` |
| 10.2 | `frontend/src/store/modules/user.ts` | 修改 | logOut 方法改造 |
| 10.3 | `frontend/src/layout/components/Header/TenantSwitcher.vue` | 修改 | 租户切换时停止调度器 |

---

## 待确认事项

### 1. clientSecret 配置

后端 `/auth/oauth2/refresh` 需要 `clientSecret`。需确认：
- **问题**: SPA (公开客户端) 通常不应持有 clientSecret
- **选项 A**: 后端提供公开客户端专用刷新接口（无需 clientSecret）
- **选项 B**: 前端环境变量配置 clientSecret（需后端配合配置该客户端）

### 2. refreshToken 一次性使用

后端 refreshToken 为一次性使用，每次刷新返回新的 refreshToken。需确认：
- 前端 Token 存储需同步更新 refreshToken
- 多标签页场景下可能存在竞态问题

### 3. 定时刷新时机

建议在 accessToken 有效期 50%（12小时）时刷新。需确认：
- 是否符合业务需求？
- 是否需要更频繁的刷新（如 25% 即 6小时）？

---

请审核以上方案，确认后开始实施。