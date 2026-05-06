# 子计划: Phase 1.1 - Sa-Token 基础认证集成

## Summary
改造 pure-admin-thin 登录模块对接 AccessMesh 后端 Sa-Token 认证系统,实现简化登录流程、Token 管理、租户选择和多租户 Header 注入。**注意: 本子计划只实现简化登录,OAuth2 授权码流程在后续子计划实现。**

## User Story
作为前端开发者,我希望改造登录模块对接 Sa-Token 认证系统,以便用户能够通过简化登录流程访问后端微服务,并支持租户选择。

## Problem → Solution
**当前状态**: pure-admin-thin 使用 mock 数据,不符合 AccessMesh 后端规范
**目标状态**: 简化登录对接,支持租户选择、统一响应体、Token 自动刷新

**重要说明**: 本子计划**不实现**完整的 OAuth2 授权码 + PKCE 流程,只实现简化登录(用户名+密码+租户)。OAuth2 授权码流程将在 Phase 1.2 子计划实现。

---

## Estimated Files
- **新增**: 8 files
- **修改**: 4 files
- **总计**: 12 files

---

## Mandatory Reading

| Priority | File | Why |
|---|---|---|
| P0 | `frontend/src/utils/http/index.ts` | HTTP 封装核心,需改造拦截器 |
| P0 | `frontend/src/utils/auth.ts` | Token 管理,需改造对接后端 |
| P0 | `frontend/src/store/modules/user.ts` | 用户状态,需改造登录逻辑 |
| P0 | `frontend/src/router/index.ts` | 路由守卫核心逻辑 |
| P0 | `frontend/src/api/user.ts` | API 定义模式参考 |
| P1 | `frontend/src/views/login/index.vue` | 登录页 UI,需添加租户选择 |
| P1 | `plan/frontend-integration.md` | 前端集成规范 |
| P1 | `plan/project-rules.md` §3-§8 | 后端接口规范 |
| P2 | `plan/services/admin-service.md` | 后端认证模块设计 |

---

## Patterns to Mirror

### HTTP_REQUEST_PATTERN
```typescript
// SOURCE: frontend/src/utils/http/index.ts:17-29
const defaultConfig: AxiosRequestConfig = {
  timeout: 10000,
  headers: {
    Accept: "application/json, text/plain, */*",
    "Content-Type": "application/json",
    "X-Requested-With": "XMLHttpRequest"
  }
};
```

### HTTP_INTERCEPTOR_PATTERN
```typescript
// SOURCE: frontend/src/utils/http/index.ts:60-113
private httpInterceptorsRequest(): void {
  PureHttp.axiosInstance.interceptors.request.use(
    async (config: PureHttpRequestConfig): Promise<any> => {
      const whiteList = ["/refresh-token", "/login"];
      return whiteList.some(url => config.url.endsWith(url))
        ? config
        : new Promise(resolve => {
            const data = getToken();
            if (data) {
              const now = new Date().getTime();
              const expired = parseInt(data.expires) - now <= 0;
              if (expired) {
                // Token 刷新逻辑
              }
            }
          });
    }
  );
}
```

### TOKEN_STORAGE_PATTERN
```typescript
// SOURCE: frontend/src/utils/auth.ts:48-116
export function setToken(data: DataInfo<Date>) {
  const { accessToken, refreshToken } = data;
  const cookieString = JSON.stringify({ accessToken, expires, refreshToken });
  Cookies.set(TokenKey, cookieString, {
    expires: (expires - Date.now()) / 86400000
  });
  storageLocal().setItem(userKey, {
    refreshToken,
    expires,
    avatar,
    username,
    roles,
    permissions
  });
}
```

---

## Step-by-Step Tasks

