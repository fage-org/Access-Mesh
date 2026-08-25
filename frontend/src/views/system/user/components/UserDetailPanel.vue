<script setup lang="ts">
import { ref, watch, computed } from "vue";
import {
  getUserRoles,
  revokeRole,
  assignRole,
  getUserOrgs,
  assignUserOrgs,
  removeUserOrg,
  setPrimaryOrg,
  getOrgTree,
  getRoleList,
  type UserItem,
  type UserRoleItem,
  type OrgBrief,
  type OrgTreeNode,
  type RoleItem
} from "@/api/user-manage";
import { message } from "@/utils/message";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { hasPerms } from "@/utils/auth";
import { ORG_USER_PERMS } from "../utils/perms";
import Remove from "~icons/ep/remove";
import AddFill from "~icons/ri/add-circle-line";
import Star from "~icons/ep/star-filled";
import StarOff from "~icons/ep/star";

defineOptions({
  name: "UserDetailPanel"
});

type UserOrgItem = {
  orgId: number;
  orgName: string;
  /** 1=普通组织, 2=岗位（特殊组织）；与 OrgBrief.orgType 对齐 */
  orgType?: number;
  isPrimary: boolean;
};

type TagType = "primary" | "success" | "warning" | "danger" | "info";

/**
 * 角色类型 → 标签色
 *
 * 注：本面板只展示「功能角色」（BASIC_ROLE / GROUP_ROLE / PERSONAL），
 * ORG 与 POSITION 已迁移为组织模型（org-user-permission-contract.md v1.2），
 * 它们在 otherRoles 中被过滤掉，对应分类的 tag 颜色仅作向后兼容保留。
 */
const roleTagTypeMap: Record<string, TagType> = {
  PERSONAL: "warning",
  GROUP_ROLE: "info",
  BASIC_ROLE: "info"
};

const ORG_TYPE_POSITION = 2;

type OrgSelectorNode = OrgTreeNode & {
  disabled?: boolean;
  children: OrgSelectorNode[];
};

const props = defineProps<{
  user: UserItem | null;
  showPermission?: boolean;
  /** 组织树（用于添加组织时选择，不传则自动加载） */
  orgTree?: OrgTreeNode[];
}>();
const emit = defineEmits<{
  syncUserOrgs: [orgs: UserOrgItem[]];
}>();

const roles = ref<UserRoleItem[]>([]);
const loading = ref(false);
const userOrgs = ref<UserOrgItem[]>([]);

// ========== 权限门控 ==========
// 与 docs/design/org-user-permission-contract.md §4 矩阵 B/C 区对齐。
// 详情弹窗自身不区分「默认树/非默认树」，前端只把它折叠到 ORG_MEMBER：
// 默认树主组织（设主）按身份目录操作处理，落地仍由后端 access-service 二次拒绝。
const canManageOrgMember = computed(() => hasPerms(ORG_USER_PERMS.ORG_MEMBER));
const canAssignFunctionalRole = computed(() =>
  hasPerms(ORG_USER_PERMS.USER_ROLE_ASSIGN)
);
const canRevokeFunctionalRole = computed(() =>
  hasPerms(ORG_USER_PERMS.USER_ROLE_REVOKE)
);

/**
 * 仅展示功能角色：排除 ORG（组织角色 = 组织成员关系）和 POSITION（岗位 = 特殊组织）。
 * 这两类已迁移到 /user-org/* 与岗位 Tab 管理；详见 org-user-permission-contract.md v1.2 §2。
 */
const otherRoles = computed(() =>
  roles.value.filter(
    r => r.roleTypeCode !== "ORG" && r.roleTypeCode !== "POSITION"
  )
);

/** 用户所属岗位（只读，由 getUserOrgs 按 orgType=2 拆出） */
const userPositions = computed(() =>
  userOrgs.value.filter(o => o.orgType === ORG_TYPE_POSITION)
);

/** 组织归属：仅普通组织（orgType !== 2），与岗位区分展示 */
const userPlainOrgs = computed(() =>
  userOrgs.value.filter(o => o.orgType !== ORG_TYPE_POSITION)
);

// 组织管理状态
const orgTreeData = ref<OrgTreeNode[]>([]);
const orgSelectorVisible = ref(false);
const selectedOrgIds = ref<number[]>([]);

// 角色分配状态
const roleSelectorVisible = ref(false);
const selectedRoleKey = ref<string | null>(null);
const assignableRoles = ref<RoleItem[]>([]);
const assignableRolesLoaded = ref(false);

