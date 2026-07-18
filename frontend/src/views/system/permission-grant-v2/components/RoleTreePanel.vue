<script setup lang="ts">
import { ref, computed, inject } from "vue";
import { usePermissionGrantV2 } from "../utils/hook";
import { type RoleTreeNode } from "@/api/permission-grant";

defineOptions({ name: "RoleTreePanelV2" });

const store = inject<ReturnType<typeof usePermissionGrantV2>>("pgV2Store")!;

// el-tree ref：disabled 不阻止 current 切换，需手动恢复高亮
const treeRef = ref();

const keyword = ref("");
const typeFilter = ref<string>("ALL");

/**
 * 稳定 nodeKey：JSON.stringify([domainCode, roleTypeCode, roleExternalId])。
 * 后端唯一键为 tenant+roleType+externalId，externalId 跨 roleTypeCode 不保证唯一；
 * 用 JSON 数组避免 externalId 含 "|" 拼接碰撞。递归注入所有子节点。
 */
type TreeNode = RoleTreeNode & { _nodeKey: string };

function withNodeKey(node: RoleTreeNode): TreeNode {
  return {
    ...node,
    _nodeKey: JSON.stringify([
      node.domainCode ?? "",
      node.roleTypeCode,
      node.roleExternalId
    ]),
    children: node.children.map(withNodeKey)
  };
}

const filteredTree = computed<TreeNode[]>(() => {
  const base =
    typeFilter.value === "ALL"
      ? store.roleTree.value
      : store.roleTree.value.filter(n => n.roleTypeCode === typeFilter.value);
  return base.map(withNodeKey);
});

const currentNodeKey = computed<string | undefined>(() => {
  const r = store.currentRole.value;
  if (!r) return undefined;
  return JSON.stringify([r.domainCode ?? "", r.roleTypeCode, r.roleExternalId]);
});

async function onSearch() {
  await store.loadRoleTree(keyword.value || undefined);
}

async function onSelect(node: unknown) {
  const role = node as RoleTreeNode;
  const prevKey = currentNodeKey.value;
  // 禁选节点（虚拟根/禁用/canView=false）：el-tree disabled 不阻止 current 切换，
  // 需手动恢复高亮到已提交节点
  if (
    role.roleExternalId.startsWith("__virtual_root_") ||
    !role.enabled ||
    !role.canView
  ) {
    treeRef.value?.setCurrentKey(prevKey ?? null);
    return;
  }
  await store.selectRole(role);
  // selectRole 失败/请求序号过期时 currentRole 不变（旧上下文不变），恢复高亮；
  // 成功时 currentRole 已更新，current-node-key prop 自动同步 el-tree current
  if (currentNodeKey.value === prevKey) {
    treeRef.value?.setCurrentKey(prevKey ?? null);
  }
}

const typeOptions = [
  { label: "全部", value: "ALL" },
  { label: "基础角色", value: "BASIC_ROLE" },
  { label: "组合角色", value: "GROUP_ROLE" },
  { label: "组织角色", value: "ORG" },
  { label: "岗位角色", value: "POSITION" },
  { label: "个人角色", value: "PERSONAL" }
];

const treeProps = {
  label: "roleName",
  children: "children",
  // 虚拟根 / 禁用角色 / canView=false 不可选；
  // directGrantable/canManage 缺失保持可选（只读标签表达，T-FE-029 acceptance）
  disabled: (data: {
    roleExternalId: string;
    enabled: boolean;
    canView: boolean;
  }) =>
    data.roleExternalId.startsWith("__virtual_root_") ||
    !data.enabled ||
    !data.canView
};
</script>

<template>
  <div class="role-tree-panel">
    <div class="panel-header">
      <span class="panel-title">角色</span>
    </div>
    <div class="panel-filters">
      <el-select v-model="typeFilter" size="small" class="type-select">
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索角色"
        clearable
        class="search-input"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
    </div>
    <el-scrollbar class="tree-scroll">
      <el-tree
        ref="treeRef"
        :data="filteredTree"
        :props="treeProps"
        node-key="_nodeKey"
        :current-node-key="currentNodeKey"
        :expand-on-click-node="false"
        highlight-current
        :load="() => {}"
        @node-click="onSelect"
      >
        <template #default="{ data }">
          <span class="role-node">
            <span class="role-name">{{ data.roleName }}</span>
            <!-- 能力标签（优先级：虚拟根 > canView=false > !directGrantable > !enabled > !canManage） -->
            <el-tag
              v-if="data.roleExternalId?.startsWith('__virtual_root_')"
              size="small"
              type="info"
              effect="plain"
              >类型根</el-tag
            >
            <el-tag
              v-else-if="!data.canView"
              size="small"
              type="danger"
              effect="plain"
              >不可查看</el-tag
            >
            <el-tag
              v-else-if="!data.directGrantable"
              size="small"
              type="warning"
              effect="plain"
              >只读</el-tag
            >
            <el-tag
              v-else-if="!data.enabled"
              size="small"
              type="danger"
              effect="plain"
              >禁用</el-tag
            >
            <el-tag
              v-else-if="!data.canManage"
              size="small"
              type="warning"
              effect="plain"
              >不可管理</el-tag
            >
          </span>
        </template>
      </el-tree>
      <el-empty
        v-if="filteredTree.length === 0"
        description="无角色"
        :image-size="60"
      />
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.role-tree-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-filters {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
}

.type-select {
  width: 100%;
}

.search-input {
  width: 100%;
}

.tree-scroll {
  flex: 1;
  min-height: 0;
  padding: 0 var(--space-2);
}

.role-node {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.role-name {
  font-size: 13px;
}
</style>
