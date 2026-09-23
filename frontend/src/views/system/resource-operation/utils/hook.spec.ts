/**
 * 资源与操作定义页共享列表上下文回归（T-FE-059 / F011 同模式）：
 * 切资源类型后旧类型的树/操作行不得在新类型下残留可写——切换取数发起即清空、失败保持空集；
 * 权限失效短路清空同样作废在途请求；编辑/删除提交前核对行所属类型与当前选中一致。
 * 旧实现（无上下文绑定、无提交守卫）：切类型失败保留 A 树、编辑/删除照发 A 的键——
 * 本组用例在旧实现下必失败。
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGetResourceTree = vi.fn();
const mockGetOperationList = vi.fn();
const mockUpdateResource = vi.fn();
const mockRemoveResources = vi.fn();
const mockUpdateOperation = vi.fn();
const mockMoveResource = vi.fn();

let hasPermsValue = true;

// 阻断 hook 模块级链（同 biz-domain hook.spec 范式：element-plus/message/auth/api 全 mock）
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) }
}));
vi.mock("@/utils/message", () => ({ message: vi.fn() }));
vi.mock("@/utils/auth", () => ({
  hasPerms: () => hasPermsValue
}));
vi.mock("@/api/resource-operation", () => ({
  getResourceTree: (...args: unknown[]) => mockGetResourceTree(...args),
  getOperationList: (...args: unknown[]) => mockGetOperationList(...args),
  createResource: vi.fn(),
  updateResource: (...args: unknown[]) => mockUpdateResource(...args),
  moveResource: (...args: unknown[]) => mockMoveResource(...args),
  removeResources: (...args: unknown[]) => mockRemoveResources(...args),
  createOperation: vi.fn(),
  updateOperation: (...args: unknown[]) => mockUpdateOperation(...args),
  removeOperations: vi.fn()
}));
vi.mock("@/api/type-def", () => ({
  getTypeDefList: vi.fn().mockResolvedValue({ items: [] }),
  TYPE_KEY: { RESOURCE_TYPE: "resource_type" }
}));

import { useResourceOperation } from "./hook";

/** 排空微任务 + 一个宏任务周期（fetcher 包装多一跳微任务，T-FE-051 同款） */
function flush() {
  return new Promise<void>(resolve => setTimeout(resolve));
}

function nodeOf(id: number, typeCode: string) {
  return {
    id,
    resourceTypeCode: typeCode,
    code: `c${id}`,
    name: `n${id}`,
    children: []
  } as any;
}

function operationOf(typeCode: string) {
  return {
    id: 1,
    resourceTypeCode: typeCode,
    code: "READ",
    name: "读",
    binaryBit: "1",
    inheritMask: "0"
  } as any;
}

describe("资源与操作定义页共享列表上下文（T-FE-059 / F011 同模式）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // mockReset 清 implementation：防「权限失效」用例的 pending-promise 实现泄漏给后续用例
    // （clearAllMocks 只清调用记录；泄漏的在途 promise 会挂起下一用例的 loadTree 直至超时）
    mockGetResourceTree.mockReset().mockResolvedValue({ items: [] });
    mockGetOperationList.mockReset().mockResolvedValue({ items: [] });
    mockUpdateResource.mockReset().mockResolvedValue(undefined);
    mockRemoveResources.mockReset().mockResolvedValue(undefined);
    mockUpdateOperation.mockReset().mockResolvedValue(undefined);
    mockMoveResource.mockReset().mockResolvedValue(undefined);
    hasPermsValue = true;
  });

  it("切资源类型 B 树/操作表加载失败：双双清空（旧实现保留 A 树必失败）", async () => {
    mockGetResourceTree
      .mockResolvedValueOnce({ items: [{ root: nodeOf(1, "type-a") }] })
      .mockRejectedValueOnce(new Error("type-b 树加载失败"));
    mockGetOperationList
      .mockResolvedValueOnce({ items: [operationOf("type-a")] })
      .mockRejectedValueOnce(new Error("type-b 操作加载失败"));
    const hook = useResourceOperation();

    hook.selectedResourceTypeCode.value = "type-a";
    await hook.loadTree();
    await hook.loadOperations();
    expect(hook.treeData.value).toHaveLength(1);
    expect(hook.operations.value).toHaveLength(1);

    await hook.onResourceTypeChange("type-b");
    expect(hook.treeData.value).toEqual([]);
    expect(hook.operations.value).toEqual([]);
  });

  it("上下文置空短路清空（权限失效共用分支）：在途旧请求晚归不回写（旧实现回写 A 树必失败）", async () => {
    const pending: Array<(v: any) => void> = [];
    mockGetResourceTree.mockImplementation(
      () => new Promise(resolve => pending.push(resolve))
    );
    const hook = useResourceOperation();

    hook.selectedResourceTypeCode.value = "type-a";
    void hook.loadTree();
    // 类型置空（如目录刷新后选中类型消失）走与权限失效相同的短路清空分支
    // （不 flip hasPerms：mock 读普通变量非响应式，canViewResource computed 不失效）
    hook.selectedResourceTypeCode.value = null;
    await hook.loadTree(); // 短路：清空并作废在途

    pending[0]({ items: [{ root: nodeOf(1, "type-a") }] });
    await flush();
    expect(hook.treeData.value).toEqual([]);
  });

  it("编辑资源提交：行所属类型 ≠ 当前选中 → 拒绝且不发货（旧实现照发必失败）", async () => {
    mockUpdateResource.mockResolvedValue(undefined);
    const hook = useResourceOperation();
    hook.selectedResourceTypeCode.value = "type-b";

    const ok = await hook.submitResource(
      { name: "n1", code: "c1", codeType: "default", status: 1 } as any,
      "edit",
      nodeOf(1, "type-a") as any
    );

    expect(ok).toBe(false);
    expect(mockUpdateResource).not.toHaveBeenCalled();
  });

  it("删除资源提交：节点所属类型 ≠ 当前选中 → 拒绝且不发货（旧实现照删必失败）", async () => {
    mockRemoveResources.mockResolvedValue(undefined);
    const hook = useResourceOperation();
    hook.selectedResourceTypeCode.value = "type-b";

    await hook.deleteResource(nodeOf(1, "type-a"));

    expect(mockRemoveResources).not.toHaveBeenCalled();
  });

  it("编辑操作权限提交：行所属类型 ≠ 当前选中 → 拒绝且不发货（旧实现照发必失败）", async () => {
    mockUpdateOperation.mockResolvedValue(undefined);
    const hook = useResourceOperation();
    hook.selectedResourceTypeCode.value = "type-b";

    const ok = await hook.submitOperation(
      {
        resourceTypeCode: "type-a",
        code: "READ",
        name: "读",
        binaryBit: 1,
        inheritMask: 0
      } as any,
      "edit",
      operationOf("type-a")
    );

    expect(ok).toBe(false);
    expect(mockUpdateOperation).not.toHaveBeenCalled();
  });

  it("移动资源提交：资源所属类型 ≠ 当前选中 → 拒绝且不发货（双轨评审处置：同页守卫补齐）", async () => {
    mockMoveResource.mockResolvedValue(undefined);
    const hook = useResourceOperation();
    hook.selectedResourceTypeCode.value = "type-b";

    const ok = await hook.submitMove({
      resource: {
        resourceTypeCode: "type-a",
        code: "c1",
        codeType: "default"
      },
      parent: null
    } as any);

    expect(ok).toBe(false);
    expect(mockMoveResource).not.toHaveBeenCalled();
  });
});
