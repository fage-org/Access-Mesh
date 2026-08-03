/**
 * 提交状态机测试（DoD-3，方案五第十四轮收窄四态）。
 * idle → dirty → saving → 成功 idle / 失败 saveFailed；
 * baseline 仅明确成功后迁移；失败全部条目保留可重试；超时 → unknownOutcome 提示刷新确认。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { RequestError } from "@/api/_envelope";
import type { RolePermissionItem } from "@/api/permission-grant";
import type { GrantContext } from "./types";
import { buildAddChange, buildSummary } from "./grant-plan";

// mock @/utils/http（真实 API 函数 + 真实错误码映射参与测试；阻断 http → store/router 链）
const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request: mockRequest } }));

import { useGrantStore, classifySaveError } from "./grant-store";

const CTX: GrantContext = {
  domainCode: null,
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "BASIC_201",
  displayName: "基础用户"
};

function makeRecord(id: number): RolePermissionItem {
  return {
    id,
    resourceTypeCode: "DATA",
    resourceCode: `data:r${id}`,
    codeType: "default",
    resourceName: `数据${id}`,
    operationCode: "VIEW",
    canGrant: false,
    conditionCode: null,
    scopeMode: "INSTANCE",
    dependOn: null,
    grantSource: "MANUAL",
    grantedBits: "2",
    createdAt: "2026-08-01T10:00:00",
    childCount: 0
  };
}

function makeAddChange() {
  const recordKey = {
    resourceTypeCode: "DATA",
    resourceCode: "data:new",
    codeType: "default",
    operationCode: "VIEW",
    scopeMode: "INSTANCE" as const,
    conditionCode: null,
    canGrant: false
  };
  return buildAddChange({
    recordKey,
    summary: buildSummary({ recordKey, resourceLabel: "新数据" })
  });
}

describe("grant-store 四态状态机（DoD-3）", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mockRequest.mockReset();
  });

  /** 统一成功信封 */
  const okEnvelope = (data: unknown) => ({
    code: 200,
    message: "success",
    data
  });

  it("selectSubject 加载基线：idle；applyChanges 有变更 → dirty，空变更 → idle", async () => {
    mockRequest.mockResolvedValue(okEnvelope({ items: [makeRecord(1)] }));
    const store = useGrantStore();
    await store.selectSubject(CTX);
    expect(mockRequest).toHaveBeenCalledWith(
      "post",
      "/api/perm/role-resource-permission/list",
      {
        data: {
          domainCode: null,
          roleTypeCode: "BASIC_ROLE",
          roleExternalId: "BASIC_201",
          includeChildren: true
        }
      }
    );
    expect(store.submit.kind).toBe("idle");
    expect(store.baseline).toHaveLength(1);
    expect(store.isDirty).toBe(false);

    store.applyChanges([makeAddChange()]);
    expect(store.submit.kind).toBe("dirty");
    expect(store.isDirty).toBe(true);

    store.applyChanges([]);
    expect(store.submit.kind).toBe("idle");
  });

  it("saveAll 成功：baseline 整体替换为响应结果，变更清空，回到 idle", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockResolvedValueOnce(
        okEnvelope({ items: [makeRecord(1), makeRecord(2)] })
      );
    const store = useGrantStore();
    await store.selectSubject(CTX);
    store.applyChanges([makeAddChange()]);
    const ok = await store.saveAll();
    expect(ok).toBe(true);
    expect(mockRequest).toHaveBeenCalledTimes(2);
    const applyCall = mockRequest.mock.calls[1];
    expect(applyCall[1]).toBe(
      "/api/perm/role-resource-permission/apply-grant-plan"
    );
    expect(applyCall[2].data.roleTypeCode).toBe("BASIC_ROLE");
    expect(applyCall[2].data.plan.creates).toHaveLength(1);
    expect(store.submit.kind).toBe("idle");
    expect(store.baseline.map(r => r.id)).toEqual([1, 2]);
    expect(store.changes).toHaveLength(0);
  });

  it("saveAll 业务失败（20034 AUTO_DEP_READONLY）：saveFailed + 错误码映射文案，条目保留可重试", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockResolvedValueOnce({
        code: 20034,
        message: "自动补全记录只读",
        data: null
      });
    const store = useGrantStore();
    await store.selectSubject(CTX);
    const change = makeAddChange();
    store.applyChanges([change]);
    const ok = await store.saveAll();
    expect(ok).toBe(false);
    expect(store.submit.kind).toBe("saveFailed");
    if (store.submit.kind === "saveFailed") {
      expect(store.submit.message).toBe("自动补全记录只读，不可修改或删除");
      expect(store.submit.unknownOutcome).toBe(false);
    }
    // 全部条目保留（整体重试，无部分成功）
    expect(store.changes).toHaveLength(1);
    expect(store.baseline).toHaveLength(1);
    // 重试成功 → baseline 迁移
    mockRequest.mockResolvedValueOnce(
      okEnvelope({ items: [makeRecord(1), makeRecord(2)] })
    );
    const retry = await store.saveAll();
    expect(retry).toBe(true);
    expect(store.submit.kind).toBe("idle");
    expect(store.baseline).toHaveLength(2);
  });

  it("saveAll 超时/网络未知：saveFailed(unknownOutcome=true) 提示刷新确认，不做自动恢复", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockRejectedValueOnce({ code: "ECONNABORTED" });
    const store = useGrantStore();
    await store.selectSubject(CTX);
    store.applyChanges([makeAddChange()]);
    const ok = await store.saveAll();
    expect(ok).toBe(false);
    expect(store.submit.kind).toBe("saveFailed");
    if (store.submit.kind === "saveFailed") {
      expect(store.submit.message).toBe("网络异常，请刷新页面确认当前状态");
      expect(store.submit.unknownOutcome).toBe(true);
    }
    expect(store.changes).toHaveLength(1);
  });

  it("saving 期间重复 saveAll 被拒（防重复点击）", async () => {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pending);
    const store = useGrantStore();
    await store.selectSubject(CTX);
    store.applyChanges([makeAddChange()]);
    const first = store.saveAll();
    expect(store.submit.kind).toBe("saving");
    expect(store.isSaving).toBe(true);
    const second = await store.saveAll();
    expect(second).toBe(false);
    expect(mockRequest).toHaveBeenCalledTimes(2);
    release!(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    expect(await first).toBe(true);
    expect(store.submit.kind).toBe("idle");
  });

  it("revertChange：草稿父被撤销时其虚拟挂载子权限一并撤销", async () => {
    mockRequest.mockResolvedValue(okEnvelope({ items: [] }));
    const store = useGrantStore();
    await store.selectSubject(CTX);
    const parent = makeAddChange();
    const child = { ...makeAddChange(), parentChangeId: parent.changeId };
    store.applyChanges([parent, child]);
    expect(store.changes).toHaveLength(2);
    store.revertChange(parent.changeId);
    expect(store.changes).toHaveLength(0);
    expect(store.submit.kind).toBe("idle");
  });

  it("revertAll 放弃全部回滚为 baseline", async () => {
    mockRequest.mockResolvedValue(okEnvelope({ items: [makeRecord(1)] }));
    const store = useGrantStore();
    await store.selectSubject(CTX);
    store.applyChanges([makeAddChange()]);
    expect(store.isDirty).toBe(true);
    store.revertAll();
    expect(store.isDirty).toBe(false);
    expect(store.baseline).toHaveLength(1);
  });

  // ========== 评审问题 3+4：saving 冻结 + 代际令牌 ==========

  /** 构造 saving 中间态：selectSubject 后 applyChanges 再 saveAll（pending） */
  async function enterSaving(store: ReturnType<typeof useGrantStore>) {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pending);
    await store.selectSubject(CTX);
    store.applyChanges([makeAddChange()]);
    const saving = store.saveAll();
    expect(store.submit.kind).toBe("saving");
    return { saving, release: release! };
  }

  it("saving 期间 applyChanges 被拒：返回 false，changes/submit 保持原样", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    const before = store.changes.slice();
    const ok = store.applyChanges([makeAddChange(), makeAddChange()]);
    expect(ok).toBe(false);
    expect(store.changes).toEqual(before);
    expect(store.submit.kind).toBe("saving");
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
  });

  it("saving 期间 revertChange 被拒：返回 false，changes 保持原样", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    const change = store.changes[0];
    const ok = store.revertChange(change.changeId);
    expect(ok).toBe(false);
    expect(store.changes).toHaveLength(1);
    expect(store.submit.kind).toBe("saving");
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
  });

  it("saving 期间 revertAll 被拒：返回 false，changes 保持原样", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    const ok = store.revertAll();
    expect(ok).toBe(false);
    expect(store.changes).toHaveLength(1);
    expect(store.submit.kind).toBe("saving");
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
  });

  it("saving 期间 selectSubject 被拒：返回 false，context 不变且不发请求", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    const beforeContext = store.context;
    const ok = await store.selectSubject({
      ...CTX,
      roleExternalId: "BASIC_OTHER"
    });
    expect(ok).toBe(false);
    expect(store.context).toBe(beforeContext);
    // 仅 selectSubject + saveAll 两次请求，被拒的 selectSubject 不发请求
    expect(mockRequest).toHaveBeenCalledTimes(2);
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
  });

  it("selectSubject 过期请求不覆盖：后发请求的 context/baseline 保留", async () => {
    let releaseA: (v: unknown) => void;
    const pendingA = new Promise(resolve => (releaseA = resolve));
    mockRequest
      .mockReturnValueOnce(pendingA)
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(2)] }));
    const store = useGrantStore();
    const selectA = store.selectSubject({ ...CTX, roleExternalId: "BASIC_A" });
    const okB = await store.selectSubject({
      ...CTX,
      roleExternalId: "BASIC_B"
    });
    expect(okB).toBe(true);
    expect(store.context?.roleExternalId).toBe("BASIC_B");
    expect(store.baseline[0].id).toBe(2);
    // A 响应晚到：token 过期，不覆盖 B 的 context/baseline
    releaseA!(okEnvelope({ items: [makeRecord(1)] }));
    await selectA;
    expect(store.context?.roleExternalId).toBe("BASIC_B");
    expect(store.baseline[0].id).toBe(2);
    expect(store.baselineLoading).toBe(false);
  });

  it("resetAll 递增 saveToken：在途 saveAll 响应不覆盖已重置状态", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    store.resetAll();
    expect(store.submit.kind).toBe("idle");
    expect(store.context).toBeNull();
    expect(store.baseline).toHaveLength(0);
    // 在途 saveAll 响应晚到：token 过期，不覆盖 resetAll 后的状态
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
    expect(store.submit.kind).toBe("idle");
    expect(store.context).toBeNull();
    expect(store.baseline).toHaveLength(0);
  });

  // ========== 评审问题 3：baselineLoading 冻结（切换期间防双提交） ==========

  /** 构造 baselineLoading 中间态：selectSubject pending（未完成） */
  async function enterBaselineLoading(
    store: ReturnType<typeof useGrantStore>,
    externalId = "BASIC_201"
  ) {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pending);
    await store.selectSubject({ ...CTX, roleExternalId: externalId });
    const selectP = store.selectSubject({ ...CTX, roleExternalId: externalId });
    expect(store.baselineLoading).toBe(true);
    return { selectP, release: release! };
  }

  it("baselineLoading 期间 applyChanges 被拒：返回 false，changes 不变", async () => {
    const store = useGrantStore();
    const { selectP, release } = await enterBaselineLoading(store);
    const ok = store.applyChanges([makeAddChange()]);
    expect(ok).toBe(false);
    expect(store.changes).toHaveLength(0);
    release(okEnvelope({ items: [] }));
    await selectP;
  });

  it("baselineLoading 期间 revertChange/revertAll 被拒：changes 保持原样", async () => {
    const store = useGrantStore();
    const { selectP, release } = await enterBaselineLoading(store);
    // 先在 baselineLoading 前无法设 changes，此处用 enterSaving 设 changes 后再进 baselineLoading
    release(okEnvelope({ items: [] }));
    await selectP;
    // 重新进入：先设 changes 再触发 baselineLoading
    let release2: (v: unknown) => void;
    const pending2 = new Promise(resolve => (release2 = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pending2);
    await store.selectSubject(CTX);
    const change = makeAddChange();
    store.applyChanges([change]);
    const selectP2 = store.selectSubject(CTX);
    expect(store.baselineLoading).toBe(true);
    const ok1 = store.revertChange(change.changeId);
    expect(ok1).toBe(false);
    expect(store.changes).toHaveLength(1);
    const ok2 = store.revertAll();
    expect(ok2).toBe(false);
    expect(store.changes).toHaveLength(1);
    release2!(okEnvelope({ items: [] }));
    await selectP2;
  });

  it("baselineLoading 期间 saveAll 被拒：不发请求", async () => {
    const store = useGrantStore();
    const { selectP, release } = await enterBaselineLoading(store);
    const ok = await store.saveAll();
    expect(ok).toBe(false);
    // 仅 2 次 selectSubject 请求，saveAll 未发
    expect(mockRequest).toHaveBeenCalledTimes(2);
    release(okEnvelope({ items: [] }));
    await selectP;
  });

  it("classifySaveError：业务码映射 / 明确拒绝状态码 / 未知来源归结果未知", () => {
    expect(
      classifySaveError(
        new RequestError("x", { appCode: 20033, kind: "business" })
      ).message
    ).toBe("同一权限键下该条件分支已存在");
    expect(
      classifySaveError({ response: { status: 403, data: {} } }).unknownOutcome
    ).toBe(false);
    expect(classifySaveError({ code: "ERR_NETWORK" }).unknownOutcome).toBe(
      true
    );
    expect(
      classifySaveError(
        new RequestError("其他", { appCode: 29999, kind: "business" })
      ).message
    ).toBe("其他");
  });
});
