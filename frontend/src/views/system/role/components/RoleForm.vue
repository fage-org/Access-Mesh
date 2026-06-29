<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import {
  ROLE_TYPE_CODE,
  ROLE_TYPE_LABEL,
  MANAGEABLE_ROLE_TYPES,
  type RoleTreeNode,
  type RoleTypeCode
} from "@/api/role-manage";
import { ArrowDown } from "@element-plus/icons-vue";
import type { RoleFormData } from "../utils/types";
import { isTypeRootNode } from "../utils/types";

defineOptions({
  name: "RoleForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自树节点） */
  initialData?: RoleTreeNode | null;
  /** 默认角色类型（新建时，从右键菜单的类型虚拟根派生） */
  defaultRoleTypeCode?: RoleTypeCode;
  /** 父节点（新建子角色时的父） */
  parentNode?: RoleTreeNode | null;
  /** 完整角色树（父角色选择器用） */
  roleTree?: RoleTreeNode[];
}>();

/** 表单默认值（新建时父角色默认挂到当前类型虚拟根） */
const defaultFormData = (): RoleFormData => ({
  roleTypeCode: props.defaultRoleTypeCode ?? ROLE_TYPE_CODE.BASIC_ROLE,
  name: "",
  externalId: "",
  parentId: props.parentNode?.id ?? null,
  status: 1,
  sortOrder: 0,
  extra: ""
});

const formData = reactive<RoleFormData>({ ...defaultFormData() });

/** 是否显示父角色选择器 */
const showParentTree = ref(false);

/** 树组件引用 */
const treeRef = ref();

/** 选中的父角色名称 */
const selectedParentName = ref("");

/** 角色类型选项（仅可管理类型） */
const roleTypeOptions = MANAGEABLE_ROLE_TYPES.map(code => ({
  label: ROLE_TYPE_LABEL[code],
  value: code
}));

/** 状态选项 */
const statusOptions = [
  { label: "启用", value: 1 },
  { label: "禁用", value: 0 }
];

/** 表单校验规则 */
const rules: FormRules = {
  roleTypeCode: [
    { required: true, message: "请选择角色类型", trigger: "change" }
  ],
  name: [
    { required: true, message: "请输入角色名称", trigger: "blur" },
    { min: 2, max: 64, message: "长度 2-64 字符", trigger: "blur" }
  ],
  externalId: [
    {
      pattern: /^[a-zA-Z0-9_-]*$/,
      message: "仅支持字母、数字、下划线、中划线",
      trigger: "blur"
    },
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ],
  sortOrder: [{ required: true, message: "请输入排序号", trigger: "blur" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

const formRef = ref<FormInstance>();

/** 弹窗标题 */
const dialogTitle = computed(() => {
  if (props.mode === "create") {
    return props.parentNode ? `新增子角色` : "新增角色";
  }
  return "编辑角色";
});

/** 父角色展示文本 */
const parentDisplay = computed(() => {
  if (selectedParentName.value) return selectedParentName.value;
  if (props.parentNode?.name) return props.parentNode.name;
  // 新建时默认挂到当前类型虚拟根
  const typeRoot = currentTypeRoot.value;
  if (typeRoot) return typeRoot.name;
  return ROLE_TYPE_LABEL[formData.roleTypeCode as RoleTypeCode] || "类型根";
});

/** 过滤父角色树：仅同类型节点（含类型虚拟根作为「挂到类型根」选项） */
function filterParentTree(nodes: RoleTreeNode[]): RoleTreeNode[] {
  const result: RoleTreeNode[] = [];
  for (const node of nodes) {
    const sameType = node.roleTypeCode === formData.roleTypeCode;
    if (!sameType) continue;
    const filteredChildren = node.children
      ? filterParentTree(node.children)
      : [];
    result.push({ ...node, children: filteredChildren });
  }
  return result;
}

/** 父角色树数据（按当前类型过滤；含类型虚拟根） */
const parentTreeData = computed(() => {
  if (!props.roleTree?.length) return [];
  return filterParentTree(props.roleTree);
});

/** 当前类型虚拟根（新建第一个角色时的默认父级） */
const currentTypeRoot = computed<RoleTreeNode | null>(() => {
  for (const node of parentTreeData.value) {
    if (isTypeRootNode(node) && node.roleTypeCode === formData.roleTypeCode) {
      return node;
    }
  }
  return null;
});

/** 过滤节点 */
function filterTreeNode(value: string, data: any) {
  if (!value) return true;
  return data.name.includes(value);
}

/** 父角色树节点点击 */
function onParentTreeNodeClick(node: RoleTreeNode) {
  selectedParentName.value = isTypeRootNode(node)
    ? ROLE_TYPE_LABEL[node.roleTypeCode] || node.name
    : node.name;
  formData.parentId = node.id;
  showParentTree.value = false;
}

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      roleTypeCode: props.initialData.roleTypeCode as RoleTypeCode,
      name: props.initialData.name,
      externalId: props.initialData.externalId ?? "",
      parentId: props.initialData.parentId,
      status: props.initialData.status,
      sortOrder: props.initialData.sortOrder,
      extra: props.initialData.extra ?? ""
    });
    selectedParentName.value = "";
  } else {
    Object.assign(formData, defaultFormData());
    selectedParentName.value = props.parentNode?.name ?? "";
  }
}

