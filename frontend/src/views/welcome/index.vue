<script setup lang="ts">
import { computed } from "vue";
import { useUserStoreHook } from "@/store/modules/user";
import type { UserMenuRoute } from "@/api/auth";

defineOptions({
  name: "Welcome"
});
const user = useUserStoreHook();
const ready = computed(() => user.menuGateStatus === "loaded");
const entries = computed(() => {
  const result = new Map<string, string>();
  function visit(nodes: UserMenuRoute[]) {
    for (const node of nodes) {
      if (node.children?.length) visit(node.children);
      else if (
        node.path &&
        /^\/(?!\/)/.test(node.path) &&
        node.path !== "/welcome"
      ) {
        result.set(
          node.path,
          String(node.meta?.title || node.name || node.path)
        );
      }
    }
  }
  visit(user.menus);
  return [...result].map(([path, title]) => ({ path, title }));
});
</script>

<template>
  <section class="p-4">
    <el-card shadow="never">
      <h1 class="text-2xl font-semibold">AccessMesh 访问管理</h1>
      <p class="mt-2">{{ user.nickname || user.username }}，欢迎回来。</p>
      <p class="mt-2 text-sm">从当前账号可用的入口开始管理账号、角色与授权。</p>
    </el-card>
    <el-alert
      v-if="user.menuLoadFailed"
      class="mt-4"
      title="菜单加载失败，当前入口暂不可用"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-card class="mt-4" shadow="never">
      <template #header>当前会话</template>
      <el-descriptions :column="1">
        <el-descriptions-item label="当前角色">
          {{ ready ? user.roles.join("、") || "无可用角色" : "尚未加载" }}
        </el-descriptions-item>
        <el-descriptions-item label="可用管理入口">{{
          ready ? entries.length : "尚未加载"
        }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="ready && entries.length" class="mt-4 flex flex-wrap gap-3">
        <router-link
          v-for="entry in entries"
          :key="entry.path"
          :to="entry.path"
        >
          <el-button>{{ entry.title }}</el-button>
        </router-link>
      </div>
      <router-link v-else to="/menu-retry"
        ><el-button class="mt-4">查看菜单状态</el-button></router-link
      >
    </el-card>
  </section>
</template>
