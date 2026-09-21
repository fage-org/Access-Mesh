<script setup lang="ts">
/**
 * 角色自动授权来源解释查看器（T-PERM-073，契约 §12.3.1）。
 *
 * 输入角色业务键（类型 + externalId）+ 可选目标事实（资源类型/资源/操作，条件恒查无条件
 * 变体——带条件变体经全集视图定位）；explain 返回共享逻辑 DAG（节点=事实键、边=直接推导
 * 关系+声明引用）。选中节点沿直接边本地展开（未截断时无需再请求）；截断/漂移显式提示，
 * 不把「应生成」当「已生效」，读取失败不解释为「无来源」。
 */
import { computed, reactive, ref } from "vue";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import {
  explainAutoGrant,
  type AutoGrantExplainResp
} from "@/api/resource-dependency";
import {
  explainFactLabel,
  explainSeedLabel,
  nodeStatus,
  edgesTouchedBy,
  nodeIndex,
  declarationLabel
} from "../utils/explain-view";

interface ResourceOption {
  id: number;
  code: string;
  name: string | null;
  resourceTypeCode: string;
}

interface OperationOption {
  code: string;
  name: string;
  resourceTypeCode: string | null;
}

const props = defineProps<{
  modelValue: boolean;
  resourceTypeOptions: Array<{ value: string; label: string }>;
  resourceList: ResourceOption[];
  operationList: OperationOption[];
}>();

const emit = defineEmits<{ (e: "update:modelValue", value: boolean): void }>();

// ========== 查询表单（角色业务键 + 可选目标；domainCode 恒 null 对齐授权页） ==========
const ROLE_TYPE_OPTIONS = [
  { value: "BASIC_ROLE", label: "功能角色（BASIC_ROLE）" },
  { value: "ORG", label: "组织（ORG）" },
  { value: "POSITION", label: "岗位（POSITION）" }
];

const form = reactive({
  roleTypeCode: "BASIC_ROLE",
  roleExternalId: "",
  useTarget: false,
  targetResourceTypeCode: "",
  targetResourceCode: "",
  targetOperationCode: ""
});

const loading = ref(false);
const loadError = ref<string | null>(null);
const resp = ref<AutoGrantExplainResp | null>(null);
const selectedNodeKey = ref<string | null>(null);

const targetResourceCandidates = computed(() => {
  const type = form.targetResourceTypeCode;
  return type
    ? props.resourceList.filter(r => r.resourceTypeCode === type)
    : [];
});
const targetOperationCandidates = computed(() => {
  const type = form.targetResourceTypeCode;
  return props.operationList.filter(op => op.resourceTypeCode === type);
});

const nodes = computed(() => resp.value?.nodes ?? []);
const edges = computed(() => resp.value?.edges ?? []);
const nodeByKey = computed(() =>
  resp.value ? nodeIndex(resp.value) : new Map()
);
const selectedEdges = computed(() =>
  selectedNodeKey.value
    ? edgesTouchedBy(edges.value, selectedNodeKey.value)
    : edges.value
);

async function runExplain() {
  if (!form.roleExternalId.trim()) {
    message("请输入角色 externalId", { type: "warning" });
    return;
  }
  if (form.useTarget) {
    if (
      !form.targetResourceTypeCode ||
      !form.targetResourceCode ||
      !form.targetOperationCode
    ) {
      message("目标事实需完整选择资源类型 / 资源 / 操作", { type: "warning" });
      return;
    }
  }
  loading.value = true;
  loadError.value = null;
  resp.value = null;
  selectedNodeKey.value = null;
  try {
    resp.value = await explainAutoGrant({
      roleTypeCode: form.roleTypeCode,
      roleExternalId: form.roleExternalId.trim(),
      target: form.useTarget
        ? {
            resourceTypeCode: form.targetResourceTypeCode,
            resourceCode: form.targetResourceCode,
            codeType: "default",
            operationCode: form.targetOperationCode,
            conditionId: null
          }
        : null
    });
  } catch (e) {
    loadError.value = toErrorMessage(e, "来源解释读取失败");
    message(loadError.value, { type: "error" });
  } finally {
    loading.value = false;
  }
}