### Task 1: 创建租户状态管理
- **ACTION**: 新建 `frontend/src/store/modules/tenant.ts`
- **FILE**: 新增文件
- **IMPLEMENT**:
  ```typescript
  import { defineStore } from "pinia";
  import { store } from "../utils";

  export interface TenantItem {
    id: number;
    name: string;
    code: string;
  }

  export const useTenantStore = defineStore("tenant", {
    state: () => ({
      currentTenantId: null as number | null,
      tenantList: [] as Array<TenantItem>
    }),
    actions: {
      SET_CURRENT_TENANT(id: number | null) {
        this.currentTenantId = id;
        // 同步到 localStorage
        if (id) {
          localStorage.setItem("currentTenantId", String(id));
        } else {
          localStorage.removeItem("currentTenantId");
        }
      },
      SET_TENANT_LIST(list: Array<TenantItem>) {
        this.tenantList = list;
      },
      // 初始化租户(从 localStorage恢复)
      INIT_TENANT() {
        const savedTenantId = localStorage.getItem("currentTenantId");
        if (savedTenantId) {
          this.currentTenantId = Number(savedTenantId);
        }
      }
    }
  });

  export function useTenantStoreHook() {
    return useTenantStore(store);
  }
  ```
- **VALIDATE**: TypeScript 无错误,store 正确注册

---

