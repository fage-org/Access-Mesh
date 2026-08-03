<script setup lang="ts">
/**
 * 左栏主体树（§1.1 两入口；首期角色入口，组织入口二期占位）。
 * 角色入口数据源：abstract-role/tree（权限中心，BASIC_ROLE + GROUP_ROLE）；
 * GROUP_ROLE 展开 = 其关联基础角色虚拟子节点（extra-roles/list，已联调），
 * 选中子节点后主体 = 该基础角色（BASIC_ROLE，与运行时展开语义一致，决策 13）。
 * GROUP_ROLE 节点本身无权限矩阵（只读提示）。
 *
 * 受控协议（评审问题 3 组件部分）：组件不持有选中态，由父组件通过 activeKey/selectingKey
 * 两阶段提交--点击候选 emit requestSelect，父组件确认成功才设 activeKey；saving 期间
 * disabled 冻结点击。节点 kind（ROLE/EXTRA_CONTAINER/EXTRA_ROLE）不依赖 roleTypeCode 猜测，
 * 嵌套真实 children 递归保留，extra-roles 装入虚拟容器追加不覆盖（评审问题 5）。
 *
 * 树加载由父组件 onActivated 驱动（问题 6：keep-alive 重入刷新树覆盖新建/改名/删除/层级变化）；
 * 本组件不自行 onMounted 加载，暴露 loadTree/findNode/preselect 供父组件编排一次性入口指令。
 */
import { nextTick, ref, watch } from "vue";
import { message } from "@/utils/message";
import {
  getRoleTree,
  listExtraRoles,
  type RoleTreeNode
} from "@/api/role-manage";
import type {
  GrantContext,
  SubjectTreeNode,
  SubjectType
} from "../utils/types";
import { buildExtraContainer, filterVisibleTree } from "../utils/subject-tree";

const props = defineProps<{
  subjectType: SubjectType;
  /** 当前激活节点 key（父组件受控；选中成功后设置，el-tree 高亮同步） */
  activeKey: string | null;
  /** 候选节点 key（父组件两阶段提交中，加载态视觉提示） */
  selectingKey: string | null;
  /** 冻结（saving 期间禁用点击，评审问题 4 冻结范围含主体切换） */
  disabled?: boolean;
}>();

const emit = defineEmits<{
  /** 请求选中可授权主体（BASIC_ROLE 或分组展开的基础角色）；父组件确认成功才设 activeKey */
  (e: "requestSelect", payload: { key: string; context: GrantContext }): void;
  /** 请求选中 GROUP_ROLE 节点本身（无独立权限矩阵，提示展开选择基础角色） */
  (e: "requestSelectGroup", payload: { key: string; name: string }): void;
}>();

const loading = ref(false);
const filterText = ref("");
const treeData = ref<SubjectTreeNode[]>([]);
const treeRef = ref();

const treeProps = { label: "name", children: "children" };

/** el-tree 高亮随父组件 activeKey 受控同步（评审问题 3：候选态不抢高亮） */
watch(
  () => props.activeKey,
  key => {
    if (key == null) return;
    nextTick(() => {
      treeRef.value?.setCurrentKey(key);
    });
  },
  { immediate: true }
);

/** 加载/刷新角色树（不预选；预选由父组件 preselect 编排，问题 6） */
async function loadTree() {
  loading.value = true;
  try {
    const roots = await getRoleTree({ domainCode: null });
    treeData.value = filterVisibleTree(roots);
  } catch (error: any) {
    message(error.message || "加载角色树失败", { type: "error" });
  } finally {
    loading.value = false;
  }
}

/** 递归查找节点（含嵌套真实 children；EXTRA_ROLE 未展开时不在树中） */
function findInTree(
  nodes: SubjectTreeNode[],
  externalId: string,
  roleTypeCode?: string
): SubjectTreeNode | null {
  for (const node of nodes) {
    if (
      node.externalId === externalId &&
      (roleTypeCode == null || node.roleTypeCode === roleTypeCode)
    ) {
      return node;
    }
    const found = findInTree(node.children ?? [], externalId, roleTypeCode);
    if (found) return found;
  }
  return null;
}

/** 暴露给父组件：按 externalId 查找节点（问题 6：刷新后当前主体处理 + 预选定位） */
function findNode(
  externalId: string,
  roleTypeCode?: string
): SubjectTreeNode | null {
  return findInTree(treeData.value, externalId, roleTypeCode);
}

/** 暴露给父组件：预选节点（一次性入口指令，问题 6）--找到则触发 requestSelect */
function preselect(externalId: string) {
  const hit = findNode(externalId, "BASIC_ROLE");
  if (hit) handleNodeClick(hit);
}