function mapUserOrgs(orgs: Array<OrgBrief | UserOrgItem>): UserOrgItem[] {
  return orgs.map(org => ({
    orgId: org.orgId,
    orgName: org.orgName,
    // OrgBrief.orgType 后端为 string；UserOrgItem 内部统一为 number，便于和岗位常量比较
    orgType:
      typeof org.orgType === "string" ? Number(org.orgType) : org.orgType,
    isPrimary: org.isPrimary
  }));
}

function setUserOrgs(orgs: Array<OrgBrief | UserOrgItem>, syncParent = false) {
  const nextOrgs = mapUserOrgs(orgs);
  userOrgs.value = nextOrgs;
  if (syncParent) {
    emit("syncUserOrgs", nextOrgs);
  }
}

async function refreshUserOrgs(userId: number, syncParent = false) {
  const orgData = await getUserOrgs(userId);
  setUserOrgs(orgData, syncParent);
}

watch(
  () => props.user?.orgs,
  orgs => {
    setUserOrgs(orgs ?? []);
  },
  { immediate: true, deep: true }
);

watch(
  () => props.user?.id,
  async userId => {
    if (!userId) return;
    loading.value = true;
    try {
      const [roleData, orgData] = await Promise.all([
        getUserRoles(userId),
        getUserOrgs(userId)
      ]);
      roles.value = roleData;
      setUserOrgs(orgData, true);
    } finally {
      loading.value = false;
    }
  },
  { immediate: true }
);

// 组织树（用于选择器）：优先用外部传入，否则自动加载
watch(
  () => props.orgTree,
  async tree => {
    if (Array.isArray(tree) && tree.length > 0) {
      orgTreeData.value = tree;
    } else {
      // v1.4 后端 /org/tree 强制要求 orgType；用户详情面板组织选择器仅可分配普通组织（orgType=1），
      // 岗位为只读视图不参与「分配组织」。
      orgTreeData.value = await getOrgTree({
        operationCode: "MANAGE",
        orgType: 1
      });
    }
  },
  { immediate: true }
);

// 可选的 org（排除已分配的）
const availableOrgs = computed(() => {
  if (!props.user) return orgTreeData.value;
  const assigned = new Set(userOrgs.value.map(org => org.orgId));
  const filter = (nodes: OrgTreeNode[]): OrgSelectorNode[] =>
    nodes.reduce<OrgSelectorNode[]>((result, node) => {
      const children = filter(node.children);
      const isAssigned = assigned.has(node.id);
      if (isAssigned && children.length === 0) {
        return result;
      }
      result.push({ ...node, disabled: isAssigned, children });
      return result;
    }, []);
  return filter(orgTreeData.value);
});

// ========== 角色操作 ==========

async function handleRevoke(role: UserRoleItem) {
  try {
    await revokeRole({
      userId: props.user!.id,
      roleTypeCode: role.roleTypeCode,
      roleExternalId: role.roleExternalId
    });
    roles.value = roles.value.filter(
      r =>
        !(
          r.roleTypeCode === role.roleTypeCode &&
          r.roleExternalId === role.roleExternalId
        )
    );
    message("角色已移除", { type: "success" });
  } catch {
    message("移除失败", { type: "error" });
  }
}

async function openRoleSelector() {
  if (!props.user) return;
  selectedRoleKey.value = null;
  roleSelectorVisible.value = true;
  // 懒加载功能角色候选；后端 /role/list 默认仅返回 BASIC_ROLE / GROUP_ROLE / PERSONAL
  if (!assignableRolesLoaded.value) {
    try {
      assignableRoles.value = await getRoleList();
      assignableRolesLoaded.value = true;
    } catch {
      message("加载角色列表失败", { type: "error" });
    }
  }
}

async function handleAssignRole() {
  if (!props.user || !selectedRoleKey.value) return;
  const role = assignableRoles.value.find(
    r => `${r.roleTypeCode}:${r.roleExternalId}` === selectedRoleKey.value
  );
  if (!role) return;
  try {
    await assignRole({
      userId: props.user.id,
      roleTypeCode: role.roleTypeCode,
      roleExternalId: role.roleExternalId
    });
    roles.value = await getUserRoles(props.user.id);
    roleSelectorVisible.value = false;
    message("角色已分配", { type: "success" });
  } catch {
    message("分配失败", { type: "error" });
  }
}

// ========== 组织操作 ==========

function openOrgSelector() {
  if (!props.user) return;
  selectedOrgIds.value = [];
  orgSelectorVisible.value = true;
}

async function handleAddOrg() {
  if (!props.user || selectedOrgIds.value.length === 0) return;
  await assignUserOrgs({
    userId: props.user.id,
    orgIds: selectedOrgIds.value
  });
  await refreshUserOrgs(props.user.id, true);
  orgSelectorVisible.value = false;
  message("组织已添加", { type: "success" });
}

