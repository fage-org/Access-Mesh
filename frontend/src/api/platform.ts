import { http } from "@/utils/http";
import { unwrap, type R } from "@/api/_envelope";
import type { CaptchaResp } from "@/api/auth";

export type PlatformAccount = {
  id: number;
  username: string;
  name: string;
  status: number;
  forceResetPwd: boolean;
  createdAt: string;
  updatedAt: string;
};
export type PlatformLoginResp = {
  accessToken: string;
  expiresIn: number;
  account: PlatformAccount;
};
export type Tenant = {
  id: number;
  code: string;
  name: string;
  status: number;
  adminUserId: number;
  accessState: "ENABLED" | "DISABLED" | "UNAVAILABLE";
  createdAt: string;
  updatedAt: string;
};
export type PlatformAudit = {
  id: number;
  operatorId: number | null;
  operatorName: string | null;
  targetTenantId: number | null;
  targetType: string;
  targetId: string;
  action: string;
  outcome: string;
  summary: string;
  requestId: string;
  ipAddress: string;
  createdAt: string;
};
export type PlatformPage<T> = {
  items: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
};
export type PageQuery = { pageNum: number; pageSize: number; keyword?: string };
export type TenantAdminPasswordResp = {
  username: string;
  password: string;
  userStatus: number;
};
export type IssuedPasswordResp = { password: string };
export type TenantCreatedResp = {
  tenant: Tenant;
  adminUsername: string;
  initialPassword: string;
};
export type PlatformAccountCreatedResp = {
  account: PlatformAccount;
  initialPassword: string;
};

async function request<T>(
  path: string,
  data: object = {},
  authorization?: string
): Promise<T> {
  return unwrap(
    await http.request<R<T>>("post", `/api/access/${path}`, {
      authRealm: "platform",
      data,
      ...(authorization ? { headers: { Authorization: authorization } } : {})
    })
  );
}
export const platformCaptcha = () =>
  request<CaptchaResp>("platform-auth/captcha");
export const platformLogin = (data: {
  username: string;
  password: string;
  captchaId: string;
  captchaCode: string;
}) => request<PlatformLoginResp>("platform-auth/login", data);
export const platformMe = () => request<PlatformAccount>("platform-auth/me");
export const platformLogout = (authorization: string) =>
  request<void>("platform-auth/logout", {}, authorization);
export const changePlatformPassword = (data: {
  oldPassword: string;
  newPassword: string;
}) => request<void>("platform-auth/change-password", data);
export const tenantPage = (data: PageQuery) =>
  request<PlatformPage<Tenant>>("tenant/page", data);
export const tenantDetail = (id: number) =>
  request<Tenant>("tenant/detail", { id });
export const createTenant = (data: { code: string; name: string }) =>
  request<TenantCreatedResp>("tenant/create", data);
export const renameTenant = (data: { id: number; name: string }) =>
  request<void>("tenant/update", data);
export const setTenantStatus = (data: { id: number; status: number }) =>
  request<Tenant>("tenant/update-status", data);
export const resetTenantAdmin = (id: number) =>
  request<TenantAdminPasswordResp>("tenant/reset-admin-password", { id });
export const platformAccountPage = (
  data: Pick<PageQuery, "pageNum" | "pageSize">
) => request<PlatformPage<PlatformAccount>>("platform-account/page", data);
export const createPlatformAccount = (data: {
  username: string;
  name: string;
}) => request<PlatformAccountCreatedResp>("platform-account/create", data);
export const renamePlatformAccount = (data: { id: number; name: string }) =>
  request<void>("platform-account/update", data);
export const setPlatformAccountStatus = (data: {
  id: number;
  status: number;
}) => request<void>("platform-account/update-status", data);
export const resetPlatformAccount = (id: number) =>
  request<IssuedPasswordResp>("platform-account/reset-password", { id });
export const platformAuditPage = (data: {
  pageNum: number;
  pageSize: number;
  targetTenantId?: number;
}) => request<PlatformPage<PlatformAudit>>("platform-audit/page", data);
