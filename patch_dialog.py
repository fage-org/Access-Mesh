# -*- coding: utf-8 -*-
import io

p = 'frontend/src/views/perm/grant/components/GrantDialog.vue'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (s.count(old), old[:120])
    s = s.replace(old, new)

# 1. imports
rep('''import type { ConditionResp } from "@/api/permission-condition";
import type {
  GrantRecordKey,
  RolePermissionItem
} from "@/api/permission-grant";''',
'''import type { ConditionResp } from "@/api/permission-condition";
import type {
  GrantRecordKey,
  InlineConditionDef,
  RolePermissionItem
} from "@/api/permission-grant";
import ReConditionEditor from "@/components/ReConditionEditor/src/index.vue";
import {
  createEmptyRules,
  parseRules,
  serializeRules,
  type ConditionRules
} from "@/utils/condition-rules";''')

# 2. focus state + inline
rep('''/** 焦点记录属性（编辑态；聚焦未授权资源时为只读默认值） */
const focusConditionCode = ref<string | null>(null);
const focusCanGrant = ref(false);
/** 焦点属性是否被用户修改过（未修改则不产生 update） */
const focusTouched = ref(false);''',
'''/** 焦点记录属性（编辑态；聚焦未授权资源时为只读默认值） */
const focusConditionCode = ref<string | null>(null);
const focusCanGrant = ref(false);
/** 焦点属性是否被用户修改过（未修改则不产生 update） */
const focusTouched = ref(false);
/** 内联条件编辑态（T-PERM-048 双轨制定案①）：非空 = 该记录最终条件为内联定义；
 *  与 focusConditionCode 互斥（内联态下 conditionCode 恒 null） */
const focusInline = ref<InlineConditionDef | null>(null);
/** 内联规则编辑模型（结构化，ReConditionEditor v-model；序列化进 focusInline.conditionRules） */
const inlineRulesModel = ref<ConditionRules>(createEmptyRules());
/** 内联编辑器实例（确认前校验入口） */
const inlineEditorRef = ref<InstanceType<
  typeof ReConditionEditor
> | null>(null);''')

# 3. focusOn preload
rep('''function focusOn(slot: FocusSlot | null) {
  focusSlot.value = slot;
  if (!slot) {
    focusConditionCode.value = null;
    focusCanGrant.value = false;
    focusTouched.value = false;
    return;
  }
  const record = slotDraft.value.suspended.has(slotKeyOf(slot))
    ? null
    : findSlotRecord(localView.value, slot);
  focusConditionCode.value = record?.conditionCode ?? null;
  focusCanGrant.value = record?.canGrant ?? false;
  focusTouched.value = false;
}''',
'''function focusOn(slot: FocusSlot | null) {
  focusSlot.value = slot;
  if (!slot) {
    focusConditionCode.value = null;
    focusCanGrant.value = false;
    focusInline.value = null;
    focusTouched.value = false;
    return;
  }
  const record = slotDraft.value.suspended.has(slotKeyOf(slot))
    ? null
    : findSlotRecord(localView.value, slot);
  focusCanGrant.value = record?.canGrant ?? false;
  // T-PERM-048 双轨：现绑定为 INLINE 条件（conditions 含内联实体）→ 内联编辑态预载规则；
  // 草稿 add 的内联绑定 → 从生效记录的 inlineCondition 预载；其余 → managed 引用态
  const boundInline = record?.conditionCode
    ? props.conditions.find(
        c => c.code === record.conditionCode && c.source === "INLINE"
      )
    : null;
  const draftInline =
    !boundInline && record?.inlineCondition ? record.inlineCondition : null;
  const inlineSource = boundInline
    ? {
        name: boundInline.name,
        conditionRules: boundInline.conditionRules,
        gatewayEvaluable: boundInline.gatewayEvaluable
      }
    : draftInline
      ? { ...draftInline }
      : null;
  if (inlineSource) {
    focusInline.value = inlineSource;
    inlineRulesModel.value = parseRules(inlineSource.conditionRules);
    focusConditionCode.value = null;
  } else {
    focusInline.value = null;
    focusConditionCode.value = record?.conditionCode ?? null;
  }
  focusTouched.value = false;
}''')

