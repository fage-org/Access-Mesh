<script setup lang="ts">
import { ref, computed, watch, h } from "vue";
import {
  getOrgPage,
  getOrgUsers,
  getUserPage,
  createOrg,
  updateOrg,
  deleteOrg,
  assignUserOrgs,
  removeUserOrg,
  type OrgPageItem,
  type OrgUserItem
} from "@/api/user-manage";
import { message } from "@/utils/message";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { ElMessageBox } from "element-plus";
import { addDialog } from "@/components/ReDialog";
import OrgForm from "./OrgForm.vue";
import AddFill from "~icons/ri/add-circle-line";
import User from "~icons/ep/user";
import ArrowDown from "~icons/ep/arrow-down";
import ArrowUp from "~icons/ep/arrow-up";
import Delete from "~icons/ep/delete";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import EditPen from "~icons/ep/edit-pen";
import Plus from "~icons/ep/plus";

defineOptions({
  name: "PositionTab"
});

const props = defineProps<{
  orgId: number | null;
  orgTree?: any[];
}>();

// ========== 类型 ==========

interface PositionItem extends OrgPageItem {
  parentOrgName?: string;
}

// ========== 状态 ==========

const loading = ref(false);
const positionList = ref<PositionItem[]>([]);
const expandedIds = ref<Set<number>>(new Set());
const positionUsers = ref<Record<number, OrgUserItem[]>>({});
const positionUserCounts = ref<Record<number, number>>({});

// 搜索
const searchKeyword = ref("");

// 用户选择弹窗
const userSelectorVisible = ref(false);
const currentPositionId = ref<number | null>(null);
const userList = ref<{ id: number; name: string; username: string }[]>([]);
const selectedUserIds = ref<number[]>([]);
const userLoading = ref(false);

// ========== 计算属性 ==========

const filteredPositions = computed(() => {
  let list = positionList.value;
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase();
    list = list.filter(p => p.orgName.toLowerCase().includes(kw));
  }
  return list;
});

/** 递归查找父组织名称 */
function findParentOrgName(
  nodes: any[],
  parentId: number | null
): string | null {
  if (!parentId) return null;
  for (const node of nodes) {
    if (node.id === parentId) return node.orgName;
    if (node.children?.length) {
      const found = findParentOrgName(node.children, parentId);
      if (found) return found;
    }
  }
  return null;
}

/** 获取组织路径（从当前节点向上追溯） */
function getOrgPath(position: PositionItem): string {
  if (!props.orgTree) return "-";
  const parts: string[] = [];
  // 从当前节点向上追溯父组织链
  let currentId = position.parentOrgId;
  while (currentId) {
    const parentName = findParentOrgName(props.orgTree, currentId);
    if (parentName) {
      parts.unshift(parentName);
      // 继续向上找
      const parentNode = findNodeById(props.orgTree, currentId);
      currentId = parentNode?.parentOrgId ?? null;
    } else {
      break;
    }
  }
  return parts.length > 0 ? parts.join(" > ") : "-";
}

/** 递归查找节点 */
function findNodeById(nodes: any[], id: number): any | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children?.length) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

// ========== 加载数据 ==========

async function loadPositions() {
  loading.value = true;
  try {
    const res = await getOrgPage({
      pageNum: 1,
      pageSize: 100,
      orgType: 2,
      status: 1,
      orgId: props.orgId ?? undefined
    });
    positionList.value = res.items as PositionItem[];
    // 加载每个岗位的用户数
    for (const pos of positionList.value) {
      await loadPositionUserCount(pos.id);
    }
  } catch {
    message("加载岗位失败", { type: "error" });
  } finally {
    loading.value = false;
  }
}

/** 加载岗位下的用户数量 */
async function loadPositionUserCount(positionId: number) {
  try {
    const users = await getOrgUsers(positionId);
    positionUsers.value[positionId] = users;
    positionUserCounts.value[positionId] = users.length;
  } catch {
    positionUserCounts.value[positionId] = 0;
  }
}

