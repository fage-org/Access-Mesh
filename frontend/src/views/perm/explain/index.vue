<script setup lang="ts">
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import {
  explainPermission,
  type PermissionExplainResult,
  type RecentChange,
  getImpactLevelTag,
  getChangeTypeTag
} from "@/api/perm/permissionView";
import { getBizDomainList, type BizDomainItem } from "@/api/perm/domain";
import { getResourceTypeList, type ResourceTypeItem } from "@/api/perm/type";
import {
  getResourceTree,
  transformResourceTreeResponse,
  type ResourceTreeNode
} from "@/api/perm/resource";
import { getUserList, type UserListItem } from "@/api/admin/user";
import {
  getOperationList,
  type OperationPermissionItem
} from "@/api/perm/operation";

defineOptions({
  name: "PermExplain"
});

// ========== 状态定义 ==========

const loading = ref(false);
const userList = ref<Array<UserListItem>>([]);
const domainList = ref<Array<BizDomainItem>>([]);
const resourceTypeList = ref<Array<ResourceTypeItem>>([]);
const resourceTree = ref<Array<ResourceTreeNode>>([]);
const operationList = ref<Array<OperationPermissionItem>>([]);

const form = reactive({
  subjectExternalId: "",
  domainCode: "",
  resourceTypeCode: "",
  resourceCode: "",
  operationCode: "",
  includeSourceRoles: true,
  includeRecentChanges: true,
  recentDays: 7
});

const explainResult = ref<PermissionExplainResult | null>(null);
const recentChanges = ref<Array<RecentChange>>([]);

// ========== 下拉数据加载 ==========

