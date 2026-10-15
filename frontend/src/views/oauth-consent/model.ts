import { ref, watch, type Ref } from "vue";
import type {
  AuthorizationPreview,
  OAuthAuthorizeReq
} from "@/api/oauth-consent";
import { authorizationRequest } from "./flow";

/** 授权请求同一性：七个契约字段全等才视为同一请求（client 切换必不等）。 */
export function sameAuthorizationRequest(
  a: OAuthAuthorizeReq,
  b: OAuthAuthorizeReq
): boolean {
  return (
    a.clientId === b.clientId &&
    a.responseType === b.responseType &&
    a.redirectUri === b.redirectUri &&
    a.state === b.state &&
    a.scope === b.scope &&
    a.codeChallenge === b.codeChallenge &&
    a.codeChallengeMethod === b.codeChallengeMethod
  );
}

export interface ConsentModel {
  loading: Ref<boolean>;
  error: Ref<string>;
  preview: Ref<AuthorizationPreview | null>;
  /** 重新核验当前 querySource()：挂载时与 query 变化时调用。 */
  refresh: () => void;
  /** 当前已核验请求；未核验/核验失败/已被新 query 作废时为 null——签码入口的唯一请求来源。 */
  currentRequest: () => OAuthAuthorizeReq | null;
}

/**
 * 授权确认页的请求/预览生命周期（T-PERM-110 切换签码修复）。
 *
 * 旧实现只在挂载时读一次 query：同一授权页 query 切换 client 后不重挂载，
 * 显示的 preview 与签码用的 request 都是旧 query 的。本模型保证：
 * - query 变化即作废旧 request/preview 并换取新 preview（作废先于请求发出）；
 * - 迟到响应按代际丢弃，不回写已被取代的状态；
 * - 核验失败（含参数缺失）时 request 置 null，签码入口不可达。
 */
export function createConsentModel(
  querySource: () => Record<string, unknown>,
  previewApi: (request: OAuthAuthorizeReq) => Promise<AuthorizationPreview>,
  formatError: (error: unknown) => string
): ConsentModel {
  const loading = ref(true);
  const error = ref("");
  const preview = ref<AuthorizationPreview | null>(null);
  let request: OAuthAuthorizeReq | null = null;
  let seq = 0;

  function refresh() {
    const ticket = ++seq;
    let next: OAuthAuthorizeReq;
    try {
      next = authorizationRequest(querySource());
    } catch (e) {
      request = null;
      preview.value = null;
      error.value = formatError(e);
      loading.value = false;
      return;
    }
    if (request && preview.value && sameAuthorizationRequest(next, request)) {
      return;
    }
    // 先作废旧态再发请求：query 已变化，旧 request/preview 不得在在途窗口内继续签码
    request = null;
    preview.value = null;
    error.value = "";
    loading.value = true;
    previewApi(next)
      .then(loaded => {
        if (ticket !== seq) return;
        request = next;
        preview.value = loaded;
      })
      .catch(e => {
        if (ticket !== seq) return;
        error.value = formatError(e);
      })
      .finally(() => {
        if (ticket === seq) loading.value = false;
      });
  }

  watch(querySource, refresh);

  return { loading, error, preview, refresh, currentRequest: () => request };
}