/** 加载岗位下的用户列表（展开时调用） */
async function loadPositionUsers(positionId: number) {
  if (positionUsers.value[positionId]) return;
  try {
    const users = await getOrgUsers(positionId);
    positionUsers.value[positionId] = users;
    positionUserCounts.value[positionId] = users.length;
  } catch {
    message("加载用户失败", { type: "error" });
  }
}

// ========== 交互操作 ==========

function toggleExpand(positionId: number) {
  if (expandedIds.value.has(positionId)) {
    expandedIds.value.delete(positionId);
  } else {
    expandedIds.value.add(positionId);
    loadPositionUsers(positionId);
  }
}

function isExpanded(positionId: number): boolean {
  return expandedIds.value.has(positionId);
}

// ========== 岗位 CRUD ==========

/** 打开新增岗位弹窗 */
function openCreatePositionDialog() {
  let formRef: any = null;
  addDialog({
    title: "新增岗位",
    width: "480px",
    contentRenderer: () =>
      h(OrgForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode: "create",
        initialData: {
          orgType: 2,
          parentOrgId: props.orgId,
          status: 1
        }
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef) {
        closeLoading();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) {
        closeLoading();
        return;
      }
      const formData = formRef.getFormData();
      try {
        await createOrg({
          orgName: formData.orgName,
          code: formData.code,
          orgType: 2,
          parentOrgId: formData.parentOrgId,
          status: formData.status,
          sort: formData.sort
        });
        message("创建成功", { type: "success" });
        done();
        await loadPositions();
      } catch {
        closeLoading();
        message("创建失败", { type: "error" });
      }
    }
  });
}

/** 打开编辑岗位弹窗 */
function openEditPositionDialog(position: PositionItem, event: Event) {
  event.stopPropagation();
  let formRef: any = null;
  addDialog({
    title: "编辑岗位",
    width: "480px",
    contentRenderer: () =>
      h(OrgForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode: "edit",
        initialData: {
          orgName: position.orgName,
          code: position.code,
          orgType: position.orgType,
          parentOrgId: position.parentOrgId,
          status: position.status,
          sort: position.sort
        }
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef) {
        closeLoading();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) {
        closeLoading();
        return;
      }
      const formData = formRef.getFormData();
      try {
        await updateOrg({
          id: position.id,
          orgName: formData.orgName,
          code: formData.code,
          orgType: 2,
          parentOrgId: formData.parentOrgId,
          status: formData.status,
          sort: formData.sort
        });
        message("更新成功", { type: "success" });
        done();
        await loadPositions();
      } catch {
        closeLoading();
        message("更新失败", { type: "error" });
      }
    }
  });
}

