/**
 * 登录族 API 层 URL 契约锁（T-ACCESS-042 外评 P0 回归锁）。
 *
 * 锁住的性质：登录链三端点必须为单命名空间形态 `/api/access/auth/...`
 * （外部=服务路径，vite proxy 单条 `/api` → Gateway 白名单精确放行）。
 * 历史缺陷：注释清扫批的子串替换套用到已迁移代码串上，产生双前缀
 * `/api/access/api/access/auth/...`（captcha/login/user-menu 三处 + http 白名单两处），
 * 登录链整体 404——typecheck/vitest 均不锁 URL 字面量而漏网（claude+grok 双通道交叉命中）。
 * 本 spec 在回归双前缀/裸 `/auth/`/`/perm` 前缀旧形态下必红，防机械替换再次逃逸。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request: mockRequest } }));

import { getCaptcha, getUserMenu, logout } from "./auth";
import { RequestError } from "./_envelope";

const PREFIX = "/api/access/auth";

beforeEach(() => {
  mockRequest.mockReset();
  mockRequest.mockResolvedValue({ code: 200, message: "success", data: null });
});

describe("auth api URL 契约（登录族单命名空间）", () => {
  it("getCaptcha → POST /api/access/auth/captcha（双前缀/裸 /auth/ 旧形态必红）", async () => {
    await getCaptcha();
    const [method, url] = mockRequest.mock.calls[0];
    expect(method).toBe("post");
    expect(url).toBe(`${PREFIX}/captcha`);
    expect(url).not.toContain("/api/access/api/access/");
  });

  it("getUserMenu → POST /api/access/auth/user-menu", async () => {
    await getUserMenu();
    const [method, url] = mockRequest.mock.calls[0];
    expect(method).toBe("post");
    expect(url).toBe(`${PREFIX}/user-menu`);
  });

  it("api 目录零双前缀字面量（仓级负向扫描，排除 spec 自身的历史形态记述）", async () => {
    const { readdirSync, readFileSync } = await import("node:fs");
    const { resolve } = await import("node:path");
    const dir = resolve(__dirname, ".");
    const offenders: string[] = [];
    for (const fn of readdirSync(dir)) {
      if (!fn.endsWith(".ts") || fn.endsWith(".spec.ts")) continue;
      if (
        readFileSync(resolve(dir, fn), "utf-8").includes(
          "/api/access/api/access/"
        )
      ) {
        offenders.push(fn);
      }
    }
    expect(offenders).toEqual([]);
  });
});

describe("logout 注销契约（T-FE-045：显式携 token，不经拦截器过期分支免头路径）", () => {
  it("→ POST /api/access/auth/logout，Authorization 头值由调用方显式传入并原样送达", async () => {
    await logout("Bearer raw-token-1");
    const [method, url, config] = mockRequest.mock.calls[0];
    expect(method).toBe("post");
    expect(url).toBe(`${PREFIX}/logout`);
    expect(config.headers).toEqual({ Authorization: "Bearer raw-token-1" });
  });

  it("unwrap 解包：code≠200 抛 RequestError（业务失败 HTTP 200 形态）", async () => {
    mockRequest.mockResolvedValueOnce({
      code: 10901,
      message: "会话不存在",
      data: null
    });
    await expect(logout("Bearer t")).rejects.toThrow(RequestError);
  });

  it("http 请求白名单含 /api/access/auth/logout（防过期分支 logOut 递归与注销空转）", async () => {
    const { readFileSync } = await import("node:fs");
    const { resolve } = await import("node:path");
    const src = readFileSync(
      resolve(__dirname, "../utils/http/index.ts"),
      "utf-8"
    );
    expect(src).toContain('"/api/access/auth/logout"');
  });
});
