import { readonly, ref } from "vue";

export type TenantSessionSnapshot = {
  token: string | null;
  expires: number | null;
};
export type TenantRequestStamp = { token: string | null; revision: number };
const blocked = ref(false);
export const tenantSessionBlocked = readonly(blocked);
let readSession: (() => TenantSessionSnapshot) | undefined;
let invalidate: (() => void) | undefined;
let expectedToken: string | null = null;
let revision = 0;
let dispose: (() => void) | undefined;

export class TenantSessionChangedError extends Error {
  constructor() {
    super("登录状态已改变，请重新载入页面");
    this.name = "TenantSessionChangedError";
  }
}

/** Shared storage is only observed here: invalidation never deletes or revokes another tab's session. */
export function assertTenantSessionCurrent() {
  if (!readSession) return;
  if (blocked.value) throw new TenantSessionChangedError();
  let current: TenantSessionSnapshot;
  try {
    current = readSession();
  } catch {
    current = { token: null, expires: null };
  }
  // Cookie expiry leaves expired local metadata: retain the existing local-expiry/logout flow.
  const expiredCookie =
    current.token === null &&
    current.expires !== null &&
    current.expires <= Date.now();
  if (current.token !== expectedToken && !expiredCookie) {
    blocked.value = true;
    invalidate?.();
    throw new TenantSessionChangedError();
  }
}

export function captureTenantRequest(): TenantRequestStamp | undefined {
  assertTenantSessionCurrent();
  return readSession ? { token: expectedToken, revision } : undefined;
}

export function assertTenantRequest(stamp?: TenantRequestStamp) {
  assertTenantSessionCurrent();
  if (stamp && (stamp.revision !== revision || stamp.token !== expectedToken)) {
    throw new TenantSessionChangedError();
  }
}

/** Only the current tab's verified login/logout write calls this, after checking the old session. */
export function acceptLocalTenantSession(token: string | null) {
  if (!readSession) return;
  if (blocked.value) throw new TenantSessionChangedError();
  if (token !== expectedToken) revision++;
  expectedToken = token;
}

export function startTenantSessionSync(options: {
  read: () => TenantSessionSnapshot;
  invalidate: () => void;
  storageKey: string;
  window: Window;
  document: Document;
}) {
  dispose?.();
  readSession = options.read;
  invalidate = options.invalidate;
  expectedToken = options.read().token;
  revision = 0;
  blocked.value = false;
  const check = () => {
    try {
      assertTenantSessionCurrent();
    } catch (error) {
      if (!(error instanceof TenantSessionChangedError)) throw error;
    }
  };
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === options.storageKey) check();
  };
  options.window.addEventListener("storage", onStorage);
  options.window.addEventListener("focus", check);
  options.window.addEventListener("pageshow", check);
  options.document.addEventListener("visibilitychange", check);
  dispose = () => {
    options.window.removeEventListener("storage", onStorage);
    options.window.removeEventListener("focus", check);
    options.window.removeEventListener("pageshow", check);
    options.document.removeEventListener("visibilitychange", check);
    readSession = undefined;
    invalidate = undefined;
    blocked.value = false;
    dispose = undefined;
  };
  return dispose;
}

/** Hash navigation alone leaves the old JS session alive; confirmation must discard the entire page runtime. */
export function reloadTenantSession(
  location: Pick<Location, "href" | "replace" | "reload">,
  destination: string
) {
  const previous = new URL(location.href);
  const next = new URL(destination, previous);
  const sameDocument =
    previous.origin === next.origin &&
    previous.pathname === next.pathname &&
    previous.search === next.search;
  location.replace(next.href);
  if (sameDocument) location.reload();
}
