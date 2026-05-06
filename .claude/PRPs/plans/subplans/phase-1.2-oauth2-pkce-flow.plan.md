# Subplan: Phase 1.2 - OAuth2 Authorization Code + PKCE Flow

## Summary
实现完整的 OAuth2 授权码 + PKCE 流程,替换 Phase 1.1 的简化登录,对接 Sa-Token OAuth2 服务端,提供更安全的 SPA 认证方案。

## User Story
作为前端用户,我希望通过标准的 OAuth2 授权码 + PKCE 流程登录,以便获得更安全的认证体验,并符合 OAuth2 安全最佳实践。

## Problem → Solution
**当前状态**: Phase 1.1 实现简化登录(username+password+tenantId),不符合 OAuth2 标准
**目标状态**: 完整的 OAuth2 授权码 + PKCE 流程,符合 RFC 7636 规范,提供防 CSRF 攻击能力

## Metadata
- **Complexity**: Medium
- **Parent Plan**: `frontend-phase1-sa-token-integration.plan.md`
- **Phase**: Phase 1.2 (OAuth2 Flow)
- **Estimated Files**: 10 files (4 修改 + 6 新增)
- **Prerequisite**: Phase 1.1 已完成(租户状态管理、HTTP 拦截器基础改造)

---

## UX Design

### Before (Phase 1.1)
```
┌─────────────────────────────┐
│  登录页                      │
│  选择租户: [租户A ▼]        │
│  用户名: [_______________]  │
│  密码:   [_______________]  │
│  [ 登 录 ]                  │
│                             │
│  简化登录流程                │
│  直接 POST /auth/login      │
└─────────────────────────────┘
```

### After (Phase 1.2)
```
┌─────────────────────────────────────┐
│  登录页                              │
│  选择租户: [租户A ▼]                │
│  用户名: [_______________]          │
│  密码:   [_______________]          │
│  验证码: [____] [图片验证码]        │
│  [ 登 录 ]                          │
│                                     │
│  OAuth2 授权码 + PKCE 流程:         │
│  1. 生成 code_verifier             │
│  2. 计算 code_challenge            │
│  3. 请求授权码                      │
│  4. 交换 Token                      │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Header 布局                         │
│  [Logo] [菜单导航...]               │
│                                     │
│  [当前租户: 租户A ▼]  [用户头像 ▼] │
│                                     │
│  租户切换器:                         │
│  - 显示当前租户名称                  │
│  - 下拉切换其他租户                  │
│  - 切换后重新 OAuth2 登录            │
└─────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before (Phase 1.1) | After (Phase 1.2) | Notes |
|---|---|---|---|
| 登录流程 | 直接 POST 登录接口 | OAuth2 授权码 + PKCE 四步流程 | 符合 RFC 7636 |
| 验证码 | 无 | 图片验证码 + 输入框 | 防暴力破解 |
| 租户切换 | 仅登录页选择 | Header 布局提供切换器 | 需重新 OAuth2 登录 |
| Token 刷新 | 拦截器基础刷新 | 定时策略 + 拦截器结合 | Token 有效期一半时定时刷新 |
| 登出 | 仅前端清除 Token | 调用后端登出接口 + 清除前端 | 双端登出 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | Phase 1.1 Subplan | 全文 | 前置依赖,租户管理和 HTTP 拦截器已完成 |
| P0 | `frontend/src/utils/http/index.ts` | 60-113 | 请求拦截器 PKCE 参数注入 |
| P0 | `frontend/src/utils/auth.ts` | 48-116 | Token 存储改造(添加 OAuth2 标识) |
| P1 | RFC 7636 (PKCE) | 全文 | PKCE code_verifier 生成规范 |
| P1 | Sa-Token OAuth2 Doc | Authorization Code 章节 | 授权码模式配置和接口 |

---

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| OAuth2 PKCE | [RFC 7636](https://tools.ietf.org/html/rfc7636) | code_verifier 长度 43-128,code_challenge_method=S256 |
| Sa-Token OAuth2 | [Sa-Token OAuth2.0](https://sa-token.cc/doc.html#/oauth2/oauth2) | 授权码请求 URL 参数,Token 交换接口 |
| Web Crypto API | [MDN Crypto.subtle](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto) | SHA-256 摘要计算,Base64 URL 编码 |

---

## Patterns to Mirror

### PKCE_CODE_VERIFIER_PATTERN
// SOURCE: RFC 7636 Section 4.1
```typescript
// 生成 code_verifier: 43-128 字符,使用 URL 安全字符集
function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

// Base64 URL 编码(无填充)
function base64UrlEncode(buffer: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...buffer));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
```
**模式要点**: 使用 Web Crypto API 生成随机值,Base64 URL 编码

### PKCE_CODE_CHALLENGE_PATTERN
// SOURCE: RFC 7636 Section 4.2
```typescript
// 计算 code_challenge: SHA-256(code_verifier) 并 Base64 URL 编码
async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(digest));
}
```
**模式要点**: 使用 SubtleCrypto.digest('SHA-256'),返回 Base64 URL 编码

### OAUTH2_AUTHORIZATION_CODE_PATTERN
// SOURCE: OAuth2 RFC 6749 Section 4.1.1
```text
GET /oauth2/authorize?
  response_type=code&
  client_id={client_id}&
  redirect_uri={redirect_uri}&
  scope={scope}&
  state={state}&
  code_challenge={code_challenge}&
  code_challenge_method=S256&
  tenant_id={tenantId}

