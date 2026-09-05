/**
 * 服务接口同步 ApiItem.operationCode 退役的前端回归锁（T-PERM-053，2026-09-05）。
 * 前后端同批锁步：后端 DTO 删字段 + 全局严格 ObjectMapper（未知字段拒绝），
 * 旧载荷提交将 400——前端解析层不得再要求该字段（旧实现下首条用例失败）。
 * 字段退役为「删除」而非「反向校验」：载荷仍携带 operationCode 时前端放行
 * （字段被忽略），由后端 400 兜底，不在前端复刻第二套拒绝语义。
 */
import { describe, it, expect, vi } from "vitest";

// mock @/utils/http：阻断 http → store/router 链（同 role/types.spec.ts 范式）
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));

import type { ServiceConfigResp } from "@/api/service-interface";
import { createSyncPayload, parseSyncGroups } from "./types";

const service: ServiceConfigResp = {
  id: 1,
  serviceCode: "my-svc",
  name: "示例服务",
  basePath: "/",
  status: 1
};

describe("服务接口同步 operationCode 退役（T-PERM-053）", () => {
  it("锁步后载荷：不含 operationCode 的接口条目解析通过", () => {
    const raw = JSON.stringify([
      {
        groupCode: "default",
        groupName: "默认",
        apis: [
          {
            name: "查询列表",
            httpMethod: "POST",
            path: "/api/demo/list",
            resourceCode: "my-svc:demo:list",
            description: "示例"
          }
        ]
      }
    ]);
    const result = parseSyncGroups(raw);
    expect(result.error).toBeUndefined();
    expect(result.groups?.[0].apis[0].resourceCode).toBe("my-svc:demo:list");
  });

  it("示例 payload（createSyncPayload）不再携带 operationCode", () => {
    const payload = createSyncPayload(service);
    const parsed = JSON.parse(payload.groupsJson);
    const api = parsed[0].apis[0];
    expect("operationCode" in api).toBe(false);
    expect(api.resourceCode).toBe("my-svc:example:list");
  });

  it("载荷仍含 operationCode 时前端放行（删除而非反向校验，后端 400 兜底）", () => {
    const raw = JSON.stringify([
      {
        groupCode: "default",
        groupName: "默认",
        apis: [
          {
            name: "查询列表",
            httpMethod: "POST",
            path: "/api/demo/list",
            operationCode: "VIEW",
            resourceCode: "my-svc:demo:list"
          }
        ]
      }
    ]);
    const result = parseSyncGroups(raw);
    expect(result.error).toBeUndefined();
    expect(result.groups).toHaveLength(1);
  });

  it("其余必填校验语义保持：缺 resourceCode 仍报错", () => {
    const raw = JSON.stringify([
      {
        groupCode: "default",
        groupName: "默认",
        apis: [
          {
            name: "查询列表",
            httpMethod: "POST",
            path: "/api/demo/list"
          }
        ]
      }
    ]);
    const result = parseSyncGroups(raw);
    expect(result.error).toContain("resourceCode");
  });
});
