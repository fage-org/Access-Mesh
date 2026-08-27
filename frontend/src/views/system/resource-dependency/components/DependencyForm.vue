<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { message } from "@/utils/message";
import { type ResourceDependencyResp } from "@/api/resource-dependency";
import {
  type ResourceTreeNode,
  type OperationPermissionResp
} from "@/api/resource-operation";
import {
  createEmptyDependencyForm,
  type DependencyFormData
} from "../utils/types";
import { hasBit } from "@/utils/bit-ops";

defineOptions({ name: "DependencyForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的完整依赖数据 */
  initialData?: ResourceDependencyResp | null;
  /** 资源类型选项 */
  resourceTypeOptions: Array<{ value: string; label: string }>;
  /** 全量资源列表（扁平） */
  resourceList: ResourceTreeNode[];
  /** 全量操作权限列表 */
  operationList: OperationPermissionResp[];
}>();

const formData = reactive<DependencyFormData>({
  ...createEmptyDependencyForm()
});
const formRef = ref<FormInstance>();

const isEdit = computed(() => props.mode === "edit");

// ========== 引用数据映射（组件内自包含） ==========

const resourceTypeById = computed(() => {
  const map = new Map<number, string>();
  for (const r of props.resourceList) {
    map.set(r.id, r.resourceTypeCode);
  }
  return map;
});

/** 操作位 -> 操作码列表（按资源类型拆解，含全局操作；BigInt 位与兼容 63 位）。
 *  P1 修复：typeCode 隔离避免跨类型同 bit 误匹配；hasBit 避免 32 位截断。 */
function bitsToOpCodes(
  bits: number | string | null,
  typeCode: string | null
): string[] {
  if (bits == null || bits === 0) return [];
  const codes: string[] = [];
  for (const op of props.operationList) {
    if (op.binaryBit == null) continue;
    if (op.resourceTypeCode !== typeCode && op.resourceTypeCode != null)
      continue;
    if (hasBit(bits, op.binaryBit)) codes.push(op.code);
  }
  return codes;
}

// ========== 联动选项 ==========

const sourceResourceOptions = computed(() =>
  props.resourceList
    .filter(r => r.resourceTypeCode === formData.sourceResourceTypeCode)
    .map(r => ({ id: r.id, name: r.name, code: r.code }))
);

const targetResourceOptions = computed(() =>
  props.resourceList
    .filter(r => r.resourceTypeCode === formData.targetResourceTypeCode)
    .map(r => ({ id: r.id, name: r.name, code: r.code }))
);

/** 按源资源类型过滤的操作（含全局操作 resourceTypeCode=null） */
const sourceOperationOptions = computed(() =>
  props.operationList.filter(
    op =>
      op.resourceTypeCode === formData.sourceResourceTypeCode ||
      op.resourceTypeCode == null
  )
);

/** 按目标资源类型过滤的操作（含全局操作） */
const targetOperationOptions = computed(() =>
  props.operationList.filter(
    op =>
      op.resourceTypeCode === formData.targetResourceTypeCode ||
      op.resourceTypeCode == null
  )
);

// ========== 校验 ==========

const rules = computed<FormRules>(() => ({}));

const fieldsValid = computed(() => {
  return (
    formData.sourceResourceTypeCode != null &&
    formData.sourceResourceEntityId != null &&
    formData.targetResourceTypeCode != null &&
    formData.targetResourceEntityId != null &&
    formData.requiredOperationCodes.length > 0
  );
});

const notSame = computed(() => {
  if (
    formData.sourceResourceEntityId == null ||
    formData.targetResourceEntityId == null
  )
    return true;
  return formData.sourceResourceEntityId !== formData.targetResourceEntityId;
});

// ========== 联动处理 ==========

function onSourceTypeChange() {
  formData.sourceResourceEntityId = null;
  formData.sourceOperationCodes = [];
}

function onTargetTypeChange() {
  formData.targetResourceEntityId = null;
  formData.requiredOperationCodes = [];
}

// ========== 初始化 ==========

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    const d = props.initialData;
    Object.assign(formData, {
      sourceResourceEntityId: d.resourceEntityId,
      sourceResourceTypeCode:
        resourceTypeById.value.get(d.resourceEntityId) ?? null,
      targetResourceEntityId: d.dependsOnResourceEntityId,
      targetResourceTypeCode:
        resourceTypeById.value.get(d.dependsOnResourceEntityId) ?? null,
      sourceOperationCodes: bitsToOpCodes(
        d.sourceOperationBits,
        resourceTypeById.value.get(d.resourceEntityId) ?? null
      ),
      requiredOperationCodes: bitsToOpCodes(
        d.requiredOperationBits,
        resourceTypeById.value.get(d.dependsOnResourceEntityId) ?? null
      ),
      autoGrant: d.autoGrant,
      description: d.description ?? ""
    });
  } else {
    Object.assign(formData, createEmptyDependencyForm());
  }
}

