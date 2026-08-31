import { afterEach, describe, expect, it, vi } from "vitest";
import conditionRoutes from "./permission-condition";
import grantRoutes from "./permission-grant";
import {
  findActiveMockCondition,
  mockConditions,
  persistMockConditions
} from "./_shared/permission-condition-store";

type FakeRoute = {
  url: string;
  response: (context: { body: Record<string, any> }) => any;
};

function routeOf(routes: unknown, url: string): FakeRoute {
  const route = (routes as FakeRoute[]).find(item => item.url === url);
  if (!route) throw new Error(`mock route not found: ${url}`);
  return route;
}

const subPermRoute = routeOf(
  grantRoutes,
  "/api/perm/role-resource-permission/sub-perm-allowed-types"
);
const applyRoute = routeOf(
  grantRoutes,
  "/api/perm/role-resource-permission/apply-grant-plan"
);
const updateConditionRoute = routeOf(
  conditionRoutes,
  "/api/perm/permission-condition/update"
);
const listRoute = routeOf(
  grantRoutes,
  "/api/perm/role-resource-permission/list"
);

function roleBody(extra: Record<string, any> = {}) {
  return {
    roleTypeCode: "BASIC_ROLE",
    roleExternalId: "BASIC_201",
    ...extra
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  const officeHours = findActiveMockCondition("office-hours");
  if (officeHours) officeHours.enabled = true;
});

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number {
    return this.values.size;
  }

  clear(): void {
    this.values.clear();
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  key(index: number): string | null {
    return [...this.values.keys()][index] ?? null;
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
}

describe("permission-grant mock 契约", () => {
  it("独立 mock 模块实例共享同一条件状态", async () => {
    vi.resetModules();
    const reloadedStore = await import("./_shared/permission-condition-store");

    expect(reloadedStore.mockConditions).toBe(mockConditions);
  });

  it("SUB_PERM：API 配置存在但无匹配项 → PARENT_NOT_CONFIGURED", () => {
    const response = subPermRoute.response({
      body: roleBody({ parentResourceTypeCode: "API" })
    });
    expect(response).toMatchObject({
      code: 200,
      data: {
        parentResourceTypeCode: "API",
        mode: "ALLOW_NONE",
        reason: "PARENT_NOT_CONFIGURED",
        allowedChildResourceTypeCodes: []
      }
    });
  });

  it("SUB_PERM：BUTTON 所属域无配置 → CONFIG_MISSING", () => {
    const response = subPermRoute.response({
      body: roleBody({ parentResourceTypeCode: "BUTTON" })
    });
    expect(response).toMatchObject({
      code: 200,
      data: { mode: "ALLOW_NONE", reason: "CONFIG_MISSING" }
    });
  });

  it("条件 CRUD 与授权校验同源：条件停用后新写入返回 20042", () => {
    const updateResponse = updateConditionRoute.response({
      body: { code: "office-hours", enabled: false }
    });
    expect(updateResponse).toMatchObject({
      code: 200,
      data: { code: "office-hours", enabled: false }
    });

    const response = applyRoute.response({
      body: roleBody({
        roleExternalId: "BASIC_202",
        plan: {
          creates: [
            {
              key: {
                resourceTypeCode: "API",
                resourceCode: "res-tree",
                codeType: "default",
                operationCode: "CREATE",
                scopeMode: "INSTANCE",
                conditionCode: "office-hours",
                canGrant: false
              }
            }
          ],
          updates: [],
          removes: []
        }
      })
    });
    expect(response).toMatchObject({ code: 20042 });
  });

  it("跨标签页条件状态：授权校验会从共享存储重载并返回 20042", () => {
    vi.stubGlobal("localStorage", new MemoryStorage());
    persistMockConditions();

    const updateResponse = updateConditionRoute.response({
      body: { code: "office-hours", enabled: false }
    });
    expect(updateResponse).toMatchObject({ code: 200 });

    // 模拟授权页标签仍保留旧模块内存；apply 时必须以共享存储中的停用状态为准。
    const staleOfficeHours = mockConditions.find(
      condition => condition.code === "office-hours"
    );
    if (!staleOfficeHours) throw new Error("office-hours fixture missing");
    staleOfficeHours.enabled = true;

    const response = applyRoute.response({
      body: roleBody({
        roleExternalId: "BASIC_202",
        plan: {
          creates: [
            {
              key: {
                resourceTypeCode: "API",
                resourceCode: "res-tree",
                codeType: "default",
                operationCode: "CREATE",
                scopeMode: "INSTANCE",
                conditionCode: "office-hours",
                canGrant: false
              }
            }
          ],
          updates: [],
          removes: []
        }
      })
    });
    expect(response).toMatchObject({ code: 20042 });
  });

  it("子权限携带条件时优先返回 20043", () => {
    const response = applyRoute.response({
      body: roleBody({
        plan: {
          creates: [
            {
              parentPermissionId: 1001,
              key: {
                resourceTypeCode: "BUTTON",
                resourceCode: "btn-add",
                codeType: "default",
                operationCode: "VIEW",
                scopeMode: "INSTANCE",
                conditionCode: "office-hours",
                canGrant: false
              }
            }
          ],
          updates: [],
          removes: []
        }
      })
    });
    expect(response).toMatchObject({ code: 20043 });
  });

  it("list：空白串 resourceTypeCode 视同缺省不过滤（契约 §6.4，与后端 isBlank 同口径）", () => {
    const unfiltered = listRoute.response({ body: roleBody() });
    const blank = listRoute.response({
      body: roleBody({ resourceTypeCode: "   " })
    });

    expect(blank.code).toBe(200);
    expect(unfiltered.data.items.length).toBeGreaterThan(0);
    expect(blank.data.items.length).toBe(unfiltered.data.items.length);
  });

  it('list：非空白值原样精确匹配——带边界空格的 " DATA " 属未知类型返回空列表', () => {
    const resp = listRoute.response({
      body: roleBody({ resourceTypeCode: " DATA " })
    });

    expect(resp.code).toBe(200);
    expect(resp.data.items).toEqual([]);
  });
});
