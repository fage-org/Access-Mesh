/**
 * 授权页类型候选降级判定回归（T-FE-018 评审收口，2026-09-03 终案）：
 * 后端 type-definition/list 门禁已放宽为「类型级或任一实例级 VIEW」（与登录权限串
 * 投影口径对齐）；前端对类型候选请求的 403 捕获保留为防御层（覆盖未来门禁变化）。
 * 权限拒绝进入降级块（可重试），不落「暂无资源类型配置」空态；资源树/操作列 403
 * 维持通用错误（无前端前置为既定口径）。
 * capability 派生三态翻转锁（T-FE-055 复评 P3-2 处置，2026-09-20）：响应式桩
 * 复现 SET_PERMS → computed 失效链，静态双值锁对回退形态无判别力已重写。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { defer, flush } from "@/test-support/async";

const getTypeDefList = vi.fn();
const getResourceTree = vi.fn();
const getOperationList = vi.fn();
const getConditionList = vi.fn();
const hasPermsMock = vi.fn();
const messageMock = vi.fn();
const routerReplaceMock = vi.fn();
const refreshCapabilityMock = vi.fn();
const confirmDiscardMock = vi.fn();
const sessionEndedMock = vi.fn(() => false);
const applyGrantPlanMock = vi.fn();
const leaveCallbacks: Array<() => Promise<boolean>> = [];
const unmountCallbacks: Array<() => void> = [];
let draftOwner: { tenantId: string; userId: string } | null = null;
vi.mock("./draft-storage", async () => ({
  ...(await vi.importActual<typeof import("./draft-storage")>(
    "./draft-storage"
  )),
  readDraftOwner: () => draftOwner
}));
vi.mock("vue", async () => ({
  ...(await vi.importActual<typeof import("vue")>("vue")),
  onBeforeUnmount: (callback: () => void) => unmountCallbacks.push(callback)
}));

/** 可控 route（refreshAndPreset 预选分支按 query.roleExternalId 分发） */
const routeMock: { query: Record<string, unknown> } = { query: {} };

// 阻断 hook 模块级的 store/router/element-plus 链（同 biz-domain hook.spec 范式）
vi.mock("vue-router", () => ({
  useRoute: () => routeMock,
  useRouter: () => ({ replace: routerReplaceMock }),
  onBeforeRouteLeave: (callback: () => Promise<boolean>) =>
    leaveCallbacks.push(callback)
}));
// 会话能力刷新入口（T-FE-048 retryLoadDeps 先行依赖）：断链真实 router/utils 图，
// 由用例控制权限串刷新时机（锁③：刷新翻真后才发依赖请求）
vi.mock("@/router/utils", () => ({
  refreshSessionCapability: (...args: unknown[]) =>
    refreshCapabilityMock(...args)
}));
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => confirmDiscardMock(...args) }
}));
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));
vi.mock("@/utils/auth", () => ({
  hasPerms: (...args: unknown[]) => hasPermsMock(...args),
  isSessionTerminated: () => sessionEndedMock(),
  userKey: "user-info"
}));
vi.mock("@/utils/message", () => ({
  message: (...args: unknown[]) => messageMock(...args)
}));
vi.mock("@/api/type-def", () => ({
  TYPE_KEY: { RESOURCE_TYPE: "resource_type" },
  getTypeDefList: (...args: unknown[]) => getTypeDefList(...args)
}));
vi.mock("@/api/resource-operation", () => ({
  getResourceTree: (...args: unknown[]) => getResourceTree(...args),
  getOperationList: (...args: unknown[]) => getOperationList(...args)
}));
vi.mock("@/api/permission-condition", () => ({
  getConditionList: (...args: unknown[]) => getConditionList(...args)
}));
vi.mock("@/api/permission-grant", () => ({
  getRolePermissionList: vi.fn(),
  applyGrantPlan: (...args: unknown[]) => applyGrantPlanMock(...args)
}));

import { ref } from "vue";
import { PERMISSION_GRANT_PERMS } from "./perms";
import { usePermissionGrant } from "./hook";
import { buildAddChange, buildSummary, buildUpdateChange } from "./grant-plan";

/** SecurityException 经 GlobalExceptionHandler 映射：HTTP 403 + R code=403 */
function axios403() {
  return Object.assign(new Error("Request failed with status code 403"), {
    response: { status: 403, data: { code: 403, message: "权限不足" } }
  });
}

