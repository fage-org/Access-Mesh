<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import ArrowLeft from "~icons/ri/arrow-left-line";
import { getRoleDetail, type RoleDetail } from "@/api/perm/role";
import {
  getResourceTree,
  transformResourceTreeResponse,
  type ResourceTreeNode
} from "@/api/perm/resource";
import {
  getRolePermissions,
  saveRolePermissions,
  revokeRolePermissions,
  type RolePermissionItem,
  type GrantAddItem
} from "@/api/perm/rolePermission";
import {
  getOperationList,
  type OperationPermission
} from "@/api/perm/operation";
import ResourceTree from "./components/ResourceTree.vue";
import OperationConfig from "./components/OperationConfig.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "PermRolePermission"
});

const route = useRoute();
const router = useRouter();
const roleId = ref<number | null>(null);
const roleDetail = ref<RoleDetail | null>(null);
const loading = ref(false);
const saving = ref(false);

// ========== 资源和权限数据 ==========

const resourceTreeData = ref<Array<ResourceTreeNode>>([]);
const operationList = ref<Array<OperationPermission>>([]);
const existingPermissions = ref<Array<RolePermissionItem>>([]);
const selectedResource = ref<ResourceTreeNode | null>(null);
const selectedResourceTypeCode = ref<string>("");

// ========== 权限计算 ==========

const canAssignPerm = hasPerms(PERM_CODES.SYS_ROLE_ASSIGN_PERM);

// ========== 组织上下文 ==========

const domainCode = ref<string>("");

// ========== 数据加载 ==========

const loadRoleDetail = async () => {
  if (!roleId.value) return;
  try {
    const res = await getRoleDetail({ id: roleId.value });
    if (res.success) {
      roleDetail.value = res.data;
    }
  } catch {
    ElMessage.error("加载角色详情失败");
  }
};

const loadResourceTree = async () => {
  try {
    const res = await getResourceTree({
      resourceTypeCode: selectedResourceTypeCode.value || undefined,
      domainCode: domainCode.value || undefined
    });
    if (res.success) {
      resourceTreeData.value = transformResourceTreeResponse(res);
    }
  } catch {
    ElMessage.error("加载资源树失败");
  }
};

const loadOperations = async () => {
  if (!selectedResourceTypeCode.value) return;
  try {
    const res = await getOperationList({
      resourceTypeCode: selectedResourceTypeCode.value,
      domainCode: domainCode.value || undefined
    });
    if (res.success) {
      operationList.value = res.data.items || [];
    }
  } catch {
    ElMessage.error("加载操作权限失败");
  }
};

const loadExistingPermissions = async () => {
  if (!roleDetail.value) return;
  try {
    const res = await getRolePermissions({
      domainCode: domainCode.value || undefined,
      roleTypeCode: roleDetail.value.roleTypeCode,
      roleExternalId: roleDetail.value.externalId
    });
    if (res.success) {
      existingPermissions.value = res.data.items || [];
    }
  } catch {
    ElMessage.error("加载已配置权限失败");
  }
};

const loadAllData = async () => {
  loading.value = true;
  try {
    await Promise.all([
      loadRoleDetail(),
      loadResourceTree(),
      loadExistingPermissions()
    ]);
  } finally {
    loading.value = false;
  }
};

// ========== 资源选择 ==========

const handleResourceSelect = (resource: ResourceTreeNode | null) => {
  selectedResource.value = resource;
  if (resource) {
    selectedResourceTypeCode.value = resource.resourceTypeCode;
    loadOperations();
  } else {
    operationList.value = [];
  }
};

const handleResourceTypeChange = (typeCode: string) => {
  selectedResourceTypeCode.value = typeCode;
  selectedResource.value = null;
  loadResourceTree();
  loadOperations();
};

const handleDomainChange = (code: string) => {
  domainCode.value = code;
  loadResourceTree();
  loadOperations();
  loadExistingPermissions();
};

// ========== 权限配置 ==========

