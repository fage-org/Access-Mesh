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

import { getCaptcha, getUserMenu } from "./auth";

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
      if (readFileSync(resolve(dir, fn), "utf-8").includes("/api/access/api/access/")) {
        offenders.push(fn);
      }
    }
    expect(offenders).toEqual([]);
  });
});
