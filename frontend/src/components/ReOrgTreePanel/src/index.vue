<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import {
  getOrgTree,
  getOrgTreeConfigs,
  type OrgTreeNode,
  type OrgTreeConfig
} from "@/api/user-manage";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import Plus from "~icons/ep/plus";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";

defineOptions({
  name: "ReOrgTreePanel"
});

const props = withDefaults(
  defineProps<{
    /** 是否显示组织树配置下拉 */
    showConfig?: boolean;
    /** 是否显示搜索框 */
    showSearch?: boolean;
    /** 紧凑模式（对话中内嵌用） */
    compact?: boolean;
    /** 指定树配置 ID（不传则自动取默认树） */
    treeConfigId?: number;
    /** 组织类型过滤（不传不过滤） */
    orgTypeFilter?: number[];
    /**
     * 细粒度权限（fail-closed）。三项均为 false 时树进入纯只读模式：
     * 不显示"+新增"、不允许拖拽、hover 操作按钮全部隐藏。
     */
    canAdd?: boolean;
    canEdit?: boolean;
    canDelete?: boolean;
  }>(),
  {
    showConfig: true,
    showSearch: true,
    compact: false,
    canAdd: false,
    canEdit: false,
    canDelete: false
  }
);

/** 树是否进入可编辑模式：任一写权限即足够。语义内聚于本组件，调用方只需传三个细粒度 prop */
const editable = computed(
  () => props.canAdd || props.canEdit || props.canDelete
);

const emit = defineEmits<{
  "node-click": [node: OrgTreeNode];
  "org-change": [orgId: number | null];
  "node-add": [parentNode: OrgTreeNode | null];
  "node-edit": [node: OrgTreeNode];
  "node-delete": [node: OrgTreeNode];
  "node-move": [node: OrgTreeNode, targetParentId: number];
}>();

// 组织树配置
const treeConfigs = ref<OrgTreeConfig[]>([]);
const selectedConfigId = ref<number>(1);

// 组织树（原始数据）
const rawOrgTree = ref<OrgTreeNode[]>([]);
// 过滤后的组织树（用于展示）
const filteredOrgTree = computed<OrgTreeNode[]>(() => {
  if (!props.orgTypeFilter || props.orgTypeFilter.length === 0) {
    return rawOrgTree.value;
  }
  return filterTreeByOrgType(rawOrgTree.value, props.orgTypeFilter);
});

const selectedOrgId = ref<number | null>(null);

// 搜索
const filterText = ref("");
const treeRef = ref();

// 紧凑模式：popover 控制
const popoverVisible = ref(false);
const selectedOrgName = ref("");

// 高亮
const highlightMap = ref<Record<string, { highlight: boolean }>>({});

// Hover 节点
const hoveredNodeId = ref<number | null>(null);

function filterOrgNode(value: string, data: any) {
  if (!value) return true;
  return data.orgName.includes(value);
}

const treeProps = {
  children: "children",
  label: "orgName"
};

/** 按 orgType 过滤树 */
function filterTreeByOrgType(
  nodes: OrgTreeNode[],
  allowedTypes: number[]
): OrgTreeNode[] {
  const result: OrgTreeNode[] = [];
  for (const node of nodes) {
    // 过滤子节点
    const filteredChildren = node.children
      ? filterTreeByOrgType(node.children, allowedTypes)
      : [];

    // 如果当前节点类型在允许列表中，保留它（带上过滤后的子节点）
    if (allowedTypes.includes(node.orgType)) {
      result.push({
        ...node,
        children: filteredChildren
      });
    } else if (filteredChildren.length > 0) {
      // 当前节点类型不匹配，但有子节点匹配——保留子节点提升到当前层级
      // 注意：这里不提升，因为会破坏树的层级关系
      // 而是跳过当前节点，只保留子节点
      result.push(...filteredChildren);
    }
  }
  return result;
}

async function loadConfigs() {
  treeConfigs.value = await getOrgTreeConfigs();
  const defaultCfg = treeConfigs.value.find(c => c.isDefault);
  if (defaultCfg) selectedConfigId.value = defaultCfg.id;
}

async function loadTree() {
  const configId = props.treeConfigId ?? selectedConfigId.value;
  rawOrgTree.value = await getOrgTree({
    operationCode: "VIEW",
    treeConfigId: configId
  });
}