/** 删除岗位 */
async function handleDeletePosition(position: PositionItem, event: Event) {
  event.stopPropagation();
  try {
    await ElMessageBox.confirm(
      `确认删除岗位 "${position.orgName}"？`,
      "删除确认",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    await deleteOrg(position.id);
    message("删除成功", { type: "success" });
    await loadPositions();
  } catch (e: any) {
    if (e !== "cancel") {
      message("删除失败", { type: "error" });
    }
  }
}

// ========== 用户挂载/卸载 ==========

function openAddUserDialog(positionId: number) {
  currentPositionId.value = positionId;
  selectedUserIds.value = [];
  userSelectorVisible.value = true;
  loadAvailableUsers();
}

async function loadAvailableUsers() {
  userLoading.value = true;
  try {
    const res = await getUserPage({
      pageNum: 1,
      pageSize: 100,
      status: 1
    });
    // 过滤掉已在当前岗位的用户
    const currentPositionUsers =
      positionUsers.value[currentPositionId.value!] || [];
    const currentUserIds = new Set(currentPositionUsers.map(u => u.userId));
    userList.value = res.items
      .filter(u => !currentUserIds.has(u.id))
      .map(u => ({ id: u.id, name: u.name, username: u.username }));
  } catch {
    message("加载用户失败", { type: "error" });
  } finally {
    userLoading.value = false;
  }
}

async function handleAddUsers() {
  if (selectedUserIds.value.length === 0) {
    message("请选择用户", { type: "warning" });
    return;
  }
  try {
    if (currentPositionId.value) {
      await assignUserOrgs({
        userId: selectedUserIds.value[0], // 逐个添加
        orgIds: [currentPositionId.value]
      });
      // 批量添加其他用户
      for (let i = 1; i < selectedUserIds.value.length; i++) {
        await assignUserOrgs({
          userId: selectedUserIds.value[i],
          orgIds: [currentPositionId.value]
        });
      }
      message("添加成功", { type: "success" });
      // 刷新该岗位的用户列表（删除缓存，强制重新加载）
      delete positionUsers.value[currentPositionId.value];
      await loadPositionUsers(currentPositionId.value);
    }
    userSelectorVisible.value = false;
  } catch {
    message("添加失败", { type: "error" });
  }
}

async function handleRemoveUser(positionId: number, userId: number) {
  try {
    await ElMessageBox.confirm("确认从该岗位移除此用户？", "提示", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning"
    });
    await removeUserOrg({ userId, orgId: positionId });
    // 刷新该岗位的用户列表
    positionUsers.value[positionId] =
      positionUsers.value[positionId]?.filter(u => u.userId !== userId) || [];
    positionUserCounts.value[positionId] =
      (positionUserCounts.value[positionId] || 0) - 1;
    message("移除成功", { type: "success" });
  } catch (e: any) {
    if (e !== "cancel") {
      message("移除失败", { type: "error" });
    }
  }
}

function onSearch() {
  // computed 自动响应
}

function onReset() {
  searchKeyword.value = "";
}

// ========== 监听 ==========

watch(
  () => props.orgId,
  () => {
    expandedIds.value.clear();
    positionUsers.value = {};
    loadPositions();
  },
  { immediate: true }
);
</script>

