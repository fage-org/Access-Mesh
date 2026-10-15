import { describe, expect, it, vi } from "vitest";
import { nextTick, ref } from "vue";
import type {
  AuthorizationPreview,
  OAuthAuthorizeReq
} from "@/api/oauth-consent";
import { defer, flush } from "@/test-support/async";
import { createConsentModel, sameAuthorizationRequest } from "./model";

/**
 * 授权确认页请求/预览生命周期回归（T-PERM-110 切换签码修复）：
 * query 切换 client 后旧 request 作废并换取新 preview（作废先于请求发出）、
 * 迟到响应按代际丢弃、核验失败时签码入口不可达。
 * 并发时序用受控 promise（defer）确定性表达，禁裸 sleep 余量。
 * query 源用 ref 承载（镜像组件内响应式 route.query 的依赖跟踪形态）。
 */
const queryA = {
  client_id: "app-a",
  response_type: "code",
  redirect_uri: "https://a.example/cb"
};
const queryB = {
  client_id: "app-b",
  response_type: "code",
  redirect_uri: "https://b.example/cb"
};

function requestOf(clientId: "app-a" | "app-b"): OAuthAuthorizeReq {
  return {
    clientId,
    responseType: "code",
    redirectUri: `https://${clientId === "app-a" ? "a" : "b"}.example/cb`
  };
}

function previewOf(clientId: "app-a" | "app-b"): AuthorizationPreview {
  return {
    clientId,
    clientName: clientId,
    redirectUri: `https://${clientId === "app-a" ? "a" : "b"}.example/cb`,
    scopes: [],
    state: null
  };
}

const identity = (e: unknown) => (e instanceof Error ? e.message : String(e));

describe("OAuth 授权页请求生命周期", () => {
  it("query 切换 client 后作废旧请求并换取新 preview", async () => {
    const query = ref<Record<string, unknown>>({ ...queryA });
    const previewApi = vi
      .fn()
      .mockImplementation((req: OAuthAuthorizeReq) =>
        Promise.resolve(previewOf(req.clientId as "app-a" | "app-b"))
      );
    const model = createConsentModel(() => query.value, previewApi, identity);

    model.refresh();
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-a"));
    expect(previewApi).toHaveBeenCalledExactlyOnceWith(requestOf("app-a"));

    query.value = { ...queryB };
    await nextTick();
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-b"));
    expect(model.currentRequest()).toEqual(requestOf("app-b"));
    expect(previewApi).toHaveBeenCalledTimes(2);
    expect(previewApi).toHaveBeenNthCalledWith(2, requestOf("app-b"));
  });

  it("切换后旧 preview 立即作废：新 preview 到位前签码入口不可达", async () => {
    const query = ref<Record<string, unknown>>({ ...queryA });
    let release = (_p: AuthorizationPreview) => {};
    const previewApi = vi.fn().mockImplementation((req: OAuthAuthorizeReq) =>
      req.clientId === "app-a"
        ? Promise.resolve(previewOf("app-a"))
        : new Promise<AuthorizationPreview>(resolve => {
            release = resolve;
          })
    );
    const model = createConsentModel(() => query.value, previewApi, identity);

    model.refresh();
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-a"));

    query.value = { ...queryB };
    await nextTick();
    await flush();
    // 新 preview 未返回期间：旧 preview 已清空、currentRequest 为 null——
    // 同意/拒绝按钮虽可点击但 decide() 无法取得签码请求
    expect(model.preview.value).toBeNull();
    expect(model.currentRequest()).toBeNull();
    expect(model.loading.value).toBe(true);

    release(previewOf("app-b"));
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-b"));
    expect(model.currentRequest()).toEqual(requestOf("app-b"));
  });

  it("迟到响应按代际丢弃，不回写已被取代的 preview", async () => {
    const query = ref<Record<string, unknown>>({ ...queryA });
    const stale = defer<AuthorizationPreview>();
    const previewApi = vi
      .fn()
      .mockImplementation((req: OAuthAuthorizeReq) =>
        req.clientId === "app-a"
          ? stale.promise
          : Promise.resolve(previewOf("app-b"))
      );
    const model = createConsentModel(() => query.value, previewApi, identity);

    model.refresh(); // app-a 核验在途
    query.value = { ...queryB };
    await nextTick(); // watch 触发第二次 refresh，代际 +1
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-b"));

    stale.resolve(previewOf("app-a")); // 旧响应迟到
    await flush();
    await flush();
    expect(model.preview.value?.clientId).toBe("app-b");
    expect(model.currentRequest()).toEqual(requestOf("app-b"));
  });

  it("切换后核验失败（参数缺失）清空状态，签码入口不可达", async () => {
    const query = ref<Record<string, unknown>>({ ...queryA });
    const previewApi = vi.fn().mockResolvedValue(previewOf("app-a"));
    const model = createConsentModel(() => query.value, previewApi, identity);

    model.refresh();
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-a"));

    query.value = { response_type: "code" }; // client_id 缺失
    await nextTick();
    await flush();
    expect(model.error.value).toContain("client_id");
    expect(model.preview.value).toBeNull();
    expect(model.currentRequest()).toBeNull();
  });

  it("语义相同的 query 变化不重复核验；sameAuthorizationRequest 全字段判定", async () => {
    const a = requestOf("app-a");
    expect(sameAuthorizationRequest(a, { ...a })).toBe(true);
    expect(sameAuthorizationRequest(a, { ...a, clientId: "app-b" })).toBe(
      false
    );
    expect(sameAuthorizationRequest(a, { ...a, state: "s1" })).toBe(false);
    expect(
      sameAuthorizationRequest(
        { ...a, codeChallenge: undefined },
        { ...a, codeChallenge: "cc" }
      )
    ).toBe(false);

    const query = ref<Record<string, unknown>>({ ...queryA, state: "s1" });
    const previewApi = vi
      .fn()
      .mockImplementation((req: OAuthAuthorizeReq) =>
        Promise.resolve(previewOf(req.clientId as "app-a" | "app-b"))
      );
    const model = createConsentModel(() => query.value, previewApi, identity);
    model.refresh();
    await vi.waitFor(() => expect(model.preview.value?.clientId).toBe("app-a"));

    query.value = { ...queryA, state: "s1", extra_ignored: "x" }; // 契约字段不变
    await nextTick();
    await flush();
    expect(previewApi).toHaveBeenCalledTimes(1);
    expect(model.currentRequest()).toEqual({
      ...requestOf("app-a"),
      state: "s1"
    });
  });
});
