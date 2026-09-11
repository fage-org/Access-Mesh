/**
 * 草稿 → 提交计划（apply-grant-plan）构建与草稿生效视图（纯函数）。
 * 设计依据：docs/design/frontend/permission-grant.md §4（单记录模型/确定语义）/ §6（草稿模型/统一提交）。
 *
 * 单记录模型：
 * - 直接授权键 = (resourceTypeCode, resourceCode, codeType, operationKey, scopeMode, parent)，
 *   operationKey = operationCode ?? "bits:"+grantedBits（组合位记录以位串为键）；
 * - 同一直接授权键只允许一条 MANUAL 记录，conditionCode/canGrant 是可变属性；
 * - 匹配范围仅限 MANUAL：直接授权键比对/草稿 diff 只匹配 grantSource=MANUAL 记录，
 *   AUTO_DEP 记录不进比对（只读），同键并存不冲突。
 */
import type {
  GrantPlan,
  GrantPlanCreate,
  GrantRecordKey,
  InlineConditionDef,
  RolePermissionItem
} from "@/api/permission-grant";
import type {
  AddChange,
  ChangeSummary,
  DraftChange,
  RemoveChange,
  ReplaceChange,
  UpdateChange
} from "./types";
import { nextChangeId } from "./types";
import { mergeOperationsForType, type OperationDefInput } from "./source-chain";
import { toBigIntBits } from "./bits";

// ========== 单记录键模型 ==========

type KeyLike = {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string | null;
  scopeMode: string;
  grantedBits?: string;
};

/** 直接授权键（operationKey = operationCode ?? "bits:"+grantedBits，P1-4） */
export function groupKeyOf(k: KeyLike): string {
  const operationKey = k.operationCode ?? `bits:${k.grantedBits ?? ""}`;
  return [
    k.resourceTypeCode,
    k.resourceCode ?? "",
    k.codeType ?? "",
    operationKey,
    k.scopeMode
  ].join("|");
}

/** 资源维度分组键（弹窗全量比对按资源聚合：resourceTypeCode + resourceCode + codeType） */
export function resourceGroupKeyOf(k: {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
}): string {
  return [k.resourceTypeCode, k.resourceCode ?? "", k.codeType ?? ""].join("|");
}

/**
 * 子权限不承载条件与再授予语义。
 *
 * 子权限编辑器与弹窗编排层都调用该函数：前者构造请求，后者作为提交前兜底，
 * 避免旧表单字段或调用方绕过界面后把主权限属性带入子权限。
 */
export function normalizeChildGrantKey(key: GrantRecordKey): GrantRecordKey {
  return {
    ...key,
    conditionCode: null,
    inlineCondition: null,
    canGrant: false
  };
}

// ========== 草稿生效视图（baseline + draft → 展示用记录集） ==========

/** 生效记录（baseline 叠加草稿标记；新增记录持负数月临时 id，与持久化 id 区分） */
export type EffectiveRecord = RolePermissionItem & {
  draftMark: "add" | "update" | "remove" | null;
  changeId?: string;
  /** 内联条件定义（T-PERM-048）：草稿 add/applyFocusEdit 后的内联绑定展示用；
   *  baseline 记录绑定内联时为 null（展示回退 conditions 按 code 查找——code 为 inline- 前缀） */
  inlineCondition?: InlineConditionDef | null;
};

export type DraftAppliedView = {
  /** 主权限（dependOn==null；含 remove 标记记录——矩阵删除线展示需要） */
  mains: EffectiveRecord[];
  /** 子权限按父分组：`id:{parentId}` 持久化父 / `chg:{parentChangeId}` 草稿新父 */
  childrenByParent: Map<string, EffectiveRecord[]>;
  /** 记录 id（含临时 id）→ 草稿标记（单元格 diff 标记用） */
  markInfo: Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >;
  /** 被移除/被替换的持久化 id 集合 */
  removedIds: Set<number>;
};

/** 草稿父的子权限分组键（详情层虚拟挂载） */
export function draftParentKey(changeId: string): string {
  return `chg:${changeId}`;
}

/** 持久化父的子权限分组键 */
export function persistedParentKey(parentId: number): string {
  return `id:${parentId}`;
}

/**
 * 解析新增记录 grantedBits（operationCode → 合并列 binaryBit 十进制字符串）。
 * 本页不支持组合位新增（设计 §12 注），新增记录 operationCode 必传；
 * 解析不到定义时兜底 "0"（不点亮任何列，防御性）。
 */
export function resolveGrantedBits(
  recordKey: GrantRecordKey,
  operations: OperationDefInput[]
): string {
  if (recordKey.operationCode == null) return "0";
  const merged = mergeOperationsForType(operations, recordKey.resourceTypeCode);
  const col = merged.find(c => c.code === recordKey.operationCode);
  return col ? col.binaryBit.toString(10) : "0";
}

/** baseline + draft → 生效记录视图（矩阵来源链输入 / 详情层子权限列表） */
export function applyDraftToRecords(input: {
  baseline: RolePermissionItem[];
  changes: DraftChange[];
  operations: OperationDefInput[];
}): DraftAppliedView {
  const { baseline, changes, operations } = input;
  const removedIds = new Set<number>();
  const updateById = new Map<number, UpdateChange>();
  const markInfo = new Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >();

  for (const change of changes) {
    if (change.kind === "remove") {
      for (const record of change.records) {
        removedIds.add(record.id);
        markInfo.set(record.id, { mark: "remove", changeId: change.changeId });
      }
    } else if (change.kind === "replace") {
      for (const record of change.removedRecords) {
        removedIds.add(record.id);
        markInfo.set(record.id, { mark: "remove", changeId: change.changeId });
      }
    } else if (change.kind === "update") {
      updateById.set(change.recordId, change);
      markInfo.set(change.recordId, {
        mark: "update",
        changeId: change.changeId
      });
    }
  }

  // 新增记录临时 id（负数，避免与持久化 id 冲突；同一次 applyDraft 内确定）
  let tempIdSeq = -1;
  const addToEffective = (
    change: AddChange | ReplaceChange,
    recordKey: GrantRecordKey,
    dependOn: number | null
  ): EffectiveRecord => {
    const id = tempIdSeq--;
    markInfo.set(id, { mark: "add", changeId: change.changeId });
    const childAdds = changes.filter(
      (c): c is AddChange =>
        c.kind === "add" && c.parentChangeId === change.changeId
    );
    return {
      id,
      resourceTypeCode: recordKey.resourceTypeCode,
      resourceCode: recordKey.resourceCode,
      codeType: recordKey.codeType,
      resourceName: null,
      operationCode: recordKey.operationCode,
      canGrant: recordKey.canGrant ?? false,
      conditionCode: recordKey.conditionCode,
      scopeMode: recordKey.scopeMode,
      dependOn,
      grantSource: "MANUAL",
      grantedBits: resolveGrantedBits(recordKey, operations),
      createdAt: "",
      childCount: dependOn == null ? childAdds.length : 0,
      draftMark: "add",
      changeId: change.changeId,
      inlineCondition: recordKey.inlineCondition ?? null
    };
  };

  const mains: EffectiveRecord[] = [];
  const childrenByParent = new Map<string, EffectiveRecord[]>();

  const pushChild = (key: string, record: EffectiveRecord) => {
    const list = childrenByParent.get(key) ?? [];
    list.push(record);
    childrenByParent.set(key, list);
  };

  // baseline 主/子权限叠加草稿标记
  for (const record of baseline) {
    const mark = markInfo.get(record.id);
    const update = updateById.get(record.id);
    const effective: EffectiveRecord = {
      ...record,
      canGrant: update ? update.after.canGrant : record.canGrant,
      conditionCode: update ? update.after.conditionCode : record.conditionCode,
      draftMark: mark?.mark ?? null,
      changeId: mark?.changeId,
      inlineCondition: update ? update.after.inlineCondition ?? null : null
    };
    if (record.dependOn == null) {
      mains.push(effective);
    } else {
      pushChild(persistedParentKey(record.dependOn), effective);
    }
  }

  // 草稿新增（主权限 / 子权限 / 替换新键）
  for (const change of changes) {
    if (change.kind === "add") {
      if (change.parentChangeId) {
        // 挂草稿新父（虚拟挂载）→ 详情层子权限分组
        pushChild(
          draftParentKey(change.parentChangeId),
          addToEffective(change, change.recordKey, null)
        );
      } else if (change.parentPermissionId != null) {
        // 挂已存在父；父被移除时静默丢弃（§6.4 子权限草稿随主权限删除丢弃）
        if (removedIds.has(change.parentPermissionId)) continue;
        pushChild(
          persistedParentKey(change.parentPermissionId),
          addToEffective(change, change.recordKey, change.parentPermissionId)
        );
      } else {
        mains.push(addToEffective(change, change.recordKey, null));
      }
    } else if (change.kind === "replace") {
      if (change.parentPermissionId != null) {
        if (removedIds.has(change.parentPermissionId)) continue;
        pushChild(
          persistedParentKey(change.parentPermissionId),
          addToEffective(change, change.newKey, change.parentPermissionId)
        );
      } else {
        mains.push(addToEffective(change, change.newKey, null));
      }
    }
  }

  return { mains, childrenByParent, markInfo, removedIds };
}