describe("授权页类型候选降级判定", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    hasPermsMock.mockReturnValue(true);
    refreshCapabilityMock.mockResolvedValue(undefined);
    getTypeDefList.mockResolvedValue({ items: [] });
    getResourceTree.mockResolvedValue({ items: [] });
    getOperationList.mockResolvedValue({ items: [] });
    getConditionList.mockResolvedValue({ items: [] });
  });

  it("API 登记类型不出现在授权类型候选或资源树中", async () => {
    vi.stubGlobal("localStorage", { getItem: vi.fn(), setItem: vi.fn() });
    getTypeDefList.mockResolvedValue({
      items: [
        { typeKey: "resource_type", typeCode: "API", sortOrder: 0 },
        { typeKey: "resource_type", typeCode: "REPORT", sortOrder: 1 }
      ]
    });
    getResourceTree.mockResolvedValue({
      items: [
        { root: { id: 1, resourceTypeCode: "API", children: [] } },
        { root: { id: 2, resourceTypeCode: "REPORT", children: [] } }
      ]
    });
    const hook = usePermissionGrant();
    try {
      await hook.retryLoadDeps();
      await flush();
      expect(getResourceTree).toHaveBeenCalledWith({ enabledOnly: true });
      expect(hook.typeCandidates.value.map(t => t.typeCode)).toEqual([
        "REPORT"
      ]);
      expect(hook.allResourceForest.value.map(t => t.resourceTypeCode)).toEqual(
        ["REPORT"]
      );
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("T-PERM-048 codex P2-4：refreshConditions 乱序守卫——旧响应晚归被丢弃（旧实现覆盖回过期列表）", async () => {
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    let resolveOld!: (v: unknown) => void;
    getConditionList.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveOld = () =>
            resolve({
              items: [{ id: 1, code: "stale", source: "INLINE" }]
            });
        })
    );
    getConditionList.mockResolvedValueOnce({
      items: [{ id: 2, code: "fresh", source: "INLINE" }]
    });
    const p1 = hook.refreshConditions();
    const p2c = hook.refreshConditions();
    await p2c;
    resolveOld(null);
    await p1;
    expect(hook.conditions.value.map(c => c.code)).toEqual(["fresh"]);
  });

  it("T-PERM-048 P2-1：refreshConditions 独立于 depsLoaded 闩锁（保存后内联条件回流通道，旧实现无此入口）", async () => {
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(getConditionList).toHaveBeenCalledWith(true);
    const callsAfterLoad = getConditionList.mock.calls.length;
    // 阀锁后再刷：绕过 depsLoaded（loadDeps 二次调用不重拉，refreshConditions 独立重拉）
    getConditionList.mockResolvedValue({
      items: [{ id: 9, code: "inline-x", source: "INLINE" }]
    });
    await hook.refreshConditions();
    expect(getConditionList.mock.calls.length).toBe(callsAfterLoad + 1);
    expect(hook.conditions.value.map(c => c.code)).toEqual(["inline-x"]);
  });

  it("权限串缺 TYPE_VIEW：不发任何依赖请求，直接降级（理解 A 原行为锁定）", async () => {
    hasPermsMock.mockReturnValue(false);
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(true);
    expect(getTypeDefList).not.toHaveBeenCalled();
    expect(getResourceTree).not.toHaveBeenCalled();
  });

  it("type-definition/list 被 403 拒（仅实例级授权场景）：进降级块、不发其余依赖请求、不弹错（旧实现弹通用错误且无重试入口）", async () => {
    getTypeDefList.mockRejectedValue(axios403());
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(true);
    expect(hook.typeCandidates.value).toEqual([]);
    expect(messageMock).not.toHaveBeenCalled();
    expect(getResourceTree).not.toHaveBeenCalled();
    expect(getOperationList).not.toHaveBeenCalled();
  });

  it("type-definition/list 非 403 失败：维持通用错误提示，不进降级", async () => {
    getTypeDefList.mockRejectedValue(new Error("boom"));
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(false);
    expect(messageMock).toHaveBeenCalledTimes(1);
  });

  it("资源树被 403 拒：不进降级（树/操作列无前端前置的既定口径），走通用错误", async () => {
    getResourceTree.mockRejectedValue(axios403());
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(false);
    expect(messageMock).toHaveBeenCalledTimes(1);
  });

  it("T-FE-048 锁③：retryLoadDeps 先走会话能力刷新入口重拉权限串——刷新使 hasPerms 翻真后重试才发依赖请求（旧实现读 store 旧权限串，重试永远降级）", async () => {
    // 初态：缺 TYPE_VIEW，首次加载进降级、零依赖请求
    hasPermsMock.mockReturnValue(false);
    const hook = usePermissionGrant();
    hook.retryLoadDeps();
    await flush();
    expect(hook.typePermDenied.value).toBe(true);
    expect(getTypeDefList).not.toHaveBeenCalled();

    // 管理员补授后点击重试：能力刷新入口先更新权限串（此处以刷新回调翻真模拟），
    // 随后 loadDeps 读到新权限串发出依赖请求——顺序锁由「翻真后才发请求」携带
    refreshCapabilityMock.mockImplementation(async () => {
      hasPermsMock.mockReturnValue(true);
    });
    await hook.retryLoadDeps();
    await flush();
    expect(refreshCapabilityMock).toHaveBeenCalledTimes(2);
    expect(getTypeDefList).toHaveBeenCalledTimes(1);
    expect(hook.typePermDenied.value).toBe(false);
  });

  it("T-FE-048 边界：能力刷新失败不阻断重试——旧权限串下照常走既有降级判定（行为不劣化）", async () => {
    hasPermsMock.mockReturnValue(false);
    refreshCapabilityMock.mockRejectedValue(new Error("network down"));
    const hook = usePermissionGrant();
    await hook.retryLoadDeps();
    await flush();
    expect(refreshCapabilityMock).toHaveBeenCalledTimes(1);
    expect(hook.typePermDenied.value).toBe(true);
    expect(messageMock).not.toHaveBeenCalled();
  });

  it("T-FE-048 评审 P3-3 处置：重试在途短路防重入——前置能力刷新段连点只发一次，完成/失败均复位（旧实现无守卫，二次点击并发双发）", async () => {
    hasPermsMock.mockReturnValue(true);
    let resolveRefresh!: () => void;
    refreshCapabilityMock.mockImplementation(
      () =>
        new Promise<void>(resolve => {
          resolveRefresh = resolve;
        })
    );
    const hook = usePermissionGrant();
    const first = hook.retryLoadDeps();
    expect(hook.retryInFlight.value).toBe(true);
    // 在途连点：短路，不并发第二次刷新
    await hook.retryLoadDeps();
    expect(refreshCapabilityMock).toHaveBeenCalledTimes(1);
    resolveRefresh();
    await first;
    await flush();
    expect(hook.retryInFlight.value).toBe(false);
    expect(getTypeDefList).toHaveBeenCalledTimes(1);
    // 复位后可再次重试
    refreshCapabilityMock.mockResolvedValue(undefined);
    await hook.retryLoadDeps();
    await flush();
    expect(refreshCapabilityMock).toHaveBeenCalledTimes(2);
  });
});

