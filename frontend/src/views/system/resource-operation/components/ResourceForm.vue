<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ArrowDown } from "@element-plus/icons-vue";
import {
  type ResourceResp,
  type ResourceTreeNode
} from "@/api/resource-operation";
import {
  RESOURCE_STATUS_OPTIONS,
  CODE_TYPE_DEFAULT,
  type ResourceFormData
} from "../utils/types";

defineOptions({ name: "ResourceForm" });

const props = defineProps<{
  mode: "create" | "edit";
  /** 编辑时的完整资源数据（含 extra，由 index.vue 调 detail 拉取） */
  initialData?: ResourceResp | null;
  /** 新建时预填的资源类型编码（= 左侧选中的 resourceTypeCode） */
  defaultResourceTypeCode?: string;
  /** 新建子资源时的父节点 */
  parentNode?: ResourceTreeNode | null;
  /** 同类型资源森林（父选择器用） */
  resourceTree?: ResourceTreeNode[];
}>();

/**
 * 表单默认值。
 * - 新建顶层：parentId=null。
 * - 新建子资源：parentId=parentNode.id。
 * 跨类型父子不合法（mock 校验 + 树按类型过滤），resourceTypeCode 固定只读。
 */
const defaultFormData = (): ResourceFormData => ({
  resourceTypeCode: props.defaultResourceTypeCode ?? "",
  code: "",
  codeType: CODE_TYPE_DEFAULT,
  name: "",
  parentId: props.parentNode?.id ?? null,
  status: 1,
  sortOrder: 0,
  extra: ""
});

const formData = reactive<ResourceFormData>({ ...defaultFormData() });
const formRef = ref<FormInstance>();
const showParentTree = ref(false);
const selectedParentName = ref("");

const isEdit = computed(() => props.mode === "edit");

/** 校验规则。
 *  code/codeType 为业务键（schema uk_resource_entity: tenant+type+code+codeType），编辑态只读。 */
const rules = computed<FormRules>(() => ({
  code: [
    { required: true, message: "请输入资源编码", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_:-]+$/,
      message: "仅支持字母、数字、下划线、中划线、冒号",
      trigger: "blur"
    },
    { max: 128, message: "最长 128 字符", trigger: "blur" }
  ],
  codeType: [
    {
      pattern: /^[a-zA-Z0-9_-]*$/,
      message: "仅支持字母、数字、下划线、中划线",
      trigger: "blur"
    }
  ],
  name: [
    { required: true, message: "请输入资源名称", trigger: "blur" },
    { max: 256, message: "最长 256 字符", trigger: "blur" }
  ],
  sortOrder: [{ required: true, message: "请输入排序号", trigger: "blur" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
}));

const parentDisplay = computed(() => {
  if (formData.parentId === null) return "（顶层）";
  if (selectedParentName.value) return selectedParentName.value;
  if (props.parentNode?.name) return props.parentNode.name;
  const parent = props.resourceTree
    ? findNodeById(props.resourceTree, formData.parentId)
    : null;
  return parent?.name ?? "（顶层）";
});

function findNodeById(
  nodes: ResourceTreeNode[],
  id: number
): ResourceTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

/** 过滤父树：排除自身及其子孙（编辑态防选自身/子孙成环） */
function filterParentTree(nodes: ResourceTreeNode[]): ResourceTreeNode[] {
  const result: ResourceTreeNode[] = [];
  for (const node of nodes) {
    if (props.mode === "edit" && props.initialData?.id === node.id) continue;
    const filteredChildren = node.children
      ? filterParentTree(node.children)
      : [];
    result.push({ ...node, children: filteredChildren });
  }
  return result;
}

const parentTreeData = computed(() => {
  if (!props.resourceTree?.length) return [];
  return filterParentTree(props.resourceTree);
});

function filterTreeNode(value: string, data: any) {
  if (!value) return true;
  return data.name.includes(value);
}

function onParentTreeNodeClick(node: ResourceTreeNode) {
  selectedParentName.value = node.name;
  formData.parentId = node.id;
  showParentTree.value = false;
}

function setParentToTop() {
  selectedParentName.value = "";
  formData.parentId = null;
  showParentTree.value = false;
}

function initFormData() {
  if (props.mode === "edit" && props.initialData) {
    Object.assign(formData, {
      resourceTypeCode: props.initialData.resourceTypeCode,
      code: props.initialData.code,
      codeType: props.initialData.codeType,
      name: props.initialData.name,
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

function getFormData(): ResourceFormData {
  return { ...formData };
}

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
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
    label-width="90px"
    class="resource-form"
  >
    <el-form-item label="资源类型" prop="resourceTypeCode">
      <el-input
        :model-value="formData.resourceTypeCode"
        readonly
        class="w-full!"
      />
    </el-form-item>

    <el-form-item label="资源编码" prop="code">
      <!-- 编辑态只读：code 为业务键（uk_resource_entity），改它会破坏既有引用 -->
      <el-input
        v-if="isEdit"
        :model-value="formData.code"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.code"
        placeholder="资源编码（业务键，创建后不可改）"
        clearable
        maxlength="128"
      />
    </el-form-item>

    <el-form-item label="编码类型" prop="codeType">
      <el-input
        v-if="isEdit"
        :model-value="formData.codeType"
        readonly
        class="w-full!"
      />
      <el-input
        v-else
        v-model="formData.codeType"
        placeholder="默认 default"
        clearable
        maxlength="64"
      />
    </el-form-item>

    <el-form-item label="资源名称" prop="name">
      <el-input
        v-model="formData.name"
        placeholder="请输入资源名称"
        clearable
        maxlength="256"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="父资源">
      <!-- 编辑态只读：层级变更走「移动」弹窗（move 端点） -->
      <el-input
        v-if="isEdit"
        :model-value="parentDisplay"
        readonly
        class="w-full!"
      />
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
            placeholder="点击选择父资源（可空=顶层）"
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
              :data="parentTreeData"
              node-key="id"
              size="small"
              :props="{ children: 'children', label: 'name' }"
              default-expand-all
              :expand-on-click-node="false"
              :filter-node-method="filterTreeNode"
              highlight-current
              @node-click="(n: ResourceTreeNode) => onParentTreeNodeClick(n)"
            >
              <template #default="{ data }">
                <span class="truncate" :title="data.name">{{ data.name }}</span>
              </template>
            </el-tree>
            <div v-if="parentTreeData.length === 0" class="empty-tip">
              暂无可选父资源
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
          v-for="opt in RESOURCE_STATUS_OPTIONS"
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
.resource-form {
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