// ========== 提交计划构建（§6.4 单入口） ==========

/**
 * 草稿变更 → apply-grant-plan 记录级 plan。
 * 规则：
 * - 新增主权限带 children 一次性建树（挂草稿父的子权限并入父 create 的 children）；
 * - parentPermissionId 仅引用提交前已存在父；父在本批被移除时该子权限草稿静默丢弃；
 * - 删除主权限 = removes 主记录 id（后端级联删子），其下子权限草稿（update/remove/add）静默丢弃；
 * - updates 仅携带实际变更字段（canGrant / conditionCode 三态：清除映射为 ""）；
 * - 跨键替换 = removes 旧 + creates 新（同一 plan 原子执行，子权限不迁移）。
 */
export function buildGrantPlan(changes: DraftChange[]): GrantPlan | null {
  const removedIds = new Set<number>();
  const dependOnById = new Map<number, number | null>();
  for (const change of changes) {
    if (change.kind === "remove") {
      for (const record of change.records) {
        removedIds.add(record.id);
        dependOnById.set(record.id, record.dependOn);
      }
    } else if (change.kind === "replace") {
      for (const record of change.removedRecords) {
        removedIds.add(record.id);
        dependOnById.set(record.id, record.dependOn);
      }
    }
  }
  // 删除主权限 = removes 主记录 id（后端级联删子）：
  // 子权限单条 remove 若其父也在本批 removes 中 → 静默丢弃（级联覆盖，§6.4）
  const isCascadeCovered = (id: number): boolean => {
    const parent = dependOnById.get(id);
    return parent != null && removedIds.has(parent);
  };
  const removes: number[] = [...removedIds].filter(id => !isCascadeCovered(id));
  // 子权限草稿（update/add）父被本批移除 → 静默丢弃
  const isChildOfRemoved = (dependOn: number | null): boolean =>
    dependOn != null && removedIds.has(dependOn);

  const creates: GrantPlanCreate[] = [];
  const updates: NonNullable<GrantPlan["updates"]> = [];

  for (const change of changes) {
    if (change.kind === "add") {
      if (change.parentChangeId) continue; // 并入父 create.children
      if (
        change.parentPermissionId != null &&
        removedIds.has(change.parentPermissionId)
      ) {
        continue; // 父被本批移除 → 静默丢弃
      }
      const children = changes
        .filter(
          (c): c is AddChange =>
            c.kind === "add" && c.parentChangeId === change.changeId
        )
        .map(c => c.recordKey);
      creates.push({
        key: change.recordKey,
        ...(change.parentPermissionId != null
          ? { parentPermissionId: change.parentPermissionId }
          : {}),
        ...(children.length ? { children } : {})
      });
    } else if (change.kind === "replace") {
      if (
        change.parentPermissionId != null &&
        removedIds.has(change.parentPermissionId)
      ) {
        continue;
      }
      creates.push({
        key: change.newKey,
        ...(change.parentPermissionId != null
          ? { parentPermissionId: change.parentPermissionId }
          : {})
      });
    } else if (change.kind === "update") {
      if (removedIds.has(change.recordId)) continue; // 自身被移除
      if (isChildOfRemoved(change.before.dependOn)) continue; // 随主权限级联删除
      const item: {
        id: number;
        canGrant?: boolean;
        conditionCode?: string;
        inlineCondition?: InlineConditionDef | null;
      } = { id: change.recordId };
      if (change.before.canGrant !== change.after.canGrant) {
        item.canGrant = change.after.canGrant;
      }
      if (change.after.inlineCondition != null) {
        // T-PERM-048 内联轨：最终条件为该内联定义（后端语义——现绑 INLINE 就地编辑 /
        // 现绑 null/MANAGED 新建换绑）；与 conditionCode 互斥不并发
        item.inlineCondition = change.after.inlineCondition;
      } else if (
        change.before.conditionCode !== change.after.conditionCode
      ) {
        // 三态：null → 清除映射为 ""；非空 → 覆盖（值域=MANAGED）
        item.conditionCode = change.after.conditionCode ?? "";
      }
      if (
        item.canGrant !== undefined ||
        item.conditionCode !== undefined ||
        item.inlineCondition != null
      ) {
        updates.push(item);
      }
    }
  }

  if (creates.length === 0 && updates.length === 0 && removes.length === 0) {
    return null;
  }
  return {
    ...(creates.length ? { creates } : {}),
    ...(updates.length ? { updates } : {}),
    ...(removes.length ? { removes } : {})
  };
}

// ========== 变更构造辅助 ==========

/** 构造变更摘要 */
export function buildSummary(input: {
  recordKey: GrantRecordKey;
  resourceLabel: string;
  conditionName?: string | null;
}): ChangeSummary {
  const { recordKey } = input;
  const conditionLabel = recordKey.conditionCode
    ?? (recordKey.inlineCondition != null
      ? `内联:${recordKey.inlineCondition.name}`
      : "无条件");
  return {
    operationCode: recordKey.operationCode,
    conditionCode: recordKey.conditionCode,
    canGrant: recordKey.canGrant ?? false,
    scopeMode: recordKey.scopeMode,
    label: `${input.resourceLabel} · ${recordKey.operationCode ?? "组合位"} · ${conditionLabel}`,
    resourceLabel: input.resourceLabel,
    resourceTypeCode: recordKey.resourceTypeCode,
    resourceCode: recordKey.resourceCode,
    codeType: recordKey.codeType
  };
}

export function buildAddChange(input: {
  recordKey: GrantRecordKey;
  parentPermissionId?: number;
  parentChangeId?: string;
  summary: ChangeSummary;
}): AddChange {
  return {
    kind: "add",
    changeId: nextChangeId(),
    recordKey: input.recordKey,
    ...(input.parentPermissionId != null
      ? { parentPermissionId: input.parentPermissionId }
      : {}),
    ...(input.parentChangeId ? { parentChangeId: input.parentChangeId } : {}),
    summary: input.summary
  };
}