describe("预选失败状态清理（T-FE-037 评审修正回归锁）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    hasPermsMock.mockReturnValue(true);
    getTypeDefList.mockResolvedValue({ items: [] });
    getResourceTree.mockResolvedValue({ items: [] });
    getOperationList.mockResolvedValue({ items: [] });
    getConditionList.mockResolvedValue({ items: [] });
    routeMock.query = {};
  });

  it("已有旧 context + preset 目标不存在：清空主体/高亮、作废在途并消费 query（旧实现仅提示后保留旧主体，后续保存会作用于错误主体）", async () => {
    const { useGrantStore } = await import("./grant-store");
    const grantStore = useGrantStore();
    const hook = usePermissionGrant();
    // 预置旧主体（keep-alive 跨入口残留：角色入口选中过 BASIC_ROLE/"1"）+ 高亮
    const committed = grantStore.commitSubject(
      {
        domainCode: null,
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "1",
        displayName: "旧角色"
      },
      []
    );
    expect(committed).toBe(true);
    hook.activeKey.value = "role:1";
    // 组织入口失效预选（树中 findNode 恒 null，如入口指向已停用组织）
    routeMock.query = { subjectType: "ORG", roleExternalId: "99999" };
    hook.subjectTreeRef.value = {
      loadTree: vi.fn(async () => {}),
      findNode: vi.fn(() => null),
      preselect: vi.fn()
    };
    await hook.refreshAndPreset();
    await flush();
    expect(grantStore.context).toBeNull();
    expect(grantStore.changes).toEqual([]);
    expect(hook.activeKey.value).toBeNull();
    // 失效 query 必须消费（否则每次 onActivated 重复弹 warning）
    expect(routerReplaceMock).toHaveBeenCalledWith({
      query: { subjectType: "ORG", roleExternalId: undefined }
    });
    expect(messageMock).toHaveBeenCalledWith(
      "未找到指定组织/岗位或该主体已停用，请重新选择",
      { type: "warning" }
    );
    // 未走 preselect（目标不存在）
    expect(hook.subjectTreeRef.value.preselect).not.toHaveBeenCalled();
  });

  it.each([false, true])("失效预选先保护草稿：确认放弃=%s", async discard => {
    const { useGrantStore } = await import("./grant-store");
    const store = useGrantStore();
    const hook = usePermissionGrant();
    const context = {
      domainCode: null,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "1",
      displayName: "旧角色"
    };
    store.commitSubject(context, []);
    const recordKey = {
      resourceTypeCode: "DATA",
      resourceCode: "data:new",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    const change = buildAddChange({
      recordKey,
      summary: buildSummary({ recordKey, resourceLabel: "新数据" })
    });
    store.applyChanges([change]);
    hook.activeKey.value = "role:1";
    routeMock.query = { subjectType: "ORG", roleExternalId: "99999" };
    hook.subjectTreeRef.value = {
      loadTree: vi.fn(async () => {}),
      findNode: vi.fn(() => null),
      preselect: vi.fn()
    };
    if (discard) confirmDiscardMock.mockResolvedValueOnce("confirm");
    else confirmDiscardMock.mockRejectedValueOnce("cancel");

    await hook.refreshAndPreset();

    expect(confirmDiscardMock).toHaveBeenCalledTimes(1);
    expect(store.context).toEqual(discard ? null : context);
    expect(store.changes).toEqual(discard ? [] : [change]);
    expect(hook.activeKey.value).toBe(discard ? null : "role:1");
    expect(routerReplaceMock).toHaveBeenCalledWith({
      query: {
        subjectType: discard ? "ORG" : "ROLE",
        roleExternalId: undefined
      }
    });
  });
});

