<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ServiceConfigResp } from "@/api/service-interface";
import {
  createSyncPayload,
  parseSyncGroups,
  type SyncFormData,
  type SyncSubmission
} from "../utils/types";

defineOptions({ name: "ServiceInterfaceSyncForm" });

const props = defineProps<{ service: ServiceConfigResp }>();

const formRef = ref<FormInstance>();
const formData = reactive<SyncFormData>(createSyncPayload(props.service));

const rules = computed<FormRules>(() => ({
  basePath: [
    { required: true, message: "请输入基础路径", trigger: "blur" },
    {
      pattern: /^\//,
      message: "基础路径必须以 / 开头",
      trigger: "blur"
    }
  ],
  groupsJson: [
    {
      validator: (_rule, value: string, callback) => {
        const { error } = parseSyncGroups(value, formData.version);
        callback(error ? new Error(error) : undefined);
      },
      trigger: "blur"
    }
  ]
}));

function reset() {
  Object.assign(formData, createSyncPayload(props.service));
}

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

function getFormData(): SyncSubmission | null {
  const { groups } = parseSyncGroups(formData.groupsJson, formData.version);
  if (!groups) return null;
  const request = {
    serviceCode: props.service.serviceCode,
    basePath: formData.basePath.trim(),
    syncMode: "FULL" as const,
    groups
  };
  return formData.version === 2
    ? {
        version: 2,
        request: {
          ...request,
          // parseSyncGroups v2 分支已逐项校验 requiredPermission 非空且格式合法
          groups: groups.map(g => ({
            groupCode: g.groupCode,
            groupName: g.groupName,
            apis: g.apis.map(a => ({
              ...a,
              requiredPermission: a.requiredPermission!
            }))
          }))
        }
      }
    : { version: 1, request };
}

watch(() => props.service, reset);

defineExpose({ validate, getFormData });
</script>

<template>
  <div class="sync-form-shell">
    <el-alert type="warning" :closable="false" show-icon>
      <template #title>FULL 同步会以本次清单为当前服务的完整事实来源</template>
      <template #default>
        缺失的、且由该服务自动维护的接口映射会被软删除；手工维护的映射不会被此操作清理。
      </template>
    </el-alert>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="88px"
      class="sync-form"
    >
      <el-form-item label="服务编码">
        <el-input
          :model-value="service.serviceCode"
          readonly
          class="font-mono"
        />
      </el-form-item>
      <el-form-item label="同步内容">
        <el-select v-model="formData.version" class="w-full!">
          <el-option label="仅接口登记（旧协议）" :value="1" />
          <el-option label="接口与业务准入操作" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="同步模式">
        <el-tag type="warning" effect="plain">FULL</el-tag>
      </el-form-item>
      <el-form-item label="基础路径" prop="basePath">
        <el-input v-model.trim="formData.basePath" class="font-mono" />
      </el-form-item>
      <el-form-item label="接口分组" prop="groupsJson">
        <el-input
          v-model="formData.groupsJson"
          type="textarea"
          :rows="15"
          spellcheck="false"
          class="sync-payload font-mono"
        />
        <div class="sync-help">
          使用 groups → apis 结构；每条接口须提供名称、HTTP
          方法、相对路径和资源编码。新版还须提供
          requiredPermission：resourceTypeCode 与
          operationCode；不会自动切换服务鉴权模式。
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<style lang="scss" scoped>
.sync-form-shell {
  display: grid;
  gap: 18px;
}

.sync-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.sync-payload :deep(textarea) {
  line-height: 1.55;
  tab-size: 2;
}

.sync-help {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
