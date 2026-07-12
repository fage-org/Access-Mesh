<script setup lang="ts">
import { ref, computed } from "vue";
import { inject } from "vue";
import { usePermissionGrant } from "../utils/hook";

defineOptions({ name: "RoleTreePanel" });

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;

const keyword = ref("");
const typeFilter = ref<string>("ALL");

const filteredTree = computed(() => {
  if (typeFilter.value === "ALL") return store.roleTree.value;
  return store.roleTree.value.filter(n => n.roleTypeCode === typeFilter.value);
});

async function onSearch() {
  await store.loadRoleTree(keyword.value || undefined);
}

function onSelect(node: unknown) {
  // el-tree node-click 返回 data
  const role = node as {
    roleExternalId: string;
    roleTypeCode: string;
    roleName: string;
    domainCode: string;
    enabled: boolean;
    directGrantable: boolean;
    canView: boolean;
    canManage: boolean;
  };
  if (role.roleExternalId.startsWith("__virtual_root_")) return;
  store.selectRole(role as never);
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
  disabled: (data: { roleExternalId: string; enabled: boolean }) =>
    data.roleExternalId.startsWith("__virtual_root_") || !data.enabled
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
        :data="filteredTree"
        :props="treeProps"
        node-key="roleExternalId"
        :expand-on-click-node="false"
        :current-node-key="store.currentRole.value?.roleExternalId"
        highlight-current
        :load="() => {}"
        @node-click="onSelect"
      >
        <template #default="{ data }">
          <span class="role-node">
            <span class="role-name">{{ data.roleName }}</span>
            <el-tag
              v-if="data.roleExternalId?.startsWith('__virtual_root_')"
              size="small"
              type="info"
              effect="plain"
              >类型根</el-tag
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
