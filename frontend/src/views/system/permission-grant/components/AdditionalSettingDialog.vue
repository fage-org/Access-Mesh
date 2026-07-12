<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { inject } from "vue";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_GRANT_PERMS } from "../utils/perms";
import { usePermissionGrant } from "../utils/hook";
import { permCellKey, type AdditionalSettingContext } from "../utils/types";
import ConditionForm from "@/views/system/permission-condition/components/ConditionForm.vue";
import {
  createCondition,
  type ConditionResp
} from "@/api/permission-condition";
import {
  serializeRules,
  summarizeRules,
  type ConditionFormData
} from "@/views/system/permission-condition/utils/types";

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

// 内联新建条件（决策点 1：直接复用 ConditionForm）
const showCreateCondition = ref(false);
const conditionFormRef = ref<InstanceType<typeof ConditionForm>>();
const creating = ref(false);

const canCreateCondition = computed(() =>
  hasPerms(PERMISSION_GRANT_PERMS.CONDITION_CREATE)
);

watch(
  () => props.context,
  ctx => {
    if (ctx) {
      selectedCondition.value = ctx.draft.conditionCode;
      canGrant.value = ctx.draft.canGrant;
      showCreateCondition.value = false;
    }
  },
  { immediate: true }
);

const conditionOptions = computed(() => store.conditions.value);

const parentInfo = computed(() => {
  if (!props.context) return "";
  const d = props.context.draft;
  const res =
    d.scopeMode === "ALL" ? "全部" : (d.resourceName ?? d.resourceCode ?? "");
  return `${d.resourceTypeCode} / ${res} / ${d.operationCode} / ${d.scopeMode}`;
});

function onSave() {
  if (!props.context) return;
  const attrs = {
    conditionCode: selectedCondition.value || null,
    canGrant: canGrant.value
  };
  // 补充修复：子权限用 setChildCellAttr，主权限用 setMainCellAttr
  if (props.context.isChild && props.context.childKey) {
    store.setChildCellAttr(props.context.childKey, attrs);
  } else {
    store.setMainCellAttr(props.context.key, attrs);
  }
  message("附加设置已应用（保存后生效）", { type: "success" });
  visible.value = false;
}

async function onCreateCondition() {
  if (!conditionFormRef.value) return;
  creating.value = true;
  try {
    const valid = await conditionFormRef.value.validate();
    if (!valid) return;
    const formData: ConditionFormData = conditionFormRef.value.getFormData();
    const resp: ConditionResp = await createCondition({
      code: formData.code,
      name: formData.name,
      conditionRules: serializeRules(formData.rules),
      enabled: formData.enabled,
      gatewayEvaluable: formData.gatewayEvaluable,
      description: formData.description
    });
    // 本地追加到条件列表
    store.conditions.value = [
      ...store.conditions.value,
      {
        conditionId: resp.id,
        code: resp.code,
        name: resp.name,
        enabled: resp.enabled,
        gatewayEvaluable: resp.gatewayEvaluable,
        summary: summarizeRules(resp.conditionRules)
      }
    ];
    selectedCondition.value = resp.code;
    showCreateCondition.value = false;
    message("条件创建成功", { type: "success" });
  } catch (e) {
    message(e instanceof Error ? e.message : "创建条件失败", {
      type: "error"
    });
  } finally {
    creating.value = false;
  }
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

      <!-- 条件选择 -->
      <div class="setting-section">
        <div class="section-title">
          <span>权限条件</span>
          <el-button
            v-if="canCreateCondition && context?.supportsCondition !== false"
            link
            type="primary"
            size="small"
            @click="showCreateCondition = !showCreateCondition"
          >
            {{ showCreateCondition ? "取消新建" : "新建条件" }}
          </el-button>
        </div>

        <el-select
          v-if="!showCreateCondition"
          v-model="selectedCondition"
          placeholder="无条件"
          clearable
          filterable
          :disabled="context?.supportsCondition === false"
          class="condition-select"
        >
          <el-option
            v-for="c in conditionOptions"
            :key="c.code"
            :label="`${c.name}${c.enabled ? '' : '（已停用）'}`"
            :value="c.code"
            :disabled="!c.enabled && c.code !== selectedCondition"
          >
            <span>{{ c.name }}</span>
            <span class="cond-summary">{{ c.summary }}</span>
          </el-option>
        </el-select>

        <!-- 内联新建条件（复用 ConditionForm） -->
        <div v-else class="condition-create">
          <ConditionForm ref="conditionFormRef" mode="create" />
          <div class="create-actions">
            <el-button size="small" @click="showCreateCondition = false"
              >取消</el-button
            >
            <el-button
              type="primary"
              size="small"
              :loading="creating"
              @click="onCreateCondition"
              >创建并选中</el-button
            >
          </div>
        </div>
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
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.condition-select {
  width: 100%;
}

.cond-summary {
  float: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.condition-create {
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.create-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-2);
}
</style>