export function buildUpdateChange(input: {
  before: RolePermissionItem;
  after: {
    canGrant: boolean;
    conditionCode: string | null;
    inlineCondition?: InlineConditionDef | null;
  };
  summary: ChangeSummary;
}): UpdateChange {
  return {
    kind: "update",
    changeId: nextChangeId(),
    recordId: input.before.id,
    before: input.before,
    after: input.after,
    summary: input.summary
  };
}

export function buildRemoveChange(input: {
  records: RolePermissionItem[];
  cascadeChildCount: number;
  reason: "dialog-uncheck" | "detail-delete";
  summary: ChangeSummary;
}): RemoveChange {
  return {
    kind: "remove",
    changeId: nextChangeId(),
    records: input.records,
    cascadeChildCount: input.cascadeChildCount,
    reason: input.reason,
    summary: input.summary
  };
}

export function buildReplaceChange(input: {
  removedRecords: RolePermissionItem[];
  newKey: GrantRecordKey;
  parentPermissionId?: number;
  cascadeChildCount: number;
  summary: ChangeSummary;
}): ReplaceChange {
  return {
    kind: "replace",
    changeId: nextChangeId(),
    removedRecords: input.removedRecords,
    newKey: input.newKey,
    ...(input.parentPermissionId != null
      ? { parentPermissionId: input.parentPermissionId }
      : {}),
    cascadeChildCount: input.cascadeChildCount,
    summary: input.summary
  };
}

// ========== 弹窗确定语义（§4 + 方案二全量比对：勾选=最终授权状态，取消勾选=撤权） ==========
// 🔧 T-FE-040 v3.1：属性为记录级——勾选仅表达授权存在性（add 使用默认属性），
// conditionCode/canGrant 的修改只作用于聚焦记录（focusSlot + focusAttributes）。

/** 授权弹窗单个范围结果（一次确认可顺序包含 ALL 撤销 + INSTANCE 最终集合）。 */
export type DialogResult = {
  /** 操作定义必属某类型（全局操作概念已退役） */
  operation: { code: string; resourceTypeCode: string };
  scopeMode: "INSTANCE" | "ALL";
  /** INSTANCE：勾选资源集合；ALL：单元素（resourceTypeCode 目标类型，resourceCode/codeType 为 null） */
  resources: Array<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    name: string;
  }>;
  /** 授权设置未被用户修改时保持原属性的已有资源；仅参与最终集合防撤销。 */
  untouchedResourceKeys?: string[];
  /** 🔧 T-FE-040 v3.1：聚焦槽位（记录级编辑；缺省 = 无聚焦，不产生属性变更） */
  focusSlot?: FocusSlot;
  /** 聚焦记录的目标属性（null = 清除条件；undefined/缺省 = 属性未修改） */
  focusAttributes?: SlotAttributes | null;
  /** v3 兼容保留：add 初始属性（v3.1 下 add 统一默认属性，本字段不再影响勾选资源） */
  conditionCode: string | null;
  canGrant: boolean;
};

export type DialogApplyResult = {
  changes: DraftChange[];
  /** 被撤销的变更 id（重新勾选恢复 / 取消草稿新增） */
  revertedChangeIds: string[];
};

/**
 * 弹窗确定 → 草稿 diff（方案二全量语义，匹配范围仅限 MANUAL 主权限）：
 * - 勾选资源：直接授权键不存在 → add；已存在 → 直接更新 conditionCode/canGrant；
 * - 取消勾选的已有记录 → remove（主权限级联删子在清单提示）；
 * - 重新勾选已标记 remove 的记录 → 撤销对应 RemoveChange（恢复原记录，保留子权限）；
 * - AUTO_DEP 记录不进比对（只读，同键并存不冲突）。
 */
