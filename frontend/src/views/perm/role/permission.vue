<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { getRoleDetail, type RoleDetail } from "@/api/perm/role";

defineOptions({
  name: "PermRolePermission"
});

const route = useRoute();
const roleId = ref<number | null>(null);
const roleDetail = ref<RoleDetail | null>(null);
const loading = ref(false);

// ========== 初始化 ==========

onMounted(async () => {
  const id = route.query.roleId as string;
  if (id) {
    roleId.value = Number(id);
    try {
      const res = await getRoleDetail({ id: roleId.value });
      if (res.success) {
        roleDetail.value = res.data;
      }
    } catch {
      ElMessage.error("加载角色详情失败");
    }
  }
});
</script>

<template>
  <div class="role-permission">
    <div v-if="!roleDetail" class="text-center py-10 text-gray-500">
      请选择角色
    </div>

    <div v-else>
      <h3 class="mb-4">配置角色权限: {{ roleDetail.name }}</h3>
      <div class="text-center py-10 text-gray-500">
        角色权限配置功能将在 Phase 3.3 详细实现
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.role-permission {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
