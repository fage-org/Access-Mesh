<script setup lang="ts">
import { loadAllTypeDefs } from "@/api/type-def-options";
import {
  resourceDeclaration,
  updateResourceDeclaration
} from "@/utils/resource-association";
import { message } from "@/utils/message";
import { ENUM_PAGE_SIZE } from "@/constants/access";

import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { TYPE_KEY_LABEL, type TypeDefResp, type TypeKey } from "@/api/type-def";
import { getRoleList, type RoleResp } from "@/api/role-manage";
import type { TypeDefFormData } from "../utils/types";
import { TYPE_KEY_OPTIONS, isSystemPreset } from "../utils/types";

defineOptions({
  name: "TypeForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自表格行） */
  initialData?: TypeDefResp | null;
  /** 默认 typeKey（新建时，从搜索下拉传入） */
  defaultTypeKey?: TypeKey | "";
}>();

/** 表单默认值 */
const defaultFormData = (): TypeDefFormData => ({
  typeKey: props.defaultTypeKey ?? "",
  typeCode: "",
  name: "",
  description: "",
  isSystem: false,
  sortOrder: 0,
  extra: "",
  ownerRoleExternalId: ""
});

const formData = reactive<TypeDefFormData>({ ...defaultFormData() });

const formRef = ref<FormInstance>();

/** 是否编辑态 */
const isEdit = computed(() => props.mode === "edit");

/** 编辑态是否系统预置（name 只读，仅 description/sortOrder/extra 可改） */
const isSystemRow = computed(
  () => isEdit.value && isSystemPreset(props.initialData)
);

// ========== 类型所有者角色（T-PERM-062，仅 resource_type 表单项可见） ==========

/** 所有者选择器可见：resource_type 且（新建 或 编辑自定义类型——内置类型转授链收窄不暴露） */
const ownerSelectorVisible = computed(
  () =>
    formData.typeKey === "resource_type" &&
    (!isEdit.value || !formData.isSystem)
);

/** BASIC_ROLE 启用角色选项（所有者接收方；后端解析要求启用态） */
const ownerRoleOptions = ref<RoleResp[]>([]);
const ownerRoleLoading = ref(false);
let ownerRolesLoaded = false;

/** 分页循环拉全（后端单页上限 200，只取首页超页角色静默截断——conflict-rule loadAllRoles 先例）；
 *  list 端点无 enabledOnly 过滤，启用态在客户端过滤（后端所有者解析要求启用角色） */
async function loadOwnerRoles() {
  if (ownerRolesLoaded) return;
  ownerRoleLoading.value = true;
  try {
    const roles: RoleResp[] = [];
    let pageNum = 1;
    for (;;) {
      const res = await getRoleList({
        roleTypeCodes: ["BASIC_ROLE"],
        pageNum,
        pageSize: ENUM_PAGE_SIZE
      });
      roles.push(...res.items);
      if (!res.hasNext) break;
      pageNum++;
    }
    ownerRoleOptions.value = roles.filter(
      role => role.status === 1 && !!role.externalId
    );
    ownerRolesLoaded = true;
  } catch {
    ownerRoleOptions.value = [];
  } finally {
    ownerRoleLoading.value = false;
  }
}

watch(ownerSelectorVisible, visible => visible && loadOwnerRoles(), {
  immediate: true
});

/** extra JSON 内既有所有者指针（编辑态初值；解析失败按无指针处理，后端 20044 兜底） */
let originalOwnerPointer: {
  roleTypeCode: string;
  roleExternalId: string;
} | null = null;

function parseOwnerPointer(extra: string | null | undefined) {
  if (!extra) return null;
  try {
    const pointer = JSON.parse(extra)?.grantOriginRole;
    if (
      pointer &&
      typeof pointer.roleTypeCode === "string" &&
      typeof pointer.roleExternalId === "string"
    ) {
      return {
        roleTypeCode: pointer.roleTypeCode,
        roleExternalId: pointer.roleExternalId
      };
    }
  } catch {
    /* extra 坏 JSON：选择器按未指定处理，提交由后端校验拒绝 */
  }
  return null;
}

/**
 * 编辑态把选择器值同步进 extra JSON（后端按 extra.grantOriginRole 判定所有者变更并同事务
 * 迁移种子）；清空选择 = 不变更（指针无清除语义，回写原值）。
 * externalId 未变（含表单初始化触发）时回写原指针整体——保留原 roleTypeCode，
 * 防止非 BASIC_ROLE 所有者被静默改写（值域仅 BASIC_ROLE，但保留原指针整体回写防漂移）。
 */
function syncOwnerPointerIntoExtra(externalId: string) {
  if (!isEdit.value) return;
  const pointer = externalId
    ? externalId === originalOwnerPointer?.roleExternalId
      ? originalOwnerPointer
      : { roleTypeCode: "BASIC_ROLE", roleExternalId: externalId }
    : originalOwnerPointer;
  if (!pointer) return;
  let root: Record<string, unknown> = {};
  if (formData.extra.trim()) {
    try {
      const parsed = JSON.parse(formData.extra);
      if (parsed && typeof parsed === "object") root = parsed;
    } catch {
      return; // extra 坏 JSON 不强写，提交由后端校验拒绝
    }
  }
  root.grantOriginRole = pointer;
  formData.extra = JSON.stringify(root);
}

watch(
  () => formData.ownerRoleExternalId,
  externalId => {
    if (ownerSelectorVisible.value) syncOwnerPointerIntoExtra(externalId);
  }
);

const declaration = computed(() => resourceDeclaration(formData.extra));
const parentTypeOptions = ref<TypeDefResp[]>([]);
const parentTypesError = ref<string | null>(null);
watch(
  ownerSelectorVisible,
  async visible => {
    if (!visible) return;
    try {
      parentTypeOptions.value = await loadAllTypeDefs({
        typeKey: "resource_type"
      });
      parentTypesError.value = null;
    } catch {
      parentTypesError.value =
        "主类型候选加载失败，可输入类型编码；保存时仍由服务端核验";
    }
  },
  { immediate: true }
);
const eligibleParents = computed(() =>
  parentTypeOptions.value.filter(type => {
    const parent = resourceDeclaration(type.extra);
    return (
      type.typeCode !== formData.typeCode &&
      parent.mode === "NONE" &&
      parent.managedMode === "SYNC" &&
      (declaration.value.mode !== "EXCLUSIVE" ||
        parent.source === declaration.value.source)
    );
  })
);
function setDeclaration(key: string, value: unknown) {
  try {
    formData.extra = updateResourceDeclaration(formData.extra, key, value);
  } catch {
    message("请先将扩展属性修正为有效 JSON 对象", { type: "warning" });
  }
}
function setRelationMode(mode: string) {
  setDeclaration("associationMode", mode);
  if (mode === "NONE") setDeclaration("associationParentTypeCodes", []);
}
function setManagedMode(mode: string) {
  setDeclaration("managedMode", mode);
  if (mode === "MANAGED") setDeclaration("syncSourceService", undefined);
}
const declarationError = computed(() => {
  const value = declaration.value;
  if (value.mode === "UNKNOWN") return "关系声明无法识别，请检查扩展属性";
  if (value.mode === "EXCLUSIVE" && value.managedMode !== "SYNC")
    return "专属资源必须选择业务服务维护";
  if (value.mode === "SHARED" && value.managedMode !== "MANAGED")
    return "通用资源必须选择 IAM 管理";
  if (value.managedMode === "SYNC" && !value.source.trim())
    return "请填写维护资源的业务服务编码";
  if (value.mode !== "NONE" && value.parentTypes.length === 0)
    return "请选择允许使用此类型的主资源类型";
  return null;
});

/** 表单校验规则。
 *  typeCode 对外稳定编码，新建可空（留空由服务端按 <TYPEKEY大写>_<typeValue> 生成），
 *  格式与大写 @Pattern 对齐（T-PERM-066 大写族，OperationForm 同款；空串放行）。
 *  不含 typeValue（服务端自动分配，D1）。 */
const rules = computed<FormRules>(() => ({
  typeKey: [{ required: true, message: "请选择类型分组", trigger: "change" }],
  name: [
    { required: true, message: "请输入名称", trigger: "blur" },
    { min: 2, max: 64, message: "长度 2-64 字符", trigger: "blur" }
  ],
  typeCode: [
    {
      pattern: /^$|^[A-Z][A-Z0-9_]*$/,
      message: "仅支持大写字母、数字、下划线（留空自动生成）",
      trigger: "blur"
    },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  sortOrder: [{ required: true, message: "请输入排序号", trigger: "blur" }]
}));

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    originalOwnerPointer = parseOwnerPointer(props.initialData.extra);
    Object.assign(formData, {
      typeKey: props.initialData.typeKey as TypeKey,
      typeCode: props.initialData.typeCode,
      name: props.initialData.name,
      description: props.initialData.description ?? "",
      isSystem: props.initialData.isSystem,
      sortOrder: props.initialData.sortOrder,
      extra: props.initialData.extra ?? "",
      ownerRoleExternalId: originalOwnerPointer?.roleExternalId ?? ""
    });
  } else {
    originalOwnerPointer = null;
    Object.assign(formData, defaultFormData());
  }
}