export function applyDialogResultToDraft(input: {
  baseline: RolePermissionItem[];
  changes: DraftChange[];
  dialog: DialogResult;
  operations: OperationDefInput[];
}): DialogApplyResult {
  // 条件不可转授（🔧 T-FE-039/T-PERM-041）：弹窗结果 conditionCode 非空时 canGrant 强制 false。
  // 纯函数层最终状态兜底——组件互斥/调度层 enforce 之外的统一防线，草稿不变量恒成立。
  const { baseline, dialog: rawDialog } = input;
  const dialog =
    rawDialog.conditionCode != null
      ? { ...rawDialog, canGrant: false }
      : rawDialog;
  const operations = input.operations;
  let changes = [...input.changes];
  const revertedChangeIds: string[] = [];

  const revertChange = (changeId: string) => {
    changes = changes.filter(c => c.changeId !== changeId);
    revertedChangeIds.push(changeId);
  };

  // 生效视图（含草稿标记）
  const view = applyDraftToRecords({ baseline, changes, operations });

  // 本弹窗比对集（问题 2）：MANUAL 主权限 + 同操作码 + 同范围 + 类型限定
  // （操作定义按类型隔离——全局操作概念已退役，一律限所属类型；
  // remove 标记视同不存在）
  const comparable = view.mains.filter(record => {
    if (record.grantSource !== "MANUAL") return false;
    if (record.draftMark === "remove") return false;
    if (record.operationCode !== dialog.operation.code) return false;
    if (record.scopeMode !== dialog.scopeMode) return false;
    return record.resourceTypeCode === dialog.operation.resourceTypeCode;
  });
  const byResource = new Map<string, EffectiveRecord[]>();
  for (const record of comparable) {
    const key = resourceGroupKeyOf(record);
    const list = byResource.get(key) ?? [];
    list.push(record);
    byResource.set(key, list);
  }

  const selectedKeys = new Set(
    dialog.resources.map(r => resourceGroupKeyOf(r))
  );
  const untouchedResourceKeys = new Set(dialog.untouchedResourceKeys ?? []);

  // ---- 勾选集合：add / 恢复（v3.1：仅表达授权存在性，属性修改由 focusAttributes 驱动） ----
  for (const resource of dialog.resources) {
    const resKey = resourceGroupKeyOf(resource);
    // 授权设置未修改：树上只表达最终授权状态，不重写已有记录属性。
    if (untouchedResourceKeys.has(resKey)) continue;

    // 重新勾选：撤销命中本资源的 RemoveChange（恢复原记录与子权限）
    for (const change of [...changes]) {
      if (
        change.kind === "remove" &&
        change.reason === "dialog-uncheck" &&
        change.records.some(r => resourceGroupKeyOf(r) === resKey)
      ) {
        revertChange(change.changeId);
      }
    }

    // 撤销后重算本资源直接授权（含恢复的记录）。后端唯一约束保证最多一条 MANUAL。
    const restoredView = applyDraftToRecords({ baseline, changes, operations });
    const directRecords = restoredView.mains.filter(
      m =>
        m.grantSource === "MANUAL" &&
        m.draftMark !== "remove" &&
        m.operationCode === dialog.operation.code &&
        m.scopeMode === dialog.scopeMode &&
        resourceGroupKeyOf(m) === resKey
    );
    const matched = directRecords[0];
    if (matched) {
      // v3.1：勾选不重写已有记录属性（保持原属性；属性修改仅作用于聚焦记录）
      continue;
    }
    // 直接授权键不存在 → add（默认属性：无条件、不可再授予）
    const recordKey: GrantRecordKey = {
      resourceTypeCode: resource.resourceTypeCode,
      resourceCode: resource.resourceCode,
      codeType: resource.codeType,
      operationCode: dialog.operation.code,
      scopeMode: dialog.scopeMode,
      conditionCode: null,
      canGrant: false
    };
    const summary = buildSummary({ recordKey, resourceLabel: resource.name });
    changes.push(buildAddChange({ recordKey, summary }));
  }

  // ---- 记录级属性编辑（v3.1）：仅聚焦记录生效（update / add 就地改 / 归一化） ----
  if (dialog.focusSlot && dialog.focusAttributes != null) {
    const rawAttributes = dialog.focusAttributes;
    // 条件不可转授兜底（与 reducer 一致）
    const attributes: SlotAttributes =
      rawAttributes.conditionCode != null
        ? { ...rawAttributes, canGrant: false }
        : rawAttributes;
    const focusView = applyDraftToRecords({ baseline, changes, operations });
    const focusRecord = findSlotRecord(focusView, dialog.focusSlot);
    if (focusRecord) {
      if (focusRecord.draftMark === "add" && focusRecord.changeId) {
        changes = changes.map(c =>
          c.changeId === focusRecord.changeId && c.kind === "add"
            ? {
                ...c,
                recordKey: {
                  ...c.recordKey,
                  conditionCode: attributes.conditionCode,
                  inlineCondition: attributes.inlineCondition ?? null,
                  canGrant: attributes.canGrant
                },
                summary: buildSummary({
                  recordKey: {
                    ...c.recordKey,
                    conditionCode: attributes.conditionCode,
                    inlineCondition: attributes.inlineCondition ?? null,
                    canGrant: attributes.canGrant
                  },
                  resourceLabel:
                    focusRecord.resourceName ??
                    dialog.focusSlot.resourceCode ??
                    "全部资源"
                })
              }
            : c
        );
      } else if (focusRecord.draftMark === "update" && focusRecord.changeId) {
        changes = changes.map(c =>
          c.changeId === focusRecord.changeId && c.kind === "update"
            ? {
                ...c,
                after: {
                  conditionCode: attributes.conditionCode,
                  inlineCondition: attributes.inlineCondition ?? null,
                  canGrant: attributes.canGrant
                },
                summary: buildSummary({
                  recordKey: {
                    resourceTypeCode: focusRecord.resourceTypeCode,
                    resourceCode: focusRecord.resourceCode,
                    codeType: focusRecord.codeType,
                    operationCode: focusRecord.operationCode,
                    scopeMode: focusRecord.scopeMode,
                    conditionCode: attributes.conditionCode,
                    inlineCondition: attributes.inlineCondition ?? null,
                    canGrant: attributes.canGrant
                  },
                  resourceLabel:
                    focusRecord.resourceName ??
                    dialog.focusSlot.resourceCode ??
                    "全部资源"
                })
              }
            : c
        );
      } else if (
        focusRecord.canGrant !== attributes.canGrant ||
        focusRecord.conditionCode !== attributes.conditionCode ||
        attributes.inlineCondition != null
      ) {
        changes.push(
          buildUpdateChange({
            before: focusRecord,
            after: {
              canGrant: attributes.canGrant,
              conditionCode: attributes.conditionCode,
              inlineCondition: attributes.inlineCondition ?? null
            },
            summary: buildSummary({
              recordKey: {
                resourceTypeCode: focusRecord.resourceTypeCode,
                resourceCode: focusRecord.resourceCode,
                codeType: focusRecord.codeType,
                operationCode: focusRecord.operationCode,
                scopeMode: focusRecord.scopeMode,
                conditionCode: attributes.conditionCode,
                inlineCondition: attributes.inlineCondition ?? null,
                canGrant: attributes.canGrant
              },
              resourceLabel:
                focusRecord.resourceName ??
                dialog.focusSlot.resourceCode ??
                "全部资源"
            })
          })
        );
      }
    }
  }

  // ---- 取消勾选：撤权 ----
  for (const [resKey, records] of byResource) {
    if (selectedKeys.has(resKey)) continue;
    const baselineRecords = records.filter(r => r.draftMark !== "add");
    const draftAddRecords = records.filter(
      r => r.draftMark === "add" && r.changeId
    );
    // 草稿新增被取消勾选 → 直接撤销对应 AddChange
    for (const record of draftAddRecords) {
      revertChange(record.changeId!);
    }
    if (baselineRecords.length > 0) {
      // 既有 update 变更随删除失效（先撤销，避免 updates/removes 同 id 交叉）
      for (const record of baselineRecords) {
        if (record.draftMark === "update" && record.changeId) {
          revertChange(record.changeId);
        }
      }
      const cascadeChildCount = baselineRecords.reduce(
        (acc, r) => acc + (r.childCount ?? 0),
        0
      );
      const first = baselineRecords[0];
      changes.push(
        buildRemoveChange({
          // EffectiveRecord 结构上兼容 RolePermissionItem（draftMark/changeId 为附加展示字段）
          records: baselineRecords,
          cascadeChildCount,
          reason: "dialog-uncheck",
          summary: buildSummary({
            recordKey: {
              resourceTypeCode: first.resourceTypeCode,
              resourceCode: first.resourceCode,
              codeType: first.codeType,
              operationCode: first.operationCode,
              scopeMode: first.scopeMode,
              conditionCode: null,
              canGrant: false
            },
            resourceLabel:
              first.resourceName ?? first.resourceCode ?? "全部资源"
          })
        })
      );
    }
  }

  // 清理：update 变更回退为无差异（after == before）时剔除（清单幽灵条目；
  // 内联在场恒有效——见 normalizeNoopUpdates 注释）
  changes = normalizeNoopUpdates(changes);

  return { changes, revertedChangeIds };
}

/**
 * 一次弹窗确认的有序批量结果。
 * 典型场景：先撤销当前类型 ALL，再以 INSTANCE 资源树最终集合继续计算草稿。
 */
export function applyDialogResultsToDraft(input: {
  baseline: RolePermissionItem[];
  changes: DraftChange[];
  dialogs: DialogResult[];
  operations: OperationDefInput[];
}): DialogApplyResult {
  let changes = input.changes;
  const revertedChangeIds: string[] = [];
  for (const dialog of input.dialogs) {
    const result = applyDialogResultToDraft({
      baseline: input.baseline,
      changes,
      dialog,
      operations: input.operations
    });
    changes = result.changes;
    revertedChangeIds.push(...result.revertedChangeIds);
  }
  return { changes, revertedChangeIds };
}

// ========== 单元格 diff 标记（§6.2） ==========

export type CellDraftMark =
  | "add" // 整格新增（绿色高亮 + ＋）
  | "partial-add" // 已有格新增来源（＋角标）
  | "update" // 内容变化（黄色角标）
  | "partial-remove" // 部分移除（⧄ 角标，仍有其他有效来源）
  | "remove" // 整格移除（删除线 + 淡出）
  | null;

