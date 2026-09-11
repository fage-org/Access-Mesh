# -*- coding: utf-8 -*-
import io

p = 'frontend/src/views/perm/grant/utils/grant-plan.ts'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (s.count(old), old[:100])
    s = s.replace(old, new)

# 1. import InlineConditionDef
rep('''import type {
  GrantPlan,
  GrantPlanCreate,
  GrantRecordKey,
  RolePermissionItem
} from "@/api/permission-grant";''',
'''import type {
  GrantPlan,
  GrantPlanCreate,
  GrantRecordKey,
  InlineConditionDef,
  RolePermissionItem
} from "@/api/permission-grant";''')

# 2. EffectiveRecord carries inlineCondition
rep('''/** 生效记录（baseline 叠加草稿标记；新增记录持负数月临时 id，与持久化 id 区分） */
export type EffectiveRecord = RolePermissionItem & {
  draftMark: "add" | "update" | "remove" | null;
  changeId?: string;
};''',
'''/** 生效记录（baseline 叠加草稿标记；新增记录持负数月临时 id，与持久化 id 区分） */
export type EffectiveRecord = RolePermissionItem & {
  draftMark: "add" | "update" | "remove" | null;
  changeId?: string;
  /** 内联条件定义（T-PERM-048）：草稿 add/applyFocusEdit 后的内联绑定展示用；
   *  baseline 记录绑定内联时为 null（展示回退 conditions 按 code 查找——code 为 inline- 前缀） */
  inlineCondition?: InlineConditionDef | null;
};''')

# 3. addToEffective sets inlineCondition
rep('''      scopeMode: recordKey.scopeMode,
      dependOn,
      grantSource: "MANUAL",
      grantedBits: resolveGrantedBits(recordKey, operations),
      createdAt: "",
      childCount: dependOn == null ? childAdds.length : 0,
      draftMark: "add",
      changeId: change.changeId
    };''',
'''      scopeMode: recordKey.scopeMode,
      dependOn,
      grantSource: "MANUAL",
      grantedBits: resolveGrantedBits(recordKey, operations),
      createdAt: "",
      childCount: dependOn == null ? childAdds.length : 0,
      draftMark: "add",
      changeId: change.changeId,
      inlineCondition: recordKey.inlineCondition ?? null
    };''')

# 4. baseline overlay carries update.after.inlineCondition
rep('''    const effective: EffectiveRecord = {
      ...record,
      canGrant: update ? update.after.canGrant : record.canGrant,
      conditionCode: update ? update.after.conditionCode : record.conditionCode,
      draftMark: mark?.mark ?? null,
      changeId: mark?.changeId
    };''',
'''    const effective: EffectiveRecord = {
      ...record,
      canGrant: update ? update.after.canGrant : record.canGrant,
      conditionCode: update ? update.after.conditionCode : record.conditionCode,
      draftMark: mark?.mark ?? null,
      changeId: mark?.changeId,
      inlineCondition: update ? update.after.inlineCondition ?? null : null
    };''')

# 5. buildGrantPlan update emit: inline branch + include condition
rep('''    } else if (change.kind === "update") {
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
    }''',
'''    } else if (change.kind === "update") {
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
    }''')

# 6. buildSummary label includes inline name
rep('''  const { recordKey } = input;
  const conditionLabel = recordKey.conditionCode ?? "无条件";''',
'''  const { recordKey } = input;
  const conditionLabel = recordKey.conditionCode
    ?? (recordKey.inlineCondition != null
      ? `内联:${recordKey.inlineCondition.name}`
      : "无条件");''')

# 7. applyDialogResultToDraft focus branch: attributes carry inline; add-recordKey & update-after & new-update all set inlineCondition
rep('''    const focusView = applyDraftToRecords({ baseline, changes, operations });
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
                  canGrant: attributes.canGrant
                },
                summary: buildSummary({
                  recordKey: {
                    ...c.recordKey,
                    conditionCode: attributes.conditionCode,
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
        focusRecord.conditionCode !== attributes.conditionCode
      ) {
        changes.push(
          buildUpdateChange({
            before: focusRecord,
            after: {
              canGrant: attributes.canGrant,
              conditionCode: attributes.conditionCode
            },
            summary: buildSummary({
              recordKey: {
                resourceTypeCode: focusRecord.resourceTypeCode,
                resourceCode: focusRecord.resourceCode,
                codeType: focusRecord.codeType,
                operationCode: focusRecord.operationCode,
                scopeMode: focusRecord.scopeMode,
                conditionCode: attributes.conditionCode,
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
    }''',
'''    const focusView = applyDraftToRecords({ baseline, changes, operations });
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
    }''')

# 8. SlotAttributes type + doc
rep('''/** 槽位属性（条件 / 再授予；条件不可转授：conditionCode 非空时 canGrant 恒 false） */
export type SlotAttributes = {
  conditionCode: string | null;
  canGrant: boolean;
};''',
'''/** 槽位属性（条件 / 再授予；条件不可转授：conditionCode 或 inlineCondition 非空时 canGrant 恒 false） */
export type SlotAttributes = {
  conditionCode: string | null;
  canGrant: boolean;
  /** 内联条件定义（T-PERM-048 双轨制）：非空时该记录最终条件为内联（conditionCode 须为 null）；
   *  null/缺省 = 非内联形态（managed 引用或无条件，由 conditionCode 表达） */
  inlineCondition?: InlineConditionDef | null;
};''')