const handleSavePermissions = async (
  addItems: Array<GrantAddItem>,
  removeIds: Array<number>
) => {
  if (!roleDetail.value) return;
  if (!canAssignPerm) {
    ElMessage.warning("无权限执行此操作");
    return;
  }

  saving.value = true;
  try {
    // 先回收
    if (removeIds.length > 0) {
      await revokeRolePermissions({
        domainCode: domainCode.value || undefined,
        roleTypeCode: roleDetail.value.roleTypeCode,
        roleExternalId: roleDetail.value.externalId,
        permissionIds: removeIds
      });
    }

    // 再添加
    if (addItems.length > 0) {
      await saveRolePermissions({
        domainCode: domainCode.value || undefined,
        roleTypeCode: roleDetail.value.roleTypeCode,
        roleExternalId: roleDetail.value.externalId,
        add: addItems
      });
    }

    ElMessage.success("保存成功");
    await loadExistingPermissions();
  } catch {
    ElMessage.error("保存失败");
  } finally {
    saving.value = false;
  }
};

// ========== 返回 ==========

const handleBack = () => {
  router.push("/perm/role");
};

// ========== 资源类型列表 ==========

const resourceTypeOptions = computed(() => {
  // 从资源树中提取所有资源类型
  const typeSet = new Set<string>();
  const traverse = (nodes: Array<ResourceTreeNode>) => {
    nodes.forEach(node => {
      typeSet.add(node.resourceTypeCode);
      if (node.children) traverse(node.children);
    });
  };
  traverse(resourceTreeData.value);
  return Array.from(typeSet).map(code => ({
    value: code,
    label: code
  }));
});

// ========== 已配置权限数量 ==========

const configuredCount = computed(() => existingPermissions.value.length);

// ========== 初始化 ==========

onMounted(() => {
  const id = route.query.roleId as string;
  if (id) {
    const numId = Number(id);
    if (!isNaN(numId) && numId > 0) {
      roleId.value = numId;
      // watch 会触发 loadAllData，无需在此重复调用
    } else {
      ElMessage.error("无效的角色ID");
    }
  }
});

// 监听角色ID变化
watch(roleId, newId => {
  if (newId) {
    loadAllData();
  }
});
</script>

<template>
  <div class="role-permission">
    <div v-if="!roleDetail" v-loading="loading" class="text-center py-10">
      <div v-if="!roleId" class="text-gray-500">请选择角色</div>
    </div>

    <div v-else class="flex flex-col h-full">
      <!-- 顶部: 角色信息 + 操作按钮 -->
      <div class="flex items-center justify-between p-4 border-b bg-white">
        <div class="flex items-center gap-4">
          <el-button link @click="handleBack">
            <IconifyIconOffline :icon="ArrowLeft" class="mr-1" />
            返回
          </el-button>
          <h3 class="font-medium">
            配置角色权限: {{ roleDetail.name }}
            <el-tag size="small" class="ml-2">
              {{ roleDetail.externalId }}
            </el-tag>
          </h3>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-gray-500">
            已配置 {{ configuredCount }} 项权限
          </span>
        </div>
      </div>

      <!-- 主体: 资源树 + 操作配置 -->
      <div class="flex flex-1 overflow-hidden">
        <!-- 左侧: 资源类型筛选 + 资源树 -->
        <div class="w-[350px] border-r flex flex-col">
          <!-- 资源类型筛选 -->
          <div class="p-4 border-b">
            <div class="flex items-center gap-2 mb-2">
              <span class="text-gray-600 text-sm">资源类型:</span>
              <el-select
                v-model="selectedResourceTypeCode"
                placeholder="全部类型"
                clearable
                size="small"
                class="w-[180px]"
                @change="handleResourceTypeChange"
              >
                <el-option
                  v-for="opt in resourceTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-gray-600 text-sm">组织上下文:</span>
              <el-input
                v-model="domainCode"
                placeholder="可选"
                clearable
                size="small"
                class="w-[180px]"
                @change="handleDomainChange"
              />
            </div>
          </div>

          <!-- 资源树 -->
          <ResourceTree
            :data="resourceTreeData"
            :loading="loading"
            :selected-resource="selectedResource"
            :existing-permissions="existingPermissions"
            @select="handleResourceSelect"
          />
        </div>

        <!-- 右侧: 操作配置 -->
        <div class="flex-1 p-4 overflow-auto">
          <div v-if="!selectedResource" class="text-center py-10 text-gray-500">
            请选择资源节点以配置权限
          </div>

          <OperationConfig
            v-else
            :resource="selectedResource"
            :operations="operationList"
            :existing-permissions="existingPermissions"
            :saving="saving"
            :can-assign="canAssignPerm"
            @save="handleSavePermissions"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.role-permission {
  padding: 20px;
  height: calc(100vh - 100px);
  background: #f5f7fa;
}
</style>
