import { beforeEach, describe, expect, it, vi } from "vitest";
import { effectScope, ref } from "vue";
import { defer, flush } from "@/test-support/async";
import type { SubPermissionPolicy } from "@/api/permission-grant";
import type { PageResp } from "@/api/role-manage";
import type { ResourceResp } from "@/api/resource-operation";
const api = vi.hoisted(() => ({
  policy: vi.fn(),
  candidates: vi.fn(),
  message: vi.fn()
}));
vi.mock("@/api/permission-grant", () => ({
  getSubPermAllowedTypes: api.policy
}));
vi.mock("@/api/resource-operation", () => ({
  getResourceAssociationCandidates: api.candidates
}));
vi.mock("@/utils/message", () => ({ message: api.message }));
import { useChildCandidates, type ChildParent } from "./child-candidates";

const allowed: SubPermissionPolicy = {
  parentResourceTypeCode: "REPORT",
  mode: "ALLOW_LIST",
  reason: null,
  allowedChildResourceTypeCodes: ["FIELD", "SCOPE"]
};
const parent = (code: string): ChildParent => ({
  id: 0,
  changeId: code,
  resourceTypeCode: "REPORT",
  resourceCode: code,
  codeType: "default",
  scopeMode: "INSTANCE"
});
const page = (id: number): PageResp<ResourceResp> => ({
  items: [
    {
      id,
      resourceTypeCode: "FIELD",
      code: `field-${id}`,
      name: "金额"
    } as ResourceResp
  ],
  total: 1,
  pageNum: 1,
  pageSize: 200,
  hasNext: false
});
describe("子权限候选上下文", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.policy.mockResolvedValue(allowed);
    api.candidates.mockResolvedValue(page(1));
  });
  it("迟到的 A 类型策略不能重新开放 B 已拒绝的通用类型", async () => {
    const a = defer<SubPermissionPolicy>(),
      b = defer<SubPermissionPolicy>();
    api.policy.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
    const owner = ref(parent("report-a"));
    const scope = effectScope();
    const model = scope.run(() =>
      useChildCandidates(
        () => owner.value,
        () => ({ roleTypeCode: "BASIC_ROLE", roleExternalId: "reader" })
      )
    )!;
    owner.value = parent("report-b");
    b.resolve({
      parentResourceTypeCode: "REPORT",
      mode: "ALLOW_NONE",
      reason: "ASSOCIATION_NOT_AVAILABLE",
      allowedChildResourceTypeCodes: []
    });
    await flush();
    a.resolve(allowed);
    await flush();
    expect(model.policy.list.value[0].mode).toBe("ALLOW_NONE");
    expect(model.policy.list.value[0].reason).toBe("ASSOCIATION_NOT_AVAILABLE");
    scope.stop();
  });
  it("新主授权没有持久化 ID，也把具体主资源传给类型策略；父 ALL 保持类型上下文", async () => {
    const owner = ref(parent("report-a"));
    const scope = effectScope();
    scope.run(() =>
      useChildCandidates(
        () => owner.value,
        () => ({ roleTypeCode: "BASIC_ROLE", roleExternalId: "reader" })
      )
    );
    await flush();
    expect(api.policy).toHaveBeenLastCalledWith(
      expect.objectContaining({
        parentResourceCode: "report-a",
        parentCodeType: "default"
      })
    );
    owner.value = { ...parent(""), scopeMode: "ALL", resourceCode: null };
    await flush();
    expect(api.policy.mock.lastCall?.[0].parentResourceCode).toBeUndefined();
    scope.stop();
  });
  it("切 A/B 清空旧页与选择，迟到成功/失败均不能覆盖新上下文", async () => {
    const a = defer<PageResp<ResourceResp>>(),
      b = defer<PageResp<ResourceResp>>();
    api.candidates
      .mockReturnValueOnce(a.promise)
      .mockReturnValueOnce(b.promise);
    const owner = ref(parent("report-a"));
    const scope = effectScope();
    const model = scope.run(() =>
      useChildCandidates(
        () => owner.value,
        () => ({ roleTypeCode: "BASIC_ROLE", roleExternalId: "reader" })
      )
    )!;
    await flush();
    model.selectedType.value = "FIELD";
    owner.value = parent("report-b");
    expect(model.selectedType.value).toBeNull();
    expect(model.resources.tableData.value).toEqual([]);
    await flush();
    model.selectedType.value = "FIELD";
    b.resolve(page(2));
    await flush();
    a.reject(new Error("A 已失效"));
    await flush();
    expect(model.resources.tableData.value[0].id).toBe(2);
    expect(model.resources.error.value).toBeNull();
    expect(api.message).not.toHaveBeenCalled();
    expect(api.candidates.mock.lastCall?.[0].parentResource.code).toBe(
      "report-b"
    );
    scope.stop();
  });
  it("新上下文加载失败保持空态，并保留可见错误而不复用旧资源", async () => {
    const owner = ref(parent("report-a"));
    const scope = effectScope();
    const model = scope.run(() =>
      useChildCandidates(
        () => owner.value,
        () => ({ roleTypeCode: "BASIC_ROLE", roleExternalId: "reader" })
      )
    )!;
    await flush();
    model.selectedType.value = "FIELD";
    await flush();
    expect(model.resources.tableData.value).toHaveLength(1);
    api.candidates.mockRejectedValueOnce(new Error("未引用该类型"));
    owner.value = parent("report-b");
    await flush();
    model.selectedType.value = "SCOPE";
    await flush();
    expect(model.resources.tableData.value).toEqual([]);
    expect(model.resources.error.value).toBe("未引用该类型");
    scope.stop();
  });
});
