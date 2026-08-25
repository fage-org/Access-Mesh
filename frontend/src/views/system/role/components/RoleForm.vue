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

defineOptions({
  name: "RoleForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据（来自树节点） */
  initialData?: RoleTreeNode | null;
  /** 默认角色类型（新建时，从新增下拉传入） */
  defaultRoleTypeCode?: RoleTypeCode;
  /** 父节点（新建子角色时的父） */
  parentNode?: RoleTreeNode | null;
  /** 完整角色树（父角色选择器用） */
  roleTree?: RoleTreeNode[];
}>();

/**
 * 表单默认值。
 *
 * C2 后树为扁平森林（无类型虚拟根）：新建顶层角色 parentId=null。
 * - 新建顶层（onAddByType 传 node=null）：parentId=null。
 * - 新建子角色（右键节点）：parentId=node.id。
 */
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

/** 选中的父角色名称（点选 popover 后填充） */
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

/**
 * 表单校验规则。externalId 对可管理类型（T-PERM-043 后仅 BASIC_ROLE）新建必填——业务键依赖
 *（schema 唯一索引 uk_abstract_role_external；原额外角色 DTO 依赖随 T-PERM-043 退役）。
 */
const rules = computed<FormRules>(() => ({
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
    { max: 128, message: "最长 128 字符", trigger: "blur" },
    // 可管理类型 + 新建态必填（额外角色业务键依赖）；编辑态 externalId 只读不校验
    ...(props.mode === "create" &&
    MANAGEABLE_ROLE_TYPES.includes(formData.roleTypeCode as RoleTypeCode)
      ? [
          {
            required: true,
            message: "本类型角色需填写外部标识（额外角色功能依赖）",
            trigger: "blur"
          }
        ]
      : [])
  ],
  sortOrder: [{ required: true, message: "请输入排序号", trigger: "blur" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
}));

const formRef = ref<FormInstance>();

/** 弹窗标题 */
const dialogTitle = computed(() => {
  if (props.mode === "create") {
    return props.parentNode ? `新增子角色` : "新增角色";
  }
  return "编辑角色";
});

/**
 * 父角色展示文本。
 * - parentId=null → "（顶层）"（C2 后顶层即森林根，合法可选项）。
 * - 点选过 popover → 显示所选父角色名。
 * - 新建子角色 → 显示 parentNode 名。
 * - 编辑态 → 反查父节点名，找不到（已删/顶层）→ "（顶层）"。
 */
const parentDisplay = computed(() => {
  if (formData.parentId === null) return "（顶层）";
  if (selectedParentName.value) return selectedParentName.value;
  if (props.parentNode?.name) return props.parentNode.name;
  // 编辑态：从 roleTree 反查父节点名
  const parent = props.roleTree
    ? findNodeById(props.roleTree, formData.parentId)
    : null;
  return parent?.name ?? "（顶层）";
});

/** 过滤父角色树：仅同类型节点（C2 后无类型虚拟根，森林里同类型真实角色） */
function filterParentTree(nodes: RoleTreeNode[]): RoleTreeNode[] {
  const result: RoleTreeNode[] = [];
  for (const node of nodes) {
    const sameType = node.roleTypeCode === formData.roleTypeCode;
    if (!sameType) continue;
    const filteredChildren = node.children
      ? filterParentTree(node.children)
      : [];
    // 过滤掉自己作为父（编辑态防选自身），保留其余同类型子树
    const isSelf = props.mode === "edit" && props.initialData?.id === node.id;
    if (isSelf) continue;
    result.push({ ...node, children: filteredChildren });
  }
  return result;
}

/** 父角色树数据（按当前类型过滤；C2 后即同类型真实角色森林） */
const parentTreeData = computed(() => {
  if (!props.roleTree?.length) return [];
  return filterParentTree(props.roleTree);
});

/** 在树中按 id 查找节点 */
function findNodeById(nodes: RoleTreeNode[], id: number): RoleTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

/** 过滤节点 */
function filterTreeNode(value: string, data: any) {
  if (!value) return true;
  return data.name.includes(value);
}

/** 父角色树节点点击 */
function onParentTreeNodeClick(node: RoleTreeNode) {
  selectedParentName.value = node.name;
  formData.parentId = node.id;
  showParentTree.value = false;
}

/** 设为顶层（parentId=null） */
function setParentToTop() {
  selectedParentName.value = "";
  formData.parentId = null;
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

/** 是否编辑态（父角色只读，P3） */
const isEdit = computed(() => props.mode === "edit");

// 监听 initialData 变化（编辑时）
watch(
  () => props.initialData,
  () => initFormData(),
  { immediate: true }
);

// 类型变化时重置父角色为顶层（C2 后无类型根，跨类型父子不合法 → 回退顶层）
watch(
  () => formData.roleTypeCode,
  () => {
    if (props.mode === "create") {
      selectedParentName.value = "";
      formData.parentId = null;
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
      <!-- 编辑态只读：externalId 是业务键/定位锚点（schema 唯一索引 uk_abstract_role_external，
           额外角色 DTO 用它定位角色），改它会破坏既有引用，与父角色只读同口径（评审 P2-externalId）。
           历史空 externalId 角色的修复应在后端切业务键后用专门手段处理，非本页编辑职责。 -->
      <el-input
        v-if="isEdit"
        :model-value="formData.externalId || '（无）'"
        readonly
        placeholder="（无）"
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.externalId"
        placeholder="必填，用于与外部系统关联"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="父角色">
      <!-- 编辑态：父角色只读（P3，层级只走拖拽/move，编辑不改 parentId） -->
      <el-input
        v-if="isEdit"
        :model-value="parentDisplay"
        readonly
        placeholder="（顶层）"
        class="w-full!"
      />
      <!-- 新建态：popover 树选同类型真实角色，或设为顶层 -->
      <el-popover
        v-else
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
            placeholder="点击选择父角色（可空=顶层）"
            class="w-full! cursor-pointer"
          >
            <template #suffix>
              <el-icon><Arrow-Down /></el-icon>
            </template>
          </el-input>
        </template>
        <div class="parent-tree-popover">
          <div class="parent-tree-actions">
            <el-button link type="primary" size="small" @click="setParentToTop">
              设为顶层
            </el-button>
          </div>
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
            <div v-if="parentTreeData.length === 0" class="empty-tip">
              暂无可选父角色（当前类型无其他角色）
            </div>
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

  .parent-tree-actions {
    display: flex;
    justify-content: flex-end;
    margin-bottom: var(--space-1);
  }

  .empty-tip {
    padding: var(--space-3);
    font-size: 13px;
    color: var(--el-text-color-secondary);
    text-align: center;
  }
}
</style>
