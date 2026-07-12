import { ref, computed } from "vue";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_GRANT_PERMS } from "./perms";
import {
  permCellKey,
  type DraftPermission,
  type DiffEntry,
  type CellState,
  type PermissionCellContext,
  type PermCellKey
} from "./types";
import {
  getRoleTree,
  getResourceTypeList,
  getResourceTree,
  getOperationList,
  getConditionList,
  getRolePermissionList,
  saveRolePermission,
  addChildPermission,
  removeChildPermission,
  type RoleTreeNode,
  type ResourceTypeItem,
  type ResourceTreeNode,
  type OperationItem,
  type ConditionOption,
  type RolePermissionItem,
  type RolePermissionAddItem,
  type RolePermissionUpdateItem,
  type DomainCapability,
  type OperatorCapability,
  type GrantScopeMode
} from "@/api/permission-grant";

/**
 * 权限授予页 hook（三栏工作台 + 草稿模型 + 两步保存 + 部分失败处理）。
 *
 * 设计要点（docs/design/frontend/permission-grant.md）：
 * 1. 草稿模型（§9.1）：baseline（服务端事实）+ draft（本地拷贝+修改），diff = draft - baseline
 * 2. 主权限稳定键：domainCode+resourceTypeCode+scopeMode+resourceCode?+codeType?+operationCode
 * 3. 子权限键：parentKey + "|" + childPermCellKey（dependOn 指向主权限 id）
 * 4. 单元格 6 态（§4.4）：UNAUTHORIZED/GRANTED/PENDING_ADD/PENDING_REMOVE/MODIFIED/ALL_COVERED
 * 5. 两步保存（§7.3）：save 主权限 -> 匹配新 id -> add-child 子权限
 * 6. 部分失败（决策点 5）：主成功子失败 -> 重载 baseline + 保留失败草稿 + 重试状态
 * 7. 离开保护（§9.3）：有草稿时切换角色/域触发确认
 * 8. 权限门控：ROLE:VIEW 查看 / ROLE:MANAGE 编辑；无 VIEW 整页无权不发请求
 * 9. 能力驱动（§11.3）：directGrantable=false 只读；grantableByOperator disabled 单元格
 */

/** 失败子权限操作（P1-6：保留 op + childKey，重试时 add->set/remove->delete） */
interface FailedChildOp {
  op: "add" | "remove";
  child: DraftPermission;
  childKey: string;
}

// ========== 模块级辅助函数 ==========

/** RolePermissionItem -> DraftPermission（baseline 转换） */
function toDraft(item: RolePermissionItem): DraftPermission {
  return {
    id: item.id,
    domainCode: item.domainCode,
    resourceTypeCode: item.resourceTypeCode,
    scopeMode: item.scopeMode,
    resourceCode: item.resourceCode,
    codeType: item.codeType,
    operationCode: item.operationCode,
    conditionCode: item.conditionCode,
    canGrant: item.canGrant,
    dependOn: item.dependOn,
    dependOnTempKey: null,
    grantSource: item.grantSource,
    resourceName: item.resourceName
  };
}

/** DraftPermission -> RolePermissionAddItem（save/add-child 请求） */
function toAddItem(d: DraftPermission): RolePermissionAddItem {
  const item: RolePermissionAddItem = {
    resourceTypeCode: d.resourceTypeCode,
    operationCode: d.operationCode,
    scopeMode: d.scopeMode
  };
  if (d.scopeMode === "INSTANCE") {
    item.resourceCode = d.resourceCode ?? undefined;
    item.codeType = d.codeType ?? undefined;
  }
  if (d.conditionCode) item.conditionCode = d.conditionCode;
  if (d.canGrant) item.canGrant = true;
  return item;
}

/** 主权限 diff（draft 相对 baseline） */
function computeMainDiff(
  mainDraft: Map<string, DraftPermission>,
  mainBaseline: Map<string, DraftPermission>
): DiffEntry[] {
  const entries: DiffEntry[] = [];
  // add + update
  for (const [key, draft] of mainDraft) {
    const base = mainBaseline.get(key);
    if (!base) {
      entries.push({
        type: "add",
        key,
        permission: draft,
        before: null,
        changedFields: [],
        isChild: false
      });
    } else {
      const changed: string[] = [];
      if (draft.conditionCode !== base.conditionCode)
        changed.push("conditionCode");
      if (draft.canGrant !== base.canGrant) changed.push("canGrant");
      if (changed.length > 0) {
        entries.push({
          type: "update",
          key,
          permission: draft,
          before: base,
          changedFields: changed,
          isChild: false
        });
      }
    }
  }
  // remove
  for (const [key, base] of mainBaseline) {
    if (!mainDraft.has(key)) {
      entries.push({
        type: "remove",
        key,
        permission: base,
        before: null,
        changedFields: [],
        isChild: false
      });
    }
  }
  return entries;
}

