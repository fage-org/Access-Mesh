import { readonly, ref } from "vue";
export type SessionSnapshot = { token: string | null; expires: number | null };
export type SessionRequestStamp = { token: string | null; revision: number };
export class SessionChangedError extends Error {
  constructor() {
    super("登录状态已改变，请重新载入页面");
    this.name = "SessionChangedError";
  }
}

/** Each authentication realm owns a separate instance; only the algorithm is shared. */
export function createSessionBoundary(allowExpiredCookie = false) {
  const blocked = ref(false);
  const sessionBlocked = readonly(blocked);
  let readSession: (() => SessionSnapshot) | undefined;
  let invalidate: (() => void) | undefined;
  let expectedToken: string | null = null;
  let revision = 0;
  let dispose: (() => void) | undefined;

  /** Shared storage is only observed here: invalidation never deletes or revokes another tab's session. */
  function assertCurrent() {
    if (!readSession) return;
    if (blocked.value) throw new SessionChangedError();
    let current: SessionSnapshot;
    try {
      current = readSession();
    } catch {
      current = { token: null, expires: null };
    }
    // Cookie expiry leaves expired local metadata: retain the existing local-expiry/logout flow.
    const expiredCookie =
      allowExpiredCookie &&
      current.token === null &&
      current.expires !== null &&
      current.expires <= Date.now();
    if (current.token !== expectedToken && !expiredCookie) {
      blocked.value = true;
      invalidate?.();
      throw new SessionChangedError();
    }
  }

  function captureRequest(): SessionRequestStamp | undefined {
    assertCurrent();
    return readSession ? { token: expectedToken, revision } : undefined;
  }

  function assertRequest(stamp?: SessionRequestStamp) {
    assertCurrent();
    if (
      stamp &&
      (stamp.revision !== revision || stamp.token !== expectedToken)
    ) {
      throw new SessionChangedError();
    }
  }

  /** Only the current tab's verified login/logout write calls this, after checking the old session. */
  function acceptLocal(token: string | null) {
    if (!readSession) return;
    if (blocked.value) throw new SessionChangedError();
    if (token !== expectedToken) revision++;
    expectedToken = token;
  }

  function start(options: {
    read: () => SessionSnapshot;
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
        assertCurrent();
      } catch (error) {
        if (!(error instanceof SessionChangedError)) throw error;
      }
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key === null || event.key === options.storageKey) check();
    };
    options.window.addEventListener("storage", onStorage);
    options.window.addEventListener("focus", check);
    options.window.addEventListener("pageshow", check);
    options.document.addEventListener("visibilitychange", check);
    const stop = () => {
      if (dispose !== stop) return;
      options.window.removeEventListener("storage", onStorage);
      options.window.removeEventListener("focus", check);
      options.window.removeEventListener("pageshow", check);
      options.document.removeEventListener("visibilitychange", check);
      readSession = undefined;
      invalidate = undefined;
      blocked.value = false;
      dispose = undefined;
    };
    dispose = stop;
    return stop;
  }

  return {
    blocked: sessionBlocked,
    assertCurrent,
    captureRequest,
    assertRequest,
    acceptLocal,
    start
  };
}

/** Hash navigation alone leaves the old JS session alive; confirmation must discard the entire page runtime. */
export function reloadSessionPage(
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