### Task 2: 创建租户查询 API
- **ACTION**: 新建 `frontend/src/api/admin/tenant.ts`
- **FILE**: 新增文件
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface TenantQueryResult {
    code: number;
    message: string;
    data: {
      items: Array<{
        id: number;
        name: string;
        code: string;
      }>;
    };
    requestId: string;
    traceId: string;
  }

  // ========== API 函数 ==========

  /** 查询租户列表 */
  export const getTenantList = () => {
    return http.request<TenantQueryResult>("post", "/admin/api/tenant/query", {
      data: {}
    });
  };
  ```
- **GOTCHA**: 接口路径遵循 `/admin/api/{module}/{action}`
- **VALIDATE**: 类型定义完整,路径正确

---

### Task 3: 改造 HTTP 类型定义
- **ACTION**: 更新 `frontend/src/utils/http/types.d.ts`
- **FILE**: 修改现有文件
- **IMPLEMENT**: 添加统一响应体类型
  ```typescript
  // ========== 新增统一响应体类型 ==========

  export interface ApiResponse<T = any> {
    code: number;
    message: string;
    data: T;
    requestId: string;
    traceId: string;
  }

  // ========== 更新 PureHttpRequestConfig ==========

  export interface PureHttpRequestConfig extends AxiosRequestConfig {
    beforeRequestCallback?: (config: PureHttpRequestConfig) => void;
    beforeResponseCallback?: (response: PureHttpResponse) => void;
    tenantId?: number; // 新增租户 ID 配置(可选)
  }
  ```
- **VALIDATE**: TypeScript 类型检查通过

---

### Task 4: 改造 HTTP 请求拦截器
- **ACTION**: 更新 `frontend/src/utils/http/index.ts:60-113`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```typescript
  import { useTenantStoreHook } from "@/store/modules/tenant";

  private httpInterceptorsRequest(): void {
    PureHttp.axiosInstance.interceptors.request.use(
      async (config: PureHttpRequestConfig): Promise<any> => {
        // 改造白名单路径
        const whiteList = [
          "/admin/api/auth/login",
          "/admin/api/auth/refresh-token",
          "/admin/api/tenant/query"
        ];

        if (whiteList.some(url => config.url?.endsWith(url))) {
          return config;
        }

        return new Promise(resolve => {
          const tokenData = getToken();
          const tenantStore = useTenantStoreHook();
          const tenantId = tenantStore.currentTenantId;

          // 注入租户 Header
          if (tenantId) {
            config.headers["X-Tenant-Id"] = String(tenantId);
          }

          // 注入 API 版本 Header
          config.headers["X-Api-Version"] = "2026-04-26";

          // Token 处理逻辑保持不变
          if (tokenData) {
            const now = new Date().getTime();
            const expired = parseInt(tokenData.expires) - now <= 0;
            if (expired) {
              if (!PureHttp.isRefreshing) {
                PureHttp.isRefreshing = true;
                useUserStoreHook()
                  .handRefreshToken({ refreshToken: tokenData.refreshToken })
                  .then(res => {
                    const token = res.data.accessToken;
                    config.headers["Authorization"] = formatToken(token);
                    PureHttp.requests.forEach(cb => cb(token));
                    PureHttp.requests = [];
                  })
                  .finally(() => {
                    PureHttp.isRefreshing = false;
                  });
              }
              resolve(PureHttp.retryOriginalRequest(config));
            } else {
              config.headers["Authorization"] = formatToken(tokenData.accessToken);
              resolve(config);
            }
          } else {
            resolve(config);
          }
        });
      },
      error => {
        return Promise.reject(error);
      }
    );
  }
  ```
- **GOTCHA**: 白名单路径必须完全匹配后端接口路径
- **VALIDATE**: 所有请求携带 X-Tenant-Id 和 X-Api-Version Header

---

### Task 5: 改造 HTTP 响应拦截器
- **ACTION**: 更新 `frontend/src/utils/http/index.ts:116-138`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```typescript
  import { message } from "@/utils/message";
  import { removeToken } from "@/utils/auth";
  import { router } from "@/router";

  private httpInterceptorsResponse(): void {
    const instance = PureHttp.axiosInstance;
    instance.interceptors.response.use(
      (response: PureHttpResponse) => {
        const $config = response.config;

        // 提取统一响应体
        const apiResponse = response.data as ApiResponse;

        // 检查业务错误码
        if (apiResponse.code !== 200) {
          const businessError = {
            code: apiResponse.code,
            message: apiResponse.message,
            requestId: apiResponse.requestId,
            traceId: apiResponse.traceId
          };

          // 按错误码范围分类处理
          if (apiResponse.code >= 10001 && apiResponse.code <= 19999) {
            message(`管理员服务错误: ${apiResponse.message}`, { type: "error" });
          } else if (apiResponse.code >= 20001 && apiResponse.code <= 29999) {
            message(`权限中心错误: ${apiResponse.message}`, { type: "error" });
          } else if (apiResponse.code >= 90001) {
            message(`系统错误: ${apiResponse.message}`, { type: "error" });
          }

          return Promise.reject(businessError);
        }

        // 成功响应,返回 data 部分
        const successData = {
          success: true,
          data: apiResponse.data
        };

        if (typeof $config.beforeResponseCallback === "function") {
          $config.beforeResponseCallback(response);
          return successData;
        }
        if (PureHttp.initConfig.beforeResponseCallback) {
          PureHttp.initConfig.beforeResponseCallback(response);
          return successData;
        }

        return successData;
      },
      (error: PureHttpError) => {
        const $error = error;
        $error.isCancelRequest = Axios.isCancel($error);

        // HTTP 网络错误处理
        if ($error.response?.status === 401) {
          message("未授权,请重新登录", { type: "error" });
          removeToken();
          router.push("/login");
        } else if ($error.response?.status === 403) {
          message("无访问权限", { type: "error" });
        } else if ($error.response?.status === 404) {
          message("请求资源不存在", { type: "error" });
        } else if ($error.response?.status >= 500) {
          message("服务器错误", { type: "error" });
        }

        return Promise.reject($error);
      }
    );
  }
  ```
- **GOTCHA**: 错误码范围必须与后端规范一致
- **VALIDATE**: 401/403/404/500 HTTP 错误正确处理,业务错误码分类正确

---

### Task 6: 改造 Token 存储逻辑
- **ACTION**: 更新 `frontend/src/utils/auth.ts:6-22,48-116`
- **FILE**: 修改现有文件
- **IMPLEMENT**: 扩展 DataInfo 接口,添加租户 ID
  ```typescript
  // ========== 扩展 DataInfo 接口 ==========

  export interface DataInfo<T> {
    accessToken: string;
    expires: T;
    refreshToken: string;
    avatar?: string;
    username?: string;
    nickname?: string;
    roles?: Array<string>;
    permissions?: Array<string>;
    tenantId?: number; // 新增租户 ID
  }

  // ========== 更新 setToken 方法 ==========

  export function setToken(data: DataInfo<Date>) {
    let expires = 0;
    const { accessToken, refreshToken, tenantId } = data;
    const { isRemembered, loginDay } = useUserStoreHook();
    expires = new Date(data.expires).getTime();
    const cookieString = JSON.stringify({ accessToken, expires, refreshToken });

    expires > 0
      ? Cookies.set(TokenKey, cookieString, {
          expires: (expires - Date.now()) / 86400000
        })
      : Cookies.set(TokenKey, cookieString);

    Cookies.set(
      multipleTabsKey,
      "true",
      isRemembered
        ? {
            expires: loginDay
          }
        : {}
    );

    function setUserKey({ avatar, username, nickname, roles, permissions, tenantId }) {
      useUserStoreHook().SET_AVATAR(avatar);
      useUserStoreHook().SET_USERNAME(username);
      useUserStoreHook().SET_NICKNAME(nickname);
      useUserStoreHook().SET_ROLES(roles);
      useUserStoreHook().SET_PERMS(permissions);

      // 同步更新租户状态
      if (tenantId) {
        useTenantStoreHook().SET_CURRENT_TENANT(tenantId);
      }

      storageLocal().setItem(userKey, {
        refreshToken,
        expires,
        avatar,
        username,
        nickname,
        roles,
        permissions,
        tenantId // 存储 tenantId
      });
    }

    if (data.username && data.roles) {
      const { username, roles } = data;
      setUserKey({
        avatar: data?.avatar ?? "",
        username,
        nickname: data?.nickname ?? "",
        roles,
        permissions: data?.permissions ?? [],
        tenantId: tenantId ?? null
      });
    } else {
      const avatar =
        storageLocal().getItem<DataInfo<number>>(userKey)?.avatar ?? "";
      const username =
        storageLocal().getItem<DataInfo<number>>(userKey)?.username ?? "";
      const nickname =
        storageLocal().getItem<DataInfo<number>>(userKey)?.nickname ?? "";
      const roles =
        storageLocal().getItem<DataInfo<number>>(userKey)?.roles ?? [];
      const permissions =
        storageLocal().getItem<DataInfo<number>>(userKey)?.permissions ?? [];
      const savedTenantId =
        storageLocal().getItem<DataInfo<number>>(userKey)?.tenantId ?? null;
      setUserKey({
        avatar,
        username,
        nickname,
        roles,
        permissions,
        tenantId: savedTenantId
      });
    }
  }
  ```
- **GOTCHA**: 租户 ID 存储在 Token 信息中,确保登录时保存
- **VALIDATE**: DataInfo 类型定义正确,tenantId 正确存储和恢复

---

### Task 7: 创建认证 API 接口
- **ACTION**: 新建 `frontend/src/api/admin/auth.ts`
- **FILE**: 新增文件
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  // ========== 类型定义 ==========

  export interface LoginResult {
    success: boolean;
    data: {
      accessToken: string;
      refreshToken: string;
      expires: string; // 格式: "xxxx/xx/xx xx:xx:xx"
      userId: number;
      username: string;
      nickname: string;
      avatar: string;
      roles: Array<string>;
      permissions: Array<string>;
      tenantId: number;
    };
  }

  export interface RefreshTokenResult {
    success: boolean;
    data: {
      accessToken: string;
      refreshToken: string;
      expires: string;
    };
  }

  export interface LoginRequest {
    username: string;
    password: string;
    tenantId: number;
  }

  export interface RefreshTokenRequest {
    refreshToken: string;
  }

  // ========== API 函数 ==========

  /** 登录 */
  export const login = (data: LoginRequest) => {
    return http.request<LoginResult>("post", "/admin/api/auth/login", { data });
  };

  /** 刷新 Token */
  export const refreshToken = (data: RefreshTokenRequest) => {
    return http.request<RefreshTokenResult>(
      "post",
      "/admin/api/auth/refresh-token",
      { data }
    );
  };

  /** 登出 */
  export const logout = () => {
    return http.request("post", "/admin/api/auth/logout", { data: {} });
  };
  ```
