<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { getOrgTree } from "@/api/user-manage";
import type { OrgTreeNode } from "@/api/user-manage";
import { ArrowDown } from "@element-plus/icons-vue";

defineOptions({
  name: "OrgForm"
});

/** 组织表单数据 */
export interface OrgFormData {
  orgName: string;
  code: string;
  orgType: number;
  parentOrgId: number | null;
  status: number;
  sort: number;
}

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的初始数据 */
  initialData?: Partial<OrgFormData>;
  /** 父组织ID（新增子组织时） */
  parentOrgId?: number | null;
  /** 父组织名称（展示用） */
  parentOrgName?: string;
}>();

const emit = defineEmits<{
  "update:formData": [value: OrgFormData];
}>();

/** 表单默认值 */
const defaultFormData = (): OrgFormData => ({
  orgName: "",
  code: "",
  orgType: 1, // 新增普通组织固定为 1
  parentOrgId: props.parentOrgId ?? null,
  status: 1,
  sort: 0
});

const formData = reactive<OrgFormData>({ ...defaultFormData() });

/** 是否显示组织树选择器 */
const showOrgTree = ref(false);

/** 组织树数据 */
const orgTreeData = ref<OrgTreeNode[]>([]);

/** 搜索过滤文本 */
const filterText = ref("");

/** 树组件引用 */
const treeRef = ref();

/** 选中的父组织名称 */
const selectedParentOrgName = ref("");

/** 组织类型选项 */
const orgTypeOptions = [
  { label: "普通组织", value: 1 },
  { label: "岗位", value: 2 }
];

/** 状态选项 */
const statusOptions = [
  { label: "启用", value: 1 },
  { label: "禁用", value: 0 }
];

/** 表单校验规则 */
const rules: FormRules = {
  orgName: [
    { required: true, message: "请输入组织名称", trigger: "blur" },
    { min: 2, max: 64, message: "长度 2-64 字符", trigger: "blur" }
  ],
  code: [
    { required: true, message: "请输入组织编码", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_-]+$/,
      message: "仅支持字母、数字、下划线、中划线",
      trigger: "blur"
    },
    { min: 2, max: 32, message: "长度 2-32 字符", trigger: "blur" }
  ],
  orgType: [{ required: true, message: "请选择组织类型", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }],
  sort: [{ required: true, message: "请输入排序号", trigger: "blur" }]
};

const formRef = ref<FormInstance>();

/** 弹窗标题 */
const dialogTitle = computed(() => {
  if (props.mode === "create") {
    return props.parentOrgId ? "新增子组织" : "新增组织";
  }
  return "编辑组织";
});

/** 父组织展示文本 */
const parentOrgDisplay = computed(() => {
  if (selectedParentOrgName.value) {
    return selectedParentOrgName.value;
  }
  if (props.parentOrgName) {
    return props.parentOrgName;
  }
  return "根组织";
});

/** 过滤岗位节点（递归过滤，只保留 orgType=1 的普通组织） */
function filterPositionNodes(nodes: OrgTreeNode[]): OrgTreeNode[] {
  const result: OrgTreeNode[] = [];
  for (const node of nodes) {
    // 只保留普通组织（orgType=1），过滤掉岗位（orgType=2）
    if (node.orgType === 1) {
      const filteredChildren = node.children
        ? filterPositionNodes(node.children)
        : [];
      result.push({
        ...node,
        children: filteredChildren
      });
    }
  }
  return result;
}

/** 加载组织树数据 */
async function loadOrgTreeData() {
  try {
    // v1.4 后端 /org/tree 强制要求 orgType；新增组织表单的父组织选择器只关心普通组织（orgType=1），
    // 后端已按类型过滤，下方 filterPositionNodes 二次过滤保留为防御性代码。
    const treeData = await getOrgTree({ orgType: 1 });
    // 过滤掉岗位节点（orgType=2），只保留普通组织
    orgTreeData.value = filterPositionNodes(treeData);
  } catch (error) {
    console.error("加载组织树失败:", error);
  }
}

