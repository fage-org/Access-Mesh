<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { ApiMappingResp } from "@/api/service-interface";
import { getTypeDefList, TYPE_KEY, type TypeDefResp } from "@/api/type-def";
import {
  getResourceTree,
  getOperationList,
  type OperationPermissionResp,
  type ResourceTreeNode
} from "@/api/resource-operation";
import { useListLoad } from "@/utils/list-load";
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
// 登记实体固定选择 API 树，业务准入操作独立选择。

type TreeSelectNode = {
  value: number;
  label: string;
  children?: TreeSelectNode[];
};

const resourceTypes = ref<TypeDefResp[]>([]);
const {
  list: operationOptions,
  loading: operationLoading,
  load: loadOperations,
  clear: clearOperations
} = useListLoad<OperationPermissionResp>({
  errorText: "加载操作列表失败",
  contextKey: () => formData.requiredResourceTypeCode,
  fetcher: async () => {
    const code = formData.requiredResourceTypeCode;
    if (!code) return [];
    const result = await getOperationList({ resourceTypeCode: code });
    return result.items.filter(op => op.resourceTypeCode !== "API");
  }
});

function onRequiredTypeChange() {
  formData.requiredOperationCode = "";
  clearOperations();
  if (formData.requiredResourceTypeCode) void loadOperations();
}

// 登记资源固定加载 API 树；异步响应由 useListLoad 管理。
let treeTargetTypeCode = "";
const {
  list: resourceTreeOptions,
  loading: resourceTreeLoading,
  load: loadResourceTreeCore
} = useListLoad<TreeSelectNode>({
  errorText: "加载资源树失败",
  fetcher: async () => {
    const res = await getResourceTree({ resourceTypeCode: treeTargetTypeCode });
    return toTreeSelectNodes(
      res.items.map(i => i.root).filter((n): n is ResourceTreeNode => n != null)
    );
  }
});

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
      // API 类型仅用于接口登记，不作准入要求（作要求恒无候选=T-ACCESS-062 后死配置）
      .filter(t => t.typeKey === TYPE_KEY.RESOURCE_TYPE && t.typeCode !== "API")
      .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  } catch {
    resourceTypes.value = [];
  }
}

function loadResourceTree(typeCode: string) {
  treeTargetTypeCode = typeCode;
  return loadResourceTreeCore();
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
  requiredResourceTypeCode: [
    {
      required: true,
      message: "请选择业务资源类型",
      trigger: "change"
    }
  ],
  requiredOperationCode: [
    {
      required: !!formData.requiredResourceTypeCode,
      message: "请选择业务操作",
      trigger: "change"
    }
  ],
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
  void loadResourceTypes();
  if (props.mode === "create") void loadResourceTree("API");
  clearOperations();
  if (formData.requiredResourceTypeCode) void loadOperations();
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
      <el-form-item label="登记类型">
        <el-input model-value="API" readonly />
      </el-form-item>
      <el-form-item label="关联资源" prop="resourceEntityId">
        <!-- 映射接口仍以 resourceId 内部主键提交（api-contract §5.4 定案）；
             登记实体为 API，业务类型与操作独立配置。 -->
        <el-tree-select
          v-model="formData.resourceEntityId"
          :data="resourceTreeOptions"
          :loading="resourceTreeLoading"
          check-strictly
          :render-after-expand="false"
          default-expand-all
          clearable
          placeholder="选择已登记 API"
          class="w-full!"
        />
        <div class="form-help">
          API 实体用于接口登记，业务准入要求在下方单独选择
        </div>
      </el-form-item>
    </template>
    <el-form-item v-else label="关联资源">
      <el-input
        :model-value="editingResourceDisplay"
        readonly
        class="w-full!"
      />
    </el-form-item>
    <el-form-item label="业务资源类型" prop="requiredResourceTypeCode">
      <el-select
        v-model="formData.requiredResourceTypeCode"
        class="w-full!"
        :clearable="false"
        placeholder="选择业务资源类型"
        @change="onRequiredTypeChange"
      >
        <el-option
          v-for="type in resourceTypes"
          :key="type.typeCode"
          :label="type.name"
          :value="type.typeCode"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="准入操作" prop="requiredOperationCode">
      <el-select
        v-model="formData.requiredOperationCode"
        class="w-full!"
        :disabled="!formData.requiredResourceTypeCode"
        :loading="operationLoading"
        placeholder="选择业务操作"
      >
        <el-option
          v-for="op in operationOptions"
          :key="op.code"
          :value="op.code"
          :label="`${op.name}（${op.code}）`"
        />
      </el-select>
      <div class="form-help">准入通过后，业务接口仍须检查实际资源权限</div>
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
        placeholder="如 /api/example/demo/hello（外部路径=服务路径）"
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
      <div class="form-help">顺序用于展示；操作准入会检查全部匹配路由</div>
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
