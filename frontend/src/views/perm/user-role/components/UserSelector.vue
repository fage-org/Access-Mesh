<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { getUserPage, type UserPageItem } from "@/api/admin/user";
import { STATUS_ENABLED } from "@/constants/common";

defineOptions({
  name: "UserSelector"
});

const props = defineProps<{
  selectedUserId: number | null;
}>();

const emit = defineEmits<{
  select: [userId: number, username: string];
}>();

// ========== 状态定义 ==========

const loading = ref(false);
const userList = ref<Array<UserPageItem>>([]);
const searchForm = reactive({
  username: "",
  name: ""
});

// ========== 数据加载 ==========

const loadUsers = async () => {
  loading.value = true;
  try {
    const res = await getUserPage({
      pageNum: 1,
      pageSize: 100,
      username: searchForm.username,
      name: searchForm.name,
      status: STATUS_ENABLED // 只加载启用用户
    });
    if (res.success) {
      userList.value = res.data.list;
    }
  } catch {
    // 加载用户列表失败，静默处理，不影响用户操作
  } finally {
    loading.value = false;
  }
};

// ========== 用户点击 ==========

const handleUserClick = (user: UserPageItem) => {
  emit("select", user.id, user.username);
};

// ========== 搜索 ==========

const handleSearch = () => {
  loadUsers();
};

// ========== 初始化 ==========

onMounted(() => {
  loadUsers();
});
</script>

<template>
  <div class="user-selector">
    <!-- 搜索栏 -->
    <div class="p-4 border-b">
      <el-input
        v-model="searchForm.username"
        placeholder="搜索用户名"
        clearable
        class="mb-2"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" size="small" @click="handleSearch">
        搜索
      </el-button>
    </div>

    <!-- 用户列表 -->
    <el-scrollbar class="flex-1">
      <div v-loading="loading">
        <div
          v-for="user in userList"
          :key="user.id"
          class="p-3 border-b cursor-pointer hover:bg-gray-50"
          :class="{ 'bg-blue-50': user.id === props.selectedUserId }"
          @click="handleUserClick(user)"
        >
          <div class="flex items-center gap-2">
            <el-avatar :size="32">
              {{ user.name.charAt(0) }}
            </el-avatar>
            <div>
              <div class="font-medium">{{ user.name }}</div>
              <div class="text-gray-500 text-sm">{{ user.username }}</div>
            </div>
          </div>
        </div>
      </div>

      <div
        v-if="userList.length === 0 && !loading"
        class="text-center py-10 text-gray-500"
      >
        暂无用户
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.user-selector {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