/** 获取表单数据 */
function getFormData(): TypeDefFormData {
  return { ...formData };
}

/** 表单校验 */
async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    if (ownerSelectorVisible.value && declarationError.value) {
      message(declarationError.value, { type: "warning" });
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

/** 重置表单 */
function resetForm() {
  Object.assign(formData, defaultFormData());
  formRef.value?.resetFields();
}

// 监听 initialData 变化（编辑时）
watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

defineExpose({
  validate,
  getFormData,
  resetForm,
  formData
});
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="90px"
    class="type-form"
  >
    <el-form-item label="类型分组" prop="typeKey">
      <!-- 编辑态只读：typeKey 是稳定分组键，不可改 -->
      <el-input
        v-if="isEdit"
        :model-value="TYPE_KEY_LABEL[formData.typeKey] || formData.typeKey"
        readonly
        class="w-full!"
      />
      <el-select
        v-else
        v-model="formData.typeKey"
        placeholder="请选择类型分组"
        class="w-full!"
      >
        <el-option
          v-for="opt in TYPE_KEY_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="类型编码" prop="typeCode">
      <!--
        新建可填（留空=服务端按规则自动生成）；编辑只读：typeCode 是对外稳定编码，
        唯一索引 uk_type_definition_code 保证唯一，改它破坏既有引用（与 typeKey 同口径）。
      -->
      <el-input
        v-if="isEdit"
        :model-value="formData.typeCode"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.typeCode"
        placeholder="留空自动生成（建议填写以便外部系统引用）"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="名称" prop="name">
      <!-- 系统预置项编辑态 name 只读（不可改名） -->
      <el-input
        v-if="isSystemRow"
        :model-value="formData.name"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.name"
        placeholder="请输入名称"
        clearable
        maxlength="64"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="描述" prop="description">
      <el-input
        v-model="formData.description"
        type="textarea"
        :rows="2"
        placeholder="可空，类型用途说明"
      />
    </el-form-item>

    <el-form-item v-if="isEdit" label="系统预置" prop="isSystem">
      <!-- 仅编辑态只读展示。新建不暴露——系统预置走初始化种子，前端创建固定为租户自定义（isSystem=false） -->
      <el-tag :type="formData.isSystem ? 'info' : 'success'">
        {{ formData.isSystem ? "系统预置" : "租户自定义" }}
      </el-tag>
    </el-form-item>

    <el-form-item label="排序号" prop="sortOrder">
      <el-input-number
        v-model="formData.sortOrder"
        :min="0"
        :max="9999"
        controls-position="right"
        class="w-full!"
      />
    </el-form-item>

    <template v-if="ownerSelectorVisible">
      <el-form-item label="资源维护方">
        <el-select
          :model-value="declaration.managedMode"
          class="w-full!"
          @update:model-value="setManagedMode"
        >
          <el-option label="IAM 管理员" value="MANAGED" />
          <el-option label="业务服务" value="SYNC" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="declaration.managedMode === 'SYNC'" label="来源服务">
        <el-input
          :model-value="declaration.source"
          placeholder="例如 example-service"
          @update:model-value="
            value => setDeclaration('syncSourceService', value)
          "
        />
      </el-form-item>
      <el-form-item label="关系性质">
        <el-select
          :model-value="declaration.mode"
          class="w-full!"
          @update:model-value="setRelationMode"
        >
          <el-option label="普通资源（不声明归属或引用）" value="NONE" />
          <el-option
            label="专属资源（每个实例归属一个主资源）"
            value="EXCLUSIVE"
          />
          <el-option label="通用资源（主资源整类引用）" value="SHARED" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="declaration.mode === 'EXCLUSIVE' || declaration.mode === 'SHARED'"
        label="允许主类型"
      >
        <el-select
          :model-value="declaration.parentTypes"
          multiple
          filterable
          allow-create
          default-first-option
          :multiple-limit="200"
          class="w-full!"
          placeholder="选择或输入主资源类型编码"
          @update:model-value="
            value => setDeclaration('associationParentTypeCodes', value)
          "
        >
          <el-option
            v-for="type in eligibleParents"
            :key="type.id"
            :label="`${type.name}（${type.typeCode}）`"
            :value="type.typeCode"
          />
        </el-select>
      </el-form-item>
      <el-alert
        v-if="declarationError || parentTypesError"
        :title="declarationError ?? parentTypesError ?? ''"
        type="warning"
        :closable="false"
      />
      <el-form-item>
        <span class="owner-role-hint"
          >专属实例与关系由业务服务发布；通用实例由 IAM
          维护，业务服务只发布整类引用。已使用的类型不能直接改用途，保存时由服务端检查。</span
        >
      </el-form-item>
    </template>
    <el-form-item label="扩展属性" prop="extra">
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="2"
        placeholder="可空，JSON 格式扩展属性"
      />
    </el-form-item>

    <!-- T-PERM-062 类型所有者（仅 resource_type 可见；创建即向所有者落首授基座，
         编辑变更所有者=同事务迁移授权根种子） -->
    <el-form-item
      v-if="ownerSelectorVisible"
      label="所有者角色"
      prop="ownerRoleExternalId"
    >
      <el-select
        v-model="formData.ownerRoleExternalId"
        :loading="ownerRoleLoading"
        placeholder="缺省引导角色（bootstrap-admin）"
        clearable
        filterable
        class="w-full!"
      >
        <el-option
          v-for="role in ownerRoleOptions"
          :key="role.id"
          :label="`${role.name}（${role.externalId ?? role.id}）`"
          :value="role.externalId!"
        />
      </el-select>
      <div v-if="isEdit" class="owner-role-hint">
        变更所有者将同事务迁移该类型的授权根种子（旧所有者行清理、新所有者补齐全部操作位）
      </div>
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.type-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.owner-role-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
