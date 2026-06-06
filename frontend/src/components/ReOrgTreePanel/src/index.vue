<script setup lang="ts">
import { ref, onMounted } from "vue";
import {
  getOrgTree,
  getOrgTreeConfigs,
  type OrgTreeNode,
  type OrgTreeConfig
} from "@/api/user-manage";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";

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
  }>(),
  {
    showConfig: true,
    showSearch: true,
    compact: false
  }
);

const emit = defineEmits<{
  "node-click": [node: OrgTreeNode];
  "org-change": [orgId: number | null];
}>();

// 组织树配置
const treeConfigs = ref<OrgTreeConfig[]>([]);
const selectedConfigId = ref<number>(1);

// 组织树
const orgTree = ref<OrgTreeNode[]>([]);
const selectedOrgId = ref<number | null>(null);

// 搜索
const filterText = ref("");
const treeRef = ref();

// 紧凑模式：popover 控制
const popoverVisible = ref(false);
const selectedOrgName = ref("");

// 高亮
const highlightMap = ref<Record<string, { highlight: boolean }>>({});

function filterOrgNode(value: string, data: any) {
  if (!value) return true;
  return data.orgName.includes(value);
}

const treeProps = {
  children: "children",
  label: "orgName"
};

async function loadConfigs() {
  treeConfigs.value = await getOrgTreeConfigs();
  const defaultCfg = treeConfigs.value.find(c => c.isDefault);
  if (defaultCfg) selectedConfigId.value = defaultCfg.id;
}

async function loadTree() {
  const configId = props.treeConfigId ?? selectedConfigId.value;
  orgTree.value = await getOrgTree({
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
  if (!props.compact && orgTree.value.length > 0) {
    selectNode(orgTree.value[0]);
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

onMounted(async () => {
  if (props.showConfig) {
    await loadConfigs();
  }
  await loadTree();
  // 非 compact 模式自动选中根组织
  if (!props.compact && orgTree.value.length > 0) {
    selectNode(orgTree.value[0]);
  }
});

defineExpose({ orgTree, selectedOrgId, selectedConfigId });
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
        <el-scrollbar max-height="260px">
          <el-tree
            ref="treeRef"
            :data="orgTree"
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
                  class="text-[#409eff] mr-1"
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
    <div v-if="showConfig || showSearch" class="org-tree-toolbar">
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
    </div>
    <el-divider v-if="showConfig || showSearch" class="my-1!" />

    <el-scrollbar class="org-tree-scroll">
      <el-tree
        ref="treeRef"
        :data="orgTree"
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
              class="text-[#409eff] mr-1"
            />
            <span class="truncate" :title="data.orgName">
              {{ data.orgName }}
            </span>
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

.org-tree-node {
  display: flex;
  align-items: center;
  padding: 2px 4px;
  user-select: none;
  border-radius: 4px;

  &:hover {
    color: var(--el-color-primary);
  }
}

:deep(.el-tree) {
  --el-tree-node-hover-bg-color: transparent;
}
</style>

<style>
/* popover 内容（teleported，需 unscoped） */
.org-tree-popover-content .el-scrollbar {
  max-height: 260px;
}
</style>
