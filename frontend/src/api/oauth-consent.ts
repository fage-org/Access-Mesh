import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";

export type OAuthAuthorizeReq = {
  clientId: string;
  responseType: string;
  redirectUri: string;
  state?: string;
  scope?: string;
  codeChallenge?: string;
  codeChallengeMethod?: string;
};

export type AuthorizationPreview = {
  clientId: string;
  clientName: string;
  redirectUri: string;
  scopes: string[];
  state: string | null;
};

export async function previewAuthorization(data: OAuthAuthorizeReq) {
  return unwrap(
    await http.request<R<AuthorizationPreview>>(
      "post",
      "/api/access/auth/oauth2/authorize-preview",
      { data }
    )
  );
}

export async function approveAuthorization(data: OAuthAuthorizeReq) {
  return unwrap(
    await http.request<R<{ code: string; state: string | null }>>(
      "post",
      "/api/access/auth/oauth2/authorize",
      { data }
    )
  );
}
