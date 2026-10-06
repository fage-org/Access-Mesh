import { describe, expect, it, vi } from "vitest";
import {
  authorizationRequest,
  consentCallback,
  oauthReturnTarget
} from "./flow";

const request = {
  clientId: "spa",
  responseType: "code",
  redirectUri: "https://untrusted.example/"
};
const preview = {
  clientId: "spa",
  clientName: "应用",
  redirectUri: "https://registered.example/cb?keep=1",
  scopes: ["profile"],
  state: "a&b"
};

describe("OAuth2 同意/拒绝", () => {
  it("拒绝不签码，只向核验过的回调传 error 与原 state", async () => {
    const authorize = vi.fn();
    const callback = new URL(
      await consentCallback(false, request, preview, authorize)
    );
    expect(authorize).not.toHaveBeenCalled();
    expect(callback.origin).toBe("https://registered.example");
    expect(callback.searchParams.get("error")).toBe("access_denied");
    expect(callback.searchParams.get("state")).toBe("a&b");
    expect(callback.searchParams.has("code")).toBe(false);
  });

  it("同意后等待服务端签码，再拼装回调", async () => {
    const authorize = vi
      .fn()
      .mockResolvedValue({ code: "one-use-code", state: "a&b" });
    const callback = new URL(
      await consentCallback(true, request, preview, authorize)
    );
    expect(authorize).toHaveBeenCalledExactlyOnceWith(request);
    expect(callback.searchParams.get("code")).toBe("one-use-code");
    expect(callback.searchParams.get("keep")).toBe("1");
  });

  it("危险协议不签码、不生成可执行回调", async () => {
    const authorize = vi.fn();
    await expect(
      consentCallback(
        true,
        request,
        { ...preview, redirectUri: "javascript:alert(1)" },
        authorize
      )
    ).rejects.toThrow();
    expect(authorize).not.toHaveBeenCalled();
  });

  it("多值参数拒绝；登录续接只接受本地授权页", () => {
    expect(() => authorizationRequest({ client_id: ["a", "b"] })).toThrow();
    expect(oauthReturnTarget("/oauth2/authorize?client_id=spa")).toBe(
      "/oauth2/authorize?client_id=spa"
    );
    expect(
      oauthReturnTarget("//evil.example/oauth2/authorize")
    ).toBeUndefined();
    expect(oauthReturnTarget("https://evil.example")).toBeUndefined();
    expect(oauthReturnTarget("/oauth2/authorize-evil")).toBeUndefined();
  });
});
