<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { message } from "@/utils/message";
import { type ConflictRuleResp, CONFLICT_TYPE } from "@/api/conflict-rule";
import {
  CONFLICT_TYPE_OPTIONS,
  createEmptyConflictForm,
  type ConflictFormData
} from "../utils/types";

defineOptions({ name: "ConflictForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的完整规则数据 */
  initialData?: ConflictRuleResp | null;
  /** 角色选项（BASIC_ROLE + GROUP_ROLE） */
  roleOptions: Array<{
    id: number;
    name: string;
    roleTypeCode: string;
  }>;
  /** 操作权限选项 */
  operationOptions: Array<{
    id: number;
    name: string;
    code: string;
    resourceTypeCode: string | null;
    resourceTypeName: string | null;
  }>;
  /** 资源类型选项（type_definition type_key=resource_type） */
  resourceTypeOptions: Array<{ typeValue: number; name: string }>;
}>();

const formData = reactive<ConflictFormData>({ ...createEmptyConflictForm() });
const formRef = ref<FormInstance>();

const isEdit = computed(() => props.mode === "edit");
const isRoleMutex = computed(
  () => formData.conflictType === CONFLICT_TYPE.ROLE_MUTEX
);

const rules = computed<FormRules>(() => ({}));

/** 字段完整性校验：根据类型校验对应字段已选 */
const fieldsValid = computed(() => {
  if (isRoleMutex.value) {
    return (
      formData.firstAbstractRoleId != null &&
      formData.secondAbstractRoleId != null
    );
  }
  return (
    formData.firstOperationPermissionId != null &&
    formData.secondOperationPermissionId != null
  );
});

/** 两个对象不能相同 */
const notSame = computed(() => {
  if (isRoleMutex.value) {
    return formData.firstAbstractRoleId !== formData.secondAbstractRoleId;
  }
  return (
    formData.firstOperationPermissionId !== formData.secondOperationPermissionId
  );
});

/** 切换冲突类型时清空对侧字段（避免提交无关字段） */
function onTypeChange() {
  if (isRoleMutex.value) {
    formData.firstOperationPermissionId = null;
    formData.secondOperationPermissionId = null;
    formData.resourceTypeValue = null;
  } else {
    formData.firstAbstractRoleId = null;
    formData.secondAbstractRoleId = null;
  }
}

/** 操作权限选项按 resourceTypeCode 分组（el-option-group 展示） */
const operationGroups = computed(() => {
  const groups = new Map<string, Array<(typeof props.operationOptions)[0]>>();
  for (const op of props.operationOptions) {
    const key = op.resourceTypeCode ?? "全局";
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(op);
  }
  return Array.from(groups.entries()).map(([key, ops]) => ({
    label: key,
    options: ops
  }));
});

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      conflictType: props.initialData
        .conflictType as ConflictFormData["conflictType"],
      firstAbstractRoleId: props.initialData.firstAbstractRoleId,
      secondAbstractRoleId: props.initialData.secondAbstractRoleId,
      firstOperationPermissionId: props.initialData.firstOperationPermissionId,
      secondOperationPermissionId:
        props.initialData.secondOperationPermissionId,
      resourceTypeValue: props.initialData.resourceTypeValue,
      description: props.initialData.description ?? ""
    });
  } else {
    Object.assign(formData, createEmptyConflictForm());
  }
}

function getFormData(): ConflictFormData {
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
    message(isRoleMutex.value ? "请选择两个角色" : "请选择两个操作权限");
    return false;
  }
  if (!notSame.value) {
    message("两个对象不能相同");
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
    class="conflict-form"
  >
    <el-form-item label="冲突类型" required>
      <el-radio-group v-model="formData.conflictType" @change="onTypeChange">
        <el-radio
          v-for="opt in CONFLICT_TYPE_OPTIONS"
          :key="opt.value"
          :label="opt.value"
        >
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- ROLE_MUTEX：角色选择 -->
    <template v-if="isRoleMutex">
      <el-form-item label="角色 A" required>
        <el-select
          v-model="formData.firstAbstractRoleId"
          placeholder="选择角色"
          filterable
          clearable
          class="w-full!"
        >
          <el-option
            v-for="r in roleOptions"
            :key="r.id"
            :label="`${r.name}（${r.roleTypeCode}）`"
            :value="r.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="角色 B" required>
        <el-select
          v-model="formData.secondAbstractRoleId"
          placeholder="选择角色"
          filterable
          clearable
          class="w-full!"
        >
          <el-option
            v-for="r in roleOptions"
            :key="r.id"
            :label="`${r.name}（${r.roleTypeCode}）`"
            :value="r.id"
          />
        </el-select>
      </el-form-item>
    </template>

    <!-- PERM_MUTEX：操作权限 + 资源类型 -->
    <template v-else>
      <el-form-item label="操作权限 A" required>
        <el-select
          v-model="formData.firstOperationPermissionId"
          placeholder="选择操作权限"
          filterable
          clearable
          class="w-full!"
        >
          <el-option-group
            v-for="grp in operationGroups"
            :key="grp.label"
            :label="grp.label"
          >
            <el-option
              v-for="op in grp.options"
              :key="op.id"
              :label="`${op.name}（${op.code}）`"
              :value="op.id"
            />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item label="操作权限 B" required>
        <el-select
          v-model="formData.secondOperationPermissionId"
          placeholder="选择操作权限"
          filterable
          clearable
          class="w-full!"
        >
          <el-option-group
            v-for="grp in operationGroups"
            :key="grp.label"
            :label="grp.label"
          >
            <el-option
              v-for="op in grp.options"
              :key="op.id"
              :label="`${op.name}（${op.code}）`"
              :value="op.id"
            />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item label="资源类型">
        <el-select
          v-model="formData.resourceTypeValue"
          placeholder="全部资源类型"
          clearable
          class="w-full!"
        >
          <el-option
            v-for="t in resourceTypeOptions"
            :key="t.typeValue"
            :label="t.name"
            :value="t.typeValue"
          />
        </el-select>
        <span class="form-tip">留空表示适用所有资源类型</span>
      </el-form-item>
    </template>

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
.conflict-form {
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