async function handleRemoveOrg(orgId: number) {
  if (!props.user) return;
  await removeUserOrg({ userId: props.user.id, orgId });
  await refreshUserOrgs(props.user.id, true);
  message("组织已移除", { type: "success" });
}

async function handleSetPrimary(orgId: number) {
  if (!props.user) return;
  await setPrimaryOrg({ userId: props.user.id, orgId });
  await refreshUserOrgs(props.user.id, true);
  message("主组织已更新", { type: "success" });
}

function getRoleTagType(typeCode: string): TagType {
  return roleTagTypeMap[typeCode] || "info";
}

function formatDate(val: string | null): string {
  if (!val) return "-";
  return val.substring(0, 10);
}
</script>

<template>
  <div v-if="user" class="role-panel">
    <!-- 用户信息卡片 -->
    <div class="user-info-card">
      <div class="flex items-center gap-3">
        <div class="user-avatar">
          <span class="text-lg font-medium">{{ user.name.slice(0, 2) }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">{{ user.name }}</span>
            <span :class="['status-dot', user.status === 1 ? 'on' : 'off']" />
            <span class="text-xs text-gray-400">{{
              user.status === 1 ? "已启用" : "已停用"
            }}</span>
          </div>
          <div class="text-xs text-gray-400 mt-0.5">
            {{ user.username }}
            <template v-if="user.email"> · {{ user.email }}</template>
            <template v-if="user.phone">
              ·
              {{
                user.phone.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2")
              }}</template
            >
          </div>
        </div>
      </div>
    </div>

    <!-- 内容 -->
    <div class="px-4 py-3 overflow-auto flex-1">
      <!-- 组织归属 -->
      <div class="mb-4">
        <div class="flex items-center gap-1.5 mb-2">
          <IconifyIconOffline
            :icon="useRenderIcon('ep/office-building')"
            width="14px"
            height="14px"
            class="text-primary"
          />
          <span class="text-sm font-medium">组织归属</span>
          <el-button
            v-if="canManageOrgMember"
            link
            type="primary"
            size="small"
            :icon="useRenderIcon(AddFill)"
            @click="openOrgSelector"
          >
            添加
          </el-button>
        </div>
        <!-- 组织选择器 -->
        <div
          v-if="orgSelectorVisible"
          class="mb-2 rounded border border-solid border-(--el-border-color) p-2"
        >
          <el-tree-select
            v-model="selectedOrgIds"
            :data="availableOrgs"
            :props="{
              children: 'children',
              label: 'orgName',
              value: 'id',
              disabled: 'disabled'
            }"
            placeholder="选择组织"
            multiple
            check-strictly
            filterable
            class="w-full! mb-2"
          />
          <div class="flex gap-1">
            <el-button size="small" type="primary" @click="handleAddOrg">
              确定
            </el-button>
            <el-button size="small" @click="orgSelectorVisible = false">
              取消
            </el-button>
          </div>
        </div>
        <div v-if="userPlainOrgs.length > 0" class="flex flex-wrap gap-1.5">
          <el-tag
            v-for="org in userPlainOrgs"
            :key="org.orgId"
            size="small"
            :type="org.isPrimary ? 'primary' : 'info'"
            effect="plain"
            :closable="canManageOrgMember"
            class="org-tag"
            @close="handleRemoveOrg(org.orgId)"
          >
            <!-- 有写权：可点击切换主组织；无写权：纯文本展示，与「添加按钮」隐藏的降级语言一致 -->
            <el-button
              v-if="canManageOrgMember"
              link
              size="small"
              class="org-primary-btn"
              :class="org.isPrimary ? 'is-primary' : ''"
              :title="org.isPrimary ? '当前主组织' : '设为主组织'"
              @click="handleSetPrimary(org.orgId)"
            >
              <IconifyIconOffline
                :icon="org.isPrimary ? Star : StarOff"
                width="12px"
                height="12px"
                class="mr-0.5"
              />
              {{ org.orgName }}
            </el-button>
            <span v-else class="org-readonly-name">
              <IconifyIconOffline
                v-if="org.isPrimary"
                :icon="Star"
                width="12px"
                height="12px"
                class="mr-0.5"
              />
              {{ org.orgName }}
            </span>
          </el-tag>
        </div>
        <span v-else class="text-xs text-gray-400">无组织归属</span>
      </div>

      <!-- 所属岗位（只读） -->
      <div class="mb-4">
        <div class="flex items-center gap-1.5 mb-2">
          <IconifyIconOffline
            :icon="useRenderIcon('ep/postcard')"
            width="14px"
            height="14px"
            class="text-warning"
          />
          <span class="text-sm font-medium">所属岗位</span>
          <el-tooltip
            content="岗位由「岗位管理」Tab 维护，本面板仅展示"
            placement="top"
          >
            <el-tag size="small" type="info" effect="plain">只读</el-tag>
          </el-tooltip>
        </div>
        <div v-if="userPositions.length > 0" class="flex flex-wrap gap-1.5">
          <el-tag
            v-for="pos in userPositions"
            :key="pos.orgId"
            size="small"
            type="success"
            effect="plain"
          >
            {{ pos.orgName }}
          </el-tag>
        </div>
        <span v-else class="text-xs text-gray-400">未分配岗位</span>
      </div>

      <!-- 角色列表 -->
      <div class="mb-4">
        <div class="flex items-center gap-1.5 mb-2">
          <IconifyIconOffline
            :icon="useRenderIcon('ep/user-filled')"
            width="14px"
            height="14px"
            class="text-success"
          />
          <span class="text-sm font-medium">角色列表</span>
          <el-button
            v-if="canAssignFunctionalRole"
            link
            type="primary"
            size="small"
            :icon="useRenderIcon(AddFill)"
            @click="openRoleSelector"
          >
            分配
          </el-button>
        </div>

        <!-- 角色选择器 -->
        <div
          v-if="roleSelectorVisible"
          class="mb-2 p-2 rounded border border-solid border-(--el-border-color)"
        >
          <el-select
            v-model="selectedRoleKey"
            placeholder="选择功能角色"
            filterable
            class="w-full! mb-2"
          >
            <el-option
              v-for="r in assignableRoles"
              :key="`${r.roleTypeCode}:${r.roleExternalId}`"
              :label="r.roleName"
              :value="`${r.roleTypeCode}:${r.roleExternalId}`"
            />
          </el-select>
          <div class="flex gap-1">
            <el-button size="small" type="primary" @click="handleAssignRole">
              确定
            </el-button>
            <el-button size="small" @click="roleSelectorVisible = false">
              取消
            </el-button>
          </div>
        </div>

        <div v-loading="loading" class="min-h-10">
          <template v-if="otherRoles.length > 0">
            <div
              v-for="role in otherRoles"
              :key="`${role.roleTypeCode}:${role.roleExternalId}`"
              class="role-item"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <el-tag
                    :type="getRoleTagType(role.roleTypeCode)"
                    size="small"
                    effect="dark"
                  >
                    {{ role.roleTypeLabel }}
                  </el-tag>
                  <span class="text-sm">{{ role.roleName }}</span>
                </div>
                <el-button
                  v-if="canRevokeFunctionalRole"
                  link
                  size="small"
                  title="移除角色"
                  :icon="useRenderIcon(Remove)"
                  @click="handleRevoke(role)"
                />
              </div>
              <div
                v-if="role.relationOrgName"
                class="ml-2 mt-1 text-xs text-gray-400"
              >
                所属组织: {{ role.relationOrgName }}
              </div>
              <div
                v-if="role.validFrom || role.validTo"
                class="ml-2 mt-0.5 text-xs text-gray-400"
              >
                有效期: {{ formatDate(role.validFrom) }} ~
                {{ formatDate(role.validTo) }}
              </div>
            </div>
          </template>
          <div v-else class="text-xs text-gray-400 py-2">暂无角色</div>
        </div>
      </div>

      <!-- 权限查询（占位） -->
      <div v-if="showPermission" class="mb-4">
        <div class="flex items-center gap-1.5 mb-2">
          <IconifyIconOffline
            :icon="useRenderIcon('ep/lock')"
            width="14px"
            height="14px"
            class="text-warning"
          />
          <span class="text-sm font-medium">权限查询</span>
          <el-tag size="small" type="warning" effect="plain">开发中</el-tag>
        </div>
        <div
          class="p-3 rounded bg-fill-light text-xs text-gray-400 text-center"
        >
          权限查询功能将在权限管理页面设计完成后补充
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.role-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: calc(100vh - var(--dialog-offset));
  background: var(--el-bg-color);
}

.user-info-card {
  padding: 16px;
  margin: 0 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.user-avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--el-color-white);
  background: var(--el-color-primary-light-5);
  border-radius: 50%;
}

.status-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.on {
    background: var(--el-color-success);
  }

  &.off {
    background: var(--el-color-danger);
  }
}

.org-primary-btn {
  height: auto;
  padding: 0;
  font-size: 12px;
  vertical-align: baseline;

  &.is-primary {
    color: var(--el-color-primary);
  }
}

.org-readonly-name {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  vertical-align: baseline;
}

.role-item {
  padding: 8px 10px;
  margin-bottom: 4px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  transition: background 0.2s;

  &:hover {
    background: var(--el-fill-color-light);
  }
}
</style>
