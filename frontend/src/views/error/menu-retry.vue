<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { reloadSessionMenus } from "./menu-retry-reload";
import { useUserStoreHook } from "@/store/modules/user";
import { message } from "@/utils/message";
import RefreshRight from "~icons/ep/refresh-right";

defineOptions({
  name: "MenuLoadRetry"
});

const router = useRouter();
const retrying = ref(false);

const userStore = useUserStoreHook();

/**
 * 着陆页两态（T-FE-049）：
 *  - 拉取失败（menuLoadFailed）：「菜单加载失败」——检查网络/后端后重试
 *  - 拉取成功但账号无菜单：不承诺重试有效，引导联系管理员；
 *    保留「重新检查」入口——管理员补配权限后无需重登，点击即重取（F5 同效）
 */
const emptyButLoaded = computed(() => !userStore.menuLoadFailed);

const pageTitle = computed(() =>
  emptyButLoaded.value ? "当前账号无可用菜单" : "菜单加载失败"
);

const pageDesc = computed(() =>
  emptyButLoaded.value
    ? "当前账号尚未被分配任何菜单权限。请联系管理员分配角色/权限后，点击下方按钮重新加载（刷新页面同效），无需重新登录。"
    : "会话菜单拉取失败，为保证安全已按最小权限展示空菜单。\n请检查网络或后端服务后重试；刷新页面同样会触发重取。"
);

/**
 * 强制重取会话菜单：经会话能力刷新入口重取（不预清——失败时 menus/侧栏/门禁
 * 全保留旧态；T-FE-056 外评处置，2026-09-20 拍板）——编排与回归锁见
 * ./menu-retry-reload.ts（SFC 逻辑提取可测化，orgTree.ts/messages.ts 先例）；
 * 三态消费消息与跳转
 */
async function retry() {
  if (retrying.value) return;
  retrying.value = true;
  try {
    const outcome = await reloadSessionMenus();
    if (outcome === "recovered") {
      message("菜单已恢复", { type: "success" });
      router.replace("/");
    } else if (outcome === "still-failed") {
      message("菜单加载仍失败，请稍后重试", { type: "error" });
    } else {
      message("当前账号仍无可用菜单，请联系管理员分配权限", {
        type: "warning"
      });
    }
  } catch (err) {
    // 会话已终结（Q-020 收口，含 401 型——拦截器已提示并 logOut）：前置/后置
    // 双判抛 SessionExpiredError，统一层已提示「会话已过期」并跳登录，不按陈旧
    // 菜单状态弹失真业务提示。预期冒泡仅此一类（普通拉取失败由本编排 catch 转 still-failed 三态——能力刷新入口自身原样抛出不吞，见其 JSDoc）；意外异常同落此处仅 console.warn 留痕，不窄化判别
    // （resetModules 跨模块图下 instanceof 不可靠）
    console.warn("[menu-retry] 菜单重试中止（会话已终结）", err);
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
          :icon="emptyButLoaded ? 'ep/menu' : 'ep/warning-filled'"
          width="42px"
          height="42px"
        />
      </div>
      <h2 class="retry-title">{{ pageTitle }}</h2>
      <p class="retry-desc">{{ pageDesc }}</p>
      <el-button
        type="primary"
        :icon="RefreshRight"
        :loading="retrying"
        @click="retry"
      >
        {{ emptyButLoaded ? "重新检查菜单" : "重新加载菜单" }}
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
    // pageDesc 失败分支以 \n 表达两段（旧实现 <br /> 同效），保留换行渲染
    white-space: pre-line;
  }
}
</style>
