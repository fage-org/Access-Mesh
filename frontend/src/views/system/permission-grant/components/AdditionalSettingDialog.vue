<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { inject } from "vue";
import { message } from "@/utils/message";
import { usePermissionGrant } from "../utils/hook";
import { permCellKey, type AdditionalSettingContext } from "../utils/types";
import { ReConditionPicker } from "@/components/ReConditionPicker";
import { type ConditionOption } from "@/api/permission-grant";

defineOptions({ name: "AdditionalSettingDialog" });

const props = defineProps<{
  modelValue: boolean;
  context: AdditionalSettingContext | null;
}>();
const emit = defineEmits<{
  "update:modelValue": [v: boolean];
}>();

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const visible = computed({
  get: () => props.modelValue,
  set: v => emit("update:modelValue", v)
});

// 条件选择
const selectedCondition = ref<string | null>(null);
const canGrant = ref(false);
const pickerRef = ref<InstanceType<typeof ReConditionPicker>>();

watch(
  () => props.context,
  ctx => {
    if (ctx) {
      selectedCondition.value = ctx.draft.conditionCode;
      canGrant.value = ctx.draft.canGrant;
      // 上下文切换时复位内联新建态，避免跨权限残留
      pickerRef.value?.reset();
    }
  },
  { immediate: true }
);

// 弹窗关闭时复位内联新建态（P2：showCreate 不随外层关闭重置）
watch(visible, v => {
  if (!v) pickerRef.value?.reset();
});

const conditionOptions = computed(() => store.conditions.value);

const parentInfo = computed(() => {
  if (!props.context) return "";
  const d = props.context.draft;
  const res =
    d.scopeMode === "ALL" ? "全部" : (d.resourceName ?? d.resourceCode ?? "");
  return `${d.resourceTypeCode} / ${res} / ${d.operationCode} / ${d.scopeMode}`;
});

/** ReConditionPicker 内联创建成功 -> 追加到 store.conditions（保持单源） */
function onCreated(option: ConditionOption) {
  store.conditions.value = [...store.conditions.value, option];
}

function onSave() {
  if (!props.context) return;
  // 停用条件保存前阻断（ReConditionPicker.validate）
  if (pickerRef.value && !pickerRef.value.validate()) return;
  const attrs = {
    conditionCode: selectedCondition.value || null,
    canGrant: canGrant.value
  };
  // 子权限用 setChildCellAttr，主权限用 setMainCellAttr
  if (props.context.isChild && props.context.childKey) {
    store.setChildCellAttr(props.context.childKey, attrs);
  } else {
    store.setMainCellAttr(props.context.key, attrs);
  }
  message("附加设置已应用（保存后生效）", { type: "success" });
  visible.value = false;
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="附加设置"
    width="560px"
    :close-on-click-modal="false"
  >
    <template v-if="context">
      <div class="setting-header">
        <span class="setting-label">权限：</span>
        <span class="setting-value">{{ parentInfo }}</span>
      </div>

      <el-divider />

      <!-- 条件选择（ReConditionPicker：搜索 + 摘要 + 启用过滤 + gatewayEvaluable 状态 + 内联新建） -->
      <div class="setting-section">
        <div class="section-title">权限条件</div>
        <ReConditionPicker
          ref="pickerRef"
          v-model="selectedCondition"
          :conditions="conditionOptions"
          :disabled="context?.supportsCondition === false"
          @created="onCreated"
        />
      </div>

      <!-- 允许继续授权 -->
      <div class="setting-section">
        <div class="section-title">允许继续授权（canGrant）</div>
        <el-switch
          v-model="canGrant"
          :disabled="context?.supportsDelegation === false"
        />
        <span class="section-hint"
          >开启后该权限持有者可将同一权限授权给他人</span
        >
        <el-tag
          v-if="context?.supportsDelegation === false"
          size="small"
          type="info"
          effect="plain"
          >该资源类型不支持转授权</el-tag
        >
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="context?.readonly" @click="onSave"
        >应用</el-button
      >
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.setting-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.setting-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.setting-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.setting-section {
  margin-bottom: var(--space-4);
}

.section-title {
  margin-bottom: var(--space-2);
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.section-hint {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
