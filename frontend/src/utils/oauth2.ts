import { useOAuth2StoreHook } from "@/store/modules/oauth2";
import { useTenantStoreHook } from "@/store/modules/tenant";
import {
  buildAuthorizationUrl,
  exchangeToken,
  type AuthorizationCodeRequest
} from "@/api/admin/oauth2";
import { setToken } from "@/utils/auth";
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
      tenantId: tenantStore.currentTenantId ?? undefined,
      isOAuth2: true // 标识 OAuth2 登录
    });

    // 6. 清理 OAuth2 状态
    oauth2Store.CLEAR();

    // 7. 跳转到首页
    router.push("/");
  } catch (err) {
    console.error("Token exchange error:", err);
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