# 4. applyFocusEdit carries inline
rep('''function applyFocusEdit() {
  const slot = focusSlot.value;
  if (!slot || !focusTouched.value) return;
  slotDraft.value = applyFocusAttributes(slotDraft.value, {
    slot,
    attributes: {
      conditionCode: focusConditionCode.value,
      canGrant: focusCanGrant.value
    },
    view: localView.value
  });
  localChanges.value = slotDraft.value.changes;
}

function handleConditionChange(value: string | null) {
  focusConditionCode.value = value;
  if (value != null) focusCanGrant.value = false; // 条件不可转授
  focusTouched.value = true;
  applyFocusEdit();
}''',
'''function applyFocusEdit() {
  const slot = focusSlot.value;
  if (!slot || !focusTouched.value) return;
  slotDraft.value = applyFocusAttributes(slotDraft.value, {
    slot,
    attributes: {
      conditionCode: focusConditionCode.value,
      inlineCondition: focusInline.value,
      canGrant: focusCanGrant.value
    },
    view: localView.value
  });
  localChanges.value = slotDraft.value.changes;
}

function handleConditionChange(value: string | null) {
  focusConditionCode.value = value;
  focusInline.value = null; // 引用轨/无条件：退出内联态（三态互斥）
  if (value != null) focusCanGrant.value = false; // 条件不可转授
  focusTouched.value = true;
  applyFocusEdit();
}

/** 切换内联编辑模式（T-PERM-048）：当前非内联态时以空定义进入；内联态重复点击为 no-op */
function handleSwitchToInline() {
  if (focusInline.value != null) return;
  focusInline.value = { name: "", conditionRules: "{}", gatewayEvaluable: false };
  inlineRulesModel.value = createEmptyRules();
  focusConditionCode.value = null;
  focusCanGrant.value = false; // 内联条件不可转授（20041 同口径）
  focusTouched.value = true;
  applyFocusEdit();
}

/** 退出内联态回无条件（内联定义未保存前移除即弃） */
function handleRemoveInline() {
  focusInline.value = null;
  focusConditionCode.value = null;
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineNameChange(value: string | string | number) {
  if (focusInline.value == null) return;
  focusInline.value = { ...focusInline.value, name: String(value ?? "") };
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineGatewayChange(value: boolean | string | number) {
  if (focusInline.value == null) return;
  focusInline.value = {
    ...focusInline.value,
    gatewayEvaluable: value === true
  };
  focusTouched.value = true;
  applyFocusEdit();
}

function handleInlineRulesChange(rules: ConditionRules) {
  inlineRulesModel.value = rules;
  if (focusInline.value == null) return;
  focusInline.value = {
    ...focusInline.value,
    conditionRules: serializeRules(rules)
  };
  focusTouched.value = true;
  applyFocusEdit();
}

/** 内联定义确认前校验：名称必填 + 规则完整性（ReConditionEditor.validate 同款口径） */
function validateInlineBeforeConfirm(): string | null {
  if (focusInline.value == null) return null;
  if (!focusInline.value.name.trim()) return "内联条件名称不能为空";
  if (!inlineEditorRef.value?.validate()) return "内联条件规则不完整";
  return null;
}''')

# 5. disabled computeds include inline
rep('''/** 条件不可转授：选择条件后自动清除 canGrant；未聚焦或聚焦未授权资源时表单不可用（P2-2）。 */
const canGrantDisabled = computed(
  () => focusedRecord.value == null || focusConditionCode.value != null
);
const conditionDelegationBlocked = computed(
  () => focusConditionCode.value != null
);''',
'''/** 条件不可转授：选择条件或内联态后自动清除 canGrant；未聚焦或聚焦未授权资源时表单不可用（P2-2）。 */
const canGrantDisabled = computed(
  () =>
    focusedRecord.value == null ||
    focusConditionCode.value != null ||
    focusInline.value != null
);
const conditionDelegationBlocked = computed(
  () => focusConditionCode.value != null || focusInline.value != null
);''')

# 6. copyEnabled for inline
rep('''const copyEnabled = computed(() => {
  const record = focusedRecord.value;
  if (!record) return false;
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    return condition?.enabled === true;
  }
  return true;
});''',
'''const copyEnabled = computed(() => {
  const record = focusedRecord.value;
  if (!record) return false;
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    // INLINE 实体 enabled 恒 true；停用不可复制仅针对 MANAGED（20042 同口径）
    return condition?.enabled === true;
  }
  return true;
});

/** 复制源内联定义（T-PERM-048）：baseline 绑定内联 → 从 conditions INLINE 实体深拷贝；
 *  草稿 add 内联 → 生效记录 inlineCondition；无内联 → null（复制走 managed 引用轨） */
const copySourceInline = computed<InlineConditionDef | null>(() => {
  const record = focusedRecord.value;
  if (!record) return null;
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode && c.source === "INLINE"
    );
    return condition
      ? {
          name: condition.name,
          conditionRules: condition.conditionRules,
          gatewayEvaluable: condition.gatewayEvaluable
        }
      : null;
  }
  return record.inlineCondition ? { ...record.inlineCondition } : null;
});''')