async function onConfigChange(configId: number) {
  selectedConfigId.value = configId;
  selectedOrgId.value = null;
  selectedOrgName.value = "";
  highlightMap.value = {};
  await loadTree();
  if (!props.compact && filteredOrgTree.value.length > 0) {
    selectNode(filteredOrgTree.value[0]);
  }
}

function selectNode(node: OrgTreeNode) {
  selectedOrgId.value = node.id;
  selectedOrgName.value = node.orgName;
  const key = String(node.id);
  Object.keys(highlightMap.value).forEach(k => {
    highlightMap.value[k].highlight = false;
  });
  highlightMap.value[key] = { highlight: true };
  emit("node-click", node);
  emit("org-change", node.id);
  // compact 模式选中后关闭弹窗
  if (props.compact) {
    popoverVisible.value = false;
  }
}

function clearSelection() {
  selectedOrgId.value = null;
  selectedOrgName.value = "";
  Object.keys(highlightMap.value).forEach(k => {
    highlightMap.value[k].highlight = false;
  });
  emit("org-change", null);
}

// 新增子组织
function onAddChild(node: OrgTreeNode) {
  emit("node-add", node);
}

// 编辑组织
function onEdit(node: OrgTreeNode) {
  emit("node-edit", node);
}

// 删除组织
async function onDelete(node: OrgTreeNode) {
  try {
    await ElMessageBox.confirm(
      `确认删除组织 "${node.orgName}"？\n删除后将同时移除其下所有子组织。`,
      "删除确认",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    emit("node-delete", node);
  } catch {
    // 取消删除
  }
}

// 新增根组织
function onAddRoot() {
  emit("node-add", null);
}

onMounted(async () => {
  if (props.showConfig) {
    await loadConfigs();
  }
  await loadTree();
  // 非 compact 模式自动选中根组织
  if (!props.compact && filteredOrgTree.value.length > 0) {
    selectNode(filteredOrgTree.value[0]);
  }
});

defineExpose({
  orgTree: rawOrgTree,
  selectedOrgId,
  selectedConfigId,
  loadTree
});
</script>

<template>
  <!-- 紧凑模式：输入框 + popover -->
  <template v-if="compact">
    <el-popover
      v-model:visible="popoverVisible"
      trigger="click"
      placement="bottom-start"
      :width="360"
      :show-arrow="false"
      :teleported="true"
    >
      <template #reference>
        <el-input
          :model-value="selectedOrgName"
          placeholder="请选择组织（可选）"
          readonly
          clearable
          @clear="clearSelection"
        />
      </template>
      <!-- popover 内容：搜索 + 树 -->
      <div class="org-tree-popover-content">
        <el-input
          v-if="showSearch"
          v-model="filterText"
          size="small"
          placeholder="搜索组织..."
          clearable
          class="mb-2"
          @input="(_val: string) => treeRef?.filter(_val)"
        />
        <el-scrollbar max-height="var(--popover-max-height)">
          <el-tree
            ref="treeRef"
            :data="filteredOrgTree"
            node-key="id"
            size="small"
            :props="treeProps"
            default-expand-all
            :expand-on-click-node="false"
            :filter-node-method="filterOrgNode"
            highlight-current
            @node-click="(_data: any) => selectNode(_data)"
          >
            <template #default="{ data }">
              <div
                class="org-tree-node"
                :style="{
                  color: highlightMap[data.id]?.highlight
                    ? 'var(--el-color-primary)'
                    : '',
                  background: highlightMap[data.id]?.highlight
                    ? 'var(--el-color-primary-light-7)'
                    : 'transparent'
                }"
              >
                <IconifyIconOffline
                  :icon="useRenderIcon('ep/office-building')"
                  width="14px"
                  height="14px"
                  class="text-primary mr-1"
                />
                <span class="truncate" :title="data.orgName">
                  {{ data.orgName }}
                </span>
              </div>
            </template>
          </el-tree>
        </el-scrollbar>
      </div>
    </el-popover>
  </template>

  <!-- 非 compact 模式：完整面板 -->
  <div v-else class="org-tree-panel">
    <div v-if="showConfig || showSearch || editable" class="org-tree-toolbar">
      <el-select
        v-if="showConfig"
        v-model="selectedConfigId"
        size="small"
        class="w-full!"
        @change="onConfigChange"
      >
        <el-option
          v-for="cfg in treeConfigs"
          :key="cfg.id"
          :label="cfg.treeName"
          :value="cfg.id"
        />
      </el-select>
      <el-input
        v-if="showSearch"
        v-model="filterText"
        size="small"
        placeholder="搜索组织..."
        clearable
        :class="showConfig ? 'mt-1.5' : ''"
        @input="(_val: string) => treeRef?.filter(_val)"
      />
      <!-- 新增组织按钮 -->
      <el-button
        v-if="editable && canAdd"
        type="primary"
        size="small"
        class="mt-1.5 w-full!"
        @click="onAddRoot"
      >
        <IconifyIconOffline
          :icon="useRenderIcon('ep/plus')"
          width="14px"
          height="14px"
          class="mr-1"
        />
        新增组织
      </el-button>
    </div>
    <el-divider v-if="showConfig || showSearch || editable" class="my-1!" />

    <el-scrollbar class="org-tree-scroll">
      <el-tree
        ref="treeRef"
        :data="filteredOrgTree"
        node-key="id"
        size="small"
        :props="treeProps"
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterOrgNode"
        highlight-current
        draggable
        :allow-drag="() => editable && canEdit"
        :allow-drop="() => editable && canEdit"
        @node-click="(_data: any) => selectNode(_data)"
        @node-drag-end="
          (draggingNode: any, dropNode: any) => {
            if (dropNode && dropNode.data) {
              emit('node-move', draggingNode.data, dropNode.data.id);
            }
          }
        "
      >
        <template #default="{ data }">
          <div
            class="org-tree-node-wrapper"
            @mouseenter="hoveredNodeId = data.id"
            @mouseleave="hoveredNodeId = null"
          >
            <div
              class="org-tree-node"
              :style="{
                color: highlightMap[data.id]?.highlight
                  ? 'var(--el-color-primary)'
                  : '',
                background: highlightMap[data.id]?.highlight
                  ? 'var(--el-color-primary-light-7)'
                  : 'transparent'
              }"
            >
              <IconifyIconOffline
                :icon="useRenderIcon('ep/office-building')"
                width="14px"
                height="14px"
                class="text-primary mr-1"
              />
              <span class="truncate" :title="data.orgName">
                {{ data.orgName }}
              </span>
            </div>
            <!-- hover 操作按钮 -->
            <div
              v-if="editable && hoveredNodeId === data.id"
              class="node-actions"
              @click.stop
            >
              <el-button
                v-if="canAdd"
                link
                type="primary"
                size="small"
                :icon="useRenderIcon('ep/plus')"
                title="新增子组织"
                @click="onAddChild(data)"
              />
              <el-button
                v-if="canEdit"
                link
                type="primary"
                size="small"
                :icon="useRenderIcon('ep/edit-pen')"
                title="编辑"
                @click="onEdit(data)"
              />
              <el-button
                v-if="canDelete"
                link
                type="danger"
                size="small"
                :icon="useRenderIcon('ep/delete')"
                title="删除"
                @click="onDelete(data)"
              />
            </div>
          </div>
        </template>
      </el-tree>
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.org-tree-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.org-tree-toolbar {
  padding: 6px;
  margin: 4px 4px 0;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.org-tree-scroll {
  flex: 1;
  min-height: 0;
}

.org-tree-node-wrapper {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.org-tree-node {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  padding: 2px 4px;
  user-select: none;
  border-radius: 4px;

  &:hover {
    color: var(--el-color-primary);
  }
}

.node-actions {
  display: flex;
  gap: 2px;
  align-items: center;
  margin-left: 4px;

  :deep(.el-button) {
    height: auto;
    padding: 2px;
  }
}

:deep(.el-tree) {
  --el-tree-node-hover-bg-color: transparent;
}

:deep(.el-tree-node__content) {
  height: auto;
  padding: 2px 0;
}
</style>

<style>
/* popover 内容（teleported，需 unscoped） */
.org-tree-popover-content .el-scrollbar {
  max-height: var(--popover-max-height);
}
</style>
