/**
 * hook 恢复路径测试（T-FE-034 验收第11条）。
 * 驱动 usePermissionGrantV2 验证：
 * - SAVE_OUTCOME_UNKNOWN reconcile 成功 -> CLEAN
 * - SAVE_OUTCOME_UNKNOWN 保留历史 failedChildren 快照（P1：unknown 分支不丢子失败）
 * - STALE_WITH_CHILD_FAILURE -> refreshAndRetry 原子刷新并重试 -> CLEAN
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import type {
  RolePermissionItem,
  RoleTreeNode,
  DomainCapability,
  OperatorCapability
} from "@/api/permission-grant";

// 消除 Vue lifecycle 警告（hook 在非组件上下文注册 onMounted/onUnmounted）；
// ref/computed 等保留真实实现，仅替换生命周期钩子为 no-op
vi.mock("vue", async () => {
  const actual = await vi.importActual<typeof vue>("vue");
  return {
    ...actual,
    onMounted: vi.fn(),
    onUnmounted: vi.fn()
  };
});
import * as vue from "vue";

vi.mock("@/utils/auth", () => ({ hasPerms: () => true }));
vi.mock("@/utils/message", () => ({ message: vi.fn() }));
vi.mock("element-plus", () => ({
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) }
}));
vi.mock("@/api/permission-grant", () => ({
  getRoleTree: vi.fn().mockResolvedValue({ items: [] })
}));
vi.mock("./useV2MatrixData", () => ({
  useV2MatrixData: () => ({
    resourceTypes: vue.ref([]),
    resourceTree: vue.ref([]),
    operationList: vue.ref([]),
    conditionOptions: vue.ref([]),
    loadingResourceTypes: vue.ref(false),
    currentResourceTypeCode: vue.ref(""),
    loadResourceTypes: vi.fn().mockResolvedValue(undefined),
    loadConditionOptions: vi.fn(),
    resetMatrix: vi.fn(),
    switchResourceType: vi.fn()
  })
}));
// 消除 Router setup 警告（hook 在非组件上下文调用 onBeforeRouteLeave）
vi.mock("vue-router", () => ({
  onBeforeRouteLeave: vi.fn()
}));

const transportMock = {
  getRolePermissionList: vi.fn(),
  saveRolePermission: vi.fn(),
  addChildPermission: vi.fn(),
  removeChildPermission: vi.fn(),
  getChildPermissions: vi.fn()
};
vi.mock("../transport", () => ({
  useV2GrantTransport: () => transportMock
}));

import { usePermissionGrantV2 } from "./hook";
import type { PermCellKey } from "@/utils/permission-grant-types";

const DOM_CAP: DomainCapability = {
  supportsChildren: true,
  childResourceTypeCodes: ["BUTTON"]
};
const OP_CAP: OperatorCapability = {
  canManage: true,
  grantableResourceTypeCodes: ["MENU", "BUTTON"],
  grantableOperationCodes: ["VIEW"]
};
const ROLE = {
  roleExternalId: "role-1",
  roleTypeCode: "ROLE",
  roleName: "测试角色",
  domainCode: "",
  directGrantable: true,
  enabled: true,
  canManage: true,
  canView: true
} as RoleTreeNode;

const MENU_CELL: PermCellKey = {
  domainCode: "",
  resourceTypeCode: "MENU",
  scopeMode: "INSTANCE",
  resourceCode: "sys",
  codeType: "MENU",
  operationCode: "VIEW"
};
const BUTTON_CELL: PermCellKey = {
  domainCode: "",
  resourceTypeCode: "BUTTON",
  scopeMode: "INSTANCE",
  resourceCode: "btn",
  codeType: "BUTTON",
  operationCode: "VIEW"
};

function mainBaseline(
  id: number,
  cond: string | null = null
): RolePermissionItem {
  return {
    id,
    domainCode: "",
    resourceTypeCode: "MENU",
    resourceCode: "sys",
    codeType: "MENU",
    resourceName: "系统",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: cond,
    canGrant: false,
    dependOn: null,
    grantSource: "MANUAL"
  };
}
function childBaseline(id: number, parentId: number): RolePermissionItem {
  return {
    id,
    domainCode: "",
    resourceTypeCode: "BUTTON",
    resourceCode: "btn",
    codeType: "BUTTON",
    resourceName: "按钮",
    operationCode: "VIEW",
    scopeMode: "INSTANCE",
    conditionCode: null,
    canGrant: false,
    dependOn: parentId,
    grantSource: "MANUAL"
  };
}
function baselineResp(items: RolePermissionItem[]) {
  return { items, domainCapability: DOM_CAP, operatorCapability: OP_CAP };
}

describe("usePermissionGrantV2 恢复路径", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("主请求超时 -> SAVE_OUTCOME_UNKNOWN，reconcile 成功（主权限已落库）-> CLEAN", async () => {
    transportMock.getRolePermissionList
      .mockResolvedValueOnce(baselineResp([])) // selectRole
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])); // reconcile：主权限已落库
    transportMock.saveRolePermission.mockRejectedValue({
      code: "ECONNABORTED"
    });

    const store = usePermissionGrantV2();
    await store.selectRole(ROLE);
    store.grantUnconditional(MENU_CELL, "系统");
    await store.requestSave();
    await store.confirmSave();

    // reconcile 身份合并：grant UUID -> 100，settled 清理 -> grantTasks 空 -> CLEAN
    expect(store.saveOutcomeUnknown.value).toBe(false);
    expect(store.savePhase.value).toBe("CLEAN");
  });

  it("子失败状态下再次保存遇超时 -> unknown 保留历史 failedChildren（P1）", async () => {
    transportMock.getRolePermissionList
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])) // selectRole
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])) // 第一轮 reconcile（子未落库）
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])); // 第二轮 unknown reconcile
    transportMock.addChildPermission.mockRejectedValue(new Error("child fail"));
    transportMock.saveRolePermission.mockRejectedValue({
      code: "ECONNABORTED"
    });

    const store = usePermissionGrantV2();
    await store.selectRole(ROLE);
    // 第一轮：子 add diff，addChild 失败 -> CHILD_PARTIAL（failedChildren 非空）
    store.addChildBranch(100, BUTTON_CELL, null, false, "按钮");
    await store.requestSave();
    await store.confirmSave();
    expect(store.failedChildren.value.length).toBe(1);

    // 第二轮：主 update diff + 超时 -> unknown，应恢复 failedSnapshot 供 reconcile
    store.updateVariant(100, "COND_A", false, MENU_CELL);
    await store.requestSave();
    await store.confirmSave();

    // P1：unknown 分支恢复 failedSnapshot，reconcile 后子失败仍保留（未落库）
    expect(store.failedChildren.value.length).toBe(1);
    expect(store.savePhase.value).toBe("SAVE_FAILED_CHILD");
  });

  it("STALE_WITH_CHILD_FAILURE -> refreshAndRetry 原子刷新并重试 -> CLEAN", async () => {
    transportMock.getRolePermissionList
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])) // selectRole
      .mockRejectedValueOnce(new Error("fetch fail")) // 第一轮 reconcile 失败 -> STALE
      .mockResolvedValueOnce(baselineResp([mainBaseline(100)])) // refreshAndRetry reconcile
      .mockResolvedValueOnce(
        baselineResp([mainBaseline(100), childBaseline(200, 100)])
      ); // doSave reloadBaseline
    transportMock.addChildPermission
      .mockRejectedValueOnce(new Error("child fail")) // 第一轮子失败
      .mockResolvedValueOnce({ items: [] }); // refreshAndRetry doSave 子成功

    const store = usePermissionGrantV2();
    await store.selectRole(ROLE);
    store.addChildBranch(100, BUTTON_CELL, null, false, "按钮");
    await store.requestSave();
    await store.confirmSave();
    // 子失败 + fetchBaseline 失败 -> STALE_WITH_CHILD_FAILURE
    expect(store.savePhase.value).toBe("STALE_WITH_CHILD_FAILURE");

    await store.refreshAndRetry();

    // doSave 子成功 + reloadBaseline 成功 -> CLEAN
    expect(store.baselineStale.value).toBe(false);
    expect(store.failedChildren.value.length).toBe(0);
    expect(store.savePhase.value).toBe("CLEAN");
  });
});
