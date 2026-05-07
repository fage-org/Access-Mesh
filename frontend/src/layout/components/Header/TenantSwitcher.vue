<script setup lang="ts">
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { useTenantStoreHook } from "@/store/modules/tenant";
import { removeToken } from "@/utils/auth";
import { startOAuth2Flow } from "@/utils/oauth2";
import { stopTokenRefreshScheduler } from "@/utils/http/tokenRefreshScheduler";
import { ArrowDown } from "@element-plus/icons-vue";

defineOptions({
  name: "TenantSwitcher"
});

const router = useRouter();
const tenantStore = useTenantStoreHook();
const loading = ref(false);

// OAuth2 是否启用
const oauth2Enabled = import.meta.env.VITE_OAUTH2_ENABLED === "true";

const currentTenantName = computed(() => {
  const tenant = tenantStore.tenantList.find(
    t => t.id === tenantStore.currentTenantId
  );
  return tenant?.name || "未选择租户";
});

// 切换租户(OAuth2 模式: 重新登录)
const handleSwitchTenant = async (tenantId: number) => {
  loading.value = true;

  // 1. 停止定时刷新调度器
  stopTokenRefreshScheduler();

  // 2. 清除当前 Token
  removeToken();

  // 3. 更新租户 ID
  tenantStore.SET_CURRENT_TENANT(tenantId);

  // 4. OAuth2 模式: 启动新的 OAuth2 流程
  if (oauth2Enabled) {
    try {
      await startOAuth2Flow(tenantId);
    } catch (error) {
      console.error("Switch tenant failed:", error);
      router.push("/login");
    }
  } else {
    // 简化登录模式: 跳转登录页
    router.push("/login");
  }

  loading.value = false;
};

// 跳转到登录页(选择其他租户)
const goToLogin = () => {
  stopTokenRefreshScheduler();
  removeToken();
  router.push("/login");
};
</script>

<template>
  <div class="tenant-switcher">
    <el-dropdown
      trigger="click"
      :loading="loading"
      @command="handleSwitchTenant"
    >
      <div class="tenant-dropdown-link">
        <span class="tenant-name">{{ currentTenantName }}</span>
        <el-icon class="el-icon--right">
          <ArrowDown />
        </el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="tenant in tenantStore.tenantList"
            :key="tenant.id"
            :command="tenant.id"
            :disabled="tenant.id === tenantStore.currentTenantId"
          >
            {{ tenant.name }}
          </el-dropdown-item>
          <el-dropdown-item divided @click="goToLogin">
            切换其他租户
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped lang="scss">
.tenant-switcher {
  display: flex;
  align-items: center;
  margin-right: 20px;
}

.tenant-dropdown-link {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 10px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.3s;

  &:hover {
    background-color: rgb(0 0 0 / 2.5%);
  }
}

.tenant-name {
  margin-right: 5px;
  font-size: 14px;
  color: #333;
}
</style>