/** 单元格 diff 标记：全部来源被移除 → 整格删除线；非最后一条 → 部分移除角标（§6.2） */
export function cellDraftMark(input: {
  sources: Array<{ recordId: number }>;
  markInfo: Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >;
}): { mark: CellDraftMark; removedCount: number; totalCount: number } {
  const { sources, markInfo } = input;
  if (sources.length === 0)
    return { mark: null, removedCount: 0, totalCount: 0 };
  const marks = sources.map(s => markInfo.get(s.recordId)?.mark ?? null);
  const removedCount = marks.filter(m => m === "remove").length;
  const addCount = marks.filter(m => m === "add").length;
  const updateCount = marks.filter(m => m === "update").length;
  if (removedCount === sources.length) {
    return { mark: "remove", removedCount, totalCount: sources.length };
  }
  if (removedCount > 0) {
    return { mark: "partial-remove", removedCount, totalCount: sources.length };
  }
  if (addCount === sources.length) {
    return { mark: "add", removedCount: 0, totalCount: sources.length };
  }
  if (addCount > 0) {
    return { mark: "partial-add", removedCount: 0, totalCount: sources.length };
  }
  if (updateCount > 0) {
    return { mark: "update", removedCount: 0, totalCount: sources.length };
  }
  return { mark: null, removedCount: 0, totalCount: sources.length };
}

/**
 * 授权弹窗预填计算（评审问题 1：选操作后按现有授权预填勾选 + 默认范围）。
 * 纯函数，便于单测；执行侧（setCheckedKeys）在 GrantDialog.applyPresetForCurrentScope。
 *
 * - INSTANCE 记录 -> checkedTripleKeys（树勾选）
 * - ALL 记录存在 -> 默认 scopeMode=ALL（hasAll 优先）
 * - AUTO_DEP / draftMark=remove 忽略
 * - 操作限本类型（全局操作概念已退役，操作定义按类型隔离）
 */
export function computePreset(args: {
  op: { code: string; resourceTypeCode: string } | null;
  records: EffectiveRecord[];
}): { scopeMode: "INSTANCE" | "ALL"; checkedTripleKeys: Set<string> } {
  const { op, records } = args;
  if (!op) {
    return { scopeMode: "INSTANCE", checkedTripleKeys: new Set() };
  }
  const checkedTripleKeys = new Set<string>();
  let hasAll = false;
  for (const record of records) {
    if (record.grantSource !== "MANUAL") continue;
    if (record.draftMark === "remove") continue;
    if (record.operationCode !== op.code) continue;
    if (record.resourceTypeCode !== op.resourceTypeCode) continue;
    if (record.scopeMode === "ALL") {
      hasAll = true;
    } else if (record.scopeMode === "INSTANCE" && record.resourceCode) {
      checkedTripleKeys.add(
        `${record.resourceTypeCode}:${record.resourceCode}:${record.codeType}`
      );
    }
  }
  return {
    scopeMode: hasAll ? "ALL" : "INSTANCE",
    checkedTripleKeys
  };
}

// ========== 定位投影（T-FE-038 review P2-4） ==========

/**
 * 子权限变更的父主权限定位键。
 * 子权限变更（含跨类型子权限）的 summary.resourceTypeCode 可能不是当前矩阵类型，
 * 而矩阵资源树只有当前类型 → 定位需投影到父主权限单元格
 * （父主权限一定在当前类型：详情层只在当前类型打开）。
 * 返回 null = 主权限变更（无父），调用方用变更自身定位。
 *
 * @param change 待定位变更
 * @param changes 当前草稿清单（草稿父经 parentChangeId 查找）
 * @param mains 当前类型主权限生效视图（持久化父经 dependOn/parentPermissionId 查找，
 *  含草稿态记录）
 */
export function parentLocateOf(
  change: DraftChange,
  changes: DraftChange[],
  mains: RolePermissionItem[]
): {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
} | null {
  // 草稿父（子权限挂草稿 add 父）：父 add 的 recordKey 即父定位键
  if (change.kind === "add" && change.parentChangeId != null) {
    const parent = changes.find(c => c.changeId === change.parentChangeId);
    if (parent?.kind === "add") {
      return {
        resourceTypeCode: parent.recordKey.resourceTypeCode,
        resourceCode: parent.recordKey.resourceCode,
        codeType: parent.recordKey.codeType,
        operationCode: parent.recordKey.operationCode
      };
    }
  }
  // 持久化父：dependOn -> 主权限 id（草稿态记录亦在生效视图中）
  const parentId =
    change.kind === "add"
      ? (change.parentPermissionId ?? null)
      : change.kind === "update"
        ? (change.before.dependOn ?? null)
        : change.kind === "remove"
          ? (change.records[0]?.dependOn ?? null)
          : (change.removedRecords[0]?.dependOn ?? null);
  if (parentId == null) return null;
  const parent = mains.find(r => r.id === parentId);
  if (!parent) return null;
  return {
    resourceTypeCode: parent.resourceTypeCode,
    resourceCode: parent.resourceCode,
    codeType: parent.codeType,
    operationCode: parent.operationCode
  };
}

/** 位运算工具 re-export（组件层便利性） */
export { toBigIntBits };

// ========== v3.1 记录槽位 reducer（T-FE-040：焦点生命周期/显式复制/停用条件） ==========
// 设计依据：docs/design/frontend/permission-grant.md §4（v3.1 记录级聚焦编辑）/ §6.1（suspended 双路径）。
// 全部为纯函数，可单测；组件层（GrantDialog.vue）以本组函数维护弹窗本地草稿状态。
//
// 模型：弹窗本地草稿 = 页面草稿副本（changes）+ suspended 暂存（取消勾选）+ 焦点槽位。
// - 勾选/取消勾选实时落草稿：勾选=add（默认属性）或恢复；取消=suspended 暂存（确认时双路径展开）；
// - 属性修改（条件/canGrant）只作用于聚焦记录（update / add 就地改），不再批量覆盖；
// - 显式复制：源聚焦记录属性 → 目标已勾选记录（add 就地改 / update 合并 / after==before 归一化）；
// - suspended 不进入变更清单，仅存在于弹窗本地草稿层；取消弹窗即整体丢弃。

/** 焦点槽位：MANUAL 编辑槽位（完整授权键，与来源记录解耦，设计 §4 v3.1） */
export type FocusSlot = {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
  scopeMode: "INSTANCE" | "ALL";
};

/** 槽位属性（条件 / 再授予；条件不可转授：conditionCode 或 inlineCondition 非空时 canGrant 恒 false） */
export type SlotAttributes = {
  conditionCode: string | null;
  canGrant: boolean;
  /** 内联条件定义（T-PERM-048 双轨制）：非空时该记录最终条件为内联（conditionCode 须为 null）；
   *  null/缺省 = 非内联形态（managed 引用或无条件，由 conditionCode 表达） */
  inlineCondition?: InlineConditionDef | null;
};

/** suspended 槽位（弹窗本地暂存态，设计 §6.1：取消勾选暂存 → 重新勾选恢复 → 确认时双路径展开） */
export type SuspendedSlot = {
  slotKey: string;
  /** baseline 持久化记录（保持未勾选 → 确认时生成 remove；update/子权限草稿随记录丢弃） */
  persistedRecord?: RolePermissionItem;
  /** 待新增 add 草稿变更 id（保持未勾选 → 确认时取消整个 add 变更组，不生成 remove） */
  addChangeId?: string;
  /** 待新增 add 草稿的完整原始变更（恢复时原样放回，保留 changeId/summary/未来扩展字段；子权限 parentChangeId 无需重映射） */
  addChange?: AddChange;
  /** 随主记录暂存的子权限草稿（重新勾选恢复；确认时随 remove 丢弃） */
  childChanges?: DraftChange[];
  /** suspended 时快照属性（重新勾选恢复用） */
  attributes: SlotAttributes;
};

/** 弹窗本地槽位草稿状态（以页面草稿为基底，取消弹窗即丢弃） */
export type SlotDraftState = {
  changes: DraftChange[];
  suspended: Map<string, SuspendedSlot>;
  focusSlotKey: string | null;
};