/**
 * GROUP_ROLE 展开 -> 加载其关联基础角色为虚拟容器子节点（extra-roles/list）。
 * 追加到真实 children 末尾，不覆盖嵌套真实子节点（评审问题 5）。
 */
async function handleNodeExpand(node: SubjectTreeNode) {
  if (node.kind !== "ROLE" || node.roleTypeCode !== "GROUP_ROLE") return;
  if (!node.externalId || node.expandedLoaded) return;
  try {
    const basics = await listExtraRoles({
      domainCode: null,
      groupRoleTypeCode: node.roleTypeCode,
      groupRoleExternalId: node.externalId
    });
    const container = buildExtraContainer(node.externalId, node.name, basics);
    // 真实 children（嵌套角色）+ 虚拟容器并存，不覆盖
    const realChildren = node.children ?? [];
    node.children = [...realChildren, container];
    // 成功后才标记已加载（失败可重试，评审问题 2）
    node.expandedLoaded = true;
  } catch (error: any) {
    message(error.message || "加载分组基础角色失败", { type: "error" });
  }
}

/** 节点点击：受控 emit，不本地设选中态（评审问题 3） */
function handleNodeClick(node: SubjectTreeNode) {
  if (props.disabled) return;
  // 虚拟容器不可选
  if (node.kind === "EXTRA_CONTAINER") return;
  if (node.kind === "ROLE" && node.roleTypeCode === "GROUP_ROLE") {
    emit("requestSelectGroup", { key: node.key, name: node.name });
    return;
  }
  if (!node.externalId) return;
  // BASIC_ROLE（ROLE）或分组展开基础角色（EXTRA_ROLE）：主体均为 BASIC_ROLE
  emit("requestSelect", {
    key: node.key,
    context: {
      domainCode: null,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: node.externalId,
      displayName: node.name,
      fromGroupRoleName: node.expandedFromGroup
        ? (node.groupRoleName ?? null)
        : null
    }
  });
}

function filterNode(value: string, data: SubjectTreeNode) {
  if (!value) return true;
  return data.name.includes(value);
}

defineExpose({ loadTree, findNode, preselect });
</script>

<template>
  <div class="subject-tree-panel">
    <!-- 组织入口二期（第十四轮收窄；联调期切 T-ADMIN-021 org-tree includePositions） -->
    <template v-if="subjectType === 'ORG'">
      <el-result
        icon="info"
        title="组织入口二期开放"
        sub-title="组织/岗位主体树将于二期接入（T-ADMIN-021 扩展 org-tree，includePositions=true）"
      />
    </template>

    <template v-else>
      <el-input
        v-model="filterText"
        placeholder="搜索角色名称"
        clearable
        class="mb-2"
        @input="val => treeRef?.filter(val)"
      />
      <el-tree
        ref="treeRef"
        v-loading="loading"
        :class="{ 'is-frozen': disabled }"
        :data="treeData"
        :props="treeProps"
        node-key="key"
        :filter-node-method="filterNode"
        :expand-on-click-node="false"
        :highlight-current="true"
        :current-node-key="activeKey ?? undefined"
        @node-expand="handleNodeExpand"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <span
            class="node-label"
            :class="{
              'is-selecting': data.key === selectingKey,
              'is-container': data.kind === 'EXTRA_CONTAINER'
            }"
          >
            <span :class="{ 'is-disabled': data.status === 0 }">
              {{ data.name }}
            </span>
            <el-tag
              v-if="data.kind === 'ROLE' && data.roleTypeCode === 'GROUP_ROLE'"
              size="small"
              type="info"
              class="ml-1"
            >
              分组
            </el-tag>
            <el-tag
              v-else-if="data.kind === 'EXTRA_ROLE'"
              size="small"
              type="success"
              class="ml-1"
            >
              基础角色
            </el-tag>
            <el-tag
              v-if="data.status === 0"
              size="small"
              type="danger"
              class="ml-1"
            >
              停用
            </el-tag>
          </span>
        </template>
      </el-tree>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.subject-tree-panel {
  height: 100%;
  padding: var(--space-2);
  overflow: auto;

  .node-label {
    display: inline-flex;
    align-items: center;

    &.is-selecting {
      font-weight: 600;
      color: var(--el-color-primary);
    }

    &.is-container {
      font-style: italic;
      color: var(--el-text-color-secondary);
    }

    .is-disabled {
      color: var(--el-text-color-secondary);
      text-decoration: line-through;
    }
  }

  // saving 冻结：降透明度提示，点击已在 handleNodeClick 拦截
  .is-frozen {
    opacity: 0.6;
  }
}
</style>
