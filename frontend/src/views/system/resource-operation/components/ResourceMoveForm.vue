<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { ArrowDown } from "@element-plus/icons-vue";
import { type ResourceTreeNode } from "@/api/resource-operation";
import { type ResourceMoveFormData } from "../utils/types";

defineOptions({ name: "ResourceMoveForm" });

const props = defineProps<{
  /** 待移动的当前节点 */
  currentNode: ResourceTreeNode;
  /** 同类型资源森林（目标父选择器用） */
  resourceTree: ResourceTreeNode[];
}>();

/** 树节点 → 业务键（codeType 缺省 default 与后端归一一致） */
function keyOf(node: ResourceTreeNode) {
  return {
    resourceTypeCode: node.resourceTypeCode,
    code: node.code,
    codeType: node.codeType || "default"
  };
}

const formData = reactive<ResourceMoveFormData>({
  resource: keyOf(props.currentNode),
  parent: null
});

const showParentTree = ref(false);
const selectedParentName = ref("");

/** 过滤父树：排除当前节点及其子孙（防选自身/子孙成环） */
function filterParentTree(nodes: ResourceTreeNode[]): ResourceTreeNode[] {
  const result: ResourceTreeNode[] = [];
  for (const node of nodes) {
    if (node.id === props.currentNode.id) continue;
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

const parentDisplay = computed(() => {
  if (formData.parent === null) return "（顶层）";
  if (selectedParentName.value) return selectedParentName.value;
  return formData.parent.code;
});

function filterTreeNode(value: string, data: any) {
  if (!value) return true;
  return data.name.includes(value);
}

function onParentTreeNodeClick(node: ResourceTreeNode) {
  selectedParentName.value = node.name;
  formData.parent = keyOf(node);
  showParentTree.value = false;
}

function setParentToTop() {
  selectedParentName.value = "";
  formData.parent = null;
  showParentTree.value = false;
}

function getFormData(): ResourceMoveFormData {
  return { ...formData, resource: { ...formData.resource } };
}

/** parent 可空（顶层合法），选择范围已由 filterParentTree 保证合法 */
async function validate(): Promise<boolean> {
  return true;
}

watch(
  () => props.currentNode,
  node => {
    formData.resource = keyOf(node);
    formData.parent = null;
    selectedParentName.value = "";
  },
  { immediate: true }
);

defineExpose({ validate, getFormData });
</script>

<template>
  <el-form label-width="90px" class="move-form">
    <el-form-item label="当前资源">
      <el-input :model-value="currentNode.name" readonly class="w-full!" />
    </el-form-item>

    <el-form-item label="目标父资源">
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
            placeholder="点击选择目标父资源（可空=顶层）"
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
              移到顶层
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
              暂无可选目标父资源
            </div>
          </el-scrollbar>
        </div>
      </el-popover>
    </el-form-item>

    <div class="move-tip">
      只能在同资源类型内移动；不可移动到自身或其子孙节点下。
    </div>
  </el-form>
</template>

<style lang="scss" scoped>
.move-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
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

.move-tip {
  padding: var(--space-2) var(--space-3);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
