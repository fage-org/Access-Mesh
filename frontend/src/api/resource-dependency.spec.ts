/**
 * 资源依赖 API 层 URL 契约锁（T-FE-044）。
 *
 * 锁住的性质：六端点必须为单命名空间形态 `/api/access/resource-dependency/...`
 * （T-ACCESS-042 起外部=服务路径，vite proxy 单条 `/api` → Gateway 无 StripPrefix 直达）。
 * 历史缺陷：曾误写缺前缀旧形态致 dev 下读端点 404 被页面 catch 吞成空数据假象
 * （mock 拦截 url 与误写路径一致而长期不可见，联调 create 404 才暴露）——
 * 本 spec 在回归旧形态（裸路径/`/perm` 双前缀）下必红，防止新增端点/复制建页时复发。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request: mockRequest } }));

import {
  getDependencyList,
  createDependency,
  updateDependency,
  removeDependencies,
  getDependencyGraph,
  checkDependencyCycle
} from "./resource-dependency";

const PREFIX = "/api/access/resource-dependency";

const okResp = <T>(data: T) => ({ code: 200, message: "success", data });

beforeEach(() => {
  mockRequest.mockReset();
  mockRequest.mockResolvedValue(okResp(null));
});

describe("resource-dependency api URL 契约（/perm Gateway 前缀）", () => {
  it("getDependencyList → POST /list", async () => {
    await getDependencyList({});
    expect(mockRequest).toHaveBeenCalledWith("post", `${PREFIX}/list`, {
      data: {}
    });
  });

  it("getDependencyGraph → POST /graph", async () => {
    await getDependencyGraph({});
    expect(mockRequest).toHaveBeenCalledWith("post", `${PREFIX}/graph`, {
      data: {}
    });
  });

  it("checkDependencyCycle → POST /check（业务键载荷直传）", async () => {
    const req = {
      sourceResourceTypeCode: "MENU",
      sourceResourceCode: "role-manage",
      targetResourceTypeCode: "API",
      targetResourceCode: "role-detail"
    };
    await checkDependencyCycle(req);
    expect(mockRequest).toHaveBeenCalledWith("post", `${PREFIX}/check`, {
      data: req
    });
  });

  it("createDependency → POST /create", async () => {
    await createDependency({
      sourceResourceTypeCode: "MENU",
      sourceResourceCode: "role-manage",
      targetResourceTypeCode: "API",
      targetResourceCode: "role-detail",
      requiredOperationCodes: ["VIEW"]
    });
    expect(mockRequest).toHaveBeenCalledTimes(1);
    const [method, url] = mockRequest.mock.calls[0];
    expect(method).toBe("post");
    expect(url).toBe(`${PREFIX}/create`);
  });

  it("updateDependency → POST /update", async () => {
    await updateDependency({
      id: 1,
      sourceResourceTypeCode: "MENU",
      sourceResourceCode: "role-manage",
      targetResourceTypeCode: "API",
      targetResourceCode: "role-detail",
      requiredOperationCodes: ["VIEW"]
    });
    const [method, url] = mockRequest.mock.calls[0];
    expect(method).toBe("post");
    expect(url).toBe(`${PREFIX}/update`);
  });

  it("removeDependencies → POST /remove（IdsReq 信封）", async () => {
    await removeDependencies([1, 2]);
    expect(mockRequest).toHaveBeenCalledWith("post", `${PREFIX}/remove`, {
      data: { ids: [1, 2] }
    });
  });
});
