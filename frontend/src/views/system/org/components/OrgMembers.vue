<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { getUserPage, type UserPageItem } from "@/api/admin/user";

defineOptions({
  name: "OrgMembers"
});

const props = defineProps<{
  orgId: number | null;
}>();

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<UserPageItem>>([]);
const total = ref(0);

// ========== 加载组织成员 ==========

const loadMembers = async () => {
  if (!props.orgId) return;

  loading.value = true;
  try {
    const res = await getUserPage({
      pageNum: 1,
      pageSize: 100,
      orgId: props.orgId
    });
    if (res.success) {
      tableData.value = res.data.list;
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("[OrgMembers] Load members failed:", error);
    ElMessage.error("加载组织成员失败");
  } finally {
    loading.value = false;
  }
};

// ========== 监听 orgId 变化 ==========

watch(
  () => props.orgId,
  () => {
    loadMembers();
  },
  { immediate: true }
);
</script>

<template>
  <div class="org-members">
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="name" label="姓名" min-width="100" />
      <el-table-column prop="email" label="邮箱" min-width="150" />
      <el-table-column prop="phone" label="手机号" min-width="120" />
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? "启用" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="主组织" min-width="100">
        <template #default="{ row }">
          <span v-if="row.orgs && row.orgs.length > 0">
            <el-tag
              v-if="row.orgs.find(o => o.orgId === props.orgId)?.isPrimary"
              type="success"
              size="small"
            >
              主组织
            </el-tag>
            <el-tag v-else type="info" size="small"> 成员 </el-tag>
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total === 0 && !loading" class="text-center py-10 text-gray-500">
      该组织暂无成员
    </div>
  </div>
</template>

<style scoped lang="scss">
.org-members {
  min-height: 300px;
}
</style>
