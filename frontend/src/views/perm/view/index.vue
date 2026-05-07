<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import {
  getEffectivePermissions,
  type EffectivePermissionItem,
  type SourceRole
} from "@/api/perm/permissionView";
import { getBizDomainList, type BizDomainItem } from "@/api/perm/domain";
import { getResourceTypeList, type ResourceTypeItem } from "@/api/perm/type";
import { getUserList, type UserListItem } from "@/api/admin/user";
import { getRoleTree, type RoleTreeNode } from "@/api/perm/role";
import { transformResourceTreeResponse } from "@/api/perm/resource";
import PermissionResultTable from "./components/PermissionResultTable.vue";
import SourceRoleDialog from "./components/SourceRoleDialog.vue";

defineOptions({
  name: "PermView"
});

// ========== 状态定义 ==========

const loading = ref(false);
const activeTab = ref<"user" | "role">("user");

// 用户查询表单
const userForm = reactive({
  subjectExternalId: "",
  domainCode: "",
  resourceTypeCodes: [] as Array<string>,
  operationCodes: [] as Array<string>,
  resourceKeyword: "",
  pageNum: 1,
  pageSize: 20
});

// 角色查询表单
const roleForm = reactive({
  roleExternalId: "",
  domainCode: "",
  resourceTypeCodes: [] as Array<string>,
  pageNum: 1,
  pageSize: 20
});

// 下拉选项数据
const userList = ref<Array<UserListItem>>([]);
const domainList = ref<Array<BizDomainItem>>([]);
const resourceTypeList = ref<Array<ResourceTypeItem>>([]);
const roleTree = ref<Array<RoleTreeNode>>([]);

// 查询结果
const resultItems = ref<Array<EffectivePermissionItem>>([]);
const total = ref(0);

// 来源角色弹窗
const sourceRoleDialogRef = ref<InstanceType<typeof SourceRoleDialog> | null>(
  null
);

// ========== 下拉数据加载 ==========

const loadUserList = async () => {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 100 });
    if (res.success) {
      userList.value = res.data.items || [];
    }
  } catch {
    // 静默处理，下拉数据加载失败不影响主要功能
  }
};

const loadDomainList = async () => {
  try {
    const res = await getBizDomainList();
    if (res.success) {
      domainList.value = res.data.items || [];
    }
  } catch {
    // 静默处理
  }
};

const loadResourceTypeList = async () => {
  try {
    const res = await getResourceTypeList({ typeCategory: "RESOURCE_TYPE" });
    if (res.success) {
      resourceTypeList.value = res.data.items || [];
    }
  } catch {
    // 静默处理
  }
};

const loadRoleTree = async () => {
  try {
    const res = await getRoleTree({ domainCode: roleForm.domainCode });
    if (res.success) {
      // 转换响应格式
      roleTree.value = res.data.items?.map(item => item.root) || [];
    }
  } catch {
    // 静默处理
  }
};

// ========== 查询权限 ==========

const handleQuery = async () => {
  if (activeTab.value === "user") {
    if (!userForm.subjectExternalId) {
      ElMessage.warning("请选择用户");
      return;
    }
    await queryUserPermissions();
  } else {
    if (!roleForm.roleExternalId) {
      ElMessage.warning("请选择角色");
      return;
    }
    await queryRolePermissions();
  }
};

const queryUserPermissions = async () => {
  loading.value = true;
  try {
    const res = await getEffectivePermissions({
      targetType: "USER",
      subjectTypeCode: "USER",
      subjectExternalId: userForm.subjectExternalId,
      domainCode: userForm.domainCode || undefined,
      resourceTypeCodes:
        userForm.resourceTypeCodes.length > 0
          ? userForm.resourceTypeCodes
          : undefined,
      operationCodes:
        userForm.operationCodes.length > 0
          ? userForm.operationCodes
          : undefined,
      resourceKeyword: userForm.resourceKeyword || undefined,
      includeSourceRoles: true,
      sourceRoleLimit: 3,
      pageNum: userForm.pageNum,
      pageSize: userForm.pageSize
    });
    if (res.success) {
      resultItems.value = res.data.items || [];
      total.value = res.data.total;
    }
  } catch {
    ElMessage.error("查询用户权限失败");
  } finally {
    loading.value = false;
  }
};

