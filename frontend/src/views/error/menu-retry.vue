<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { initRouter } from "@/router/utils";
import { useUserStoreHook } from "@/store/modules/user";
import { message } from "@/utils/message";
import RefreshRight from "~icons/ep/refresh-right";

defineOptions({
  name: "MenuLoadRetry"
});

const router = useRouter();
const retrying = ref(false);

/**
 * 强制重取会话菜单：清空 store 内存态后 initRouter 即走重取分支
 * （T-FE-015 设计定案：失败 fail-closed 空菜单 + 可重试，不持久化、不回退全量静态菜单）
 */
async function retry() {
  if (retrying.value) return;
  retrying.value = true;
  try {
    useUserStoreHook().SET_MENUS([]);
    await initRouter();
    if (useUserStoreHook().menus.length > 0) {
      message("菜单已恢复", { type: "success" });
      router.replace("/");
    } else {
      message("菜单加载仍失败，请稍后重试", { type: "error" });
    }
  } finally {
    retrying.value = false;
  }
}
</script>

<template>
  <div class="menu-retry-page">
    <el-card shadow="never" class="retry-card">
      <div class="retry-icon">
        <IconifyIconOffline
          icon="ep/warning-filled"
          width="42px"
          height="42px"
        />
      </div>
      <h2 class="retry-title">菜单加载失败</h2>
      <p class="retry-desc">
        会话菜单拉取失败，为保证安全已按最小权限展示空菜单。<br />
        请检查网络或后端服务后重试；刷新页面同样会触发重取。
      </p>
      <el-button
        type="primary"
        :icon="RefreshRight"
        :loading="retrying"
        @click="retry"
      >
        重新加载菜单
      </el-button>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.menu-retry-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  background: var(--el-bg-color-page);
}

.retry-card {
  max-width: 420px;
  padding: 24px 8px;
  text-align: center;

  .retry-icon {
    color: var(--el-color-warning);
  }

  .retry-title {
    margin: 12px 0 8px;
    font-size: 18px;
    font-weight: 600;
  }

  .retry-desc {
    margin-bottom: 20px;
    font-size: 13px;
    line-height: 1.8;
    color: var(--el-text-color-secondary);
  }
}
</style>
