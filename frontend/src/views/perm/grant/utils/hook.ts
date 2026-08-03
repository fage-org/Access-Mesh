/**
 * 权限授予页（4.1 v3）编排 composable。
 * 职责：只读依赖加载（资源树/操作/条件）→ 门控派生 → 生效视图/来源链计算 →
 * 弹窗/详情层/变更清单事件编排 → 保存/放弃/离开保护。
 * 状态归属：baseline + 草稿 + 提交状态机在 grant-store（Pinia 四态）；本 hook 只做编排与派生。
 */
import {
  computed,
  onActivated,
  onMounted,
  onBeforeUnmount,
  ref,
  watch
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import {
  getOperationList,
  getResourceTree,
  type ResourceTreeNode
} from "@/api/resource-operation";
import {
  getConditionList,
  type ConditionResp
} from "@/api/permission-condition";
import { PERMISSION_GRANT_PERMS } from "./perms";
import { useGrantStore } from "./grant-store";
import {
  applyDialogResultToDraft,
  applyDraftToRecords,
  buildAddChange,
  buildRemoveChange,
  buildReplaceChange,
  buildSummary,
  buildUpdateChange,
  cellDraftMark,
  findBranchConflict,
  groupKeyOf,
  persistedParentKey,
  draftParentKey,
  type DialogResult,
  type EffectiveRecord
} from "./grant-plan";
import {
  computeSourceChain,
  coveredSetOf,
  mergeOperationsForType,
  type OperationDefInput
} from "./source-chain";
import { decideRefreshAction } from "./subject-tree";
import type {
  AddChange,
  DraftChange,
  GrantContext,
  SubjectType,
  SubjectTreeNode
} from "./types";

/** 操作列配置 localStorage 键（按 subjectType 隔离，§3.2） */
const COLUMN_STORAGE_KEY = "perm-grant:columns";

export function usePermissionGrant() {
  const route = useRoute();
  const router = useRouter();
  const grantStore = useGrantStore();

  // ========== 主体入口（§1.1：角色首期；组织二期占位；PERSONAL 预留不挂路由） ==========

  const subjectType = computed<SubjectType>(() =>
    route.query.subjectType === "ORG" ? "ORG" : "ROLE"
  );

  // ========== 门控（§10 双层门禁；页面 capability 仅门禁派生） ==========

  const canView = computed(() => hasPerms(PERMISSION_GRANT_PERMS.ROLE_VIEW));
  const canManage = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.ROLE_MANAGE)
  );
  const canCondition = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.CONDITION_VIEW)
  );
  const canResource = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.RESOURCE_VIEW)
  );
  const canOperation = computed(() =>
    hasPerms(PERMISSION_GRANT_PERMS.OPERATION_VIEW)
  );

  // ========== 只读依赖（首次加载；切换主体不重复拉取） ==========

  const resourceForest = ref<ResourceTreeNode[]>([]);
  const operationDefs = ref<OperationDefInput[]>([]);
  const conditions = ref<ConditionResp[]>([]);
  const depsLoading = ref(false);
  let depsLoaded = false;

  async function loadDeps() {
    if (depsLoaded || depsLoading.value) return;
    depsLoading.value = true;
    try {
      const [treeResp, opResp, conditionResp] = await Promise.all([
        canResource.value
          ? getResourceTree({})
          : Promise.resolve({ items: [] }),
        canOperation.value
          ? getOperationList({})
          : Promise.resolve({ items: [] }),
        canCondition.value ? getConditionList() : Promise.resolve({ items: [] })
      ]);
      resourceForest.value = (treeResp.items ?? [])
        .map(it => it.root)
        .filter(Boolean) as ResourceTreeNode[];
      operationDefs.value = (opResp.items ?? []).map(op => ({
        code: op.code,
        name: op.name,
        resourceTypeCode: op.resourceTypeCode,
        binaryBit: op.binaryBit,
        inheritMask: op.inheritMask
      }));
      conditions.value = conditionResp.items ?? [];
      depsLoaded = true;
    } catch (error: any) {
      message(error.message || "加载基础数据失败", { type: "error" });
    } finally {
      depsLoading.value = false;
    }
  }

  // ========== 查看态（§3.4 开关为查看态过滤，不影响草稿与数据） ==========

  const includeResourceInherit = ref(true);
  const includeOpInherit = ref(true);
  const resourceKeyword = ref("");

  /** 隐藏操作列集合（localStorage 按 subjectType 隔离，刷新保留） */
  const hiddenColumnCodes = ref<string[]>(loadHiddenColumns());

  function loadHiddenColumns(): string[] {
    try {
      const raw = localStorage.getItem(
        `${COLUMN_STORAGE_KEY}:${subjectType.value}`
      );
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed)
        ? parsed.filter(x => typeof x === "string")
        : [];
    } catch {
      return [];
    }
  }

  watch([hiddenColumnCodes, subjectType], () => {
    localStorage.setItem(
      `${COLUMN_STORAGE_KEY}:${subjectType.value}`,
      JSON.stringify(hiddenColumnCodes.value)
    );
  });

  // ========== 生效视图 + 来源链（§3.5 前端自算） ==========

  const effective = computed(() =>
    applyDraftToRecords({
      baseline: grantStore.baseline,
      changes: grantStore.changes,
      operations: operationDefs.value
    })
  );

  const sourceChain = computed(() =>
    computeSourceChain({
      records: effective.value.mains,
      resources: resourceForest.value,
      operations: operationDefs.value,
      includeResourceInherit: includeResourceInherit.value,
      includeOpInherit: includeOpInherit.value
    })
  );

  /** 矩阵涉及的资源类型（有权限或已授权过的类型，§3.1；新增草稿类型即时并入） */
  const matrixTypeCodes = computed(() => {
    const types = new Set<string>();
    for (const record of effective.value.mains) {
      types.add(record.resourceTypeCode);
    }
    return [...types].sort();
  });

  /** 合并操作列（union by code 跨类型去重展示，§3.2；专属优先、全局回退已在 merge 内完成） */
  const unionColumns = computed(() => {
    const columnsByType = sourceChain.value.columnsByType;
    const byCode = new Map<
      string,
      { code: string; name: string; globalFallback: boolean }
    >();
    for (const typeCode of matrixTypeCodes.value) {
      for (const op of columnsByType.get(typeCode) ?? []) {
        if (!byCode.has(op.code)) {
          byCode.set(op.code, {
            code: op.code,
            name: op.name,
            globalFallback: op.globalFallback
          });
        }
      }
    }
    return [...byCode.values()];
  });

  /** 可见操作列（操作列配置过滤） */
  const visibleColumns = computed(() =>
    unionColumns.value.filter(c => !hiddenColumnCodes.value.includes(c.code))
  );

  // ========== 主体选择（未保存变更拦截，§6.5 离开保护同源） ==========

  async function confirmDiscardIfDirty(): Promise<boolean> {
    if (!grantStore.isDirty) return true;
    try {
      await ElMessageBox.confirm(
        "当前存在未保存的授权变更，切换/离开将放弃这些变更，是否继续？",
        "未保存变更",
        {
          confirmButtonText: "放弃变更",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
      return true;
    } catch {
      return false;
    }
  }

  async function handleSelectSubject(context: GrantContext): Promise<boolean> {
    if (
      grantStore.context?.roleTypeCode === context.roleTypeCode &&
      grantStore.context?.roleExternalId === context.roleExternalId
    ) {
      // 同主体 no-op：消费残留 query（评审问题 5 防御）
      if (route.query.roleExternalId === context.roleExternalId) {
        router.replace({
          query: { ...route.query, roleExternalId: undefined }
        });
      }
      return true;
    }
    if (!(await confirmDiscardIfDirty())) return false;
    try {
      // selectSubject saving 拒绝/过期不写状态（评审问题 3+4）；成功才设 capability
      const ok = await grantStore.selectSubject(context);
      if (!ok) return false;
    } catch (error: any) {
      message(error.message || "加载权限配置失败", { type: "error" });
      return false;
    }
    grantStore.setCapability(canManage.value ? "edit" : "view");
    // 一次性入口指令消费：预选成功后移除 query（问题 6）
    if (route.query.roleExternalId === context.roleExternalId) {
      router.replace({ query: { ...route.query, roleExternalId: undefined } });
    }
    return true;
  }

  // ========== 主体选择 UI 状态（受控协议，问题 3+6） ==========

  type SubjectTreeExpose = {
    loadTree: () => Promise<void>;
    findNode: (
      externalId: string,
      roleTypeCode?: string
    ) => SubjectTreeNode | null;
    preselect: (externalId: string) => void;
  };
  const subjectTreeRef = ref<SubjectTreeExpose | null>(null);
  const activeKey = ref<string | null>(null);
  const selectingKey = ref<string | null>(null);
  const groupHint = ref<string | null>(null);
  const refreshInFlight = ref(false);

  /** 冻结态（saving/切换期间，评审问题 3+4：禁用所有写入口） */
  const frozen = computed(
    () =>
      grantStore.isSaving ||
      grantStore.baselineLoading ||
      selectingKey.value != null
  );

  async function onSelectSubject(payload: {
    key: string;
    context: GrantContext;
  }) {
    selectingKey.value = payload.key;
    try {
      const ok = await handleSelectSubject(payload.context);
      if (ok) {
        activeKey.value = payload.key;
        groupHint.value = null;
      }
    } finally {
      selectingKey.value = null;
    }
  }

  async function onSelectGroup(payload: { key: string; name: string }) {
    if (!(await confirmDiscardIfDirty())) return;
    selectingKey.value = payload.key;
    try {
      grantStore.resetAll();
      activeKey.value = payload.key;
      groupHint.value = payload.name;
    } finally {
      selectingKey.value = null;
    }
  }

  /**
   * 刷新树 + 预选/当前主体处理（问题 6）。
   * single-flight 防止 onMounted/onActivated/watch 并发重复加载。
   */
  async function refreshAndPreset() {
    if (refreshInFlight.value) return;
    refreshInFlight.value = true;
    try {
      await subjectTreeRef.value?.loadTree();
      const externalId = route.query.roleExternalId;
      const hasQuery = typeof externalId === "string" && externalId;
      const ctx = grantStore.context;
      // 无 query 时才查当前主体角色是否仍在树中（有 query 走 preset 分支）
      const node =
        !hasQuery && ctx
          ? (subjectTreeRef.value?.findNode(
              ctx.roleExternalId,
              ctx.roleTypeCode
            ) ?? null)
          : null;
      const action = decideRefreshAction({
        queryExternalId: externalId,
        context: ctx,
        node
      });
      switch (action.kind) {
        case "preset": {
          const found = subjectTreeRef.value?.findNode(
            action.externalId,
            "BASIC_ROLE"
          );
          if (!found) {
            message("未找到指定角色，请重新选择", { type: "warning" });
            break;
          }
          if (grantStore.context?.roleExternalId === action.externalId) {
            // 同主体 no-op：同步名称 + 消费 query（不重载 baseline，评审问题 5）
            grantStore.syncDisplayName(found.name);
            router.replace({
              query: { ...route.query, roleExternalId: undefined }
            });
          } else {
            // 不同主体：preselect -> handleSelectSubject -> selectSubject + router.replace
            subjectTreeRef.value?.preselect(action.externalId);
          }
          break;
        }
        case "syncName":
          // 角色仍存在：同步改名，不重载 baseline（问题 6）
          grantStore.syncDisplayName(action.displayName);
          // EXTRA_ROLE 选中刷新后高亮转移到顶层 BASIC_ROLE（评审问题 1B）
          if (ctx?.fromGroupRoleName != null && node) {
            activeKey.value = node.key;
          }
          break;
        case "clearSubject":
          // 角色已删除：清空主体 + 提示
          grantStore.resetAll();
          activeKey.value = null;
          message("当前角色已不存在，请重新选择", { type: "warning" });
          break;
        case "none":
          break;
      }
    } finally {
      refreshInFlight.value = false;
    }
  }

  // ========== 授权弹窗（§4；触发：无权限单元格 / 工具栏授权按钮） ==========

  const dialogVisible = ref(false);
  const dialogInitial = ref<{
    operationCode?: string;
    resourceTypeCode?: string;
    resourceCode?: string | null;
    codeType?: string | null;
    scopeMode?: "INSTANCE" | "ALL";
  }>({});

  function openGrantDialog(initial: typeof dialogInitial.value = {}) {
    if (grantStore.capability !== "edit") {
      message("当前为只读视图，无授权权限", { type: "warning" });
      return;
    }
    if (frozen.value) {
      message("正在保存或切换主体，请稍后", { type: "warning" });
      return;
    }
    dialogInitial.value = initial;
    dialogVisible.value = true;
  }

  function handleDialogConfirm(result: DialogResult) {
    const { changes } = applyDialogResultToDraft({
      baseline: grantStore.baseline,
      changes: grantStore.changes,
      dialog: result,
      operations: operationDefs.value
    });
    grantStore.applyChanges(changes);
    dialogVisible.value = false;
  }

  // ========== 详情层（§5；触发：有权限单元格点击） ==========

  const drawerVisible = ref(false);
  /** 详情目标：单元格（资源行 × 操作列） */
  const drawerTarget = ref<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    resourceName: string;
    operationCode: string;
    scopeMode: "INSTANCE" | "ALL";
  } | null>(null);

  function openDetail(target: NonNullable<typeof drawerTarget.value>) {
    drawerTarget.value = target;
    drawerVisible.value = true;
  }

  /** 详情目标分组键下的全部生效记录（MANUAL + AUTO_DEP 并列，§3.3） */
  const drawerRecords = computed<EffectiveRecord[]>(() => {
    const target = drawerTarget.value;
    if (!target) return [];
    return effective.value.mains.filter(record => {
      if (record.resourceTypeCode !== target.resourceTypeCode) return false;
      if ((record.resourceCode ?? null) !== target.resourceCode) return false;
      if ((record.codeType ?? null) !== target.codeType) return false;
      if (record.scopeMode !== target.scopeMode) return false;
      // 操作列命中：operationCode 相等，或组合位记录该列位被覆盖（按位拆解，P1-4）
      if (record.operationCode === target.operationCode) return true;
      if (record.operationCode == null) {
        const merged = mergeOperationsForType(
          operationDefs.value,
          record.resourceTypeCode
        );
        const col = merged.find(c => c.code === target.operationCode);
        if (!col) return false;
        const { coveredSet } = coveredSetOf(record.grantedBits, merged);
        return (coveredSet & col.binaryBit) !== 0n;
      }
      return false;
    });
  });

  /** 子权限（详情层 Tab2）：按父记录 id 分组（含草稿虚拟挂载） */
  function childrenOf(record: EffectiveRecord): EffectiveRecord[] {
    if (record.draftMark === "add" && record.changeId) {
      return (
        effective.value.childrenByParent.get(draftParentKey(record.changeId)) ??
        []
      );
    }
    return (
      effective.value.childrenByParent.get(persistedParentKey(record.id)) ?? []
    );
  }

  // ---- 详情层事件（统一走草稿，保存全部时单请求提交） ----

  function pushChange(change: DraftChange) {
    grantStore.applyChanges([...grantStore.changes, change]);
  }

  /** 添加分支（同键多条件并存；撞已占用条件由组件层 findBranchConflict 前置禁用） */
  function handleAddBranch(input: {
    record: EffectiveRecord;
    conditionCode: string | null;
    canGrant: boolean;
  }) {
    const { record } = input;
    const recordKey = {
      resourceTypeCode: record.resourceTypeCode,
      resourceCode: record.resourceCode,
      codeType: record.codeType,
      operationCode: record.operationCode,
      scopeMode: record.scopeMode,
      conditionCode: input.conditionCode,
      canGrant: input.canGrant
    };
    pushChange(
      buildAddChange({
        recordKey,
        summary: buildSummary({
          recordKey,
          resourceLabel:
            record.resourceName ?? record.resourceCode ?? "全部资源"
        })
      })
    );
  }

  /** 编辑分支（updates 改条件/canGrant；改条件撞已占用条件 → 组件层前置禁用 + 后端 20033） */
  function handleUpdateBranch(input: {
    record: EffectiveRecord;
    canGrant: boolean;
    conditionCode: string | null;
  }) {
    const { record } = input;
    if (record.grantSource === "AUTO_DEP") {
      message("自动补全记录只读，不可修改或删除", { type: "warning" });
      return;
    }
    // 草稿新增就地改（不产生 update 变更）
    if (record.draftMark === "add" && record.changeId) {
      grantStore.applyChanges(
        grantStore.changes.map(c =>
          c.changeId === record.changeId && c.kind === "add"
            ? {
                ...c,
                recordKey: {
                  ...c.recordKey,
                  canGrant: input.canGrant,
                  conditionCode: input.conditionCode
                }
              }
            : c
        )
      );
      return;
    }
    // 已有 update 变更就地改 after
    const existing = grantStore.changes.find(
      (c): c is Extract<DraftChange, { kind: "update" }> =>
        c.kind === "update" && c.recordId === record.id
    );
    if (existing) {
      grantStore.applyChanges(
        grantStore.changes.map(c =>
          c.changeId === existing.changeId && c.kind === "update"
            ? {
                ...c,
                after: {
                  canGrant: input.canGrant,
                  conditionCode: input.conditionCode
                }
              }
            : c
        )
      );
      return;
    }
    pushChange(
      buildUpdateChange({
        before: record,
        after: { canGrant: input.canGrant, conditionCode: input.conditionCode },
        summary: buildSummary({
          recordKey: {
            ...record,
            conditionCode: input.conditionCode,
            canGrant: input.canGrant
          },
          resourceLabel:
            record.resourceName ?? record.resourceCode ?? "全部资源"
        })
      })
    );
  }

  /** 级联清理主权限下草稿子权限变更（主权限删除/替换时，§6.4 静默丢弃前置到草稿层） */
  function dropStaleChildrenChanges(parentId: number) {
    const staleIds = grantStore.changes
      .filter(
        c =>
          (c.kind === "add" && c.parentPermissionId === parentId) ||
          (c.kind === "update" && c.before.dependOn === parentId)
      )
      .map(c => c.changeId);
    if (staleIds.length > 0) {
      const stale = new Set(staleIds);
      grantStore.applyChanges(
        grantStore.changes.filter(c => !stale.has(c.changeId))
      );
    }
  }

  /** 删除分支/子权限（= remove；主权限级联删子在清单提示） */
  function handleDeleteRecord(record: EffectiveRecord) {
    if (record.grantSource === "AUTO_DEP") {
      message("自动补全记录只读，不可修改或删除", { type: "warning" });
      return;
    }
    // 草稿新增 → 撤销对应 AddChange（级联撤销其虚拟挂载子权限）
    if (record.draftMark === "add" && record.changeId) {
      grantStore.revertChange(record.changeId);
      return;
    }
    if (record.dependOn == null) {
      dropStaleChildrenChanges(record.id);
    }
    pushChange(
      buildRemoveChange({
        records: [record],
        cascadeChildCount: record.childCount ?? 0,
        reason: "detail-delete",
        summary: buildSummary({
          recordKey: record,
          resourceLabel:
            record.resourceName ?? record.resourceCode ?? "全部资源"
        })
      })
    );
  }

  /** 添加子权限（creates parentPermissionId 挂已存在父 / parentChangeId 虚拟挂载草稿父） */
  function handleAddChild(input: {
    parent: EffectiveRecord;
    recordKey: {
      resourceTypeCode: string;
      resourceCode: string | null;
      codeType: string | null;
      operationCode: string | null;
      scopeMode: "INSTANCE" | "ALL";
      conditionCode: string | null;
      canGrant: boolean;
    };
    resourceLabel: string;
  }) {
    const { parent, recordKey } = input;
    const change: AddChange = buildAddChange({
      recordKey,
      ...(parent.draftMark === "add" && parent.changeId
        ? { parentChangeId: parent.changeId }
        : { parentPermissionId: parent.id }),
      summary: buildSummary({ recordKey, resourceLabel: input.resourceLabel })
    });
    pushChange(change);
  }

  /** 子权限跨键变更 = 移除 + 新建（removes+creates 同事务原子，子权限不迁移） */
  function handleReplaceChild(input: {
    record: EffectiveRecord;
    newKey: Parameters<typeof buildReplaceChange>[0]["newKey"];
    resourceLabel: string;
  }) {
    const { record, newKey } = input;
    if (record.grantSource === "AUTO_DEP") {
      message("自动补全记录只读，不可修改或删除", { type: "warning" });
      return;
    }
    // 草稿新增记录的跨键编辑 → 就地改 AddChange.recordKey（未持久化，无 removes）
    if (record.draftMark === "add" && record.changeId) {
      grantStore.applyChanges(
        grantStore.changes.map(c =>
          c.changeId === record.changeId && c.kind === "add"
            ? {
                ...c,
                recordKey: newKey,
                summary: buildSummary({
                  recordKey: newKey,
                  resourceLabel: input.resourceLabel
                })
              }
            : c
        )
      );
      return;
    }
    if (record.dependOn == null) {
      // 主权限跨键变更（范围/资源/操作变化）——同样 removes+creates，子权限不迁移
      dropStaleChildrenChanges(record.id);
      pushChange(
        buildReplaceChange({
          removedRecords: [record],
          newKey,
          cascadeChildCount: record.childCount ?? 0,
          summary: buildSummary({
            recordKey: newKey,
            resourceLabel: input.resourceLabel
          })
        })
      );
      return;
    }
    pushChange(
      buildReplaceChange({
        removedRecords: [record],
        newKey,
        parentPermissionId: record.dependOn,
        cascadeChildCount: 0,
        summary: buildSummary({
          recordKey: newKey,
          resourceLabel: input.resourceLabel
        })
      })
    );
  }

  // ========== 保存 / 放弃（§6.4 单入口） ==========

  async function handleSaveAll() {
    const ok = await grantStore.saveAll();
    if (ok) {
      message("保存成功", { type: "success" });
    } else if (grantStore.submit.kind === "saveFailed") {
      // 失败条目保留标红、整体重试；文案经错误码映射（DoD-1）
      message(grantStore.submit.message, { type: "error" });
    }
  }

  async function handleRevertAll() {
    if (!grantStore.isDirty) return;
    try {
      await ElMessageBox.confirm(
        `确认放弃全部 ${grantStore.changeCount} 条未保存变更？`,
        "放弃全部",
        { confirmButtonText: "放弃", cancelButtonText: "取消", type: "warning" }
      );
    } catch {
      return;
    }
    grantStore.revertAll();
  }

  // ========== 变更清单定位（§6.3：滚动矩阵到对应单元格并闪烁） ==========

  const locateRequest = ref<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    operationCode: string | null;
    ts: number;
  } | null>(null);

  function handleLocateChange(change: DraftChange) {
    const summary = change.summary;
    locateRequest.value = {
      resourceTypeCode: summary.resourceTypeCode,
      resourceCode: summary.resourceCode,
      codeType: summary.codeType,
      operationCode: summary.operationCode,
      ts: Date.now()
    };
  }

  // ========== 离开保护（§6.5：路由切换/刷新/切换主体 → 确认提示） ==========

  onBeforeRouteLeave(async () => {
    // saving 优先拦截（评审问题 1）：请求已发出不可放弃，禁止离开且不提供放弃选项
    if (grantStore.isSaving) {
      message("正在保存，禁止离开", { type: "warning" });
      return false;
    }
    // 未保存离开拦截（§6.5）：确认放弃 → 清空草稿放行（keepAlive 下组件不销毁，需显式回滚）
    if (!grantStore.isDirty) return true;
    const ok = await confirmDiscardIfDirty();
    if (ok) grantStore.revertAll();
    return ok;
  });

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    // dirty 或 saving 均拦截（评审问题 1：saving 期间刷新/关闭也禁止；returnValue 兼容浏览器）
    if (grantStore.isDirty || grantStore.isSaving) {
      event.preventDefault();
      event.returnValue = "";
    }
  }

  // ========== 生命周期 ==========

  onMounted(() => {
    loadDeps();
    window.addEventListener("beforeunload", handleBeforeUnload);
    // 首次加载树（onActivated 也会触发，single-flight 去重，问题 6）
    refreshAndPreset();
  });

  onActivated(() => {
    // keep-alive 重入刷新树（覆盖新建/改名/删除/层级/关联变化，问题 6）
    refreshAndPreset();
  });

  // 已激活时 query 更新（角色管理页跳转）：预选（问题 6；空 query 忽略）
  watch(
    () => route.query.roleExternalId,
    externalId => {
      if (typeof externalId !== "string" || !externalId) return;
      refreshAndPreset();
    },
    { flush: "post" }
  );

  onBeforeUnmount(() => {
    window.removeEventListener("beforeunload", handleBeforeUnload);
    grantStore.resetAll();
  });

  return {
    // 主体/门控
    subjectType,
    canView,
    canManage,
    canCondition,
    canResource,
    canOperation,
    grantStore,
    // 依赖数据
    resourceForest,
    operationDefs,
    conditions,
    depsLoading,
    // 查看态
    includeResourceInherit,
    includeOpInherit,
    resourceKeyword,
    hiddenColumnCodes,
    // 派生
    effective,
    sourceChain,
    matrixTypeCodes,
    unionColumns,
    visibleColumns,
    // 主体选择
    handleSelectSubject,
    confirmDiscardIfDirty,
    subjectTreeRef,
    activeKey,
    selectingKey,
    groupHint,
    onSelectSubject,
    onSelectGroup,
    frozen,
    // 弹窗
    dialogVisible,
    dialogInitial,
    openGrantDialog,
    handleDialogConfirm,
    // 详情层
    drawerVisible,
    drawerTarget,
    drawerRecords,
    openDetail,
    childrenOf,
    handleAddBranch,
    handleUpdateBranch,
    handleDeleteRecord,
    handleAddChild,
    handleReplaceChild,
    // 保存/放弃
    handleSaveAll,
    handleRevertAll,
    // 定位
    locateRequest,
    handleLocateChange,
    // 单元格工具（组件层复用）
    cellDraftMark,
    findBranchConflict,
    groupKeyOf
  };
}