/** 焦点槽位 → 直接授权键字符串（与 groupKeyOf 同构；operationCode 必传） */
export function slotKeyOf(slot: FocusSlot): string {
  return groupKeyOf(slot);
}

/** 生效视图中查找槽位的 MANUAL 主记录（不含 remove 标记；子权限/继承来源不参与） */
export function findSlotRecord(
  view: DraftAppliedView,
  slot: FocusSlot
): EffectiveRecord | null {
  return (
    view.mains.find(
      m =>
        m.grantSource === "MANUAL" &&
        m.draftMark !== "remove" &&
        m.resourceTypeCode === slot.resourceTypeCode &&
        (m.resourceCode ?? null) === (slot.resourceCode ?? null) &&
        (m.codeType ?? null) === (slot.codeType ?? null) &&
        m.operationCode === slot.operationCode &&
        m.scopeMode === slot.scopeMode
    ) ?? null
  );
}

/** 挂载在指定主记录下的子权限草稿 changeId 列表（add 虚拟挂载 / 持久化父 dependOn 双路径） */
function childChangeIdsOf(
  changes: DraftChange[],
  record: EffectiveRecord
): string[] {
  const ids: string[] = [];
  for (const change of changes) {
    if (change.kind === "add") {
      if (
        (change.parentPermissionId != null &&
          change.parentPermissionId === record.id) ||
        (change.parentChangeId != null &&
          change.parentChangeId === record.changeId)
      ) {
        ids.push(change.changeId);
      }
    } else if (change.kind === "update") {
      if (change.before.dependOn === record.id) ids.push(change.changeId);
    } else if (change.kind === "remove") {
      if (change.records[0]?.dependOn === record.id) ids.push(change.changeId);
    } else if (change.kind === "replace") {
      if (change.removedRecords[0]?.dependOn === record.id)
        ids.push(change.changeId);
    }
  }
  return ids;
}

/** 资源展示名（add/remove 摘要用；缺省回退 resourceCode / "全部资源"） */
function resourceLabelOf(
  slot: FocusSlot,
  fallbackName?: string | null
): string {
  return fallbackName ?? slot.resourceCode ?? "全部资源";
}

/**
 * 取消勾选 → suspended 暂存（设计 §6.1）：
 * - 该槽位的 update/add 草稿及其子权限草稿从草稿中移出，属性快照入 suspended；
 * - 焦点记录被取消时焦点清空（设计 §4：取消勾选焦点记录 → 焦点清空）；
 * - 不生成 remove（确认时由 expandSuspended 按来源双路径展开）。
 */
export function uncheckSlot(
  state: SlotDraftState,
  input: { slot: FocusSlot; view: DraftAppliedView }
): SlotDraftState {
  const slotKey = slotKeyOf(input.slot);
  if (state.suspended.has(slotKey)) return state;
  const record = findSlotRecord(input.view, input.slot);
  // 快照含内联绑定（T-PERM-048 双轨评审 P3-1）：草稿 add 的内联定义随快照恢复；
  // baseline 内联绑定的定义不在记录上（conditions 按 code 回显），恢复走 conditionCode
  // round-trip（快照与 baseline 同值不产生 update，编辑内联重新进内联态）
  const attributes: SlotAttributes = record
    ? {
        conditionCode: record.conditionCode,
        canGrant: record.canGrant,
        inlineCondition: record.inlineCondition ?? null
      }
    : { conditionCode: null, canGrant: false };

  let changes = [...state.changes];
  let persistedRecord: EffectiveRecord | undefined;
  let addChangeId: string | undefined;
  let addChange: AddChange | undefined;
  let childChanges: DraftChange[] | undefined;

  if (record) {
    if (record.draftMark === "add" && record.changeId) {
      // add 路径：移出 add 主变更 + 其虚拟挂载子权限（确认时整个变更组取消）；
      // 保存完整原始 add 变更（恢复时原样放回，保留 changeId/summary）
      addChangeId = record.changeId;
      addChange = state.changes.find(
        (c): c is AddChange =>
          c.kind === "add" && c.changeId === record.changeId
      );
      const childIds = new Set(childChangeIdsOf(changes, record));
      changes = changes.filter(
        c => !childIds.has(c.changeId) && c.changeId !== record.changeId
      );
      if (childIds.size > 0) {
        childChanges = state.changes.filter(c => childIds.has(c.changeId));
      }
    } else {
      // baseline 路径：主记录自身 update 随记录丢弃（恢复时按属性快照相对 baseline 重建至多一条）；
      // childChanges 只保存真正挂载于父记录的子权限变更
      persistedRecord = record;
      const childIds = new Set(childChangeIdsOf(changes, record));
      if (record.draftMark === "update" && record.changeId) {
        changes = changes.filter(c => c.changeId !== record.changeId);
      }
      if (childIds.size > 0) {
        childChanges = state.changes.filter(c => childIds.has(c.changeId));
        changes = changes.filter(c => !childIds.has(c.changeId));
      }
    }
  }

  const suspended = new Map(state.suspended);
  suspended.set(slotKey, {
    slotKey,
    ...(persistedRecord ? { persistedRecord } : {}),
    ...(addChangeId ? { addChangeId } : {}),
    ...(addChange ? { addChange } : {}),
    ...(childChanges && childChanges.length > 0 ? { childChanges } : {}),
    attributes
  });
  const focusSlotKey =
    state.focusSlotKey === slotKey ? null : state.focusSlotKey;
  return { changes, suspended, focusSlotKey };
}

/**
 * 重新勾选 → 恢复（设计 §4/§6.1）：
 * - suspended 中存在：add 路径按快照属性重建 add；baseline 路径恢复属性（差异时生成 update）；
 *   子权限草稿随主记录恢复；
 * - suspended 中不存在（普通勾选）：撤销该槽位上的 remove 标记变更（恢复原记录保留子权限）；
 *   无 MANUAL 记录 → 以默认属性（无条件、不可再授予）add。
 */