const queryRolePermissions = async () => {
  loading.value = true;
  try {
    const res = await getEffectivePermissions({
      targetType: "ROLE",
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: roleForm.roleExternalId,
      domainCode: roleForm.domainCode || undefined,
      resourceTypeCodes:
        roleForm.resourceTypeCodes.length > 0
          ? roleForm.resourceTypeCodes
          : undefined,
      pageNum: roleForm.pageNum,
      pageSize: roleForm.pageSize
    });
    if (res.success) {
      resultItems.value = res.data.items || [];
      total.value = res.data.total;
    }
  } catch {
    ElMessage.error("查询角色权限失败");
  } finally {
    loading.value = false;
  }
};

// ========== 分页 ==========

const handlePageChange = (page: number) => {
  if (activeTab.value === "user") {
    userForm.pageNum = page;
  } else {
    roleForm.pageNum = page;
  }
  handleQuery();
};

const handleSizeChange = (size: number) => {
  if (activeTab.value === "user") {
    userForm.pageSize = size;
    userForm.pageNum = 1;
  } else {
    roleForm.pageSize = size;
    roleForm.pageNum = 1;
  }
  handleQuery();
};

// ========== 来源角色查看 ==========

const handleViewSourceRoles = (item: EffectivePermissionItem) => {
  sourceRoleDialogRef.value?.openDialog(item);
};

// ========== Tab切换 ==========

const handleTabChange = () => {
  resultItems.value = [];
  total.value = 0;
  userForm.pageNum = 1;
  roleForm.pageNum = 1;
};

// ========== 初始化 ==========

loadUserList();
loadDomainList();
loadResourceTypeList();
</script>

<template>
  <div class="permission-view">
    <el-card class="mb-4">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 用户权限查询 -->
        <el-tab-pane label="用户权限" name="user">
          <el-form :model="userForm" label-width="100px" class="mt-4">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-form-item label="用户">
                  <el-select
                    v-model="userForm.subjectExternalId"
                    placeholder="选择用户"
                    filterable
                    clearable
                  >
                    <el-option
                      v-for="user in userList"
                      :key="user.id"
                      :label="user.nickname || user.username"
                      :value="user.id.toString()"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="业务域">
                  <el-select
                    v-model="userForm.domainCode"
                    placeholder="选择业务域"
                    clearable
                  >
                    <el-option
                      v-for="domain in domainList"
                      :key="domain.id"
                      :label="domain.name"
                      :value="domain.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="资源类型">
                  <el-select
                    v-model="userForm.resourceTypeCodes"
                    placeholder="选择资源类型"
                    multiple
                    clearable
                    collapse-tags
                    collapse-tags-tooltip
                  >
                    <el-option
                      v-for="type in resourceTypeList"
                      :key="type.id"
                      :label="type.name"
                      :value="type.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="关键词">
                  <el-input
                    v-model="userForm.resourceKeyword"
                    placeholder="资源名称搜索"
                    clearable
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label-width="0" class="text-right">
                  <el-button type="primary" @click="handleQuery">
                    查询权限
                  </el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- 角色权限查询 -->
        <el-tab-pane label="角色权限" name="role">
          <el-form :model="roleForm" label-width="100px" class="mt-4">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-form-item label="业务域">
                  <el-select
                    v-model="roleForm.domainCode"
                    placeholder="选择业务域"
                    clearable
                    @change="loadRoleTree"
                  >
                    <el-option
                      v-for="domain in domainList"
                      :key="domain.id"
                      :label="domain.name"
                      :value="domain.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="角色">
                  <el-tree-select
                    v-model="roleForm.roleExternalId"
                    :data="roleTree"
                    placeholder="选择角色"
                    check-strictly
                    :render-after-expand="false"
                    filterable
                    clearable
                  />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="资源类型">
                  <el-select
                    v-model="roleForm.resourceTypeCodes"
                    placeholder="选择资源类型"
                    multiple
                    clearable
                    collapse-tags
                    collapse-tags-tooltip
                  >
                    <el-option
                      v-for="type in resourceTypeList"
                      :key="type.id"
                      :label="type.name"
                      :value="type.code"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label-width="0" class="text-right">
                  <el-button type="primary" @click="handleQuery">
                    查询权限
                  </el-button>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 查询结果 -->
    <el-card>
      <PermissionResultTable
        :data="resultItems"
        :loading="loading"
        :total="total"
        :page-num="activeTab === 'user' ? userForm.pageNum : roleForm.pageNum"
        :page-size="
          activeTab === 'user' ? userForm.pageSize : roleForm.pageSize
        "
        :show-source-roles="activeTab === 'user'"
        @view-source-roles="handleViewSourceRoles"
        @page-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 来源角色弹窗 -->
    <SourceRoleDialog ref="sourceRoleDialogRef" />
  </div>
</template>

<style scoped lang="scss">
.permission-view {
  padding: 20px;
}
</style>