const loadUserList = async () => {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 100 });
    if (res.success) {
      userList.value = res.data.items || [];
    }
  } catch {
    // 静默处理
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

const loadResourceTree = async () => {
  if (!form.domainCode || !form.resourceTypeCode) return;
  try {
    const res = await getResourceTree({
      domainCode: form.domainCode,
      resourceTypeCode: form.resourceTypeCode
    });
    if (res.success) {
      // 转换响应格式
      resourceTree.value = transformResourceTreeResponse(res);
    }
  } catch {
    // 静默处理
  }
};

const loadOperationList = async () => {
  if (!form.resourceTypeCode) return;
  try {
    const res = await getOperationList({
      resourceTypeCode: form.resourceTypeCode
    });
    if (res.success) {
      operationList.value = res.data.items || [];
    }
  } catch {
    // 静默处理
  }
};

// ========== 资源类型变更时重新加载 ==========

const handleResourceTypeChange = () => {
  form.resourceCode = "";
  form.operationCode = "";
  resourceTree.value = [];
  operationList.value = [];
  loadResourceTree();
  loadOperationList();
};

const handleDomainChange = () => {
  form.resourceCode = "";
  resourceTree.value = [];
  loadResourceTree();
};

// ========== 权限解释 ==========

const handleExplain = async () => {
  if (!form.subjectExternalId) {
    ElMessage.warning("请选择用户");
    return;
  }
  if (!form.resourceTypeCode) {
    ElMessage.warning("请选择资源类型");
    return;
  }
  if (!form.resourceCode) {
    ElMessage.warning("请选择资源");
    return;
  }
  if (!form.operationCode) {
    ElMessage.warning("请选择操作");
    return;
  }

  loading.value = true;
  try {
    const res = await explainPermission({
      targetType: "USER",
      subjectTypeCode: "USER",
      subjectExternalId: form.subjectExternalId,
      domainCode: form.domainCode || undefined,
      resourceTypeCode: form.resourceTypeCode,
      resourceCode: form.resourceCode,
      operationCode: form.operationCode,
      includeSourceRoles: form.includeSourceRoles,
      includeRecentChanges: form.includeRecentChanges,
      recentDays: form.recentDays
    });
    if (res.success) {
      explainResult.value = res;
      recentChanges.value = res.data.recentChanges || [];
    }
  } catch {
    ElMessage.error("权限解释失败");
  } finally {
    loading.value = false;
  }
};

// ========== 初始化 ==========

loadUserList();
loadDomainList();
loadResourceTypeList();
</script>

<template>
  <div class="permission-explain">
    <!-- 权限查询表单 -->
    <el-card class="mb-4">
      <template #header>
        <span class="font-medium">权限排查</span>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="用户">
              <el-select
                v-model="form.subjectExternalId"
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
                v-model="form.domainCode"
                placeholder="选择业务域"
                clearable
                @change="handleDomainChange"
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
                v-model="form.resourceTypeCode"
                placeholder="选择资源类型"
                clearable
                @change="handleResourceTypeChange"
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
            <el-form-item label="资源">
              <el-tree-select
                v-model="form.resourceCode"
                :data="resourceTree"
                placeholder="选择资源"
                check-strictly
                :render-after-expand="false"
                filterable
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="操作">
              <el-select
                v-model="form.operationCode"
                placeholder="选择操作"
                clearable
              >
                <el-option
                  v-for="op in operationList"
                  :key="op.id"
                  :label="op.name"
                  :value="op.code"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="近期天数">
              <el-input-number
                v-model="form.recentDays"
                :min="1"
                :max="30"
                :step="1"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label-width="0" class="text-right">
              <el-button
                type="primary"
                :loading="loading"
                @click="handleExplain"
              >
                解释权限
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 权限判定结果 -->
    <el-card v-if="explainResult" class="mb-4">
      <template #header>
        <span class="font-medium">权限判定结果</span>
      </template>

      <div class="explain-result">
        <!-- 允许/拒绝状态 -->
        <div
          class="p-4 rounded mb-4"
          :class="explainResult.data.allowed ? 'bg-green-50' : 'bg-red-50'"
        >
          <div class="flex items-center gap-4">
            <el-tag
              :type="explainResult.data.allowed ? 'success' : 'danger'"
              size="large"
            >
              {{ explainResult.data.allowed ? "允许" : "拒绝" }}
            </el-tag>
            <span class="text-gray-700">{{ explainResult.data.reason }}</span>
          </div>
        </div>

        <!-- 权限详情 -->
        <div class="mb-4">
          <div class="font-medium text-gray-700 mb-2">权限详情</div>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="业务域">
              {{ explainResult.data.permission.domainCode || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="资源类型">
              {{ explainResult.data.permission.resourceTypeCode }}
            </el-descriptions-item>
            <el-descriptions-item label="资源编码">
              {{ explainResult.data.permission.resourceCode }}
            </el-descriptions-item>
            <el-descriptions-item label="操作">
              {{ explainResult.data.permission.operationCode }}
            </el-descriptions-item>
            <el-descriptions-item label="范围">
              <el-tag
                :type="
                  explainResult.data.permission.scopeAll ? 'success' : 'info'
                "
                size="small"
              >
                {{ explainResult.data.permission.scopeAll ? "全部" : "限定" }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="匹配权限ID">
              {{ explainResult.data.matchedPermissionIds.length }} 个
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 来源角色 -->
        <div v-if="explainResult.data.sourceRoles.length > 0" class="mb-4">
          <div class="font-medium text-gray-700 mb-2">来源角色</div>
          <el-table :data="explainResult.data.sourceRoles" stripe border>
            <el-table-column prop="roleName" label="角色名称" width="150" />
            <el-table-column
              prop="roleExternalId"
              label="角色编码"
              width="150"
            />
            <el-table-column prop="roleTypeCode" label="角色类型" width="120">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.roleTypeCode === 'GROUP_ROLE' ? 'warning' : 'success'
                  "
                  size="small"
                >
                  {{
                    row.roleTypeCode === "GROUP_ROLE" ? "分组角色" : "基本角色"
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="via" label="授权路径" min-width="200">
              <template #default="{ row }">
                <span class="text-gray-600">
                  {{ row.via?.length > 0 ? row.via.join(" → ") : "直接授权" }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>

    <!-- 近期变更历史 -->
    <el-card v-if="recentChanges.length > 0">
      <template #header>
        <span class="font-medium">近期可能影响的变更</span>
        <span class="text-gray-500 ml-2">(最近 {{ form.recentDays }} 天)</span>
      </template>

      <el-table :data="recentChanges" stripe>
        <el-table-column prop="eventType" label="事件类型" width="120" />
        <el-table-column prop="changeType" label="变更类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getChangeTypeTag(row.changeType).type" size="small">
              {{ getChangeTypeTag(row.changeType).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="impactLevel" label="影响级别" width="100">
          <template #default="{ row }">
            <el-tag
              :type="getImpactLevelTag(row.impactLevel).type"
              size="small"
            >
              {{ getImpactLevelTag(row.impactLevel).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="变更说明" min-width="200" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.permission-explain {
  padding: 20px;
}
</style>