<template>
  <div class="position-tab">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索岗位名称"
        clearable
        class="w-60!"
        @keyup.enter="onSearch"
      >
        <template #prefix>
          <IconifyIconOffline
            :icon="useRenderIcon(Search)"
            width="14px"
            height="14px"
            class="text-gray-400"
          />
        </template>
      </el-input>
      <el-button type="primary" :icon="useRenderIcon(Search)" @click="onSearch">
        搜索
      </el-button>
      <el-button :icon="useRenderIcon(Refresh)" @click="onReset">
        重置
      </el-button>
      <el-button
        type="primary"
        :icon="useRenderIcon(Plus)"
        @click="openCreatePositionDialog"
      >
        新增岗位
      </el-button>
    </div>

    <!-- 岗位折叠卡片列表 -->
    <div v-loading="loading" class="position-list">
      <template v-if="filteredPositions.length > 0">
        <div
          v-for="position in filteredPositions"
          :key="position.id"
          class="position-card"
        >
          <!-- 卡片头部（可点击展开/收起） -->
          <div class="position-card-header" @click="toggleExpand(position.id)">
            <div class="position-info">
              <div class="position-name">
                <IconifyIconOffline
                  :icon="useRenderIcon(User)"
                  width="16px"
                  height="16px"
                  class="text-primary mr-1"
                />
                <span class="font-medium">{{ position.orgName }}</span>
                <el-tag size="small" type="info" effect="plain" class="ml-2">
                  {{ position.code }}
                </el-tag>
              </div>
              <div class="position-meta">
                <span class="meta-item">
                  <IconifyIconOffline
                    :icon="useRenderIcon(User)"
                    width="12px"
                    height="12px"
                    class="text-gray-400 mr-0.5"
                  />
                  {{ positionUserCounts[position.id] || 0 }} 人已分配
                </span>
                <span class="meta-item"> 📍 {{ getOrgPath(position) }} </span>
              </div>
            </div>
            <div class="position-actions">
              <el-button
                link
                type="primary"
                size="small"
                :icon="useRenderIcon(AddFill)"
                @click.stop="openAddUserDialog(position.id)"
              >
                添加成员
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                :icon="useRenderIcon(EditPen)"
                @click.stop="openEditPositionDialog(position, $event)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :icon="useRenderIcon(Delete)"
                @click.stop="handleDeletePosition(position, $event)"
              >
                删除
              </el-button>
              <el-button link type="primary" size="small" class="expand-btn">
                <IconifyIconOffline
                  :icon="
                    useRenderIcon(isExpanded(position.id) ? ArrowUp : ArrowDown)
                  "
                  width="14px"
                  height="14px"
                />
                {{ isExpanded(position.id) ? "收起" : "展开" }}
              </el-button>
            </div>
          </div>

          <!-- 展开后的用户列表 -->
          <el-collapse-transition>
            <div v-show="isExpanded(position.id)" class="position-users">
              <div
                v-if="
                  positionUsers[position.id] &&
                  positionUsers[position.id].length > 0
                "
                class="user-list"
              >
                <div
                  v-for="user in positionUsers[position.id]"
                  :key="user.userId"
                  class="user-item"
                >
                  <div class="user-info">
                    <div class="user-avatar-sm">
                      {{ user.name.slice(0, 1) }}
                    </div>
                    <div class="user-detail">
                      <span class="user-name">{{ user.name }}</span>
                      <span class="user-username">{{ user.username }}</span>
                    </div>
                  </div>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :icon="useRenderIcon(Delete)"
                    @click="handleRemoveUser(position.id, user.userId)"
                  >
                    移除
                  </el-button>
                </div>
              </div>
              <el-empty v-else description="暂无成员" :image-size="60" />
            </div>
          </el-collapse-transition>
        </div>
      </template>

      <!-- 空状态 -->
      <el-empty v-else description="暂无岗位数据" :image-size="80" />
    </div>

    <!-- 添加用户弹窗 -->
    <el-dialog
      v-model="userSelectorVisible"
      title="添加成员到岗位"
      width="500px"
      destroy-on-close
    >
      <el-select
        v-model="selectedUserIds"
        multiple
        filterable
        placeholder="选择要添加的用户"
        class="w-full!"
        :loading="userLoading"
      >
        <el-option
          v-for="user in userList"
          :key="user.id"
          :label="`${user.name} (${user.username})`"
          :value="user.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="userSelectorVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddUsers">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
/* 响应式 */
@media (width <= 768px) {
  .position-card-header {
    flex-direction: column;
    gap: var(--space-2);
    align-items: flex-start;
  }

  .position-actions {
    margin-left: 0;
  }
}

.position-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* 搜索栏 */
.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-3) var(--space-3) 0;
}

/* 岗位列表 */
.position-list {
  flex: 1;
  min-height: 0;
  padding: var(--space-3);
  overflow-y: auto;
}

/* 岗位卡片 */
.position-card {
  margin-bottom: var(--space-3);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 12px 0 rgb(0 0 0 / 6%);
  }
}

/* 卡片头部 */
.position-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4);
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-lighter);
  }
}

.position-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.position-name {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.position-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-item {
  display: flex;
  align-items: center;
}

/* 操作按钮区域 */
.position-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
  align-items: center;
  margin-left: var(--space-3);
}

.expand-btn {
  display: flex;
  align-items: center;
}

/* 用户列表区域 */
.position-users {
  padding: 0 var(--space-4) var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
}

.user-list {
  padding-top: var(--space-3);
}

.user-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-2);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.user-info {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.user-avatar-sm {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 12px;
  color: var(--el-color-white);
  background: var(--el-color-primary-light-5);
  border-radius: 50%;
}

.user-detail {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.user-username {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
