<template>
  <el-config-provider :locale="zhCn">
    <el-result
      v-if="showSessionChange"
      icon="warning"
      title="登录状态已改变"
      :sub-title="
        platformPage
          ? '旧页面与一次性密码弹窗已清除。未复制的密码需重新重置取得，请重新载入后继续。'
          : '未保存的普通表单已清除。已保存的授权草稿仍归原账号，请重新载入后继续。'
      "
    >
      <template #extra>
        <el-button type="primary" @click="reloadSession">重新载入</el-button>
      </template>
    </el-result>
    <template v-else>
      <router-view />
      <ReDialog />
    </template>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { ElConfigProvider, ElResult, ElButton } from "element-plus";
import { ReDialog } from "@/components/ReDialog";
import { tenantSessionBlocked } from "@/utils/tenant-session";
import { reloadSessionPage } from "@/utils/session-boundary";
import { platformSessionBlocked } from "@/utils/platform-session";
import {
  getTenantSession,
  isSessionTerminated,
  getPlatformSession
} from "@/utils/auth";
import zhCn from "element-plus/es/locale/lang/zh-cn";
defineOptions({ name: "app" });
const router = useRouter();
const platformPage = computed(() =>
  router.currentRoute.value.path.startsWith("/platform")
);
const showSessionChange = computed(() =>
  platformPage.value ? platformSessionBlocked.value : tenantSessionBlocked.value
);
function reloadSession() {
  let path: string;
  if (platformPage.value) {
    const platform = getPlatformSession();
    path =
      platform && platform.expires > Date.now()
        ? "/platform/tenants"
        : "/platform/login";
  } else {
    path = isSessionTerminated(getTenantSession()) ? "/login" : "/";
  }
  reloadSessionPage(window.location, router.resolve(path).href);
}
</script>