# 7. handleCopyConfirm passes sourceInline
rep('''  slotDraft.value = copySlotAttributes(slotDraft.value, {
    sourceSlot: slot,
    targetSlots,
    view: localView.value
  });
  localChanges.value = slotDraft.value.changes;
  copyVisible.value = false;
}''',
'''  slotDraft.value = copySlotAttributes(slotDraft.value, {
    sourceSlot: slot,
    targetSlots,
    view: localView.value,
    sourceInline: copySourceInline.value
  });
  localChanges.value = slotDraft.value.changes;
  copyVisible.value = false;
}''')

# 8. nodeSummary inline tag
rep('''  if (!record) return [];
  const tags: string[] = [];
  if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    tags.push(condition?.name ?? record.conditionCode);
  } else {
    tags.push("无条件");
  }''',
'''  if (!record) return [];
  const tags: string[] = [];
  if (record.inlineCondition != null) {
    // 草稿内联绑定（T-PERM-048）：定义未落库，无 code 可查——直接展示定义名
    tags.push(`内联:${record.inlineCondition.name || "未命名"}`);
  } else if (record.conditionCode != null) {
    const condition = props.conditions.find(
      c => c.code === record.conditionCode
    );
    // baseline 内联绑定按 code 命中 INLINE 实体（includeInline 加载），标注内联来源
    const inlineTag =
      condition?.source === "INLINE" ? `内联:${condition.name}` : null;
    tags.push(inlineTag ?? condition?.name ?? record.conditionCode);
  } else {
    tags.push("无条件");
  }''')

# 9. handleConfirm validation gate
rep('''/** 确定：expandSuspended 双路径展开（baseline→remove / add→取消变更组）后提交完整草稿 */
function handleConfirm() {
  emit("confirm", expandSuspended(slotDraft.value));
}''',
'''/** 确定：expandSuspended 双路径展开（baseline→remove / add→取消变更组）后提交完整草稿。
 *  内联定义在场时先校验（名称必填 + 规则完整性），不合法阻断提交（T-PERM-048）。 */
function handleConfirm() {
  const inlineError = validateInlineBeforeConfirm();
  if (inlineError != null) {
    ElMessage.warning(inlineError);
    return;
  }
  emit("confirm", expandSuspended(slotDraft.value));
}''')

# 10. template: picker props + inline panel
rep('''          <ConditionPicker
            :model-value="focusConditionCode"
            :conditions="conditions"
            :disabled="!focusedRecord"
            :placeholder="conditionPlaceholder"
            @update:model-value="handleConditionChange"
          />''',
'''          <ConditionPicker
            :model-value="focusConditionCode"
            :conditions="conditions"
            :disabled="!focusedRecord"
            :placeholder="conditionPlaceholder"
            :inline-active="focusInline != null"
            @update:model-value="handleConditionChange"
            @select-inline="handleSwitchToInline"
          />
          <div v-if="focusInline != null" class="inline-condition-editor">
            <div class="inline-editor-heading">
              <span class="field-label">内联条件（随本条授权保存创建）</span>
              <el-button size="small" text type="danger" @click="handleRemoveInline">
                移除内联条件
              </el-button>
            </div>
            <el-input
              :model-value="focusInline.name"
              placeholder="内联条件名称（必填）"
              size="small"
              class="inline-name-input"
              @update:model-value="handleInlineNameChange"
            />
            <ReConditionEditor
              ref="inlineEditorRef"
              :model-value="inlineRulesModel"
              :gateway-evaluable="focusInline.gatewayEvaluable === true"
              @update:model-value="handleInlineRulesChange"
            />
            <el-checkbox
              :model-value="focusInline.gatewayEvaluable === true"
              @update:model-value="handleInlineGatewayChange"
            >
              可下发 Gateway 评估
            </el-checkbox>
          </div>''')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('GrantDialog ok')