/** 子权限 diff（结构同主权限，isChild=true） */
function computeChildDiff(
  childDraft: Map<string, DraftPermission>,
  childBaseline: Map<string, DraftPermission>
): DiffEntry[] {
  const entries: DiffEntry[] = [];
  for (const [key, draft] of childDraft) {
    const base = childBaseline.get(key);
    if (!base) {
      entries.push({
        type: "add",
        key,
        permission: draft,
        before: null,
        changedFields: [],
        isChild: true
      });
    } else {
      const changed: string[] = [];
      if (draft.conditionCode !== base.conditionCode)
        changed.push("conditionCode");
      if (draft.canGrant !== base.canGrant) changed.push("canGrant");
      if (changed.length > 0) {
        entries.push({
          type: "update",
          key,
          permission: draft,
          before: base,
          changedFields: changed,
          isChild: true
        });
      }
    }
  }
  for (const [key, base] of childBaseline) {
    if (!childDraft.has(key)) {
      entries.push({
        type: "remove",
        key,
        permission: base,
        before: null,
        changedFields: [],
        isChild: true
      });
    }
  }
  return entries;
}

/** 构建主权限 key（domainCode + PermCellKey） */
function mainKeyOf(
  domainCode: string,
  resourceTypeCode: string,
  scopeMode: GrantScopeMode,
  resourceCode: string | null,
  codeType: string | null,
  operationCode: string
): string {
  return permCellKey({
    domainCode,
    resourceTypeCode,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  });
}

// ========== 主 hook ==========

