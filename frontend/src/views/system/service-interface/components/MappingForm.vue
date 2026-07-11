<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ApiMappingResp } from "@/api/service-interface";
import {
  createEmptyMappingForm,
  mappingToForm,
  HTTP_METHOD_OPTIONS,
  validateOptionalJson,
  type MappingFormData
} from "../utils/types";

defineOptions({ name: "ServiceInterfaceMappingForm" });

const props = defineProps<{
  mode: "create" | "edit";
  serviceCode: string;
  initialData?: ApiMappingResp | null;
}>();

const formRef = ref<FormInstance>();
const formData = reactive<MappingFormData>(createEmptyMappingForm());
const isEdit = computed(() => props.mode === "edit");

const rules = computed<FormRules>(() => ({
  resourceEntityId: [
    {
      required: true,
      type: "number",
      message: "请输入资源实体 ID",
      trigger: "blur"
    },
    {
      validator: (_rule, value: number | null, callback) => {
        callback(
          value != null && Number.isInteger(value) && value > 0
            ? undefined
            : new Error("资源实体 ID 必须是正整数")
        );
      },
      trigger: "blur"
    }
  ],
  httpMethod: [
    { required: true, message: "请选择请求方法", trigger: "change" }
  ],
  pathPattern: [
    { required: true, message: "请输入路径模式", trigger: "blur" },
    {
      pattern: /^\//,
      message: "路径模式必须以 / 开头",
      trigger: "blur"
    },
    { max: 512, message: "路径模式最长 512 字符", trigger: "blur" }
  ],
  matchOrder: [
    {
      validator: (_rule, value: number, callback) => {
        callback(
          Number.isInteger(value) && value >= 0
            ? undefined
            : new Error("匹配顺序必须是不小于 0 的整数")
        );
      },
      trigger: "blur"
    }
  ],
  extra: [
    {
      validator: (_rule, value: string, callback) => {
        const error = validateOptionalJson(value);
        callback(error ? new Error(error) : undefined);
      },
      trigger: "blur"
    }
  ]
}));

function initForm() {
  Object.assign(
    formData,
    props.mode === "edit" && props.initialData
      ? mappingToForm(props.initialData)
      : createEmptyMappingForm()
  );
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

function getFormData(): MappingFormData {
  return { ...formData };
}

watch(() => props.initialData, initForm, { immediate: true });

defineExpose({ validate, getFormData });
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    label-width="104px"
    class="mapping-form"
  >
    <el-form-item label="所属服务">
      <el-input :model-value="serviceCode" readonly class="font-mono" />
    </el-form-item>
    <el-form-item label="资源实体 ID" prop="resourceEntityId">
      <!-- 当前映射接口以 resourceId 内部主键定位；Phase 2 将接入资源选择器。 -->
      <el-input-number
        v-model="formData.resourceEntityId"
        :min="1"
        :precision="0"
        :controls="false"
        placeholder="例如 10001"
        class="w-full!"
        :disabled="isEdit"
      />
      <div class="form-help">与资源实体建立接口级鉴权关联</div>
    </el-form-item>
    <el-form-item label="请求方法" prop="httpMethod">
      <el-select v-model="formData.httpMethod" class="w-full!">
        <el-option
          v-for="item in HTTP_METHOD_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="路径模式" prop="pathPattern">
      <el-input
        v-model.trim="formData.pathPattern"
        placeholder="如 /admin/api/user/list"
        maxlength="512"
        class="font-mono"
      />
    </el-form-item>
    <el-form-item label="匹配顺序" prop="matchOrder">
      <el-input-number
        v-model="formData.matchOrder"
        :min="0"
        :precision="0"
        class="w-full!"
      />
      <div class="form-help">数值越小，Gateway 匹配优先级越高</div>
    </el-form-item>
    <el-form-item label="映射状态">
      <el-switch
        v-model="formData.enabled"
        inline-prompt
        active-text="启用"
        inactive-text="停用"
      />
    </el-form-item>
    <el-form-item label="扩展属性" prop="extra">
      <el-input
        v-model="formData.extra"
        type="textarea"
        :rows="3"
        placeholder='可选 JSON，如 {"gatewayOnly":true}'
        class="font-mono"
      />
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.mapping-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.form-help {
  width: 100%;
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