export function resumeSlot(
  state: SlotDraftState,
  input: {
    slot: FocusSlot;
    view: DraftAppliedView;
    baseline: RolePermissionItem[];
    operations: OperationDefInput[];
  }
): SlotDraftState {
  const slotKey = slotKeyOf(input.slot);
  let changes = [...state.changes];
  const suspended = new Map(state.suspended);
  const stashed = suspended.get(slotKey);

  if (stashed) {
    if (stashed.addChange) {
      // add 路径：原样放回原始 add 变更（保留 changeId/summary；子权限 parentChangeId 无需重映射）
      changes.push(stashed.addChange);
      if (stashed.childChanges && stashed.childChanges.length > 0) {
        changes = [...changes, ...stashed.childChanges];
      }
    } else if (stashed.addChangeId) {
      // 兜底（无完整 addChange 快照）：按属性快照重建 add，子权限 parentChangeId 重映射到新 changeId
      // （三审 P3：不再丢弃 childChanges——旧子权限变更随重建父恢复，避免静默丢失）
      const recordKey: GrantRecordKey = {
        resourceTypeCode: input.slot.resourceTypeCode,
        resourceCode: input.slot.resourceCode,
        codeType: input.slot.codeType,
        operationCode: input.slot.operationCode,
        scopeMode: input.slot.scopeMode,
        conditionCode: stashed.attributes.conditionCode,
        canGrant: stashed.attributes.canGrant
      };
      const rebuilt = buildAddChange({
        recordKey,
        summary: buildSummary({
          recordKey,
          resourceLabel: resourceLabelOf(input.slot)
        })
      });
      changes.push(rebuilt);
      if (stashed.childChanges && stashed.childChanges.length > 0) {
        changes = [
          ...changes,
          ...stashed.childChanges.map(change =>
            change.kind === "add" &&
            change.parentChangeId === stashed.addChangeId
              ? { ...change, parentChangeId: rebuilt.changeId }
              : change
          )
        ];
      }
    } else if (stashed.persistedRecord) {
      // baseline 路径：子权限草稿随主记录恢复；主记录自身 update 已随取消丢弃，
      // 用属性快照相对 baseline 重建至多一条 update（避免同 recordId 重复）
      if (stashed.childChanges && stashed.childChanges.length > 0) {
        changes = [...changes, ...stashed.childChanges];
      }
      const baselineRecord = input.baseline.find(
        m =>
          m.dependOn == null &&
          m.grantSource === "MANUAL" &&
          m.resourceTypeCode === input.slot.resourceTypeCode &&
          (m.resourceCode ?? null) === (input.slot.resourceCode ?? null) &&
          (m.codeType ?? null) === (input.slot.codeType ?? null) &&
          m.operationCode === input.slot.operationCode &&
          m.scopeMode === input.slot.scopeMode
      );
      const after: SlotAttributes = {
        conditionCode: stashed.attributes.conditionCode,
        inlineCondition: stashed.attributes.inlineCondition ?? null,
        canGrant: stashed.attributes.canGrant
      };
      if (
        baselineRecord &&
        (baselineRecord.canGrant !== after.canGrant ||
          baselineRecord.conditionCode !== after.conditionCode)
      ) {
        changes.push(
          buildUpdateChange({
            before: baselineRecord,
            after,
            summary: buildSummary({
              recordKey: {
                resourceTypeCode: baselineRecord.resourceTypeCode,
                resourceCode: baselineRecord.resourceCode,
                codeType: baselineRecord.codeType,
                operationCode: baselineRecord.operationCode,
                scopeMode: baselineRecord.scopeMode,
                conditionCode: after.conditionCode,
                canGrant: after.canGrant
              },
              resourceLabel: resourceLabelOf(
                input.slot,
                baselineRecord.resourceName
              )
            })
          })
        );
      }
    }
    suspended.delete(slotKey);
    return { changes, suspended, focusSlotKey: state.focusSlotKey };
  }

  // ---- 普通勾选路径（无 suspended 暂存）----
  // 撤销该槽位上的 remove 标记变更（恢复原记录，保留子权限）
  for (const change of [...changes]) {
    if (
      change.kind === "remove" &&
      change.reason === "dialog-uncheck" &&
      change.records.some(
        r =>
          slotKeyOf({
            resourceTypeCode: r.resourceTypeCode,
            resourceCode: r.resourceCode,
            codeType: r.codeType,
            operationCode: r.operationCode ?? "",
            scopeMode: r.scopeMode
          }) === slotKey
      )
    ) {
      changes = changes.filter(c => c.changeId !== change.changeId);
    }
  }
  const view = applyDraftToRecords({
    baseline: input.baseline,
    changes,
    operations: input.operations
  });
  const matched = findSlotRecord(view, input.slot);
  if (!matched) {
    // 直接授权键不存在 → add（默认属性：无条件、不可再授予）
    const recordKey: GrantRecordKey = {
      resourceTypeCode: input.slot.resourceTypeCode,
      resourceCode: input.slot.resourceCode,
      codeType: input.slot.codeType,
      operationCode: input.slot.operationCode,
      scopeMode: input.slot.scopeMode,
      conditionCode: null,
      canGrant: false
    };
    changes.push(
      buildAddChange({
        recordKey,
        summary: buildSummary({
          recordKey,
          resourceLabel: resourceLabelOf(input.slot)
        })
      })
    );
  }
  return { changes, suspended, focusSlotKey: state.focusSlotKey };
}

/**
 * 修改聚焦记录属性（设计 §4 v3.1：设置区修改只作用于聚焦记录）：
 * - 记录存在（持久化）→ 生成 update（或就地改已有 update 变更；after==before 归一化剔除）；
 * - 记录为 add 草稿 → 就地改属性（不产生新变更）；
 * - 记录不存在（未授权资源）→ 不生成（设置区只读展示默认值）。
 * 条件非空时 canGrant 强制 false（条件不可转授兜底，与 T-FE-039 一致）。
 */
export function applyFocusAttributes(
  state: SlotDraftState,
  input: {
    slot: FocusSlot;
    attributes: SlotAttributes;
    view: DraftAppliedView;
  }
): SlotDraftState {
  const { slot, view } = input;
  const raw = input.attributes;
  const attributes: SlotAttributes =
    raw.conditionCode != null || raw.inlineCondition != null
      ? { ...raw, canGrant: false }
      : raw;
  const record = findSlotRecord(view, slot);
  if (!record) return state;

  let changes = [...state.changes];
  if (record.draftMark === "add" && record.changeId) {
    changes = changes.map(c =>
      c.changeId === record.changeId && c.kind === "add"
        ? {
            ...c,
            recordKey: {
              ...c.recordKey,
              conditionCode: attributes.conditionCode,
              inlineCondition: attributes.inlineCondition ?? null,
              canGrant: attributes.canGrant
            },
            summary: buildSummary({
              recordKey: {
                ...c.recordKey,
                conditionCode: attributes.conditionCode,
                inlineCondition: attributes.inlineCondition ?? null,
                canGrant: attributes.canGrant
              },
              resourceLabel: resourceLabelOf(slot, record.resourceName)
            })
          }
        : c
    );
  } else if (record.draftMark === "update" && record.changeId) {
    changes = changes.map(c =>
      c.changeId === record.changeId && c.kind === "update"
        ? {
            ...c,
            after: {
              conditionCode: attributes.conditionCode,
              inlineCondition: attributes.inlineCondition ?? null,
              canGrant: attributes.canGrant
            },
            summary: buildSummary({
              recordKey: {
                resourceTypeCode: record.resourceTypeCode,
                resourceCode: record.resourceCode,
                codeType: record.codeType,
                operationCode: record.operationCode,
                scopeMode: record.scopeMode,
                conditionCode: attributes.conditionCode,
                inlineCondition: attributes.inlineCondition ?? null,
                canGrant: attributes.canGrant
              },
              resourceLabel: resourceLabelOf(slot, record.resourceName)
            })
          }
        : c
    );
  } else if (
    record.canGrant !== attributes.canGrant ||
    record.conditionCode !== attributes.conditionCode ||
    attributes.inlineCondition != null
  ) {
    changes.push(
      buildUpdateChange({
        before: record,
        after: {
          canGrant: attributes.canGrant,
          conditionCode: attributes.conditionCode,
          inlineCondition: attributes.inlineCondition ?? null
        },
        summary: buildSummary({
          recordKey: {
            resourceTypeCode: record.resourceTypeCode,
            resourceCode: record.resourceCode,
            codeType: record.codeType,
            operationCode: record.operationCode,
            scopeMode: record.scopeMode,
            conditionCode: attributes.conditionCode,
            inlineCondition: attributes.inlineCondition ?? null,
            canGrant: attributes.canGrant
          },
          resourceLabel: resourceLabelOf(slot, record.resourceName)
        })
      })
    );
  }
  // 归一化：update 回退为无差异时剔除（清单幽灵条目）
  changes = normalizeNoopUpdates(changes);
  return {
    changes,
    suspended: state.suspended,
    focusSlotKey: state.focusSlotKey
  };
}