# 9. attributes sanitize in applyFocusAttributes (canGrant forced false also for inline)
rep('''  const { slot, view } = input;
  const raw = input.attributes;
  const attributes: SlotAttributes =
    raw.conditionCode != null ? { ...raw, canGrant: false } : raw;
  const record = findSlotRecord(view, slot);
  if (!record) return state;''',
'''  const { slot, view } = input;
  const raw = input.attributes;
  const attributes: SlotAttributes =
    raw.conditionCode != null || raw.inlineCondition != null
      ? { ...raw, canGrant: false }
      : raw;
  const record = findSlotRecord(view, slot);
  if (!record) return state;''')

# 10. applyFocusAttributes three branches add inlineCondition
rep('''            recordKey: {
              ...c.recordKey,
              conditionCode: attributes.conditionCode,
              canGrant: attributes.canGrant
            },
            summary: buildSummary({
              recordKey: {
                ...c.recordKey,
                conditionCode: attributes.conditionCode,
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
                canGrant: attributes.canGrant
              },
              resourceLabel: resourceLabelOf(slot, record.resourceName)
            })
          }
        : c
    );
  } else if (
    record.canGrant !== attributes.canGrant ||
    record.conditionCode !== attributes.conditionCode
  ) {
    changes.push(
      buildUpdateChange({
        before: record,
        after: {
          canGrant: attributes.canGrant,
          conditionCode: attributes.conditionCode
        },
        summary: buildSummary({
          recordKey: {
            resourceTypeCode: record.resourceTypeCode,
            resourceCode: record.resourceCode,
            codeType: record.codeType,
            operationCode: record.operationCode,
            scopeMode: record.scopeMode,
            conditionCode: attributes.conditionCode,
            canGrant: attributes.canGrant
          },
          resourceLabel: resourceLabelOf(slot, record.resourceName)
        })
      })
    );
  }''',
'''            recordKey: {
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
  }''')

# 11. copySlotAttributes: sourceInline input + attributes + branches
rep('''export function copySlotAttributes(
  state: SlotDraftState,
  input: {
    sourceSlot: FocusSlot;
    targetSlots: FocusSlot[];
    view: DraftAppliedView;
  }
): SlotDraftState {
  const { sourceSlot, targetSlots, view } = input;
  const sourceKey = slotKeyOf(sourceSlot);
  const source = findSlotRecord(view, sourceSlot);
  if (!source) return state;
  const attributes: SlotAttributes = {
    conditionCode: source.conditionCode,
    canGrant: source.conditionCode != null ? false : source.canGrant
  };''',
'''export function copySlotAttributes(
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
  };''')

rep('''    const after: SlotAttributes = {
      conditionCode: attributes.conditionCode,
      canGrant: attributes.canGrant
    };
    if (
      record.canGrant === after.canGrant &&
      record.conditionCode === after.conditionCode
    ) {
      continue; // 属性相同：无变更
    }''',
'''    const after: SlotAttributes = {
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
    }''')

rep('''              recordKey: {
                ...c.recordKey,
                conditionCode: after.conditionCode,
                canGrant: after.canGrant
              },
              summary: buildSummary({
                recordKey: {
                  ...c.recordKey,
                  conditionCode: after.conditionCode,
                  canGrant: after.canGrant
                },
                resourceLabel: resourceLabelOf(target, record.resourceName)
              })''',
'''              recordKey: {
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
              })''')

rep('''                after: {
                  conditionCode: after.conditionCode,
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
                    canGrant: after.canGrant
                  },
                  resourceLabel: resourceLabelOf(target, record.resourceName)
                })''',
'''                after: {
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
                })''')

rep('''          buildUpdateChange({
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
                canGrant: after.canGrant
              },
              resourceLabel: resourceLabelOf(target, record.resourceName)
            })
          })''',
'''          buildUpdateChange({
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
          })''')

# 12. normalizeNoopUpdates: keep updates with inline (baseline 无条件 → 加内联 diff 仅在 inline)
rep('''/** 归一化：剔除 after==before 的 update 变更（无差异幽灵条目清理，设计 §4 复制合并规则⑥） */
export function normalizeNoopUpdates(changes: DraftChange[]): DraftChange[] {
  return changes.filter(c => {
    if (c.kind !== "update") return true;
    return (
      c.before.canGrant !== c.after.canGrant ||
      c.before.conditionCode !== c.after.conditionCode
    );
  });
}''',
'''/** 归一化：剔除 after==before 的 update 变更（无差异幽灵条目清理，设计 §4 复制合并规则⑥）。
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
}''')

# 13. applyDialogResultToDraft 清理段 noop filter（同款扩展）
rep('''  // 清理：update 变更回退为无差异（after == before）时剔除（清单幽灵条目）
  changes = changes.filter(c => {
    if (c.kind !== "update") return true;
    return (
      c.before.canGrant !== c.after.canGrant ||
      c.before.conditionCode !== c.after.conditionCode
    );
  });''',
'''  // 清理：update 变更回退为无差异（after == before）时剔除（清单幽灵条目；
  // 内联在场恒有效——见 normalizeNoopUpdates 注释）
  changes = normalizeNoopUpdates(changes);''')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('grant-plan.ts ok')
