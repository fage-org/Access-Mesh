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

import {
  useGrantStore,
  classifySaveError,
  filterBaselineByType
} from "./grant-store";

const CTX: GrantContext = {
  domainCode: null,
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "BASIC_201",
  displayName: "基础用户"
};

/** 矩阵类型（T-FE-038：selectSubject/switchMatrixType 按类型加载 baseline） */
const MATRIX_TYPE = "DATA";

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
    await store.selectSubject(CTX, MATRIX_TYPE);
    expect(mockRequest).toHaveBeenCalledWith(
      "post",
      "/api/perm/role-resource-permission/list",
      {
        data: {
          domainCode: null,
          roleTypeCode: "BASIC_ROLE",
          roleExternalId: "BASIC_201",
          resourceTypeCode: "DATA",
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    await store.selectSubject(CTX, MATRIX_TYPE);
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
    const ok = await store.selectSubject(
      {
        ...CTX,
        roleExternalId: "BASIC_OTHER"
      },
      MATRIX_TYPE
    );
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
    const selectA = store.selectSubject(
      { ...CTX, roleExternalId: "BASIC_A" },
      MATRIX_TYPE
    );
    const okB = await store.selectSubject(
      {
        ...CTX,
        roleExternalId: "BASIC_B"
      },
      MATRIX_TYPE
    );
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
    await store.selectSubject(
      { ...CTX, roleExternalId: externalId },
      MATRIX_TYPE
    );
    const selectP = store.selectSubject(
      { ...CTX, roleExternalId: externalId },
      MATRIX_TYPE
    );
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
    await store.selectSubject(CTX, MATRIX_TYPE);
    const change = makeAddChange();
    store.applyChanges([change]);
    const selectP2 = store.selectSubject(CTX, MATRIX_TYPE);
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

  it.each([
    [20033, "同一资源与操作已存在直接授权，请编辑已有授权"],
    [20034, "自动补全记录只读，不可修改或删除"],
    [20036, "目标记录不存在或已被修改，请刷新页面确认当前状态"],
    [
      20041,
      "条件权限不可转授：带条件的权限不能设置可再授予，请先清除条件后重试"
    ],
    [20042, "该权限条件已停用，请重新选择启用中的条件后重试"],
    [
      20043,
      "子权限不承载条件与再授予属性（系统不变量），仅可删除，请修正后重试"
    ]
  ] as const)(
    "classifySaveError：业务码 %i 映射为稳定页面文案",
    (code, expected) => {
      expect(
        classifySaveError(
          new RequestError("后端原始文案", {
            appCode: code,
            kind: "business"
          })
        )
      ).toEqual({ message: expected, unknownOutcome: false });
    }
  );

  it("classifySaveError：明确拒绝状态码 / 未知来源归结果未知", () => {
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

  // ========== T-FE-038：switchMatrixType（类型切换，§3.6） ==========

  it("switchMatrixType 成功：请求携带 resourceTypeCode；旧草稿已清空，新 baseline 原子提交", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(2)] }));
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    expect(store.isDirty).toBe(true);
    const ok = await store.switchMatrixType("MENU");
    expect(ok).toBe(true);
    // 请求体带 resourceTypeCode（T-PERM-040 契约 §6.4）
    const call = mockRequest.mock.calls[1];
    expect(call[1]).toBe("/api/perm/role-resource-permission/list");
    expect(call[2].data).toMatchObject({
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "BASIC_201",
      resourceTypeCode: "MENU",
      includeChildren: true
    });
    expect(store.baseline[0].id).toBe(2);
    expect(store.changes).toHaveLength(0);
    expect(store.submit.kind).toBe("idle");
    expect(store.context?.roleExternalId).toBe("BASIC_201");
    expect(store.baselineLoading).toBe(false);
  });

  it("switchMatrixType 立即清空：加载期间 baseline/changes/submit 已清空（§3.6 步骤 2）", async () => {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pending);
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    const p = store.switchMatrixType("MENU");
    // 请求发出后、响应前：旧数据已清空（已确认放弃，不回滚）
    expect(store.baseline).toHaveLength(0);
    expect(store.changes).toHaveLength(0);
    expect(store.submit.kind).toBe("idle");
    expect(store.baselineLoading).toBe(true);
    release!(okEnvelope({ items: [makeRecord(2)] }));
    await p;
    expect(store.baseline[0].id).toBe(2);
    expect(store.baselineLoading).toBe(false);
  });

  it("switchMatrixType 过期响应丢弃：快速连续切换后旧类型迟到响应不覆盖（S8-8）", async () => {
    let releaseA: (v: unknown) => void;
    const pendingA = new Promise(resolve => (releaseA = resolve));
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockReturnValueOnce(pendingA)
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(3)] }));
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    const switchA = store.switchMatrixType("DATA");
    const okB = await store.switchMatrixType("MENU");
    expect(okB).toBe(true);
    expect(store.baseline[0].id).toBe(3);
    // A 响应晚到：token 过期，不覆盖 B 的 baseline
    releaseA!(okEnvelope({ items: [makeRecord(2)] }));
    await switchA;
    expect(store.baseline[0].id).toBe(3);
    expect(store.baselineLoading).toBe(false);
  });

  it("switchMatrixType 失败：清空保留 + 抛错 + loading 结束（不回滚旧类型视图）", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] }))
      .mockRejectedValueOnce(new Error("网络错误"));
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    await expect(store.switchMatrixType("MENU")).rejects.toThrow("网络错误");
    expect(store.baseline).toHaveLength(0);
    expect(store.baselineLoading).toBe(false);
    expect(store.context?.roleExternalId).toBe("BASIC_201");
  });

  it("saving 期间 switchMatrixType 被拒：返回 false 且不发请求", async () => {
    const store = useGrantStore();
    const { saving, release } = await enterSaving(store);
    const ok = await store.switchMatrixType("MENU");
    expect(ok).toBe(false);
    // 仅 selectSubject + saveAll 两次请求，switchMatrixType 未发
    expect(mockRequest).toHaveBeenCalledTimes(2);
    release(okEnvelope({ items: [makeRecord(1), makeRecord(2)] }));
    await saving;
  });

  // ========== T-FE-038 review P1-1：saveAll 按当前类型过滤响应 ==========

  it("filterBaselineByType：保留当前类型主权限 + 其全部子权限（跨类型子权限按 depend_on 挂父保留），丢弃其他类型主权限及其子权限", () => {
    const items: RolePermissionItem[] = [
      makeRecord(1), // DATA 主权限（保留）
      { ...makeRecord(2), resourceTypeCode: "MENU" }, // MENU 主权限（丢弃）
      { ...makeRecord(3), dependOn: 1 }, // DATA 子权限（保留）
      {
        ...makeRecord(4),
        resourceTypeCode: "REPORT",
        dependOn: 1
      }, // 跨类型子权限（保留）
      { ...makeRecord(5), resourceTypeCode: "MENU", dependOn: 2 } // MENU 子权限（丢弃）
    ];
    const filtered = filterBaselineByType(items, "DATA");
    expect(filtered.map(r => r.id)).toEqual([1, 3, 4]);
  });

  it("saveAll 携带当前类型：成功后 baseline = 响应按类型过滤，避免其他类型主权限进入单类型 baseline 被误删", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })) // selectSubject
      .mockResolvedValueOnce(
        // saveAll 响应 = 角色完整权限集合（契约 §6.5.1，含其他类型主权限）
        okEnvelope({
          items: [
            makeRecord(1),
            { ...makeRecord(2), resourceTypeCode: "MENU" },
            { ...makeRecord(3), dependOn: 1 }
          ]
        })
      );
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    const ok = await store.saveAll("DATA");
    expect(ok).toBe(true);
    // 其他类型主权限被过滤，当前类型子权限保留
    expect(store.baseline.map(r => r.id)).toEqual([1, 3]);
    expect(store.changes).toHaveLength(0);
    expect(store.submit.kind).toBe("idle");
  });

  it("saveAll 缺省不传类型：响应整体替换（兼容既有调用方）", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })) // selectSubject
      .mockResolvedValueOnce(
        okEnvelope({
          items: [makeRecord(1), { ...makeRecord(2), resourceTypeCode: "MENU" }]
        })
      );
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    const ok = await store.saveAll();
    expect(ok).toBe(true);
    expect(store.baseline.map(r => r.id)).toEqual([1, 2]);
  });

  it("cancelPending：在途 selectSubject 的迟到响应被作废（不提交 context，review P1-1）", async () => {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest.mockReturnValueOnce(pending);
    const store = useGrantStore();
    const selectP = store.selectSubject(
      { ...CTX, roleExternalId: "B" },
      MATRIX_TYPE
    );
    // 新交互（点击当前主体/切分组）作废在途
    store.cancelPending();
    release!(okEnvelope({ items: [makeRecord(9)] }));
    expect(await selectP).toBe(false);
    expect(store.context).toBeNull();
    expect(store.baseline).toHaveLength(0);
    expect(store.baselineLoading).toBe(false);
  });

  it("cancelPending：不动已提交状态（context/baseline/changes 保留）", async () => {
    mockRequest.mockResolvedValue(okEnvelope({ items: [makeRecord(1)] }));
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    store.cancelPending();
    expect(store.context?.roleExternalId).toBe(CTX.roleExternalId);
    expect(store.baseline.map(r => r.id)).toEqual([1]);
    expect(store.changes).toHaveLength(1);
    expect(store.baselineLoading).toBe(false);
  });

  it("prepareBaseline + commitSubject：预取成功后原子提交 context/baseline/清空草稿（review P2-4）", async () => {
    mockRequest.mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })); // prepareBaseline
    const store = useGrantStore();
    store.applyChanges([makeAddChange()]);
    const items = await store.prepareBaseline(
      { ...CTX, roleExternalId: "B" },
      MATRIX_TYPE
    );
    expect(items?.map(r => r.id)).toEqual([1]);
    // 预取无副作用：context/baseline 未动
    expect(store.context).toBeNull();
    expect(store.changes).toHaveLength(1);
    expect(store.baselineLoading).toBe(false);
    const ok = store.commitSubject({ ...CTX, roleExternalId: "B" }, items!);
    expect(ok).toBe(true);
    expect(store.context?.roleExternalId).toBe("B");
    expect(store.baseline.map(r => r.id)).toEqual([1]);
    expect(store.changes).toHaveLength(0);
    expect(store.submit.kind).toBe("idle");
  });

  it("prepareBaseline 期间 cancelPending：返回 null，数据不得用于提交", async () => {
    let release: (v: unknown) => void;
    const pending = new Promise(resolve => (release = resolve));
    mockRequest.mockReturnValueOnce(pending);
    const store = useGrantStore();
    const prep = store.prepareBaseline(CTX, MATRIX_TYPE);
    store.cancelPending();
    release!(okEnvelope({ items: [makeRecord(1)] }));
    expect(await prep).toBeNull();
    expect(store.context).toBeNull();
    expect(store.baseline).toHaveLength(0);
  });

  it("commitSubject：saving 期间拒绝（返回 false，状态不变）", async () => {
    mockRequest
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })) // prepareBaseline
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })) // saveAll
      .mockResolvedValueOnce(okEnvelope({ items: [makeRecord(1)] })); // prepareBaseline（saving 中）
    const store = useGrantStore();
    await store.selectSubject(CTX, MATRIX_TYPE);
    store.applyChanges([makeAddChange()]);
    const saving = store.saveAll();
    expect(store.isSaving).toBe(true);
    const ok = store.commitSubject({ ...CTX, roleExternalId: "B" }, []);
    expect(ok).toBe(false);
    expect(store.context?.roleExternalId).toBe(CTX.roleExternalId);
    await saving;
  });
});
