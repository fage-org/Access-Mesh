<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { usePlatformStoreHook } from "@/store/modules/platform";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
defineOptions({ name: "PlatformLayout" });
const route = useRoute();
const router = useRouter();
const operator = usePlatformStoreHook();
onMounted(() =>
  operator
    .refresh()
    .catch(error =>
      message(toErrorMessage(error, "平台账号信息加载失败"), { type: "error" })
    )
);
</script>
<template>
  <el-container direction="vertical">
    <el-header
      ><el-menu
        mode="horizontal"
        :default-active="route.path"
        :ellipsis="false"
        router
      >
        <el-menu-item index="/platform/tenants">AccessMesh · 租户</el-menu-item>
        <el-menu-item index="/platform/accounts">平台账号</el-menu-item>
        <el-menu-item index="/platform/audit">平台审计</el-menu-item>
        <el-sub-menu index="operator"
          ><template #title>{{
            operator.account?.name || "平台账号"
          }}</template>
          <el-menu-item index="/platform/change-password"
            >修改密码</el-menu-item
          >
          <el-menu-item @click="operator.logout()">退出平台</el-menu-item>
        </el-sub-menu>
      </el-menu></el-header
    >
    <el-main><router-view /></el-main>
    <el-footer
      ><el-button link @click="router.push('/login')"
        >进入租户登录</el-button
      ></el-footer
    >
  </el-container>
</template>