describe("capability 派生（T-FE-055 外评处置：codex P2 根因修——canManage 响应式派生取代主体加载时快照；复评 P3-2 重写为响应式桩三态翻转锁——静态双值锁对「setup 一次性取值」回退形态无判别力）", () => {
  // hasPerms 桩读响应式 ref：computed 经 mockImplementation 内的 ref 读取建立依赖，
  // 权限串（ref）变化即触发派生重算——复现生产链 SET_PERMS → computed 失效
  const permRef = ref<string[]>([PERMISSION_GRANT_PERMS.ROLE_MANAGE]);

  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    permRef.value = [PERMISSION_GRANT_PERMS.ROLE_MANAGE];
    hasPermsMock.mockImplementation(p => permRef.value.includes(p as string));
    refreshCapabilityMock.mockResolvedValue(undefined);
    getTypeDefList.mockResolvedValue({ items: [] });
    getResourceTree.mockResolvedValue({ items: [] });
    getOperationList.mockResolvedValue({ items: [] });
    getConditionList.mockResolvedValue({ items: [] });
  });

  it("三态翻转：同一 hook 实例上权限串变化即时重算（持 MANAGE=edit 未选主体不拒 → 撤销=view 拒开 → 复授=edit 复原）——「setup 一次性取值/快照」回退形态下本用例失败", () => {
    const hook = usePermissionGrant();
    // 持 MANAGE 未选主体（形态①：每用户进页初始稳态）
    expect(hook.capability.value).toBe("edit");
    // 撤销 ROLE:MANAGE（形态⑤降权）：派生即时翻 view，openGrantDialog 拒绝并提示
    permRef.value = [];
    expect(hook.capability.value).toBe("view");
    hook.openGrantDialog();
    expect(messageMock).toHaveBeenCalledWith("当前为只读视图，无授权权限", {
      type: "warning"
    });
    expect(hook.dialogVisible.value).toBe(false);
    // 复授（形态④升权）：派生即时翻 edit，打开不再被拒
    permRef.value = [PERMISSION_GRANT_PERMS.ROLE_MANAGE];
    expect(hook.capability.value).toBe("edit");
    hook.openGrantDialog();
    expect(hook.dialogVisible.value).toBe(true);
  });
});

