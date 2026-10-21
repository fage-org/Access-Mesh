<script setup lang="ts">
import { computed, onScopeDispose, watch } from "vue";
import {
  getResourceAssociationDetail,
  type ResourceAssociationDetailResp,
  type ResourceTreeNode
} from "@/api/resource-operation";
import { useListLoad } from "@/utils/list-load";
import {
  associationLabel,
  type AssociationMode
} from "@/utils/resource-association";

defineOptions({ name: "ResourceAssociationInfo" });
const props = defineProps<{ resource: ResourceTreeNode }>();
const key = computed(() =>
  JSON.stringify([
    props.resource.resourceTypeCode,
    props.resource.codeType,
    props.resource.code
  ])
);
const { list, loading, error, load, clear } =
  useListLoad<ResourceAssociationDetailResp>({
    contextKey: () => key.value,
    errorText: "资源归属与引用信息加载失败",
    fetcher: async () => [
      await getResourceAssociationDetail({
        resourceTypeCode: props.resource.resourceTypeCode,
        code: props.resource.code,
        codeType: props.resource.codeType
      })
    ]
  });
const detail = computed(() => list.value[0]);
const label = computed(
  () =>
    associationLabel(
      (detail.value?.associationMode ?? "UNKNOWN") as AssociationMode
    ) ?? "不支持的关系声明"
);
watch(
  () => props.resource,
  () => {
    clear();
    void load();
  },
  { immediate: true, flush: "sync" }
);
onScopeDispose(clear);
</script>

<template>
  <el-skeleton v-if="loading" :rows="2" animated />
  <el-alert v-else-if="error" :title="error" type="error" :closable="false">
    <el-button link type="primary" @click="load">重试</el-button>
  </el-alert>
  <el-descriptions
    v-else-if="detail"
    title="归属与引用（只读）"
    :column="2"
    border
  >
    <el-descriptions-item label="关系性质">{{ label }}</el-descriptions-item>
    <el-descriptions-item label="维护方">{{
      detail.sourceService ? `业务服务 ${detail.sourceService}` : "IAM 管理员"
    }}</el-descriptions-item>
    <el-descriptions-item
      v-if="detail.associationOwner"
      label="所属主资源"
      :span="2"
    >
      {{ detail.associationOwner.name }}（{{
        detail.associationOwner.resourceTypeCode
      }}
      / {{ detail.associationOwner.code }}）
      <el-tag v-if="detail.associationOwner.status !== 1" type="warning"
        >主资源已停用</el-tag
      >
    </el-descriptions-item>
    <el-descriptions-item label="整类引用" :span="2">{{
      detail.referencedTypeCodes.join("、") || "无"
    }}</el-descriptions-item>
    <el-descriptions-item label="专属实例数">{{
      detail.exclusiveResourceCount
    }}</el-descriptions-item>
    <el-descriptions-item label="引用本类型的主资源数">{{
      detail.referencingResourceCount
    }}</el-descriptions-item>
  </el-descriptions>
</template>
