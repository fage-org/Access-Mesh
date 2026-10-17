import {
  assertPlatformSessionCurrent,
  acceptLocalPlatformSession
} from "@/utils/platform-session";
import {
  assertTenantSessionCurrent,
  acceptLocalTenantSession
} from "@/utils/tenant-session";
import Cookies from "js-cookie";
import { useUserStoreHook } from "@/store/modules/user";
import { storageLocal, isString, isIncludeAllChildren } from "@pureadmin/utils";

/** Script-readable metadata; sessionId is a CSRF/context marker, never a login credential. */
export interface DataInfo<T> {
  sessionId: string;
  expires: T;
  avatar?: string;
  username?: string;
  nickname?: string;
  roles?: string[];
  permissions?: string[];
  userId?: number;
  tenantId?: number;
  forceResetPwd?: boolean;
}
export const userKey = "tenant-browser-session-v3";
export const multipleTabsKey = "tenant-tabs-v3";
export const platformSessionKey = "platform-browser-session-v3";

/** V1: no conversion of a script-readable legacy credential into a browser session. */
export function clearLegacyAuthentication() {
  Cookies.remove("tenant-authorized-token-v2");
  Cookies.remove("tenant-tabs-v2");
  storageLocal().removeItem("tenant-user-info-v2");
  storageLocal().removeItem("platform-operator-session");
}
export function getTenantSession(): DataInfo<number> | null {
  const session = storageLocal().getItem<DataInfo<number>>(userKey);
  return typeof session?.sessionId === "string" && session.sessionId
    ? session
    : null;
}
export function isSessionTerminated(data = getTenantSession()): boolean {
  return (
    !data?.sessionId ||
    !Number.isFinite(Number(data.expires)) ||
    Number(data.expires) <= Date.now()
  );
}
export function setTenantSession(data: DataInfo<Date>) {
  assertTenantSessionCurrent();
  const current = getTenantSession();
  const previous = current?.sessionId === data.sessionId ? current : null;
  const session: DataInfo<number> = {
    sessionId: data.sessionId,
    expires: new Date(data.expires).getTime(),
    avatar: data.avatar ?? previous?.avatar ?? "",
    username: data.username ?? previous?.username ?? "",
    nickname: data.nickname ?? previous?.nickname ?? "",
    roles: data.roles ?? previous?.roles ?? [],
    permissions: data.permissions ?? previous?.permissions ?? [],
    userId: data.userId,
    tenantId: data.tenantId,
    forceResetPwd: data.forceResetPwd
  };
  storageLocal().setItem(userKey, session);
  acceptLocalTenantSession(session.sessionId);
  const store = useUserStoreHook();
  store.SET_AVATAR(session.avatar);
  store.SET_USERNAME(session.username);
  store.SET_NICKNAME(session.nickname);
  store.SET_ROLES(session.roles);
  store.SET_PERMS(session.permissions);
  Cookies.set(
    multipleTabsKey,
    "true",
    store.isRemembered ? { expires: store.loginDay } : {}
  );
}
/** Removing metadata prevents refresh from reviving a pending/failed logout via its residual Cookie. */
export function removeTenantSession() {
  assertTenantSessionCurrent();
  storageLocal().removeItem(userKey);
  Cookies.remove(multipleTabsKey);
  acceptLocalTenantSession(null);
}

/** 是否有按钮级别的权限（根据登录接口返回的`permissions`字段进行判断）*/
export const hasPerms = (value: string | Array<string>): boolean => {
  if (!value) return false;
  const allPerms = "*:*:*";
  const { permissions } = useUserStoreHook();
  if (!permissions) return false;
  if (permissions.length === 1 && permissions[0] === allPerms) return true;
  const isAuths = isString(value)
    ? permissions.includes(value)
    : isIncludeAllChildren(value, permissions);
  return isAuths ? true : false;
};

/** Platform metadata uses a separate namespace; no native token is persisted. */
export type PlatformSession = {
  sessionId: string;
  expires: number;
  account: {
    id: number;
    username: string;
    name: string;
    status: number;
    forceResetPwd: boolean;
  };
};
export function getPlatformSession(): PlatformSession | null {
  const session = storageLocal().getItem<PlatformSession>(platformSessionKey);
  return typeof session?.sessionId === "string" && session.sessionId
    ? session
    : null;
}
export function setPlatformSession(session: PlatformSession) {
  assertPlatformSessionCurrent();
  storageLocal().setItem(platformSessionKey, {
    sessionId: session.sessionId,
    expires: session.expires,
    account: session.account
  });
  acceptLocalPlatformSession(session.sessionId);
}
export function removePlatformSession() {
  assertPlatformSessionCurrent();
  storageLocal().removeItem(platformSessionKey);
  acceptLocalPlatformSession(null);
}