describe("授权草稿会话恢复", () => {
  let entries: Map<string, string>;
  beforeEach(() => {
    vi.clearAllMocks();
    entries = new Map();
    vi.stubGlobal("sessionStorage", {
      getItem: (key: string) => entries.get(key) ?? null,
      setItem: (key: string, value: string) => entries.set(key, value),
      removeItem: (key: string) => entries.delete(key)
    });
    vi.stubGlobal("localStorage", { getItem: vi.fn(), setItem: vi.fn() });
    vi.stubGlobal("window", {
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    });
    setActivePinia(createPinia());
    draftOwner = { tenantId: "1", userId: "7" };
    sessionEndedMock.mockReturnValue(false);
    hasPermsMock.mockReturnValue(true);
    getResourceTree.mockResolvedValue({ items: [] });
    getOperationList.mockResolvedValue({ items: [] });
    confirmDiscardMock.mockResolvedValue("confirm");
    leaveCallbacks.length = 0;
    unmountCallbacks.length = 0;
    routeMock.query = {};
  });
  afterEach(() => {
    draftOwner = null;
    sessionEndedMock.mockReturnValue(false);
    vi.unstubAllGlobals();
  });

  it("401 终结会话后卸载不删除草稿；同账号重新选角色可确认恢复，且不自动提交", async () => {
    const api = await import("@/api/permission-grant");
    vi.mocked(api.getRolePermissionList).mockResolvedValue({ items: [] });
    const context = {
      domainCode: null,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "role-7",
      displayName: "角色 7"
    };
    const hook = usePermissionGrant();
    hook.typeCandidates.value = [{ typeCode: "DATA", sortOrder: 0 } as any];
    await hook.handleSelectSubject(context);
    const recordKey = {
      resourceTypeCode: "DATA",
      resourceCode: "one",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    const change = buildAddChange({
      recordKey,
      summary: buildSummary({ recordKey, resourceLabel: "一条数据" })
    });
    hook.grantStore.applyChanges([change]);
    expect(entries.size).toBe(1);
    sessionEndedMock.mockReturnValue(true);
    expect(await leaveCallbacks.at(-1)!()).toBe(true);
    unmountCallbacks.at(-1)!();
    expect(entries.size).toBe(1);
    expect(hook.grantStore.changes).toEqual([]);

    setActivePinia(createPinia());
    sessionEndedMock.mockReturnValue(false);
    const reopened = usePermissionGrant();
    reopened.typeCandidates.value = [{ typeCode: "DATA", sortOrder: 0 } as any];
    await reopened.handleSelectSubject(context);
    expect(confirmDiscardMock).toHaveBeenCalledWith(
      expect.stringContaining("恢复"),
      expect.any(String),
      expect.any(Object)
    );
    expect(reopened.grantStore.changes).toEqual([change]);
    expect(applyGrantPlanMock).not.toHaveBeenCalled();
    reopened.grantStore.revertAll();
    expect(entries.size).toBe(0);
    unmountCallbacks.at(-1)!();
  });
  it("确认放弃变更即清内存 changes：切换完成前的窗口内不再存活（2026-10-06 逐任务评审 P2，旧实现只删存储项必红）", async () => {
    const api = await import("@/api/permission-grant");
    vi.mocked(api.getRolePermissionList).mockResolvedValue({ items: [] });
    const contextA = {
      domainCode: null,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "role-7",
      displayName: "角色 7"
    };
    const hook = usePermissionGrant();
    hook.typeCandidates.value = [{ typeCode: "DATA", sortOrder: 0 } as any];
    await hook.handleSelectSubject(contextA);
    const recordKey = {
      resourceTypeCode: "DATA",
      resourceCode: "one",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    const change = buildAddChange({
      recordKey,
      summary: buildSummary({ recordKey, resourceLabel: "一条数据" })
    });
    hook.grantStore.applyChanges([change]);
    expect(hook.grantStore.changes).toHaveLength(1);

    // 切换到 B：确认「放弃变更」；用挂起的 getResourceTree 卡在 commitSubject 前的窗口内
    const gate = defer<{ items: never[] }>();
    getResourceTree.mockImplementation(() => gate.promise);
    const contextB = {
      ...contextA,
      roleExternalId: "role-8",
      displayName: "角色 8"
    };
    const switching = hook.handleSelectSubject(contextB);
    await flush();
    expect(hook.grantStore.changes).toHaveLength(0); // 旧实现只删存储项，内存 changes 存活到 commitSubject——此处为 1 必红
    gate.resolve({ items: [] });
    expect(await switching).toBe(true);
    expect(hook.grantStore.changes).toHaveLength(0);
  });

  it("恢复草稿重定基：update 变更 before 对齐当前基线行（2026-10-06 逐任务评审 P2，旧实现保留上次会话快照必红）", async () => {
    const api = await import("@/api/permission-grant");
    const row = {
      id: 301,
      resourceTypeCode: "DATA",
      resourceCode: "data:r301",
      codeType: "default",
      resourceName: "数据301",
      operationCode: "VIEW",
      canGrant: true,
      conditionCode: null,
      scopeMode: "INSTANCE" as const,
      dependOn: null,
      grantSource: "MANUAL" as const,
      grantedBits: "2",
      createdAt: "2026-08-01T10:00:00",
      childCount: 0
    };
    vi.mocked(api.getRolePermissionList).mockResolvedValue({ items: [row] });
    const context = {
      domainCode: null,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "role-9",
      displayName: "角色 9"
    };
    const hook = usePermissionGrant();
    hook.typeCandidates.value = [{ typeCode: "DATA", sortOrder: 0 } as any];
    await hook.handleSelectSubject(context);
    const recordKey = {
      resourceTypeCode: "DATA",
      resourceCode: "data:r301",
      codeType: "default",
      operationCode: "VIEW",
      scopeMode: "INSTANCE" as const,
      conditionCode: null,
      canGrant: false
    };
    const change = buildUpdateChange({
      before: row,
      after: { canGrant: true, conditionCode: null },
      summary: buildSummary({ recordKey, resourceLabel: "数据301" })
    });
    hook.grantStore.applyChanges([change]);
    expect(entries.size).toBe(1);

    // 会话 2：同主体基线已漂移（行 301 canGrant=false），恢复草稿
    vi.mocked(api.getRolePermissionList).mockResolvedValue({
      items: [{ ...row, canGrant: false }]
    });
    setActivePinia(createPinia());
    const reopened = usePermissionGrant();
    reopened.typeCandidates.value = [{ typeCode: "DATA", sortOrder: 0 } as any];
    await reopened.handleSelectSubject(context);
    const restored = reopened.grantStore.changes[0];
    expect(restored?.kind).toBe("update");
    if (restored?.kind !== "update") throw new Error("expected update change");
    expect(restored.before.canGrant).toBe(false); // 旧实现=true（上次会话快照）→ 提交差异失真
    reopened.grantStore.revertAll();
    expect(entries.size).toBe(0);
    unmountCallbacks.at(-1)!();
  });

  it.each(["account", "tenant", "role", "type"])(
    "%s 不同不会恢复或清除其他上下文草稿",
    async different => {
      const api = await import("@/api/permission-grant");
      vi.mocked(api.getRolePermissionList).mockResolvedValue({ items: [] });
      const { draftKey, writeDraft } = await import("./draft-storage");
      const context = {
        domainCode: null,
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "role-7",
        displayName: "角色 7"
      };
      const recordKey = {
        resourceTypeCode: "DATA",
        resourceCode: "one",
        codeType: "default",
        operationCode: "VIEW",
        scopeMode: "INSTANCE" as const,
        conditionCode: null,
        canGrant: false
      };
      const change = buildAddChange({
        recordKey,
        summary: buildSummary({ recordKey, resourceLabel: "数据" })
      });
      const savedKey = draftKey(draftOwner!, context, "DATA");
      writeDraft(sessionStorage, savedKey, [change], false);
      if (different === "account") draftOwner = { tenantId: "1", userId: "8" };
      if (different === "tenant") draftOwner = { tenantId: "2", userId: "7" };
      const hook = usePermissionGrant();
      hook.typeCandidates.value = [
        {
          typeCode: different === "type" ? "REPORT" : "DATA",
          sortOrder: 0
        } as any
      ];
      await hook.handleSelectSubject({
        ...context,
        roleExternalId: different === "role" ? "other" : context.roleExternalId
      });
      expect(hook.grantStore.changes).toEqual([]);
      expect(confirmDiscardMock).not.toHaveBeenCalled();
      expect(entries.has(savedKey)).toBe(true);
      unmountCallbacks.at(-1)!();
    }
  );
});
