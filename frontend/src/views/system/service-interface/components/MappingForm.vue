<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ApiMappingResp } from "@/api/service-interface";
import { getTypeDefList, TYPE_KEY, type TypeDefResp } from "@/api/type-def";
import {
  getResourceTree,
  type ResourceTreeNode
} from "@/api/resource-operation";
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

// ========== 资源选择器（T-PERM-027 §7.6 / T-PERM-028 联动落地） ==========
// 映射 create/update 仍以内部 resourceId 提交（api-contract §5.4 定案）；
// 选取 UI 从裸数字输入升级为「类型下拉 + 资源树选择」，数据源 resource-entity/tree。

type TreeSelectNode = {
  value: number;
  label: string;
  children?: TreeSelectNode[];
};

const resourceTypes = ref<TypeDefResp[]>([]);
const selectedResourceTypeCode = ref<string | null>(null);
const resourceTreeOptions = ref<TreeSelectNode[]>([]);
const resourceTreeLoading = ref(false);

function toTreeSelectNodes(nodes: ResourceTreeNode[]): TreeSelectNode[] {
  return nodes.map(node => ({
    value: node.id,
    label: node.code === node.name ? node.name : `${node.name}（${node.code}）`,
    children: node.children?.length
      ? toTreeSelectNodes(node.children)
      : undefined
  }));
}

async function loadResourceTypes() {
  try {
    const res = await getTypeDefList({ typeKey: TYPE_KEY.RESOURCE_TYPE });
    resourceTypes.value = res.items
      .filter(t => t.typeKey === TYPE_KEY.RESOURCE_TYPE)
      .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  } catch {
    resourceTypes.value = [];
  }
}

async function loadResourceTree(typeCode: string) {
  resourceTreeLoading.value = true;
  try {
    const res = await getResourceTree({ resourceTypeCode: typeCode });
    resourceTreeOptions.value = toTreeSelectNodes(
      res.items.map(i => i.root).filter((n): n is ResourceTreeNode => n != null)
    );
  } catch {
    resourceTreeOptions.value = [];
  } finally {
    resourceTreeLoading.value = false;
  }
}

function onResourceTypeChange(typeCode: string) {
  // 切换类型后原选中资源不再属于该类型，清空重选
  formData.resourceEntityId = null;
  loadResourceTree(typeCode);
}

/** 编辑态资源展示（树节点不含 extra 之外的展示问题——直接用映射行的资源业务字段） */
const editingResourceDisplay = computed(() => {
  const row = props.initialData;
  if (!row) return "";
  if (row.resourceCode) {
    return row.resourceName
      ? `${row.resourceName}（${row.resourceCode}）`
      : row.resourceCode;
  }
  return `#${row.resourceEntityId}`;
});

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
  if (props.mode === "create") {
    loadResourceTypes();
  }
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
    <template v-if="!isEdit">
      <el-form-item label="资源类型">
        <el-select
          v-model="selectedResourceTypeCode"
          placeholder="选择资源类型"
          class="w-full!"
          @change="onResourceTypeChange"
        >
          <el-option
            v-for="t in resourceTypes"
            :key="t.typeCode"
            :label="t.name"
            :value="t.typeCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="关联资源" prop="resourceEntityId">
        <!-- 映射接口仍以 resourceId 内部主键提交（api-contract §5.4 定案）；
             选取 UI 为资源树选择（T-PERM-027 §7.6 / T-PERM-028 联动）。 -->
        <el-tree-select
          v-model="formData.resourceEntityId"
          :data="resourceTreeOptions"
          :loading="resourceTreeLoading"
          check-strictly
          :render-after-expand="false"
          default-expand-all
          clearable
          placeholder="先选资源类型，再选资源"
          class="w-full!"
          :disabled="!selectedResourceTypeCode"
        />
        <div class="form-help">与资源实体建立接口级鉴权关联</div>
      </el-form-item>
    </template>
    <el-form-item v-else label="关联资源">
      <el-input
        :model-value="editingResourceDisplay"
        readonly
        class="w-full!"
      />
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