export function usePermissionGrant() {
  // ---- 权限门控 ----
  const canView = computed(() => hasPerms(PERMISSION_GRANT_PERMS.ROLE_VIEW));
  const canManage = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.ROLE_MANAGE)
  );
  const canCreateCondition = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.CONDITION_CREATE)
  );

  // ---- 当前上下文 ----
  const currentRole = ref<RoleTreeNode | null>(null);
  const currentResourceTypeCode = ref<string>("MENU");
  const currentDomainCode = ref<string>("example");

  // ---- 数据 ----
  const roleTree = ref<RoleTreeNode[]>([]);
  const resourceTypes = ref<ResourceTypeItem[]>([]);
  const resourceTree = ref<ResourceTreeNode[]>([]);
  const operations = ref<OperationItem[]>([]);
  const conditions = ref<ConditionOption[]>([]);

  // ---- 草稿模型 ----
  const mainBaseline = ref<Map<string, DraftPermission>>(new Map());
  const mainDraft = ref<Map<string, DraftPermission>>(new Map());
  const childBaseline = ref<Map<string, DraftPermission>>(new Map());
  const childDraft = ref<Map<string, DraftPermission>>(new Map());

  // ---- 能力 ----
  const domainCapability = ref<DomainCapability>({
    supportsChildren: false,
    childResourceTypeCodes: []
  });
  const operatorCapability = ref<OperatorCapability>({
    canManage: false,
    grantableResourceTypeCodes: [],
    grantableOperationCodes: []
  });

  // ---- 加载状态 ----
  const loadingRoleTree = ref(false);
  const loadingContext = ref(false);
  const saving = ref(false);

  // ---- 部分失败（决策点 5） ----
  const saveError = ref<string | null>(null);
  const failedChildren = ref<FailedChildOp[]>([]);
  // P1-3：baseline 过期标志（reloadBaseline 失败时置 true，阻止继续保存直至重新加载成功）
  const baselineStale = ref(false);

  // ========== 计算 ==========

  /** 是否只读（角色不可直接配权 / 操作者无 MANAGE / 角色禁用） */
  const readonly = computed(() => {
    if (!currentRole.value) return true;
    const r = currentRole.value;
    if (r.roleExternalId.startsWith("__virtual_root_")) return true;
    if (!r.directGrantable) return true;
    if (!r.enabled) return true;
    if (!canManage.value) return true;
    if (!r.canManage) return true;
    // P1-1：fail-closed，操作者能力 canManage=false（含真实后端 default）时整页只读
    if (!operatorCapability.value.canManage) return true;
    return false;
  });

  /** 主权限 diff */
  const mainDiff = computed(() =>
    computeMainDiff(mainDraft.value, mainBaseline.value)
  );
  /** 子权限 diff */
  const childDiff = computed(() =>
    computeChildDiff(childDraft.value, childBaseline.value)
  );
  /** 全部 diff（右栏本次变更） */
  const allDiff = computed<DiffEntry[]>(() => [
    ...mainDiff.value,
    ...childDiff.value
  ]);
  /** 是否有未保存变更 */
  const hasDraft = computed(() => allDiff.value.length > 0);

  /** 当前资源类型能力 */
  const currentResourceType = computed(
    () =>
      resourceTypes.value.find(
        t => t.resourceTypeCode === currentResourceTypeCode.value
      ) ?? null
  );

  // ========== 能力判定 ==========

  /** 候选权限单元 grantableByOperator（决策点 6） */
  function isGrantableByOperator(
    resourceTypeCode: string,
    operationCode: string
  ): { grantable: boolean; reason: string | null } {
    if (!canManage.value)
      return { grantable: false, reason: "无 ROLE:MANAGE 权限" };
    const oc = operatorCapability.value;
    // P1-1：fail-closed，显式检查操作者 canManage（真实后端 default=false 时拒绝）
    if (!oc.canManage) return { grantable: false, reason: "操作者无管理能力" };
    if (!oc.grantableResourceTypeCodes.includes(resourceTypeCode)) {
      return { grantable: false, reason: "资源类型不可授予" };
    }
    if (!oc.grantableOperationCodes.includes(operationCode)) {
      return { grantable: false, reason: "操作不可授予" };
    }
    return { grantable: true, reason: null };
  }

  // ========== 单元格状态 ==========

  /** 计算主权限单元格状态 */
  function getMainCellState(
    resourceTypeCode: string,
    scopeMode: GrantScopeMode,
    resourceCode: string | null,
    codeType: string | null,
    operationCode: string
  ): CellState {
    const key = mainKeyOf(
      currentDomainCode.value,
      resourceTypeCode,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    );
    const inDraft = mainDraft.value.has(key);
    const inBase = mainBaseline.value.has(key);
    if (inDraft && inBase) {
      const d = mainDraft.value.get(key)!;
      const b = mainBaseline.value.get(key)!;
      if (d.conditionCode !== b.conditionCode || d.canGrant !== b.canGrant) {
        return "MODIFIED";
      }
      return "GRANTED";
    }
    if (inDraft && !inBase) return "PENDING_ADD";
    if (!inDraft && inBase) return "PENDING_REMOVE";
    // 未授权：检查是否被 ALL 覆盖（实例单元格 + 同操作有 ALL 授权）
    if (scopeMode === "INSTANCE") {
      const allKey = mainKeyOf(
        currentDomainCode.value,
        resourceTypeCode,
        "ALL",
        null,
        null,
        operationCode
      );
      if (mainDraft.value.has(allKey)) return "ALL_COVERED";
    }
    return "UNAUTHORIZED";
  }

  /** 构建单元格上下文（PermissionCell 渲染入参） */
  function buildMainCellContext(
    resourceTypeCode: string,
    scopeMode: GrantScopeMode,
    resourceCode: string | null,
    codeType: string | null,
    operationCode: string
  ): PermissionCellContext {
    const state = getMainCellState(
      resourceTypeCode,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    );
    const key = mainKeyOf(
      currentDomainCode.value,
      resourceTypeCode,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    );
    const draft =
      state === "PENDING_REMOVE"
        ? (mainBaseline.value.get(key) ?? null)
        : (mainDraft.value.get(key) ?? null);
    const allCovered =
      scopeMode === "INSTANCE" &&
      state !== "GRANTED" &&
      state !== "PENDING_REMOVE" &&
      mainDraft.value.has(
        mainKeyOf(
          currentDomainCode.value,
          resourceTypeCode,
          "ALL",
          null,
          null,
          operationCode
        )
      );
    const { grantable, reason } = isGrantableByOperator(
      resourceTypeCode,
      operationCode
    );
    // 子权限数量
    let childCount = 0;
    const childPrefix = key + "|";
    for (const k of childDraft.value.keys()) {
      if (k.startsWith(childPrefix)) childCount++;
    }
    // 条件摘要
    const condCode = draft?.conditionCode;
    const condSummary = condCode
      ? (conditions.value.find(c => c.code === condCode)?.name ?? condCode)
      : null;
    return {
      state,
      draft,
      allCovered: allCovered || state === "ALL_COVERED",
      grantableByOperator: grantable,
      denyReason: reason,
      readonly: readonly.value,
      childCount,
      conditionSummary: condSummary
    };
  }

  // ========== 单元格操作 ==========

  /** 主区域点击切换授权（§4.4 主区域点击） */
  function toggleMainCell(
    resourceTypeCode: string,
    scopeMode: GrantScopeMode,
    resourceCode: string | null,
    codeType: string | null,
    operationCode: string
  ) {
    if (readonly.value) return;
    const state = getMainCellState(
      resourceTypeCode,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    );
    // P1-6：只在新增时检查 grantableByOperator；撤销/恢复不检查
    // （后端 canGrant 校验约束新增和提升转授权，不约束普通回收）
    if (state === "UNAUTHORIZED" || state === "ALL_COVERED") {
      const { grantable, reason } = isGrantableByOperator(
        resourceTypeCode,
        operationCode
      );
      if (!grantable) {
        message(`不可授予：${reason}`, { type: "warning" });
        return;
      }
    }
    const key = mainKeyOf(
      currentDomainCode.value,
      resourceTypeCode,
      scopeMode,
      resourceCode,
      codeType,
      operationCode
    );
    const newMap = new Map(mainDraft.value);
    if (state === "UNAUTHORIZED" || state === "ALL_COVERED") {
      // 新增
      const resourceName = resolveResourceName(resourceTypeCode, resourceCode);
      newMap.set(key, {
        id: null,
        domainCode: currentDomainCode.value,
        resourceTypeCode,
        scopeMode,
        resourceCode,
        codeType,
        operationCode,
        conditionCode: null,
        canGrant: false,
        dependOn: null,
        dependOnTempKey: null,
        grantSource: "MANUAL",
        resourceName
      });
    } else if (
      state === "GRANTED" ||
      state === "PENDING_ADD" ||
      state === "MODIFIED"
    ) {
      // 移除（含撤销新增 / 恢复后移除）
      newMap.delete(key);
      // 级联删除子权限草稿
      const childPrefix = key + "|";
      const newChildMap = new Map(childDraft.value);
      for (const k of childDraft.value.keys()) {
        if (k.startsWith(childPrefix)) newChildMap.delete(k);
      }
      childDraft.value = newChildMap;
    } else if (state === "PENDING_REMOVE") {
      // 恢复
      const base = mainBaseline.value.get(key);
      if (base) newMap.set(key, base);
    }
    mainDraft.value = newMap;
  }

  /** 附加设置：更新主权限属性（条件/canGrant） */
  function setMainCellAttr(
    key: string,
    attr: { conditionCode?: string | null; canGrant?: boolean }
  ) {
    if (readonly.value) return;
    const newMap = new Map(mainDraft.value);
    const existing = newMap.get(key) ?? mainBaseline.value.get(key);
    if (!existing) return;
    const updated: DraftPermission = { ...existing };
    if (attr.conditionCode !== undefined)
      updated.conditionCode = attr.conditionCode;
    if (attr.canGrant !== undefined) updated.canGrant = attr.canGrant;
    newMap.set(key, updated);
    mainDraft.value = newMap;
  }

  // ========== 子权限操作 ==========

  /** 子权限 key = parentKey + "|" + childPermCellKey */
  function childKeyOf(parentKey: string, child: PermCellKey): string {
    return parentKey + "|" + permCellKey(child);
  }

  /** 获取主权限的子权限草稿列表 */
  function getChildrenOfParent(parentKey: string): DraftPermission[] {
    const prefix = parentKey + "|";
    const list: DraftPermission[] = [];
    for (const [k, v] of childDraft.value) {
      if (k.startsWith(prefix)) list.push(v);
    }
    return list;
  }

  /** 切换子权限单元格 */
  function toggleChildCell(
    parentKey: string,
    parent: DraftPermission,
    childResourceTypeCode: string,
    childScopeMode: GrantScopeMode,
    childResourceCode: string | null,
    childCodeType: string | null,
    childOperationCode: string
  ) {
    if (readonly.value) return;
    const ck = childKeyOf(parentKey, {
      domainCode: currentDomainCode.value,
      resourceTypeCode: childResourceTypeCode,
      scopeMode: childScopeMode,
      resourceCode: childResourceCode,
      codeType: childCodeType,
      operationCode: childOperationCode
    });
    const inDraft = childDraft.value.has(ck);
    const inBase = childBaseline.value.has(ck);
    const newMap = new Map(childDraft.value);
    if (!inDraft && !inBase) {
      // 新增子权限
      newMap.set(ck, {
        id: null,
        domainCode: currentDomainCode.value,
        resourceTypeCode: childResourceTypeCode,
        scopeMode: childScopeMode,
        resourceCode: childResourceCode,
        codeType: childCodeType,
        operationCode: childOperationCode,
        conditionCode: null,
        canGrant: false,
        dependOn: parent.id,
        dependOnTempKey: parent.id ? null : parentKey,
        grantSource: "MANUAL",
        resourceName: resolveResourceName(
          childResourceTypeCode,
          childResourceCode
        )
      });
    } else if (inDraft) {
      newMap.delete(ck);
    } else if (inBase) {
      // 恢复后移除
      newMap.delete(ck);
    }
    childDraft.value = newMap;
  }

  /** 附加设置：更新子权限属性 */
  function setChildCellAttr(
    childKey: string,
    attr: { conditionCode?: string | null; canGrant?: boolean }
  ) {
    if (readonly.value) return;
    const newMap = new Map(childDraft.value);
    const existing = newMap.get(childKey) ?? childBaseline.value.get(childKey);
    if (!existing) return;
    const updated: DraftPermission = { ...existing };
    if (attr.conditionCode !== undefined)
      updated.conditionCode = attr.conditionCode;
    if (attr.canGrant !== undefined) updated.canGrant = attr.canGrant;
    newMap.set(childKey, updated);
    childDraft.value = newMap;
  }

  /** 资源名称查找（从 resourceTree 查） */
  function resolveResourceName(
    resourceTypeCode: string,
    resourceCode: string | null
  ): string | null {
    if (!resourceCode) return null;
    const find = (nodes: ResourceTreeNode[]): string | null => {
      for (const n of nodes) {
        if (n.resourceCode === resourceCode) return n.resourceName;
        const r = find(n.children);
        if (r) return r;
      }
      return null;
    };
    if (resourceTypeCode === currentResourceTypeCode.value) {
      return find(resourceTree.value);
    }
    return resourceCode;
  }

  // ========== 加载 ==========

  /** 加载角色树 */
  async function loadRoleTree(keyword?: string) {
    if (!canView.value) return;
    loadingRoleTree.value = true;
    try {
      const resp = await getRoleTree({ keyword });
      roleTree.value = resp.items;
    } catch (e) {
      message(e instanceof Error ? e.message : "加载角色树失败", {
        type: "error"
      });
    } finally {
      loadingRoleTree.value = false;
    }
  }

  /** 加载资源类型 + 条件候选（页面初始化一次） */
  async function loadStaticData() {
    if (!canView.value) return;
    try {
      const [rtResp, condResp] = await Promise.all([
        getResourceTypeList({ domainCode: currentDomainCode.value }),
        getConditionList({ enabledOnly: false })
      ]);
      resourceTypes.value = rtResp.items;
      conditions.value = condResp.items;
    } catch (e) {
      message(e instanceof Error ? e.message : "加载静态数据失败", {
        type: "error"
      });
    }
  }

  /** 选择角色 -> 加载快照 + 成功后一次性提交（P1-3：失败时旧上下文不变） */
  async function selectRole(role: RoleTreeNode) {
    if (role.roleExternalId.startsWith("__virtual_root_")) return;
    // 离开保护
    if (hasDraft.value && currentRole.value) {
      try {
        await ElMessageBox.confirm(
          "当前角色有未保存的变更，切换角色将丢弃。是否继续？",
          "切换角色",
          { type: "warning" }
        );
      } catch {
        return;
      }
    }
    const domainCode = role.domainCode ?? "";
    loadingContext.value = true;
    try {
      // P1-3：先加载快照，成功后一次性提交；失败时旧上下文完全不变
      const snapshot = await loadRolePermissionSnapshot(role, domainCode);
      currentRole.value = role;
      // P1-7：全局角色 domainCode 保持空（契约空域=全局对象）
      currentDomainCode.value = domainCode;
      mainBaseline.value = snapshot.mainBaseline;
      mainDraft.value = new Map(snapshot.mainBaseline);
      childBaseline.value = snapshot.childBaseline;
      childDraft.value = new Map(snapshot.childBaseline);
      domainCapability.value = snapshot.domainCapability;
      operatorCapability.value = snapshot.operatorCapability;
      failedChildren.value = [];
      saveError.value = null;
      baselineStale.value = false;
    } catch (e) {
      message(e instanceof Error ? e.message : "加载权限事实失败", {
        type: "error"
      });
      // 旧上下文不变
    } finally {
      loadingContext.value = false;
    }
    // 资源上下文单独加载（失败不影响角色权限数据）
    if (currentRole.value === role) {
      await reloadResourceContext();
    }
  }

  /**
   * 加载角色权限快照（P1-3：不直接修改 refs，返回完整 snapshot 供调用方提交）。
   * 失败时抛错，调用方决定回滚/清空策略。
   */
  async function loadRolePermissionSnapshot(
    role: RoleTreeNode,
    domainCode: string
  ): Promise<{
    mainBaseline: Map<string, DraftPermission>;
    childBaseline: Map<string, DraftPermission>;
    domainCapability: DomainCapability;
    operatorCapability: OperatorCapability;
  }> {
    const resp = await getRolePermissionList({
      domainCode,
      roleTypeCode: role.roleTypeCode,
      roleExternalId: role.roleExternalId
    });
    const main = new Map<string, DraftPermission>();
    const child = new Map<string, DraftPermission>();
    for (const item of resp.items) {
      const d = toDraft(item);
      if (item.dependOn === null) {
        const key = mainKeyOf(
          item.domainCode,
          item.resourceTypeCode,
          item.scopeMode,
          item.resourceCode,
          item.codeType,
          item.operationCode
        );
        main.set(key, d);
      } else {
        // 子权限：需找父权限 key
        const parent = resp.items.find(p => p.id === item.dependOn);
        if (parent) {
          const parentKey = mainKeyOf(
            parent.domainCode,
            parent.resourceTypeCode,
            parent.scopeMode,
            parent.resourceCode,
            parent.codeType,
            parent.operationCode
          );
          const childK =
            parentKey +
            "|" +
            permCellKey({
              domainCode: item.domainCode,
              resourceTypeCode: item.resourceTypeCode,
              scopeMode: item.scopeMode,
              resourceCode: item.resourceCode,
              codeType: item.codeType,
              operationCode: item.operationCode
            });
          d.dependOnTempKey = null;
          child.set(childK, d);
        }
      }
    }
    return {
      mainBaseline: main,
      childBaseline: child,
      domainCapability: resp.domainCapability,
      operatorCapability: resp.operatorCapability
    };
  }

  /** 切换资源类型 -> 加载资源树 + 操作 */
  async function switchResourceType(code: string) {
    if (currentResourceTypeCode.value === code) return;
    currentResourceTypeCode.value = code;
    await reloadResourceContext();
  }

  /** 重新加载资源树 + 操作列 */
  async function reloadResourceContext() {
    if (!canView.value) return;
    loadingContext.value = true;
    try {
      const [rtResp, opResp] = await Promise.all([
        getResourceTree({
          domainCode: currentDomainCode.value,
          resourceTypeCode: currentResourceTypeCode.value
        }),
        getOperationList({
          resourceTypeCode: currentResourceTypeCode.value
        })
      ]);
      resourceTree.value = rtResp.items;
      operations.value = opResp.items;
    } catch (e) {
      message(e instanceof Error ? e.message : "加载资源树失败", {
        type: "error"
      });
    } finally {
      loadingContext.value = false;
    }
  }

  /** 重载 baseline（保存后 / 重试，P1-3：返回成功状态，失败时标记 stale 阻止继续保存） */
  async function reloadBaseline(): Promise<boolean> {
    if (!currentRole.value) return false;
    const role = currentRole.value;
    loadingContext.value = true;
    try {
      const snapshot = await loadRolePermissionSnapshot(
        role,
        currentDomainCode.value
      );
      mainBaseline.value = snapshot.mainBaseline;
      mainDraft.value = new Map(snapshot.mainBaseline);
      childBaseline.value = snapshot.childBaseline;
      childDraft.value = new Map(snapshot.childBaseline);
      domainCapability.value = snapshot.domainCapability;
      operatorCapability.value = snapshot.operatorCapability;
      failedChildren.value = [];
      saveError.value = null;
      baselineStale.value = false;
      return true;
    } catch (e) {
      message(e instanceof Error ? e.message : "重载权限事实失败", {
        type: "error"
      });
      baselineStale.value = true;
      return false;
    } finally {
      loadingContext.value = false;
    }
  }

  // ========== 保存（两步 + 部分失败，决策点 5） ==========

  async function saveAll(): Promise<boolean> {
    if (!currentRole.value || readonly.value || saving.value) return false;
    // P1-3：baseline 过期时阻止继续保存（避免基于旧数据重复操作）
    if (baselineStale.value) {
      message("权限事实已过期，请重新选择角色刷新后再保存", {
        type: "warning"
      });
      return false;
    }
    const role = currentRole.value;
    saving.value = true;
    saveError.value = null;
    failedChildren.value = [];

    try {
      const mDiff = mainDiff.value;
      const cDiff = childDiff.value;

      const mainAdd: RolePermissionAddItem[] = [];
      const updateItems: RolePermissionUpdateItem[] = [];
      const mainRemove: number[] = [];
      const childRemoveIds: number[] = [];
      const childRemoveItems: FailedChildOp[] = [];
      const childAddGrouped: {
        parentKey: string;
        childKey: string;
        child: DraftPermission;
      }[] = [];

      // 主权限 diff
      for (const d of mDiff) {
        if (d.type === "add") mainAdd.push(toAddItem(d.permission));
        else if (d.type === "update")
          updateItems.push({
            id: d.permission.id!,
            conditionCode: d.permission.conditionCode,
            canGrant: d.permission.canGrant
          });
        else if (d.type === "remove") mainRemove.push(d.permission.id!);
      }
      // 子权限 diff
      for (const d of cDiff) {
        if (d.type === "add") {
          childAddGrouped.push({
            parentKey: d.permission.dependOnTempKey ?? "",
            childKey: d.key,
            child: d.permission
          });
        } else if (d.type === "update") {
          // 子权限属性更新通过 save.update（传子权限 id）
          updateItems.push({
            id: d.permission.id!,
            conditionCode: d.permission.conditionCode,
            canGrant: d.permission.canGrant
          });
        } else if (d.type === "remove") {
          // P1-5：父权限已在 mainRemove 中时，后端级联删除子权限，跳过 remove-child
          if (
            d.permission.dependOn &&
            mainRemove.includes(d.permission.dependOn)
          ) {
            continue;
          }
          childRemoveIds.push(d.permission.id!);
          childRemoveItems.push({
            op: "remove",
            child: d.permission,
            childKey: d.key
          });
        }
      }

      if (
        mainAdd.length === 0 &&
        updateItems.length === 0 &&
        mainRemove.length === 0 &&
        childAddGrouped.length === 0 &&
        childRemoveIds.length === 0
      ) {
        message("无变更", { type: "info" });
        return;
      }

      // P1-2：主权限 add/update/remove + 子权限 update 全空时跳过 save
      // （真实后端 GRANT_REQUEST_EMPTY，mock save 端点已兜底校验）
      const tempKeyToServerId = new Map<string, number>();
      const needMainSave =
        mainAdd.length > 0 || updateItems.length > 0 || mainRemove.length > 0;
      if (needMainSave) {
        const saveResp = await saveRolePermission({
          domainCode: currentDomainCode.value,
          roleTypeCode: role.roleTypeCode,
          roleExternalId: role.roleExternalId,
          add: mainAdd,
          update: updateItems,
          remove: mainRemove
        });
        // 匹配新主权限 id（按稳定键）
        for (const item of saveResp.items) {
          const key = mainKeyOf(
            item.domainCode,
            item.resourceTypeCode,
            item.scopeMode,
            item.resourceCode,
            item.codeType,
            item.operationCode
          );
          tempKeyToServerId.set(key, item.id);
        }
      }

      // Step 3: add-child（新子权限，按 parentPermissionId 分组）
      let childFailureOccurred = false;
      const failedChildAdd: FailedChildOp[] = [];
      const addChildByParent = new Map<
        number,
        {
          item: RolePermissionAddItem;
          childKey: string;
          child: DraftPermission;
        }[]
      >();
      for (const { parentKey, childKey, child } of childAddGrouped) {
        // P1-3：优先用 child.dependOn（已保存主权限），否则用 tempKeyToServerId（新主权限）
        // P1-2：retry 时主权限已保存但 tempKeyToServerId 为空，回退到 mainBaseline
        const parentId =
          child.dependOn ??
          tempKeyToServerId.get(parentKey) ??
          mainBaseline.value.get(parentKey)?.id ??
          null;
        if (!parentId) {
          // P1-4：父权限未保存成功，标记部分失败并保留可重试的 childKey
          failedChildAdd.push({ op: "add", child, childKey });
          childFailureOccurred = true;
          continue;
        }
        if (!addChildByParent.has(parentId)) addChildByParent.set(parentId, []);
        addChildByParent.get(parentId)!.push({
          item: toAddItem(child),
          childKey,
          child
        });
      }

      for (const [parentId, entries] of addChildByParent) {
        try {
          await addChildPermission({
            parentPermissionId: parentId,
            children: entries.map(e => e.item)
          });
        } catch (e) {
          childFailureOccurred = true;
          const errMsg = e instanceof Error ? e.message : "子权限保存失败";
          // 该组子权限全部标记失败（P1-6：保留 childKey 供重试）
          for (const entry of entries) {
            failedChildAdd.push({
              op: "add",
              child: entry.child,
              childKey: entry.childKey
            });
          }
          saveError.value = `主权限已保存，部分子权限保存失败：${errMsg}`;
        }
      }

      // Step 4: remove-child（删除的子权限，P1-4：失败保留重试信息）
      const failedChildRemove: FailedChildOp[] = [];
      for (let i = 0; i < childRemoveIds.length; i++) {
        try {
          await removeChildPermission({ permissionId: childRemoveIds[i] });
        } catch {
          childFailureOccurred = true;
          failedChildRemove.push(childRemoveItems[i]);
        }
      }

      // Step 5: 重载 baseline（P1-3：返回成功状态）
      const reloadOk = await reloadBaseline();

      // Step 6: 部分失败处理（决策点 5，P1-4：remove-child 失败也纳入）
      if (childFailureOccurred) {
        failedChildren.value = [...failedChildAdd, ...failedChildRemove];
        message(saveError.value ?? "部分子权限操作失败", { type: "warning" });
      } else if (!reloadOk) {
        // P1-3：保存已提交但刷新失败，阻止继续保存直至重新加载成功
        saveError.value = "保存已提交，但刷新权限事实失败，请重新选择角色刷新";
        message(saveError.value, { type: "warning" });
      } else {
        message("保存成功", { type: "success" });
      }
      return true;
    } catch (e) {
      saveError.value = e instanceof Error ? e.message : "保存失败";
      message(saveError.value, { type: "error" });
      return true; // 已开始保存（通过门禁），失败状态已记录在 saveError/failedChildren
    } finally {
      saving.value = false;
    }
  }

  /**
   * 重试失败的子权限（P1-6：按 op 直接 set/delete childKey，不重建父键）
   * P1-重试：stale 状态下先刷新 baseline，避免被 saveAll 的 stale guard 拦截后丢失失败操作信息；
   * failedChildren 不在此处清空，统一由 saveAll 开头清空（保存实际开始），避免被前置 guard 拦截后丢失。
   */
  async function retryFailedChildren() {
    if (failedChildren.value.length === 0) return;

    // stale 状态：reloadBaseline 成功会清空 failedChildren 并重置 childDraft，需先备份再重新应用
    if (baselineStale.value) {
      const pendingOps = [...failedChildren.value];
      const reloadOk = await reloadBaseline();
      if (!reloadOk) {
        // 刷新仍失败，reloadBaseline 失败路径保留 failedChildren，用户可重新选择角色或再次重试
        message("权限事实仍刷新失败，无法重试，请重新选择角色刷新", {
          type: "warning"
        });
        return;
      }
      applyChildOpsToDraft(pendingOps);
      saveError.value = null;
      // P2：reload 后能力可能变更（canManage=false 只读）或 saving 占用，saveAll 前置门禁会拦截；
      // 未真正开始保存时恢复 failedChildren 与提示，避免重试入口和失败元数据丢失
      const started = await saveAll();
      if (!started) {
        failedChildren.value = pendingOps;
        saveError.value = readonly.value
          ? "权限能力已变更，当前为只读，无法继续保存"
          : "保存正在进行中，请稍后重试";
        message(saveError.value, { type: "warning" });
      }
      return;
    }

    // 非 stale：直接应用失败操作（failedChildren 由 saveAll 开头清空）
    applyChildOpsToDraft(failedChildren.value);
    saveError.value = null;
    // P2：非 stale 下若被前置门禁拦截（readonly/saving），failedChildren 已保留，仅需恢复提示
    const started = await saveAll();
    if (!started) {
      saveError.value = readonly.value
        ? "权限能力已变更，当前为只读，无法继续保存"
        : "保存正在进行中，请稍后重试";
      message(saveError.value, { type: "warning" });
    }
  }

  /** 将失败操作应用到 childDraft（add->set childKey，remove->delete childKey） */
  function applyChildOpsToDraft(ops: FailedChildOp[]) {
    const newMap = new Map(childDraft.value);
    for (const fop of ops) {
      if (fop.op === "add") {
        newMap.set(fop.childKey, fop.child);
      } else {
        newMap.delete(fop.childKey);
      }
    }
    childDraft.value = newMap;
  }

  /** 放弃全部更改 */
  function discardAll() {
    mainDraft.value = new Map(mainBaseline.value);
    childDraft.value = new Map(childBaseline.value);
    failedChildren.value = [];
    saveError.value = null;
  }

  /** 撤销单项变更 */
  function revertDiff(entry: DiffEntry) {
    if (entry.type === "add") {
      if (entry.isChild) {
        childDraft.value = new Map(
          [...childDraft.value].filter(([k]) => k !== entry.key)
        );
      } else {
        mainDraft.value = new Map(
          [...mainDraft.value].filter(([k]) => k !== entry.key)
        );
      }
    } else if (entry.type === "remove") {
      if (entry.isChild) {
        childDraft.value = new Map(childDraft.value).set(
          entry.key,
          entry.permission
        );
      } else {
        mainDraft.value = new Map(mainDraft.value).set(
          entry.key,
          entry.permission
        );
      }
    } else if (entry.type === "update" && entry.before) {
      if (entry.isChild) {
        childDraft.value = new Map(childDraft.value).set(
          entry.key,
          entry.before
        );
      } else {
        mainDraft.value = new Map(mainDraft.value).set(entry.key, entry.before);
      }
    }
  }

  // ========== 离开保护（§9.3，P1-8：可移除监听器 + 路由导航由 index.vue onBeforeRouteLeave 拦截） ==========

  const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
    if (hasDraft.value) {
      e.preventDefault();
      e.returnValue = "";
    }
  };
  if (typeof window !== "undefined") {
    window.addEventListener("beforeunload", beforeUnloadHandler);
  }

  /** 移除监听器（组件卸载时调用，避免内存泄漏） */
  function cleanup() {
    if (typeof window !== "undefined") {
      window.removeEventListener("beforeunload", beforeUnloadHandler);
    }
  }

  return {
    // 权限
    canView,
    canManage,
    canCreateCondition,
    // 上下文
    currentRole,
    currentResourceTypeCode,
    currentDomainCode,
    // 数据
    roleTree,
    resourceTypes,
    resourceTree,
    operations,
    conditions,
    // 草稿
    mainDraft,
    mainBaseline,
    childDraft,
    childBaseline,
    // 能力
    domainCapability,
    operatorCapability,
    currentResourceType,
    readonly,
    // diff
    mainDiff,
    childDiff,
    allDiff,
    hasDraft,
    // 加载状态
    loadingRoleTree,
    loadingContext,
    saving,
    saveError,
    failedChildren,
    // 加载
    loadRoleTree,
    loadStaticData,
    selectRole,
    switchResourceType,
    reloadResourceContext,
    reloadBaseline,
    // 单元格
    getMainCellState,
    buildMainCellContext,
    toggleMainCell,
    setMainCellAttr,
    isGrantableByOperator,
    // 子权限
    getChildrenOfParent,
    toggleChildCell,
    setChildCellAttr,
    // 保存
    saveAll,
    discardAll,
    revertDiff,
    retryFailedChildren,
    // 离开保护
    cleanup
  };
}