/** 过滤组织节点 */
function filterOrgNode(value: string, data: any) {
  if (!value) return true;
  return data.orgName.includes(value);
}

/** 处理组织树节点选择 */
function onOrgTreeSelect(orgId: number | null) {
  if (orgId) {
    formData.parentOrgId = orgId;
  } else {
    formData.parentOrgId = null;
  }
  showOrgTree.value = false;
}

/** 处理组织树节点点击（获取名称） */
function onOrgTreeNodeClick(node: OrgTreeNode) {
  selectedParentOrgName.value = node.orgName;
  formData.parentOrgId = node.id;
  showOrgTree.value = false;
}

/** 初始化表单数据 */
function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      orgName: props.initialData.orgName ?? "",
      code: props.initialData.code ?? "",
      orgType: props.initialData.orgType ?? 1,
      parentOrgId: props.initialData.parentOrgId ?? null,
      status: props.initialData.status ?? 1,
      sort: props.initialData.sort ?? 0
    });
    if (props.parentOrgName) {
      selectedParentOrgName.value = props.parentOrgName;
    }
  } else {
    // 创建模式：重置为默认值
    Object.assign(formData, defaultFormData());
    selectedParentOrgName.value = props.parentOrgName ?? "";
  }
}

/** 获取表单数据 */
function getFormData(): OrgFormData {
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
  selectedParentOrgName.value = "";
  formRef.value?.resetFields();
}

// 监听 initialData 变化（编辑时）
watch(
  () => props.initialData,
  () => {
    initFormData();
  },
  { immediate: true }
);

// 监听 parentOrgId 变化
watch(
  () => props.parentOrgId,
  val => {
    formData.parentOrgId = val ?? null;
  },
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
    label-width="80px"
    class="org-form"
  >
    <el-form-item label="组织名称" prop="orgName">
      <el-input
        v-model="formData.orgName"
        placeholder="请输入组织名称"
        clearable
        maxlength="64"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="组织编码" prop="code">
      <el-input
        v-model="formData.code"
        placeholder="唯一编码，如：dev-center"
        clearable
        maxlength="32"
        show-word-limit
        :disabled="mode === 'edit'"
      />
    </el-form-item>

    <el-form-item label="上级组织">
      <el-popover
        v-model:visible="showOrgTree"
        trigger="click"
        placement="bottom-start"
        :width="360"
        :show-arrow="false"
        :teleported="true"
        @show="loadOrgTreeData"
      >
        <template #reference>
          <el-input
            :model-value="parentOrgDisplay"
            readonly
            placeholder="点击选择上级组织"
            class="w-full! cursor-pointer"
          >
            <template #suffix>
              <el-icon><Arrow-Down /></el-icon>
            </template>
          </el-input>
        </template>
        <div class="org-tree-popover-content">
          <el-input
            v-model="filterText"
            size="small"
            placeholder="搜索组织..."
            clearable
            class="mb-2"
          />
          <el-scrollbar max-height="var(--popover-max-height)">
            <el-tree
              ref="treeRef"
              :data="orgTreeData"
              node-key="id"
              size="small"
              :props="{ children: 'children', label: 'orgName' }"
              default-expand-all
              :expand-on-click-node="false"
              :filter-node-method="filterOrgNode"
              highlight-current
              @node-click="(_data: any) => onOrgTreeNodeClick(_data)"
            >
              <template #default="{ data }">
                <div class="org-tree-node">
                  <span class="truncate" :title="data.orgName">
                    {{ data.orgName }}
                  </span>
                </div>
              </template>
            </el-tree>
          </el-scrollbar>
        </div>
      </el-popover>
    </el-form-item>

    <el-form-item label="排序号" prop="sort">
      <el-input-number
        v-model="formData.sort"
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
  </el-form>
</template>

<style lang="scss" scoped>
.org-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.org-tree-popover-content {
  max-height: 300px;
  overflow-y: auto;
}
</style>