/**
 * 显式复制（设计 §4 v3.1）：源聚焦记录属性应用到目标已勾选槽位。
 * - add 目标：就地改属性（不产生新变更）；update 目标：合并 after；其余：生成新 update；
 * - 禁止同 id 多条：先撤销目标记录上的既有 update，再合并；
 * - 合并后 after==before 归一化剔除（复用无差异清理）；
 * - 跳过源自身与属性相同目标；条件非空时 canGrant 强制 false。
 */
export function copySlotAttributes(
  state: SlotDraftState,
  input: {
    sourceSlot: FocusSlot;
    targetSlots: FocusSlot[];
    view: DraftAppliedView;
    /** 源内联条件定义（T-PERM-048）：源绑定为内联时由调用方从 conditions（INLINE 实体）
     *  或草稿 add 的 recordKey 解析深拷贝传入——内联 1:1 不复制引用，逐目标独立定义 */
    sourceInline?: InlineConditionDef | null;
  }
): SlotDraftState {
  const { sourceSlot, targetSlots, view } = input;
  const sourceKey = slotKeyOf(sourceSlot);
  const source = findSlotRecord(view, sourceSlot);
  if (!source) return state;
  const sourceInline = input.sourceInline ?? null;
  const attributes: SlotAttributes = {
    conditionCode: sourceInline == null ? source.conditionCode : null,
    inlineCondition: sourceInline,
    canGrant:
      source.conditionCode != null || sourceInline != null
        ? false
        : source.canGrant
  };

  let changes = [...state.changes];
  for (const target of targetSlots) {
    if (slotKeyOf(target) === sourceKey) continue;
    const record = findSlotRecord(view, target);
    if (!record) continue;
    const after: SlotAttributes = {
      conditionCode: attributes.conditionCode,
      inlineCondition: attributes.inlineCondition ?? null,
      canGrant: attributes.canGrant
    };
    if (
      record.canGrant === after.canGrant &&
      record.conditionCode === after.conditionCode &&
      after.inlineCondition == null
    ) {
      continue; // 属性相同：无变更（内联复制恒产生逐目标独立定义，不做相同跳过）
    }
    // 禁止同 id 多条：目标已有 update 变更时保留其 before/changeId，仅合并 after
    // （复制值回到 baseline 时 noop 归一化剔除伪变更；before 恒为基线值）
    if (record.draftMark === "add" && record.changeId) {
      // add 就地改
      changes = changes.map(c =>
        c.changeId === record.changeId && c.kind === "add"
          ? {
              ...c,
              recordKey: {
                ...c.recordKey,
                conditionCode: after.conditionCode,
                inlineCondition: after.inlineCondition ?? null,
                canGrant: after.canGrant
              },
              summary: buildSummary({
                recordKey: {
                  ...c.recordKey,
                  conditionCode: after.conditionCode,
                  inlineCondition: after.inlineCondition ?? null,
                  canGrant: after.canGrant
                },
                resourceLabel: resourceLabelOf(target, record.resourceName)
              })
            }
          : c
      );
    } else {
      const existingUpdate = changes.find(
        (c): c is UpdateChange =>
          c.kind === "update" && c.recordId === record.id
      );
      if (existingUpdate) {
        // 合并：保留既有 before/changeId，只覆盖 after（同 id 恒一条）
        changes = changes.map(c =>
          c.changeId === existingUpdate.changeId && c.kind === "update"
            ? {
                ...c,
                after: {
                  conditionCode: after.conditionCode,
                  inlineCondition: after.inlineCondition ?? null,
                  canGrant: after.canGrant
                },
                summary: buildSummary({
                  recordKey: {
                    resourceTypeCode: target.resourceTypeCode,
                    resourceCode: target.resourceCode,
                    codeType: target.codeType,
                    operationCode: target.operationCode,
                    scopeMode: target.scopeMode,
                    conditionCode: after.conditionCode,
                    inlineCondition: after.inlineCondition ?? null,
                    canGrant: after.canGrant
                  },
                  resourceLabel: resourceLabelOf(target, record.resourceName)
                })
              }
            : c
        );
      } else {
        changes.push(
          buildUpdateChange({
            before: record,
            after,
            summary: buildSummary({
              recordKey: {
                resourceTypeCode: target.resourceTypeCode,
                resourceCode: target.resourceCode,
                codeType: target.codeType,
                operationCode: target.operationCode,
                scopeMode: target.scopeMode,
                conditionCode: after.conditionCode,
                inlineCondition: after.inlineCondition ?? null,
                canGrant: after.canGrant
              },
              resourceLabel: resourceLabelOf(target, record.resourceName)
            })
          })
        );
      }
    }
  }
  changes = normalizeNoopUpdates(changes);
  return {
    changes,
    suspended: state.suspended,
    focusSlotKey: state.focusSlotKey
  };
}

/** 归一化：剔除 after==before 的 update 变更（无差异幽灵条目清理，设计 §4 复制合并规则⑥）。
 *  内联轨（T-PERM-048）：after.inlineCondition 非空恒保留——baseline 无条件记录加内联时
 *  conditionCode 两侧均为 null 无 diff，仅 inline 在场即为有效变更。 */
export function normalizeNoopUpdates(changes: DraftChange[]): DraftChange[] {
  return changes.filter(c => {
    if (c.kind !== "update") return true;
    return (
      c.before.canGrant !== c.after.canGrant ||
      c.before.conditionCode !== c.after.conditionCode ||
      c.after.inlineCondition != null
    );
  });
}

/**
 * 确认时展开 suspended（设计 §6.1 双路径）：
 * - persistedRecord（baseline 路径）保持未勾选 → 生成 remove（子权限草稿已随取消移出，级联由后端执行）；
 * - addChangeId（add 路径）保持未勾选 → 取消整个 add 变更组，不生成 remove（无持久化 ID）；
 * - 返回展开后的最终草稿集合（组件 emit 后写入页面）。
 */
export function expandSuspended(state: SlotDraftState): DraftChange[] {
  const changes = [...state.changes];
  for (const stashed of state.suspended.values()) {
    if (stashed.persistedRecord) {
      const record = stashed.persistedRecord;
      const cascadeChildCount = record.childCount ?? 0;
      changes.push(
        buildRemoveChange({
          records: [record],
          cascadeChildCount,
          reason: "dialog-uncheck",
          summary: buildSummary({
            recordKey: {
              resourceTypeCode: record.resourceTypeCode,
              resourceCode: record.resourceCode,
              codeType: record.codeType,
              operationCode: record.operationCode,
              scopeMode: record.scopeMode,
              conditionCode: null,
              canGrant: false
            },
            resourceLabel: resourceLabelOf(
              {
                resourceTypeCode: record.resourceTypeCode,
                resourceCode: record.resourceCode,
                codeType: record.codeType,
                operationCode: record.operationCode ?? "",
                scopeMode: record.scopeMode
              },
              record.resourceName
            )
          })
        })
      );
    }
    // addChangeId 路径：变更组已从草稿移出（uncheckSlot），此处无需再操作；
    // 子权限草稿同样已随主变更移出。
  }
  return normalizeNoopUpdates(changes);
}

/** 弹窗本地槽位状态的初始值（以页面草稿为基底） */
export function createSlotDraftState(changes: DraftChange[]): SlotDraftState {
  return {
    changes: [...changes],
    suspended: new Map(),
    focusSlotKey: null
  };
}
