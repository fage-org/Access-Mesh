<script setup lang="ts">
import { ref, reactive, computed } from "vue";
import { message } from "@/utils/message";
import {
  type DependencyCycleCheckResp,
  checkDependencyCycle
} from "@/api/resource-dependency";
import { type ResourceTreeNode } from "@/api/resource-operation";

defineOptions({ name: "CycleCheckDialog" });

const props = defineProps<{
  /** 资源类型选项 */
  resourceTypeOptions: Array<{ value: string; label: string }>;
  /** 全量资源列表（扁平） */
  resourceList: ResourceTreeNode[];
}>();

const form = reactive({
  sourceResourceTypeCode: null as string | null,
  sourceResourceEntityId: null as number | null,
  targetResourceTypeCode: null as string | null,
  targetResourceEntityId: null as number | null
});

const checking = ref(false);
const result = ref<DependencyCycleCheckResp | null>(null);

const resourceMap = computed(() => {
  const map = new Map<number, ResourceTreeNode>();
  for (const r of props.resourceList) map.set(r.id, r);
  return map;
});

const sourceResourceOptions = computed(() =>
  props.resourceList
    .filter(r => r.resourceTypeCode === form.sourceResourceTypeCode)
    .map(r => ({ id: r.id, name: r.name, code: r.code }))
);

const targetResourceOptions = computed(() =>
  props.resourceList
    .filter(r => r.resourceTypeCode === form.targetResourceTypeCode)
    .map(r => ({ id: r.id, name: r.name, code: r.code }))
);

function onSourceTypeChange() {
  form.sourceResourceEntityId = null;
}

function onTargetTypeChange() {
  form.targetResourceEntityId = null;
}

function resolveLabel(id: number | null): string {
  if (id == null) return "-";
  const r = resourceMap.value.get(id);
  return r ? `${r.name}（${r.code}）` : `#${id}`;
}

async function onCheck() {
  if (
    form.sourceResourceEntityId == null ||
    form.targetResourceEntityId == null
  ) {
    message("请选择源资源与目标资源");
    return;
  }
  const source = resourceMap.value.get(form.sourceResourceEntityId);
  const target = resourceMap.value.get(form.targetResourceEntityId);
  if (!source || !target) {
    message("资源选择无效");
    return;
  }
  checking.value = true;
  result.value = null;
  try {
    result.value = await checkDependencyCycle({
      sourceResourceTypeCode: source.resourceTypeCode,
      sourceResourceCode: source.code,
      sourceCodeType: source.codeType,
      targetResourceTypeCode: target.resourceTypeCode,
      targetResourceCode: target.code,
      targetCodeType: target.codeType
    });
  } catch (e: any) {
    message(e.message || "循环检测失败", { type: "error" });
  } finally {
    checking.value = false;
  }
}

function reset() {
  form.sourceResourceTypeCode = null;
  form.sourceResourceEntityId = null;
  form.targetResourceTypeCode = null;
  form.targetResourceEntityId = null;
  result.value = null;
}
</script>

<template>
  <div class="cycle-check-dialog">
    <el-alert type="info" :closable="false" show-icon class="mb-4">
      检测添加「源资源 ->
      目标资源」依赖是否会形成循环引用。循环依赖会导致权限判定死循环。
    </el-alert>

    <el-form label-width="100px">
      <el-form-item label="源资源类型" required>
        <el-select
          v-model="form.sourceResourceTypeCode"
          placeholder="选择资源类型"
          clearable
          class="w-full!"
          @change="onSourceTypeChange"
        >
          <el-option
            v-for="t in resourceTypeOptions"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="源资源" required>
        <el-select
          v-model="form.sourceResourceEntityId"
          placeholder="选择源资源"
          filterable
          clearable
          :disabled="!form.sourceResourceTypeCode"
          class="w-full!"
        >
          <el-option
            v-for="r in sourceResourceOptions"
            :key="r.id"
            :label="`${r.name}（${r.code}）`"
            :value="r.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="目标资源类型" required>
        <el-select
          v-model="form.targetResourceTypeCode"
          placeholder="选择资源类型"
          clearable
          class="w-full!"
          @change="onTargetTypeChange"
        >
          <el-option
            v-for="t in resourceTypeOptions"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标资源" required>
        <el-select
          v-model="form.targetResourceEntityId"
          placeholder="选择目标资源"
          filterable
          clearable
          :disabled="!form.targetResourceTypeCode"
          class="w-full!"
        >
          <el-option
            v-for="r in targetResourceOptions"
            :key="r.id"
            :label="`${r.name}（${r.code}）`"
            :value="r.id"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="dialog-footer">
      <el-button @click="reset">重置</el-button>
      <el-button type="primary" :loading="checking" @click="onCheck">
        检测循环
      </el-button>
    </div>

    <el-alert
      v-if="result"
      :type="result.hasCycle ? 'error' : 'success'"
      :closable="false"
      show-icon
      class="mt-4"
    >
      <template #title>
        <span v-if="result.hasCycle">
          ⚠️ 检测到循环依赖：添加「{{
            resolveLabel(form.sourceResourceEntityId)
          }}
          -> {{ resolveLabel(form.targetResourceEntityId) }}」会形成环
        </span>
        <span v-else>
          ✅ 未检测到循环依赖：可以安全添加「{{
            resolveLabel(form.sourceResourceEntityId)
          }}
          -> {{ resolveLabel(form.targetResourceEntityId) }}」
        </span>
      </template>
    </el-alert>
  </div>
</template>

<style lang="scss" scoped>
.cycle-check-dialog {
  .dialog-footer {
    display: flex;
    gap: var(--space-2);
    justify-content: flex-end;
    margin-top: var(--space-3);
  }
}
</style>