function getFormData(): DependencyFormData {
  return { ...formData };
}

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
  } catch {
    return false;
  }
  if (!fieldsValid.value) {
    message("请选择源资源、目标资源并指定要求操作");
    return false;
  }
  if (!notSame.value) {
    message("源资源与目标资源不能相同");
    return false;
  }
  return true;
}

watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

defineExpose({ validate, getFormData });
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="100px"
    class="dependency-form"
  >
    <el-form-item label="源资源类型" required>
      <el-select
        v-model="formData.sourceResourceTypeCode"
        placeholder="选择资源类型"
        clearable
        class="w-full!"
        @change="onSourceTypeChange"
      >
        <el-option
          v-for="t in resourceTypeOptions"
          :key="t.value"
          :label="t.label"
          :value="t.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="源资源" required>
      <el-select
        v-model="formData.sourceResourceEntityId"
        placeholder="选择源资源（被授权资源）"
        filterable
        clearable
        :disabled="!formData.sourceResourceTypeCode"
        class="w-full!"
      >
        <el-option
          v-for="r in sourceResourceOptions"
          :key="r.id"
          :label="`${r.name}（${r.code}）`"
          :value="r.id"
        />
      </el-select>
      <span class="form-tip">授权该资源时触发依赖补全</span>
    </el-form-item>

    <el-form-item label="目标资源类型" required>
      <el-select
        v-model="formData.targetResourceTypeCode"
        placeholder="选择资源类型"
        clearable
        class="w-full!"
        @change="onTargetTypeChange"
      >
        <el-option
          v-for="t in resourceTypeOptions"
          :key="t.value"
          :label="t.label"
          :value="t.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="目标资源" required>
      <el-select
        v-model="formData.targetResourceEntityId"
        placeholder="选择目标资源（被依赖资源）"
        filterable
        clearable
        :disabled="!formData.targetResourceTypeCode"
        class="w-full!"
      >
        <el-option
          v-for="r in targetResourceOptions"
          :key="r.id"
          :label="`${r.name}（${r.code}）`"
          :value="r.id"
        />
      </el-select>
      <span class="form-tip">自动补全该资源的权限</span>
    </el-form-item>

    <el-form-item label="触发操作">
      <el-select
        v-model="formData.sourceOperationCodes"
        placeholder="留空表示任意操作触发"
        multiple
        clearable
        collapse-tags
        collapse-tags-tooltip
        :disabled="!formData.sourceResourceTypeCode"
        class="w-full!"
      >
        <el-option
          v-for="op in sourceOperationOptions"
          :key="op.id"
          :label="`${op.name}（${op.code}）`"
          :value="op.code"
        />
      </el-select>
      <span class="form-tip">源资源授权含这些操作时触发，空=任意操作</span>
    </el-form-item>

    <el-form-item label="要求操作" required>
      <el-select
        v-model="formData.requiredOperationCodes"
        placeholder="选择目标资源需补全的操作"
        multiple
        clearable
        collapse-tags
        collapse-tags-tooltip
        :disabled="!formData.targetResourceTypeCode"
        class="w-full!"
      >
        <el-option
          v-for="op in targetOperationOptions"
          :key="op.id"
          :label="`${op.name}（${op.code}）`"
          :value="op.code"
        />
      </el-select>
      <span class="form-tip">目标资源需要自动补全的操作权限</span>
    </el-form-item>

    <el-form-item label="自动授权">
      <el-switch v-model="formData.autoGrant" disabled />
      <span class="form-tip">未实现（预留字段）：自动补全依赖授权暂缓（T-PERM-035），后端拒绝保存 true（20048）</span>
    </el-form-item>

    <el-form-item label="描述">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空"
        maxlength="512"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.dependency-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.form-tip {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
