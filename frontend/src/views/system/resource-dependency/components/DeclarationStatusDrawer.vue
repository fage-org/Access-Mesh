<script setup lang="ts">
/**
 * 依赖声明诊断抽屉（T-PERM-073）：每服务 manifest 发布状态 + 声明行（含 REJECTED 原因）。
 * 只读——变更由所属服务 manifest 发布承担；加载失败显式提示（不伪装空结果）。
 */
import { ref } from "vue";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import {
  getDeclarationStatus,
  type DependencyDeclarationStatusResp
} from "@/api/resource-dependency";

defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (e: "update:modelValue", value: boolean): void }>();

const loading = ref(false);
const data = ref<DependencyDeclarationStatusResp | null>(null);
const loadError = ref<string | null>(null);

async function load() {
  loading.value = true;
  loadError.value = null;
  try {
    data.value = await getDeclarationStatus();
  } catch (e) {
    loadError.value = toErrorMessage(e, "加载声明诊断失败");
    message(loadError.value, { type: "error" });
  } finally {
    loading.value = false;
  }
}

function onOpen() {
  if (data.value == null && !loading.value) {
    load();
  }
}

function compileTagType(status: string | null): "success" | "danger" | "info" {
  if (status === "RESOLVED") return "success";
  if (status === "REJECTED") return "danger";
  return "info";
}

const REJECT_LABELS: Record<string, string> = {
  RESOURCE_MISSING: "目标/源资源不存在",
  TYPE_MISSING: "资源类型不存在",
  OPERATION_INVALID: "操作码无效",
  CROSS_OWNER: "跨 owner 声明",
  SELF_DEPENDENCY: "自依赖",
  CYCLE: "成环"
};
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="依赖声明与发布状态"
    size="760px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <div v-loading="loading" class="declaration-status">
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
      />

      <template v-if="data">
        <div class="section-head">
          <span class="section-title">服务发布状态</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
        <el-table :data="data.manifestSyncs" size="small" border>
          <el-table-column
            prop="sourceService"
            label="来源服务"
            min-width="140"
          />
          <el-table-column
            prop="publicationGeneration"
            label="发布代次"
            width="90"
          />
          <el-table-column prop="revision" label="revision" min-width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.syncStatus === 'SUCCESS' ? 'success' : 'warning'"
              >
                {{ row.syncStatus ?? "-" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="待重判" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isDirty" size="small" type="warning">
                dirty
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastSyncedAt" label="最近同步" width="170" />
          <template #empty>
            <el-empty :image-size="60" description="暂无服务发布过依赖声明" />
          </template>
        </el-table>

        <div class="section-head">
          <span class="section-title">声明行</span>
          <span class="section-hint">
            REJECTED 行保留用于诊断，由所属服务重新发布恢复
          </span>
        </div>
        <el-table :data="data.declarations" size="small" border>
          <el-table-column prop="sourceService" label="来源服务" width="130" />
          <el-table-column prop="declarationKey" label="声明键" width="110" />
          <el-table-column label="源 -> 目标" min-width="200">
            <template #default="{ row }">
              <span class="mono">
                {{ row.sourceResourceCode ?? "?" }}:{{
                  row.sourceOperationCode ?? "任意"
                }}
                -&gt;
                {{ row.targetResourceCode ?? "?" }}:{{
                  row.requiredOperationCodes.join("/")
                }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="编译状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="compileTagType(row.compileStatus)">
                {{ row.compileStatus ?? "-" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="拒绝原因" min-width="150">
            <template #default="{ row }">
              <span v-if="row.rejectReason">
                {{ row.rejectReason }}（{{
                  REJECT_LABELS[row.rejectReason] ?? "未知"
                }}）
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty :image-size="60" description="暂无声明" />
          </template>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.declaration-status {
  display: flex;
  flex-direction: column;
  gap: var(--space-2, 8px);
}

.section-head {
  display: flex;
  gap: var(--space-2, 8px);
  align-items: center;
  margin: var(--space-2, 8px) 0;

  .section-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .section-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
