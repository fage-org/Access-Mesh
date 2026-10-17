<template>
  <el-config-provider :locale="zhCn">
    <el-result
      v-if="showSessionChange"
      icon="warning"
      title="登录状态已在其他标签页改变"
      sub-title="未保存的普通表单已清除。已保存的授权草稿仍归原账号，请重新载入后继续。"
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
import {
  tenantSessionBlocked,
  reloadTenantSession
} from "@/utils/tenant-session";
import { getToken, isSessionTerminated } from "@/utils/auth";
import zhCn from "element-plus/es/locale/lang/zh-cn";
defineOptions({ name: "app" });
const router = useRouter();
const showSessionChange = computed(
  () =>
    tenantSessionBlocked.value &&
    !router.currentRoute.value.path.startsWith("/platform")
);
function reloadSession() {
  const path = isSessionTerminated(getToken()) ? "/login" : "/";
  reloadTenantSession(window.location, router.resolve(path).href);
}
</script>