function edgeLabel(nodeKey: string): string {
  const node = nodeByKey.value.get(nodeKey);
  return node ? explainFactLabel(node.fact) : nodeKey;
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="自动授权来源解释"
    size="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="explain-viewer">
      <el-alert
        type="info"
        :closable="false"
        title="解释「为什么生成该角色的自动权限」"
        description="节点为逻辑事实（显式种子 / 推导事实），边为直接推导关系；源事实权限不代表用户当前必然被允许。"
      />

      <!-- 查询表单 -->
      <div class="query-form">
        <el-select
          v-model="form.roleTypeCode"
          class="w-52!"
          placeholder="角色类型"
        >
          <el-option
            v-for="option in ROLE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-model="form.roleExternalId"
          class="w-56!"
          placeholder="角色 externalId（如 bootstrap-admin）"
        />
        <el-checkbox v-model="form.useTarget">按目标事实收窄</el-checkbox>
        <el-button type="primary" :loading="loading" @click="runExplain">
          查询
        </el-button>
      </div>

      <div v-if="form.useTarget" class="target-form">
        <el-select
          v-model="form.targetResourceTypeCode"
          class="w-44!"
          placeholder="资源类型"
          clearable
        >
          <el-option
            v-for="option in props.resourceTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-select
          v-model="form.targetResourceCode"
          class="w-56!"
          placeholder="目标资源"
          clearable
          filterable
        >
          <el-option
            v-for="resource in targetResourceCandidates"
            :key="resource.id"
            :label="`${resource.name ?? resource.code} (${resource.code})`"
            :value="resource.code"
          />
        </el-select>
        <el-select
          v-model="form.targetOperationCode"
          class="w-40!"
          placeholder="操作"
          clearable
        >
          <el-option
            v-for="operation in targetOperationCandidates"
            :key="operation.code"
            :label="operation.name"
            :value="operation.code"
          />
        </el-select>
      </div>

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
      />

      <template v-if="resp">
        <el-alert
          v-if="resp.driftDetected"
          class="mt-2"
          type="warning"
          :closable="false"
          title="该角色存在漂移"
          description="desired 与实际 AUTO_DEP 不一致：应有未落库（黄）或无来源存量（红）。「应生成」不等于「已生效」。"
        />
        <el-alert
          v-if="resp.truncated"
          class="mt-2"
          type="info"
          :closable="false"
          :title="`结果已截断（子图共 ${resp.totalNodeCount} 节点 / ${resp.totalEdgeCount} 边，仅展示部分）——未展示不代表无来源`"
        />

        <div class="result-head">
          <span class="result-title">
            节点 {{ resp.nodes.length }}/{{ resp.totalNodeCount }}
          </span>
          <span class="result-hint">
            点击节点行按直接边展开来源{{
              selectedNodeKey ? "（再次点击取消）" : ""
            }}
          </span>
        </div>
        <el-table
          :data="nodes"
          size="small"
          border
          highlight-current-row
          @row-click="
            (row: any) =>
              (selectedNodeKey =
                selectedNodeKey === row.nodeKey ? null : row.nodeKey)
          "
        >
          <el-table-column prop="nodeKey" label="ID" width="60" />
          <el-table-column label="事实" min-width="260">
            <template #default="{ row }">
              <span class="mono">{{ explainFactLabel(row.fact) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="nodeStatus(row).type">
                {{ nodeStatus(row).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="显式来源" min-width="150">
            <template #default="{ row }">
              <span v-if="row.seedRefs.length">
                {{ row.seedRefs.map(explainSeedLabel).join("、") }}
              </span>
              <span v-else class="text-gray-400">—</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty
              :image-size="60"
              description="该角色在当前视图下无自动授权事实"
            />
          </template>
        </el-table>

        <div class="result-head">
          <span class="result-title">
            直接推导边 {{ selectedEdges.length }}/{{ resp.totalEdgeCount }}
          </span>
        </div>
        <el-table :data="selectedEdges" size="small" border>
          <el-table-column label="来源 -> 目标" min-width="300">
            <template #default="{ row }">
              <span class="mono">
                {{ edgeLabel(row.fromNodeKey) }} -&gt;
                {{ edgeLabel(row.toNodeKey) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="触发操作" width="100">
            <template #default="{ row }">
              <span class="mono">{{ row.triggerOperationCode ?? "-" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="声明引用" min-width="180">
            <template #default="{ row }">
              <span>{{ declarationLabel(row.declarationRefs) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :image-size="60" description="无直接推导边" />
          </template>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.explain-viewer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2, 8px);
}

.query-form,
.target-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2, 8px);
  align-items: center;
}

.result-head {
  display: flex;
  gap: var(--space-2, 8px);
  align-items: center;
  margin-top: var(--space-2, 8px);

  .result-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .result-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
