import { describe, expect, it } from "vitest";
import operationRoutes from "./resource-operation";

type FakeRoute = {
  url: string;
  response: (context: { body: Record<string, any> }) => any;
};

function routeOf(routes: unknown, url: string): FakeRoute {
  const route = (routes as FakeRoute[]).find(item => item.url === url);
  if (!route) throw new Error(`mock route not found: ${url}`);
  return route;
}

const listRoute = routeOf(
  operationRoutes,
  "/api/perm/operation-permission/list"
);

describe("resource-operation mock 契约", () => {
  it("list：空白串 resourceTypeCode 视同缺省不过滤（契约 §5.3，与后端 isBlank 同口径）", () => {
    const unfiltered = listRoute.response({ body: {} });
    const blank = listRoute.response({ body: { resourceTypeCode: "   " } });

    expect(blank.code).toBe(200);
    expect(unfiltered.data.items.length).toBeGreaterThan(0);
    expect(blank.data.items.length).toBe(unfiltered.data.items.length);
  });

  it("list：已知类型过滤仅返回该类型定义", () => {
    const resp = listRoute.response({ body: { resourceTypeCode: "MENU" } });

    expect(resp.code).toBe(200);
    expect(resp.data.items.length).toBeGreaterThan(0);
    expect(
      resp.data.items.every(
        (op: { resourceTypeCode: string }) => op.resourceTypeCode === "MENU"
      )
    ).toBe(true);
  });

  it("list：未知类型返回空列表（fail-closed，对齐后端口径）", () => {
    const resp = listRoute.response({
      body: { resourceTypeCode: "NO_SUCH_TYPE" }
    });

    expect(resp.code).toBe(200);
    expect(resp.data.items).toEqual([]);
  });
});
