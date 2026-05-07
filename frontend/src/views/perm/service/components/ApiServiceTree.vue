<script setup lang="ts">
import { computed } from "vue";
import { type ApiMappingItem, getHttpMethodTag } from "@/api/perm/service";

defineOptions({
  name: "ApiServiceTree"
});

const props = defineProps<{
  data: Array<ApiMappingItem>;
}>();

// ========== 按路径分组 ==========

interface ApiGroup {
  pathPrefix: string;
  apis: Array<ApiMappingItem>;
}

const groupedApis = computed(() => {
  const groups: Map<string, Array<ApiMappingItem>> = new Map();

  props.data.forEach(api => {
    // 提取路径前缀（第一级）
    const parts = api.pathPattern.split("/");
    const prefix = parts.length > 1 ? parts[1] : "root";

    if (!groups.has(prefix)) {
      groups.set(prefix, []);
    }
    groups.get(prefix).push(api);
  });

  return Array.from(groups.entries()).map(([pathPrefix, apis]) => ({
    pathPrefix,
    apis
  }));
});
</script>

<template>
  <div class="api-service-tree">
    <el-scrollbar class="flex-1" style="max-height: 400px">
      <div class="p-3">
        <div v-for="group in groupedApis" :key="group.pathPrefix" class="mb-4">
          <div class="font-medium text-gray-700 mb-2">
            /{{ group.pathPrefix }}
            <span class="text-gray-400 text-sm ml-2">
              {{ group.apis.length }} 个接口
            </span>
          </div>
          <div class="border rounded">
            <div
              v-for="api in group.apis"
              :key="api.id"
              class="flex items-center justify-between p-2 border-b last:border-b-0"
            >
              <div class="flex items-center gap-2">
                <el-tag
                  :type="getHttpMethodTag(api.httpMethod).type"
                  size="small"
                >
                  {{ getHttpMethodTag(api.httpMethod).text }}
                </el-tag>
                <span class="text-gray-600">{{ api.pathPattern }}</span>
              </div>
              <el-tag :type="api.enabled ? 'success' : 'info'" size="small">
                {{ api.enabled ? "启用" : "禁用" }}
              </el-tag>
            </div>
          </div>
        </div>

        <div
          v-if="props.data.length === 0"
          class="text-center py-10 text-gray-500"
        >
          暂无接口映射，点击"同步接口"生成
        </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.api-service-tree {
  display: flex;
  flex-direction: column;
}
</style>
