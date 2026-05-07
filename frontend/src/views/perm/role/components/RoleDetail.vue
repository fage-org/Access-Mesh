<script setup lang="ts">
import { ref } from "vue";
import {
  type RoleDetail,
  getStatusTag,
  ROLE_TYPE_CODES
} from "@/api/perm/role";
import ExtraRolesPanel from "./ExtraRolesPanel.vue";

defineOptions({
  name: "RoleDetail"
});

const props = defineProps<{
  roleDetail: RoleDetail | null;
  roleId: number | null;
  canUpdate: boolean;
  canAssignPerm: boolean;
}>();

const emit = defineEmits<{
  edit: [];
  configPermission: [];
}>();

// ========== 状态定义 ==========

const activeTab = ref("detail");

// ========== 事件处理 ==========

const handleEdit = () => {
  emit("edit");
};

const handleConfigPermission = () => {
  emit("configPermission");
};
</script>

<template>
  <div class="role-detail">
    <div v-if="!props.roleDetail" class="text-center py-10 text-gray-500">
      请选择角色节点
    </div>

    <div v-else>
      <!-- 操作按钮 -->
      <div class="flex gap-2 mb-4">
        <el-button
          type="primary"
          size="small"
          :disabled="!props.canUpdate"
          @click="handleEdit"
        >
          编辑
        </el-button>
        <el-button
          type="success"
          size="small"
          :disabled="!props.canAssignPerm"
          @click="handleConfigPermission"
        >
          配置权限
        </el-button>
      </div>

      <!-- Tab 切换(分组角色专属) -->
      <el-tabs
        v-if="props.roleDetail.roleTypeCode === ROLE_TYPE_CODES.GROUP_ROLE"
        v-model="activeTab"
      >
        <el-tab-pane label="角色详情" name="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="角色名称">
              {{ props.roleDetail.name }}
            </el-descriptions-item>
            <el-descriptions-item label="角色编码">
              {{ props.roleDetail.externalId }}
            </el-descriptions-item>
            <el-descriptions-item label="角色类型">
              {{ props.roleDetail.roleTypeName }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusTag(props.roleDetail.status).type">
                {{ getStatusTag(props.roleDetail.status).text }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="排序">
              {{ props.roleDetail.sortOrder }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ props.roleDetail.createdAt }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="额外角色配置" name="extraRoles">
          <ExtraRolesPanel
            :role-id="props.roleId"
            :role-type-code="props.roleDetail.roleTypeCode"
            :role-external-id="props.roleDetail.externalId"
          />
        </el-tab-pane>
      </el-tabs>

      <!-- 基本角色只显示详情 -->
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="角色名称">
          {{ props.roleDetail.name }}
        </el-descriptions-item>
        <el-descriptions-item label="角色编码">
          {{ props.roleDetail.externalId }}
        </el-descriptions-item>
        <el-descriptions-item label="角色类型">
          {{ props.roleDetail.roleTypeName }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTag(props.roleDetail.status).type">
            {{ getStatusTag(props.roleDetail.status).text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="排序">
          {{ props.roleDetail.sortOrder }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ props.roleDetail.createdAt }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<style scoped lang="scss">
.role-detail {
  max-width: 600px;
}
</style>
