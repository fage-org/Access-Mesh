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
    canGrant: false
  };
}

// ========== 草稿生效视图（baseline + draft → 展示用记录集） ==========

/** 生效记录（baseline 叠加草稿标记；新增记录持负数月临时 id，与持久化 id 区分） */
export type EffectiveRecord = RolePermissionItem & {
  draftMark: "add" | "update" | "remove" | null;
  changeId?: string;
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
      changeId: change.changeId
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
      changeId: mark?.changeId
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
      const item: { id: number; canGrant?: boolean; conditionCode?: string } = {
        id: change.recordId
      };
      if (change.before.canGrant !== change.after.canGrant) {
        item.canGrant = change.after.canGrant;
      }
      if (change.before.conditionCode !== change.after.conditionCode) {
        // 三态：null → 清除映射为 ""；非空 → 覆盖
        item.conditionCode = change.after.conditionCode ?? "";
      }
      if (item.canGrant !== undefined || item.conditionCode !== undefined) {
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
  const conditionLabel = recordKey.conditionCode ?? "无条件";
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
  after: { canGrant: boolean; conditionCode: string | null };
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

/** 授权弹窗单个范围结果（一次确认可顺序包含 ALL 撤销 + INSTANCE 最终集合）。 */
export type DialogResult = {
  operation: { code: string; resourceTypeCode: string | null };
  scopeMode: "INSTANCE" | "ALL";
  /** ALL 空集合撤销时仍需保留目标类型；非 ALL 可缺省。 */
  targetResourceTypeCode?: string;
  /** INSTANCE：勾选资源集合；ALL：单元素（resourceTypeCode 目标类型，resourceCode/codeType 为 null） */
  resources: Array<{
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    name: string;
  }>;
  /** 授权设置未被用户修改时保持原属性的已有资源；仅参与最终集合防撤销。 */
  untouchedResourceKeys?: string[];
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
  // - 专属操作：限所属类型
  // - 全局 ALL：限 dialog.resources[0] 目标类型（一次只编辑一个类型，其他类型不受影响）
  // - 全局 INSTANCE：不限类型（全类型最终实例授权集合，取消勾选=撤权）
  // remove 标记视同不存在
  const comparable = view.mains.filter(record => {
    if (record.grantSource !== "MANUAL") return false;
    if (record.draftMark === "remove") return false;
    if (record.operationCode !== dialog.operation.code) return false;
    if (record.scopeMode !== dialog.scopeMode) return false;
    if (dialog.operation.resourceTypeCode != null) {
      return record.resourceTypeCode === dialog.operation.resourceTypeCode;
    }
    if (dialog.scopeMode === "ALL") {
      const targetType =
        dialog.targetResourceTypeCode ?? dialog.resources[0]?.resourceTypeCode;
      return record.resourceTypeCode === targetType;
    }
    return true;
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

  // ---- 勾选集合：add / update ----
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
    const recordKey: GrantRecordKey = {
      resourceTypeCode: resource.resourceTypeCode,
      resourceCode: resource.resourceCode,
      codeType: resource.codeType,
      operationCode: dialog.operation.code,
      scopeMode: dialog.scopeMode,
      conditionCode: dialog.conditionCode,
      canGrant: dialog.canGrant
    };
    const summary = buildSummary({ recordKey, resourceLabel: resource.name });

    if (matched) {
      if (matched.draftMark === "add" && matched.changeId) {
        // 草稿新增就地改 canGrant/条件（不产生新变更）
        changes = changes.map(c =>
          c.changeId === matched.changeId && c.kind === "add"
            ? {
                ...c,
                recordKey: {
                  ...c.recordKey,
                  conditionCode: dialog.conditionCode,
                  canGrant: dialog.canGrant
                },
                summary
              }
            : c
        );
      } else if (matched.draftMark === "update" && matched.changeId) {
        // 已有 update 变更 → 就地改 after（不重复建变更）
        changes = changes.map(c =>
          c.changeId === matched.changeId && c.kind === "update"
            ? {
                ...c,
                after: {
                  conditionCode: dialog.conditionCode,
                  canGrant: dialog.canGrant
                },
                summary
              }
            : c
        );
      } else if (
        matched.canGrant !== dialog.canGrant ||
        matched.conditionCode !== dialog.conditionCode
      ) {
        changes.push(
          buildUpdateChange({
            before: matched,
            after: {
              canGrant: dialog.canGrant,
              conditionCode: dialog.conditionCode
            },
            summary
          })
        );
      }
    } else {
      // 直接授权键不存在 → add
      changes.push(buildAddChange({ recordKey, summary }));
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

  // 清理：update 变更回退为无差异（after == before）时剔除（清单幽灵条目）
  changes = changes.filter(c => {
    if (c.kind !== "update") return true;
    return (
      c.before.canGrant !== c.after.canGrant ||
      c.before.conditionCode !== c.after.conditionCode
    );
  });

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
 * - 专属操作限本类型（全局操作不限）
 */
export function computePreset(args: {
  op: { code: string; resourceTypeCode: string | null } | null;
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
    if (
      op.resourceTypeCode != null &&
      record.resourceTypeCode !== op.resourceTypeCode
    ) {
      continue;
    }
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
