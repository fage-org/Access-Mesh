import type {
  OAuthAuthorizeReq,
  AuthorizationPreview
} from "@/api/oauth-consent";

/** 授权入口使用 OAuth 标准 query 名，发送给平台 POST API 时转换为其 camelCase DTO。 */
export function authorizationRequest(
  query: Record<string, unknown>
): OAuthAuthorizeReq {
  function value(key: string, required = false): string | undefined {
    const raw = query[key];
    if (raw == null && !required) return undefined;
    if (typeof raw !== "string" || (required && !raw.trim())) {
      throw new Error(`授权参数 ${key} 缺失或不合法`);
    }
    return raw;
  }
  return {
    clientId: value("client_id", true),
    responseType: value("response_type", true),
    redirectUri: value("redirect_uri", true),
    state: value("state"),
    scope: value("scope"),
    codeChallenge: value("code_challenge"),
    codeChallengeMethod: value("code_challenge_method")
  };
}

/** 只接收服务端预览返回的回调；拒绝时不调用签码入口。 */
export async function consentCallback(
  approved: boolean,
  request: OAuthAuthorizeReq,
  preview: AuthorizationPreview,
  authorize: (
    request: OAuthAuthorizeReq
  ) => Promise<{ code: string; state: string | null }>
): Promise<string> {
  const target = new URL(preview.redirectUri);
  if (
    ["javascript:", "data:", "vbscript:", "file:", "blob:", "about:"].includes(
      target.protocol
    )
  ) {
    throw new Error("回调地址协议不可用");
  }
  target.searchParams.delete("code");
  target.searchParams.delete("error");
  target.searchParams.delete("error_description");
  target.searchParams.delete("state");
  if (approved) {
    const result = await authorize(request);
    target.searchParams.set("code", result.code);
    if (result.state != null) target.searchParams.set("state", result.state);
  } else {
    target.searchParams.set("error", "access_denied");
    if (preview.state != null) target.searchParams.set("state", preview.state);
  }
  return target.toString();
}

/** 登录/强制改密只接受本地授权页续接，拒绝外站和协议相对 URL。 */
export function oauthReturnTarget(raw: unknown): string | undefined {
  return typeof raw === "string" && /^\/oauth2\/authorize(?:\?|$)/.test(raw)
    ? raw
    : undefined;
}