- **GOTCHA**: LoginRequest 必须包含 tenantId
- **VALIDATE**: 类型定义与后端契约一致

---

### Task 8: 改造 User Store 登录逻辑
- **ACTION**: 更新 `frontend/src/store/modules/user.ts:67-78`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```typescript
  import { login, type LoginRequest, type LoginResult } from "@/api/admin/auth";
  import { useTenantStoreHook } from "@/store/modules/tenant";

  async loginByUsername(data: LoginRequest) {
    return new Promise<LoginResult>((resolve, reject) => {
      login(data)
        .then(res => {
          if (res.success) {
            // 转换 expires 格式
            const tokenData = {
              ...res.data,
              expires: new Date(res.data.expires)
            };
            setToken(tokenData);

            // 更新租户状态
            useTenantStoreHook().SET_CURRENT_TENANT(res.data.tenantId);
          }
          resolve(res);
        })
        .catch(error => {
          reject(error);
        });
    });
  }
  ```
- **GOTCHA**: expires 格式转换必须正确
- **VALIDATE**: 登录成功后 Token 和租户 ID 正确存储

---

### Task 9: 改造登录页组件
- **ACTION**: 更新 `frontend/src/views/login/index.vue:39-73`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, onMounted } from "vue";
  import { useTenantStoreHook } from "@/store/modules/tenant";
  import { getTenantList } from "@/api/admin/tenant";

  const tenantStore = useTenantStoreHook();
  const selectedTenantId = ref<number>();

  const ruleForm = reactive({
    username: "admin",
    password: "admin123",
    tenantId: null as number | null
  });

  // 初始化租户列表
  onMounted(async () => {
    try {
      const res = await getTenantList();
      if (res.success) {
        tenantStore.SET_TENANT_LIST(res.data.items);
        // 默认选择第一个租户
        if (res.data.items.length > 0) {
          selectedTenantId.value = res.data.items[0].id;
          ruleForm.tenantId = res.data.items[0].id;
        }
      }
    } catch (error) {
      message("获取租户列表失败", { type: "error" });
    }
  });

  const onLogin = async (formEl: FormInstance | undefined) => {
    if (!formEl) return;
    await formEl.validate(valid => {
      if (valid) {
        loading.value = true;
        useUserStoreHook()
          .loginByUsername({
            username: ruleForm.username,
            password: ruleForm.password,
            tenantId: ruleForm.tenantId!
          })
          .then(res => {
            if (res.success) {
              initRouter().then(() => {
                disabled.value = true;
                router
                  .push(getTopMenu(true).path)
                  .then(() => {
                    message("登录成功", { type: "success" });
                  })
                  .finally(() => (disabled.value = false));
              });
            }
          })
          .finally(() => (loading.value = false));
      }
    });
  };
  </script>

  <template>
    <!-- 在用户名输入框前添加租户选择 -->
    <Motion :delay="50">
      <el-form-item
        :rules="[
          {
            required: true,
            message: '请选择租户',
            trigger: 'change'
          }
        ]"
        prop="tenantId"
      >
        <el-select
          v-model="selectedTenantId"
          placeholder="选择租户"
          size="large"
          @change="ruleForm.tenantId = selectedTenantId"
        >
          <el-option
            v-for="tenant in tenantStore.tenantList"
            :key="tenant.id"
            :label="tenant.name"
            :value="tenant.id"
          />
        </el-select>
      </el-form-item>
    </Motion>
  </template>
  ```
- **GOTCHA**: 租户列表获取失败时,应阻止登录
- **VALIDATE**: 租户下拉框正确显示,登录携带 tenantId

---

### Task 10: 配置环境变量
- **ACTION**: 更新 `frontend/.env.development`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```bash
  # 平台本地运行端口号
  VITE_PORT = 8848

  # 开发环境读取配置文件路径
  VITE_PUBLIC_PATH = /

  # 开发环境路由历史模式
  VITE_ROUTER_HISTORY = "hash"

  # 后端 API 基础地址
  VITE_API_BASE_URL = http://localhost:8080

  # API 版本
  VITE_API_VERSION = 2026-04-26
  ```
- **VALIDATE**: 配置文件正确,环境变量可读取

---

### Task 11: 更新 HTTP 默认配置
- **ACTION**: 更新 `frontend/src/utils/http/index.ts:17-29`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```typescript
  const defaultConfig: AxiosRequestConfig = {
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 10000,
    headers: {
      Accept: "application/json, text/plain, */*",
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
      "X-Api-Version": import.meta.env.VITE_API_VERSION
    },
    paramsSerializer: {
      serialize: stringify as unknown as CustomParamsSerializer
    }
  };
  ```
- **VALIDATE**: HTTP 请求携带正确的 baseURL 和 X-Api-Version

---

### Task 12: 改造路由守卫简化版
- **ACTION**: 更新 `frontend/src/router/index.ts:122-218`
- **FILE**: 修改现有文件
- **IMPLEMENT**:
  ```typescript
  router.beforeEach((to: ToRouteType, _from, next) => {
    const userInfo = storageLocal().getItem<DataInfo<number>>(userKey);
    const hasToken = Cookies.get(multipleTabsKey);

    if (hasToken && userInfo) {
      // 已登录状态
      if (to.path === "/login") {
        next({ path: "/" });
      } else {
        // 权限校验(简化版)
        if (to.meta?.roles && !isOneOfArray(to.meta?.roles, userInfo?.roles)) {
          next({ path: "/error/403" });
        } else {
          // Phase 1 暂不加载动态路由
          next();
        }
      }
    } else {
      // 未登录状态
      if (to.path !== "/login") {
        removeToken();
        next({ path: "/login" });
      } else {
        next();
      }
    }
  });
  ```
- **GOTCHA**: Phase 1 先简化权限校验,不实现动态路由加载
- **VALIDATE**: 登录/未登录状态正确判断,403 错误正确处理

---

## Validation Commands

### Static Analysis
```bash
cd frontend
pnpm typecheck
```
EXPECT: Zero type errors

### Lint Check
```bash
cd frontend
pnpm lint:eslint
```
EXPECT: All lint checks pass

### Dev Server
```bash
cd frontend
pnpm dev
```
EXPECT:
1. 登录页显示租户下拉框
2. 租户列表正确加载
3. 选择租户后可输入账号密码
4. 登录成功后跳转首页
5. HTTP 请求携带正确 Headers

---

## Acceptance Criteria
- [ ] 所有 12 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] 登录页显示租户选择下拉框
- [ ] 登录请求携带 tenantId 参数
- [ ] HTTP 拦截器正确注入租户 Header
- [ ] HTTP 拦截器正确注入 API 版本 Header
- [ ] Token 存储包含租户 ID
- [ ] 登录成功后跳转首页
- [ ] 错误处理符合后端规范

---

## NOT Building

**本子计划明确不包含以下功能(将在后续子计划实现)**:

- ❌ OAuth2 授权码 + PKCE 完整流程
- ❌ 动态路由完整加载(Phase 7.1 实现)
- ❌ 权限按钮级控制完整实现(Phase 7.1 实现)
- ❌ 租户切换器布局组件(Phase 1.2 实现)
- ❌ Token 定时刷新完整策略(Phase 1.2 实现)
- ❌ 登出后端接口调用(Phase 1.2 实现)
- ❌ 验证码功能(Phase 1.2 实现)

---

## Next Subplan

完成本子计划后,继续执行:

**Phase 1.2**: OAuth2 授权码 + PKCE 完整流程实现

---

**Generated**: 2026-05-06
**Plan Status**: Ready for Implementation
**Confidence Score**: 9/10