/** 获取表单数据 */
function getFormData(): RoleFormData {
  return { ...formData };
}

/** 表单校验 */
async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

/** 重置表单 */
function resetForm() {
  Object.assign(formData, defaultFormData());
  selectedParentName.value = "";
  formRef.value?.resetFields();
}

// 监听 initialData 变化（编辑时）
watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

// 类型变化时重置父角色为新类型虚拟根（不同类型树不同）
watch(
  () => formData.roleTypeCode,
  () => {
    if (props.mode === "create") {
      selectedParentName.value = "";
      // nextTick 后 currentTypeRoot 才会随 parentTreeData 更新
      const typeRoot = parentTreeData.value.find(
        n => isTypeRootNode(n) && n.roleTypeCode === formData.roleTypeCode
      );
      formData.parentId = typeRoot?.id ?? null;
    }
  }
);

defineExpose({
  validate,
  getFormData,
  resetForm,
  formData,
  dialogTitle
});
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="80px"
    class="role-form"
  >
    <el-form-item label="角色类型" prop="roleTypeCode">
      <el-select
        v-model="formData.roleTypeCode"
        placeholder="请选择角色类型"
        :disabled="mode === 'edit'"
        class="w-full!"
      >
        <el-option
          v-for="opt in roleTypeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="角色名称" prop="name">
      <el-input
        v-model="formData.name"
        placeholder="请输入角色名称"
        clearable
        maxlength="64"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="外部标识" prop="externalId">
      <el-input
        v-model="formData.externalId"
        placeholder="可空，用于与外部系统关联"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="父角色">
      <el-popover
        v-model:visible="showParentTree"
        trigger="click"
        placement="bottom-start"
        :width="360"
        :show-arrow="false"
        :teleported="true"
      >
        <template #reference>
          <el-input
            :model-value="parentDisplay"
            readonly
            placeholder="点击选择父角色（可空=类型根）"
            class="w-full! cursor-pointer"
          >
            <template #suffix>
              <el-icon><Arrow-Down /></el-icon>
            </template>
          </el-input>
        </template>
        <div class="parent-tree-popover">
          <el-scrollbar max-height="var(--popover-max-height)">
            <el-tree
              ref="treeRef"
              :data="parentTreeData"
              node-key="id"
              size="small"
              :props="{ children: 'children', label: 'name' }"
              default-expand-all
              :expand-on-click-node="false"
              :filter-node-method="filterTreeNode"
              highlight-current
              @node-click="(n: RoleTreeNode) => onParentTreeNodeClick(n)"
            >
              <template #default="{ data }">
                <span class="truncate" :title="data.name">{{ data.name }}</span>
              </template>
            </el-tree>
          </el-scrollbar>
        </div>
      </el-popover>
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

    <el-form-item label="状态" prop="status">
      <el-radio-group v-model="formData.status">
        <el-radio
          v-for="opt in statusOptions"
          :key="opt.value"
          :label="opt.value"
        >
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
    </el-form-item>

    <el-form-item label="扩展属性" prop="extra">
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="2"
        placeholder="可空，JSON 格式扩展属性"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.role-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.parent-tree-popover {
  max-height: 300px;
  overflow-y: auto;
}
</style>