Response: HTTP 302 to redirect_uri?code={authorization_code}&state={state}
```
**模式要点**: GET 请求,包含 code_challenge 和 code_challenge_method=S256

### OAUTH2_TOKEN_EXCHANGE_PATTERN
// SOURCE: OAuth2 RFC 6749 Section 4.1.3
```text
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code={authorization_code}&
redirect_uri={redirect_uri}&
client_id={client_id}&
code_verifier={code_verifier}

Response: { access_token, refresh_token, expires_in, token_type }
```
**模式要点**: POST application/x-www-form-urlencoded,包含 code_verifier

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `frontend/src/utils/pkce.ts` | CREATE | PKCE code_verifier/code_challenge 生成工具 |
| `frontend/src/utils/oauth2.ts` | CREATE | OAuth2 授权码请求和 Token 交换逻辑 |
| `frontend/src/api/admin/oauth2.ts` | CREATE | OAuth2 接口定义 |
| `frontend/src/store/modules/oauth2.ts` | CREATE | OAuth2 状态管理(state 参数存储) |
| `frontend/src/views/login/index.vue` | UPDATE | 改造登录逻辑为 OAuth2 流程 |
| `frontend/src/views/login/components/Captcha.vue` | CREATE | 验证码组件 |
| `frontend/src/layout/components/Header/TenantSwitcher.vue` | CREATE | 租户切换器组件 |
| `frontend/src/utils/auth.ts` | UPDATE | 添加 OAuth2 登录标识字段 |
| `frontend/src/utils/http/index.ts` | UPDATE | 改造刷新策略为定时 + 拦截器结合 |
| `frontend/src/store/modules/user.ts` | UPDATE | 改造登出逻辑调用后端接口 |

## NOT Building

- OpenID Connect (OIDC) 协议 - Phase 1.2 只实现 OAuth2,OIDC 在后续 Phase
- Refresh Token Rotation - Phase 1.2 只实现基础刷新,Rotation 在后续优化
- Token Introspection - 后端验证 Token,前端不实现
- 多因素认证(MFA) - 后续 Phase 实现

---

## Step-by-Step Tasks

### Task 1: 创建 PKCE 工具模块
- **ACTION**: 新建 `frontend/src/utils/pkce.ts`
- **IMPLEMENT**:
  ```typescript
  /**
   * PKCE 工具模块
   * RFC 7636: Proof Key for Code Exchange by OAuth Public Clients
   */

  /**
   * Base64 URL 编码(无填充)
   * 字符集: [A-Z] / [a-z] / [0-9] / "-" / "_"
   */
  function base64UrlEncode(buffer: Uint8Array): string {
    const base64 = btoa(String.fromCharCode(...buffer));
    return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  /**
   * 生成 code_verifier
   * 要求: 43-128 字符,使用 URL 安全字符集
   * 实现: 32 字节随机值 → Base64 URL 编码(43 字符)
   */
  export function generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return base64UrlEncode(array);
  }

  /**
   * 计算 code_challenge
   * 方法: code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
   * 支持: S256 方法(推荐)
   */
  export async function generateCodeChallenge(
    verifier: string,
    method: "S256" | "plain" = "S256"
  ): Promise<string> {
    if (method === "plain") {
      return verifier;
    }

    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest("SHA-256", data);
    return base64UrlEncode(new Uint8Array(digest));
  }

  /**
   * 验证 code_verifier 长度
   * 要求: 43 <= length <= 128
   */
  export function validateVerifierLength(verifier: string): boolean {
    const len = verifier.length;
    return len >= 43 && len <= 128;
  }
  ```
- **MIRROR**: PKCE_CODE_VERIFIER_PATTERN + PKCE_CODE_CHALLENGE_PATTERN
- **IMPORTS**: 无外部依赖,使用 Web Crypto API
- **GOTCHA**: 
  - code_verifier 必须使用 crypto.getRandomValues 生成,不能用 Math.random
  - Base64 URL 编码必须去除 "=" 填充字符
  - SHA-256 使用 SubtleCrypto.digest,返回 Promise
- **VALIDATE**: 
  - generateCodeVerifier 返回 43 字符字符串
  - generateCodeChallenge 返回正确的 Base64 URL 编码
  - 单元测试验证长度和字符集

### Task 2: 创建 OAuth2 状态管理
- **ACTION**: 新建 `frontend/src/store/modules/oauth2.ts`
- **IMPLEMENT**:
  ```typescript
  import { defineStore } from "pinia";
  import { store } from "../utils";

  export interface OAuth2State {
    codeVerifier: string;
    codeChallenge: string;
    state: string; // CSRF 防护参数
    authorizationCode: string;
  }

  export const useOAuth2Store = defineStore("oauth2", {
    state: (): OAuth2State => ({
      codeVerifier: "",
      codeChallenge: "",
      state: "",
      authorizationCode: ""
    }),
    actions: {
      SET_CODE_VERIFIER(verifier: string) {
        this.codeVerifier = verifier;
      },
      SET_CODE_CHALLENGE(challenge: string) {
        this.codeChallenge = challenge;
      },
      SET_STATE(state: string) {
        this.state = state;
      },
      SET_AUTHORIZATION_CODE(code: string) {
        this.authorizationCode = code;
      },
      CLEAR() {
        this.codeVerifier = "";
        this.codeChallenge = "";
        this.state = "";
        this.authorizationCode = "";
      },
      // 初始化 OAuth2 参数
      async initOAuth2Params() {
        const verifier = generateCodeVerifier();
        const challenge = await generateCodeChallenge(verifier);
        const state = this.generateState();

        this.SET_CODE_VERIFIER(verifier);
        this.SET_CODE_CHALLENGE(challenge);
        this.SET_STATE(state);
      },
      // 生成 state 参数(UUID)
      generateState(): string {
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(
          /[xy]/g,
          function (c) {
            const r = (Math.random() * 16) | 0;
            const v = c === "x" ? r : (r & 0x3) | 0x8;
            return v.toString(16);
          }
        );
      }
    }
  });

  export function useOAuth2StoreHook() {
    return useOAuth2Store(store);
  }
  ```
- **MIRROR**: STORE_PATTERN from user.ts
- **IMPORTS**: `defineStore` from pinia, `store` from utils, PKCE utils from "@/utils/pkce"
- **GOTCHA**: 
  - state 参数用于 CSRF 防护,必须存储并在回调时验证
  - codeVerifier 必须在 Token 交换前保存,不能丢失
  - initOAuth2Params 是异步方法,需要 await
- **VALIDATE**: 
  - state 参数为 UUID 格式
  - codeVerifier/codeChallenge 正确生成和存储

### Task 3: 创建 OAuth2 API 接口
- **ACTION**: 新建 `frontend/src/api/admin/oauth2.ts`
- **IMPLEMENT**:
  ```typescript
  import { http } from "@/utils/http";

  /**
   * OAuth2 授权码请求参数
   * GET /oauth2/authorize
   */
  export interface AuthorizationCodeRequest {
    response_type: "code";
    client_id: string;
    redirect_uri: string;
    scope?: string;
    state: string;
    code_challenge: string;
    code_challenge_method: "S256";
    tenant_id: number;
  }

  /**
   * OAuth2 Token 交换请求参数
   * POST /oauth2/token
   */
  export interface TokenExchangeRequest {
    grant_type: "authorization_code";
    code: string;
    redirect_uri: string;
    client_id: string;
    code_verifier: string;
  }

  /**
   * OAuth2 Token 响应
   */
  export interface OAuth2TokenResult {
    access_token: string;
    refresh_token: string;
    expires_in: number; // 秒数
    token_type: "Bearer";
    scope?: string;
    user_info?: {
      userId: number;
      username: string;
      nickname: string;
      avatar: string;
      roles: Array<string>;
      permissions: Array<string>;
    };
  }

  /**
   * 验证码响应
   */
  export interface CaptchaResult {
    code: number;
    message: string;
    data: {
      captchaId: string;
      captchaImage: string; // Base64 图片
    };
  }

  /**
   * 获取验证码
   */
  export const getCaptcha = (tenantId: number) => {
    return http.request<CaptchaResult>("post", "/admin/api/auth/captcha", {
      data: { tenantId }
    });
  };

  /**
   * 请求授权码(GET 请求,直接重定向)
   * 前端不直接调用,通过 window.location.href 触发
   */
  export function buildAuthorizationUrl(params: AuthorizationCodeRequest): string {
    const baseUrl = import.meta.env.VITE_API_BASE_URL;
    const query = new URLSearchParams({
      response_type: params.response_type,
      client_id: params.client_id,
      redirect_uri: params.redirect_uri,
      state: params.state,
      code_challenge: params.code_challenge,
      code_challenge_method: params.code_challenge_method,
      tenant_id: String(params.tenant_id)
    });

    if (params.scope) {
      query.append("scope", params.scope);
    }

    return `${baseUrl}/oauth2/authorize?${query.toString()}`;
  }

  /**
   * 交换 Token(POST application/x-www-form-urlencoded)
   * 注意: 不使用 http.request,直接 axios.post
   */
  export const exchangeToken = async (params: TokenExchangeRequest) => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL;
    const formData = new URLSearchParams({
      grant_type: params.grant_type,
      code: params.code,
      redirect_uri: params.redirect_uri,
      client_id: params.client_id,
      code_verifier: params.code_verifier
    });

    const response = await axios.post<{ code: number; message: string; data: OAuth2TokenResult }>(
      `${baseUrl}/oauth2/token`,
      formData,
      {
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        }
      }
    );

    return response.data;
  };

  /**
   * 刷新 Token
   */
  export const refreshToken = (refreshToken: string) => {
    return http.request<{ code: number; message: string; data: OAuth2TokenResult }>(
      "post",
      "/oauth2/token",
      {
        data: {
          grant_type: "refresh_token",
          refresh_token: refreshToken,
          client_id: import.meta.env.VITE_OAUTH2_CLIENT_ID
        }
      }
    );
  };
  ```
- **MIRROR**: API_TYPE_DEFINITION_PATTERN
- **IMPORTS**: `http` from "@/utils/http", `axios` (直接导入用于 form-urlencoded)
- **GOTCHA**: 
  - Token 交换接口使用 application/x-www-form-urlencoded,不是 JSON
  - 授权码请求是 GET,前端通过 window.location.href 触发,不走 axios
  - client_id 从环境变量读取
  - expires_in 是秒数,需要转换为 Date
- **VALIDATE**: 
  - buildAuthorizationUrl 返回正确的 URL 格式
  - exchangeToken 使用正确的 Content-Type

### Task 4: 创建 OAuth2 流程工具
- **ACTION**: 新建 `frontend/src/utils/oauth2.ts`
- **IMPLEMENT**:
  ```typescript
  import { useOAuth2StoreHook } from "@/store/modules/oauth2";
  import { useTenantStoreHook } from "@/store/modules/tenant";
  import {
    buildAuthorizationUrl,
    exchangeToken,
    type AuthorizationCodeRequest,
    type OAuth2TokenResult
  } from "@/api/admin/oauth2";
  import { setToken, removeToken } from "@/utils/auth";
  import { router } from "@/router";

  /**
   * 启动 OAuth2 授权码流程
   * 1. 生成 PKCE 参数
   * 2. 构建授权码请求 URL
   * 3. 重定向到授权端点
   */
  export async function startOAuth2Flow(tenantId: number): Promise<void> {
    const oauth2Store = useOAuth2StoreHook();

    // 1. 初始化 OAuth2 参数
    await oauth2Store.initOAuth2Params();

    // 2. 构建授权码请求参数
    const params: AuthorizationCodeRequest = {
      response_type: "code",
      client_id: import.meta.env.VITE_OAUTH2_CLIENT_ID,
      redirect_uri: import.meta.env.VITE_OAUTH2_REDIRECT_URI,
      scope: "read write",
      state: oauth2Store.state,
      code_challenge: oauth2Store.codeChallenge,
      code_challenge_method: "S256",
      tenant_id: tenantId
    };

    // 3. 构建授权 URL 并重定向
    const authUrl = buildAuthorizationUrl(params);
    window.location.href = authUrl;
  }

  /**
   * 处理 OAuth2 回调
   * 1. 解析 authorization_code 和 state
   * 2. 验证 state 参数
   * 3. 交换 Token
   * 4. 存储 Token 和用户信息
   * 5. 跳转到首页
   */
  export async function handleOAuth2Callback(): Promise<void> {
    const oauth2Store = useOAuth2StoreHook();
    const tenantStore = useTenantStoreHook();

    // 1. 解析 URL 参数
    const urlParams = new URLSearchParams(window.location.search);
    const code = urlParams.get("code");
    const state = urlParams.get("state");
    const error = urlParams.get("error");
    const errorDescription = urlParams.get("error_description");

    // 错误处理
    if (error) {
      console.error(`OAuth2 Error: ${error} - ${errorDescription}`);
      oauth2Store.CLEAR();
      router.push("/login?error=oauth2_failed");
      return;
    }

    // 2. 验证 state 参数(CSRF 防护)
    if (!state || state !== oauth2Store.state) {
      console.error("OAuth2 state mismatch");
      oauth2Store.CLEAR();
      router.push("/login?error=state_mismatch");
      return;
    }

    // 3. 检查 authorization_code
    if (!code) {
      console.error("Missing authorization code");
      oauth2Store.CLEAR();
      router.push("/login?error=missing_code");
      return;
    }

    // 4. 交换 Token
    try {
      const tokenResult = await exchangeToken({
        grant_type: "authorization_code",
        code: code,
        redirect_uri: import.meta.env.VITE_OAUTH2_REDIRECT_URI,
        client_id: import.meta.env.VITE_OAUTH2_CLIENT_ID,
        code_verifier: oauth2Store.codeVerifier
      });

      if (tokenResult.code !== 200) {
        console.error(`Token exchange failed: ${tokenResult.message}`);
        oauth2Store.CLEAR();
        router.push("/login?error=token_exchange_failed");
        return;
      }

      const tokenData = tokenResult.data;

      // 5. 存储 Token
      const expires = new Date(Date.now() + tokenData.expires_in * 1000);
      setToken({
        accessToken: tokenData.access_token,
        refreshToken: tokenData.refresh_token,
        expires: expires,
        username: tokenData.user_info?.username,
        nickname: tokenData.user_info?.nickname,
        avatar: tokenData.user_info?.avatar,
        roles: tokenData.user_info?.roles || [],
        permissions: tokenData.user_info?.permissions || [],
        tenantId: tenantStore.currentTenantId,
        isOAuth2: true // 标识 OAuth2 登录
      });

      // 6. 清理 OAuth2 状态
      oauth2Store.CLEAR();

      // 7. 跳转到首页
      router.push("/");
    } catch (error) {
      console.error("Token exchange error:", error);
      oauth2Store.CLEAR();
      router.push("/login?error=network_error");
    }
  }

  /**
   * 检查是否在 OAuth2 回调页面
   */
  export function isOAuth2Callback(): boolean {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.has("code") && urlParams.has("state");
  }
  ```
- **MIRROR**: OAuth2 Authorization Code Flow
- **IMPORTS**: OAuth2 store, tenant store, OAuth2 API, auth utils, router
- **GOTCHA**: 
  - state 验证必须严格执行,防止 CSRF 攻击
  - codeVerifier 在 Token 交换后必须清除
  - Token 交换失败需要清理状态并重定向登录页
  - expires_in 转换为 Date 需要乘 1000(毫秒)
- **VALIDATE**: 
  - startOAuth2Flow 正确生成 URL 并重定向
  - handleOAuth2Callback 正确验证 state 并交换 Token
  - Token 正确存储并跳转首页

### Task 5: 创建验证码组件
- **ACTION**: 新建 `frontend/src/views/login/components/Captcha.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, onMounted } from "vue";
  import { getCaptcha } from "@/api/admin/oauth2";
  import { useTenantStoreHook } from "@/store/modules/tenant";

  defineOptions({
    name: "Captcha"
  });

  const tenantStore = useTenantStoreHook();

  const captchaId = ref<string>("");
  const captchaImage = ref<string>("");
  const captchaCode = ref<string>("");
  const loading = ref(false);

  // 加载验证码
  const loadCaptcha = async () => {
    if (!tenantStore.currentTenantId) {
      return;
    }

    loading.value = true;
    try {
      const res = await getCaptcha(tenantStore.currentTenantId);
      if (res.code === 200) {
        captchaId.value = res.data.captchaId;
        captchaImage.value = res.data.captchaImage;
      }
    } catch (error) {
      console.error("Load captcha failed:", error);
    } finally {
      loading.value = false;
    }
  };

  // 刷新验证码(点击图片)
  const refreshCaptcha = () => {
    captchaCode.value = "";
    loadCaptcha();
  };

  // 暴露给父组件
  defineExpose({
    captchaId,
    captchaCode,
    loadCaptcha
  });

  onMounted(() => {
    loadCaptcha();
  });
  </script>

  <template>
    <div class="captcha-container">
      <el-input
        v-model="captchaCode"
        placeholder="请输入验证码"
        size="large"
        class="captcha-input"
        maxlength="4"
      />
      <div class="captcha-image-wrapper" @click="refreshCaptcha">
        <img
          v-if="captchaImage"
          :src="`data:image/png;base64,${captchaImage}`"
          alt="验证码"
          class="captcha-image"
        />
        <el-icon v-else class="captcha-loading">
          <Loading />
        </el-icon>
      </div>
    </div>
  </template>

  <style scoped lang="scss">
  .captcha-container {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .captcha-input {
    flex: 1;
  }

  .captcha-image-wrapper {
    width: 120px;
    height: 40px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #f5f5f5;
    border-radius: 4px;
  }

  .captcha-image {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .captcha-loading {
    font-size: 20px;
    color: #999;
  }
  </style>
  ```
- **MIRROR**: Vue Component Pattern
- **IMPORTS**: `getCaptcha` API, tenant store, Element Plus components
- **GOTCHA**: 
  - 验证码图片为 Base64 格式,需要拼接 `data:image/png;base64,`
  - 点击图片刷新验证码时,需要清空输入框
  - captchaId 和 captchaCode 需要通过 defineExpose 暴露给父组件
- **VALIDATE**: 
  - 验证码图片正确显示
  - 点击图片刷新验证码
  - 输入框限制 4 字符

### Task 6: 改造登录页组件
- **ACTION**: 更新 `frontend/src/views/login/index.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, reactive, onMounted } from "vue";
  import { type FormInstance } from "element-plus";
  import { useTenantStoreHook } from "@/store/modules/tenant";
  import { startOAuth2Flow, isOAuth2Callback, handleOAuth2Callback } from "@/utils/oauth2";
  import { getTenantList } from "@/api/admin/tenant";
  import Captcha from "./components/Captcha.vue";

  defineOptions({
    name: "Login"
  });

  const tenantStore = useTenantStoreHook();
  const captchaRef = ref();
  const loading = ref(false);
  const selectedTenantId = ref<number>();

  const ruleForm = reactive({
    username: "admin",
    password: "admin123",
    tenantId: null as number | null
  });

  // 初始化租户列表
  onMounted(async () => {
    // 检查是否是 OAuth2 回调
    if (isOAuth2Callback()) {
      await handleOAuth2Callback();
      return;
    }

    // 加载租户列表
    try {
      const res = await getTenantList();
      if (res.success) {
        tenantStore.SET_TENANT_LIST(res.data.items);
        if (res.data.items.length > 0) {
          selectedTenantId.value = res.data.items[0].id;
          ruleForm.tenantId = res.data.items[0].id;
          tenantStore.SET_CURRENT_TENANT(res.data.items[0].id);
        }
      }
    } catch (error) {
      message("获取租户列表失败", { type: "error" });
    }
  });

  // 登录按钮:启动 OAuth2 流程
  const onLogin = async (formEl: FormInstance | undefined) => {
    if (!formEl) return;
    await formEl.validate(async valid => {
      if (valid) {
        loading.value = true;

        // 保存租户 ID
        tenantStore.SET_CURRENT_TENANT(ruleForm.tenantId!);

        // 启动 OAuth2 授权码流程
        try {
          await startOAuth2Flow(ruleForm.tenantId!);
        } catch (error) {
          console.error("OAuth2 flow start failed:", error);
          message("登录失败", { type: "error" });
        } finally {
          loading.value = false;
        }
      }
    });
  };

  // 租户切换
  const onTenantChange = (tenantId: number) => {
    ruleForm.tenantId = tenantId;
    tenantStore.SET_CURRENT_TENANT(tenantId);
    // 切换租户时重新加载验证码
    captchaRef.value?.loadCaptcha();
  };
  </script>

  <template>
    <div class="login-container">
      <!-- 租户选择 -->
      <Motion :delay="50">
        <el-form-item
          :rules="[{ required: true, message: '请选择租户', trigger: 'change' }]"
          prop="tenantId"
        >
          <el-select
            v-model="selectedTenantId"
            placeholder="选择租户"
            size="large"
            @change="onTenantChange"
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

      <!-- 用户名 -->
      <Motion :delay="100">
        <el-form-item prop="username">
          <el-input
            v-model="ruleForm.username"
            placeholder="用户名"
            size="large"
          />
        </el-form-item>
      </Motion>

      <!-- 密码 -->
      <Motion :delay="150">
        <el-form-item prop="password">
          <el-input
            v-model="ruleForm.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          />
        </el-form-item>
      </Motion>

      <!-- 验证码 -->
      <Motion :delay="200">
        <el-form-item prop="captcha">
          <Captcha ref="captchaRef" />
        </el-form-item>
      </Motion>

      <!-- 登录按钮 -->
      <Motion :delay="250">
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="onLogin(ruleFormRef)"
          >
            登 录
          </el-button>
        </el-form-item>
      </Motion>
    </div>
  </template>
  ```
- **MIRROR**: Vue Composition API Pattern
- **IMPORTS**: OAuth2 utils, tenant store, tenant API, Captcha component
- **GOTCHA**: 
  - onMounted 必须优先检查 OAuth2 回调,isOAuth2Callback() 判断
  - 登录按钮不再直接 POST login,而是启动 OAuth2 流程
  - 租户切换时需要重新加载验证码
  - 验证码的 captchaId 和 captchaCode 需要传递给后端(后续 Task 实现)
- **VALIDATE**: 
  - OAuth2 回调正确处理
  - 租户选择正确
  - 登录按钮触发 OAuth2 流程

### Task 7: 创建租户切换器组件
- **ACTION**: 新建 `frontend/src/layout/components/Header/TenantSwitcher.vue`
- **IMPLEMENT**:
  ```vue
  <script setup lang="ts">
  import { ref, computed } from "vue";
  import { useRouter } from "vue-router";
  import { useTenantStoreHook } from "@/store/modules/tenant";
  import { removeToken } from "@/utils/auth";
  import { startOAuth2Flow } from "@/utils/oauth2";

  defineOptions({
    name: "TenantSwitcher"
  });

  const router = useRouter();
  const tenantStore = useTenantStoreHook();
  const loading = ref(false);

  const currentTenantName = computed(() => {
    const tenant = tenantStore.tenantList.find(
      t => t.id === tenantStore.currentTenantId
    );
    return tenant?.name || "未选择租户";
  });

  // 切换租户(需要重新 OAuth2 登录)
  const handleSwitchTenant = async (tenantId: number) => {
    loading.value = true;

    // 1. 清除当前 Token
    removeToken();

    // 2. 更新租户 ID
    tenantStore.SET_CURRENT_TENANT(tenantId);

    // 3. 启动新的 OAuth2 流程
    try {
      await startOAuth2Flow(tenantId);
    } catch (error) {
      console.error("Switch tenant failed:", error);
      router.push("/login");
    } finally {
      loading.value = false;
    }
  };

  // 跳转到登录页(选择其他租户)
  const goToLogin = () => {
    removeToken();
    router.push("/login");
  };
  </script>

  <template>
    <div class="tenant-switcher">
      <el-dropdown
        trigger="click"
        :loading="loading"
        @command="handleSwitchTenant"
      >
        <div class="tenant-dropdown-link">
          <span class="tenant-name">{{ currentTenantName }}</span>
          <el-icon class="el-icon--right">
            <ArrowDown />
          </el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="tenant in tenantStore.tenantList"
              :key="tenant.id"
              :command="tenant.id"
              :disabled="tenant.id === tenantStore.currentTenantId"
            >
              {{ tenant.name }}
            </el-dropdown-item>
            <el-dropdown-item divided @click="goToLogin">
              切换其他租户
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </template>

  <style scoped lang="scss">
  .tenant-switcher {
    display: flex;
    align-items: center;
    margin-right: 20px;
  }

  .tenant-dropdown-link {
    display: flex;
    align-items: center;
    cursor: pointer;
    padding: 0 10px;
    height: 40px;
    border-radius: 4px;
    transition: background-color 0.3s;

    &:hover {
      background-color: rgba(0, 0, 0, 0.025);
    }
  }

  .tenant-name {
    font-size: 14px;
    color: #333;
    margin-right: 5px;
  }
  </style>
  ```
- **MIRROR**: Vue Component Pattern + Dropdown Pattern
- **IMPORTS**: tenant store, auth utils, OAuth2 utils, router
- **GOTCHA**: 
  - 切换租户必须清除 Token,重新 OAuth2 登录
  - 当前租户在下拉列表中 disabled
  - 切换失败需要跳转登录页
- **VALIDATE**: 
  - 当前租户名称正确显示
  - 下拉列表正确显示所有租户
  - 切换租户触发 OAuth2 流程

### Task 8: 改造 Token 存储逻辑
- **ACTION**: 更新 `frontend/src/utils/auth.ts`
- **IMPLEMENT**: 添加 OAuth2 登录标识
  ```typescript
  export interface DataInfo<T> {
    accessToken: string;
    expires: T;
    refreshToken: string;
    avatar?: string;
    username?: string;
    nickname?: string;
    roles?: Array<string>;
    permissions?: Array<string>;
    tenantId?: number;
    isOAuth2?: boolean; // 新增: OAuth2 登录标识
  }

  export function setToken(data: DataInfo<Date>) {
    let expires = 0;
    const { accessToken, refreshToken, isOAuth2 } = data;
    const { isRemembered, loginDay } = useUserStoreHook();
    expires = new Date(data.expires).getTime();

    const cookieString = JSON.stringify({
      accessToken,
      expires,
      refreshToken,
      isOAuth2 // 存储 OAuth2 标识
    });

    // Cookies 存储 Token 核心信息
    expires > 0
      ? Cookies.set(TokenKey, cookieString, {
          expires: (expires - Date.now()) / 86400000
        })
      : Cookies.set(TokenKey, cookieString);

    // multiple-tabs key 用于多标签页判断
    Cookies.set(
      multipleTabsKey,
      "true",
      isRemembered ? { expires: loginDay } : {}
    );

    // localStorage 存储用户信息
    storageLocal().setItem(userKey, {
      refreshToken,
      expires,
      avatar: data.avatar,
      username: data.username,
      nickname: data.nickname,
      roles: data.roles,
      permissions: data.permissions,
      tenantId: data.tenantId,
      isOAuth2: data.isOAuth2
    });
  }
  ```
- **MIRROR**: TOKEN_STORAGE_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: isOAuth2 标识用于区分简化登录和 OAuth2 登录
- **VALIDATE**: Token 存储包含 isOAuth2 字段

### Task 9: 改造 Token 刷新策略
- **ACTION**: 更新 `frontend/src/utils/http/index.ts:60-113`
- **IMPLEMENT**: 定时刷新 + 拦截器结合
  ```typescript
  import { refreshToken as refreshTokenApi } from "@/api/admin/oauth2";

  // 定时刷新调度器
  class TokenRefreshScheduler {
    private timer: number | null = null;
    private readonly REFRESH_THRESHOLD = 0.5; // Token 有效期一半时刷新

    start() {
      const tokenData = getToken();
      if (!tokenData) return;

      const expires = new Date(tokenData.expires).getTime();
      const now = Date.now();
      const remaining = expires - now;
      const refreshTime = remaining * this.REFRESH_THRESHOLD;

      // 设置定时刷新
      this.timer = window.setTimeout(async () => {
        try {
          const res = await refreshTokenApi(tokenData.refreshToken);
          if (res.code === 200) {
            const newExpires = new Date(
              Date.now() + res.data.expires_in * 1000
            );
            setToken({
              accessToken: res.data.access_token,
              refreshToken: res.data.refresh_token,
              expires: newExpires,
              isOAuth2: tokenData.isOAuth2
            });
            // 递归启动下一次刷新
            this.start();
          }
        } catch (error) {
          console.error("Scheduled token refresh failed:", error);
          // 刷新失败,跳转登录
          removeToken();
          router.push("/login");
        }
      }, refreshTime);
    }

    stop() {
      if (this.timer) {
        clearTimeout(this.timer);
        this.timer = null;
      }
    }
  }

  const scheduler = new TokenRefreshScheduler();

  // HTTP 请求拦截器(保留原有逻辑,增加定时刷新)
  private httpInterceptorsRequest(): void {
    PureHttp.axiosInstance.interceptors.request.use(
      async (config: PureHttpRequestConfig): Promise<any> => {
        const whiteList = [
          "/oauth2/token",
          "/admin/api/auth/captcha",
          "/admin/api/tenant/query"
        ];

        if (whiteList.some(url => config.url?.endsWith(url))) {
          return config;
        }

        return new Promise(resolve => {
          const tokenData = getToken();
          const tenantId = useTenantStoreHook().currentTenantId;

          // 注入租户 Header
          if (tenantId) {
            config.headers["X-Tenant-Id"] = String(tenantId);
          }

          // 注入 API 版本 Header
          config.headers["X-Api-Version"] = "2026-04-26";

          if (tokenData) {
            const now = Date.now();
            const expires = new Date(tokenData.expires).getTime();
            const expired = expires - now <= 0;

            if (expired) {
              // Token 已过期,拦截器刷新(应急)
              if (!PureHttp.isRefreshing) {
                PureHttp.isRefreshing = true;
                refreshTokenApi(tokenData.refreshToken)
                  .then(res => {
                    if (res.code === 200) {
                      const newExpires = new Date(
                        Date.now() + res.data.expires_in * 1000
                      );
                      setToken({
                        accessToken: res.data.access_token,
                        refreshToken: res.data.refresh_token,
                        expires: newExpires,
                        isOAuth2: tokenData.isOAuth2
                      });
                      config.headers["Authorization"] = formatToken(
                        res.data.access_token
                      );
                      // 重启定时刷新
                      scheduler.start();
                    }
                  })
                  .finally(() => {
                    PureHttp.isRefreshing = false;
                  });
              }
              resolve(PureHttp.retryOriginalRequest(config));
            } else {
              config.headers["Authorization"] = formatToken(
                tokenData.accessToken
              );
              resolve(config);
            }
          } else {
            resolve(config);
          }
        });
      }
    );
  }

  // 启动定时刷新(登录成功后调用)
  export function startTokenRefreshScheduler() {
    scheduler.start();
  }

  // 停止定时刷新(登出时调用)
  export function stopTokenRefreshScheduler() {
    scheduler.stop();
  }
  ```
- **MIRROR**: HTTP_INTERCEPTOR_REQUEST_PATTERN + Token Refresh Strategy
- **IMPORTS**: `refreshTokenApi` from "@/api/admin/oauth2", `getToken/setToken/removeToken` from "@/utils/auth"
- **GOTCHA**: 
  - 定时刷新在 Token 有效期一半时触发(REFRESH_THRESHOLD = 0.5)
  - 拦截器刷新作为应急措施(Token 已过期时)
  - 刷新成功后需要递归启动下一次定时刷新
  - 刷新失败需要跳转登录页
- **VALIDATE**: 
  - 定时刷新正确触发
  - 拦截器刷新正确处理过期 Token
  - 刷新成功后重启定时器

### Task 10: 改造登出逻辑
- **ACTION**: 更新 `frontend/src/store/modules/user.ts`
- **IMPLEMENT**: 调用后端登出接口
  ```typescript
  import { logout as logoutApi } from "@/api/admin/auth";
  import { removeToken } from "@/utils/auth";
  import { stopTokenRefreshScheduler } from "@/utils/http";
  import { router } from "@/router";

  // 登出方法
  async logOut() {
    // 1. 停止定时刷新
    stopTokenRefreshScheduler();

    // 2. 调用后端登出接口
    try {
      await logoutApi();
    } catch (error) {
      console.error("Backend logout failed:", error);
      // 后端登出失败不影响前端登出
    }

    // 3. 清除前端 Token
    removeToken();

    // 4. 跳转登录页
    router.push("/login");
  }
  ```
- **MIRROR**: STORE_ACTION_PATTERN
- **IMPORTS**: logout API, auth utils, http scheduler, router
- **GOTCHA**: 后端登出失败不影响前端登出流程
- **VALIDATE**: 登出时正确调用后端接口并清理前端状态

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| PKCE code_verifier 生成 | 无参数 | 43 字符 Base64 URL 字符串 | 长度验证 |
| PKCE code_challenge 计算 | code_verifier | SHA-256 Base64 URL 编码 | plain 方法 |
| OAuth2 state 生成 | 无参数 | UUID 格式字符串 | 唯一性验证 |
| 授权 URL 构建 | AuthorizationCodeRequest | 正确的 URL 格式 | scope 为空 |
| Token 交换 | authorization_code + code_verifier | OAuth2TokenResult | code 过期 |
| state 参数验证 | URL 参数 state | 与 store.state 比对 | state 不匹配 |
| 租户切换 | tenantId | 重新 OAuth2 流程 | Token 清除 |

### Edge Cases Checklist
- [ ] code_verifier 长度不足 43 或超过 128
- [ ] code_challenge_method 使用 plain 方法
- [ ] authorization_code 过期或无效
- [ ] state 参数不匹配(CSRF 攻击)
- [ ] Token 交换接口网络错误
- [ ] 验证码加载失败
- [ ] 租户切换时 Token 清除失败
- [ ] 定时刷新时 refreshToken 过期
- [ ] 多标签页同时刷新 Token 冲突

---

## Validation Commands

### Static Analysis
```bash
cd frontend
pnpm typecheck
```
EXPECT: Zero type errors

### Unit Tests
```bash
cd frontend
pnpm test:unit --grep "PKCE|OAuth2"
```
EXPECT: All PKCE/OAuth2 tests pass

### Browser Validation
```bash
cd frontend
pnpm dev
```
EXPECT:
1. 登录页显示验证码组件
2. 点击登录按钮跳转到 OAuth2 授权端点
3. OAuth2 回调页面正确处理 authorization_code
4. state 参数正确验证
5. Token 正确存储
6. Header 布局显示租户切换器
7. 切换租户触发重新 OAuth2 登录
8. Token 定时刷新正确触发
9. 登出调用后端接口并清除前端 Token

### Manual Validation
- [ ] 启动前端开发服务器
- [ ] 选择租户,输入账号密码和验证码
- [ ] 点击登录,观察是否跳转到 OAuth2 授权端点
- [ ] 确认授权后,观察是否跳转回前端并携带 code 参数
- [ ] 检查是否正确验证 state 参数
- [ ] 检查是否正确交换 Token
- [ ] 检查 Token 是否正确存储
- [ ] 检查 Header 布局是否显示租户切换器
- [ ] 点击租户切换器下拉菜单,切换租户
- [ ] 观察是否清除 Token 并重新 OAuth2 登录
- [ ] 登出后检查是否调用后端接口并清除前端 Token
- [ ] 观察 Token 定时刷新是否触发(需要等待 Token 有效期一半时间)

---

## Acceptance Criteria
- [ ] 所有 10 个任务完成
- [ ] TypeScript 类型检查通过
- [ ] ESLint 检查通过
- [ ] PKCE code_verifier/code_challenge 正确生成
- [ ] OAuth2 授权码请求 URL 正确构建
- [ ] OAuth2 Token 交换正确执行
- [ ] state 参数正确验证(CSRF 防护)
- [ ] 验证码组件正确显示和刷新
- [ ] 登录页正确启动 OAuth2 流程
- [ ] OAuth2 回调正确处理
- [ ] 租户切换器正确显示和切换
- [ ] Token 定时刷新正确触发
- [ ] 登出正确调用后端接口

## Completion Checklist
- [ ] 所有代码遵循 Frontend 编码规范
- [ ] 使用内联类型导入 `{ type X }`
- [ ] API 类型定义前置导出
- [ ] 使用 `http.request<T>` 泛型
- [ ] Pinia store 使用 `defineStore` + Hook 导出
- [ ] Vue 组件使用 `<script setup lang="ts">` + `defineOptions`
- [ ] 无硬编码字符串
- [ ] 无相对路径导入,使用 `@/` 别名
- [ ] HTML 标签全部自闭合
- [ ] OAuth2 流程符合 RFC 6749 和 RFC 7636 规范

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 后端 OAuth2 接口未就绪 | High | High | 使用 mock OAuth2 服务先实现前端流程 |
| Web Crypto API 浏览器兼容性 | Low | Medium | 提供 polyfill 或降级方案 |
| Token 定时刷新时序问题 | Medium | Medium | 严格测试刷新时间点和递归逻辑 |
| 租户切换流程复杂 | Medium | Medium | 确保清除 Token 并重新 OAuth2 登录 |
| state 验证失败处理 | Medium | High | 明确错误提示和重定向逻辑 |

## Notes
1. **Phase 1.2 目标**: 完成完整的 OAuth2 授权码 + PKCE 流程,符合 RFC 规范
2. **前置依赖**: Phase 1.1 已完成租户状态管理和 HTTP 拦截器基础改造
3. **安全要求**: state 参数验证必须严格执行,防止 CSRF 攻击
4. **浏览器兼容**: Web Crypto API 在现代浏览器已支持,IE11 需 polyfill
5. **Token 刷新策略**: 定时刷新为主,拦截器刷新为应急措施
6. **依赖后端**: 需要后端提供完整的 OAuth2 授权端点和 Token 交换接口

---

**Generated**: 2026-05-06
**Subplan Status**: Ready for Implementation
**Parent Plan**: `.claude/PRPs/plans/frontend-phase1-sa-token-integration.plan.md`
**Confidence Score**: 9/10 — OAuth2 流程明确,PKCE 实现标准