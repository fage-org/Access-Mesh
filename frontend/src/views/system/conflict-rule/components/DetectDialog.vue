<script setup lang="ts">
import { ref, reactive, computed } from "vue";
import { message } from "@/utils/message";
import {
  detectConflictRule,
  type ConflictDetectResp
} from "@/api/conflict-rule";

defineOptions({ name: "DetectDialog" });

const props = defineProps<{
  /** 操作权限选项（选择器 + 结果名称映射用） */
  operationOptions: Array<{
    id: number;
    name: string;
    code: string;
    resourceTypeCode: string | null;
  }>;
  /** 资源类型选项 */
  resourceTypeOptions: Array<{ typeValue: number; name: string }>;
  /** 角色选项（角色对模式选择器 + 结果名称映射用，T-PERM-063） */
  roleOptions: Array<{ id: number; name: string; externalId: string | null }>;
}>();

/** 检测形态：操作权限对（PERM_MUTEX）/ 角色对（ROLE_MUTEX，存量持有预检） */
const mode = ref<"PERM_MUTEX" | "ROLE_MUTEX">("PERM_MUTEX");

const form = reactive({
  firstOperationPermissionId: null as number | null,
  secondOperationPermissionId: null as number | null,
  resourceTypeValue: null as number | null,
  firstAbstractRoleId: null as number | null,
  secondAbstractRoleId: null as number | null
});
const detecting = ref(false);
const result = ref<ConflictDetectResp | null>(null);

/** 操作权限选项按 resourceTypeCode 分组 */
const operationGroups = computed(() => {
  const groups = new Map<string, Array<(typeof props.operationOptions)[0]>>();
  for (const op of props.operationOptions) {
    const key = op.resourceTypeCode ?? "全局";
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(op);
  }
  return Array.from(groups.entries()).map(([key, ops]) => ({
    label: key,
    options: ops
  }));
});

function opLabel(id: number | null): string {
  if (id == null) return "-";
  const op = props.operationOptions.find(o => o.id === id);
  return op ? `${op.name}（${op.code}）` : `#${id}`;
}

function resourceTypeLabel(value: number | null): string {
  if (value == null) return "全部";
  const t = props.resourceTypeOptions.find(r => r.typeValue === value);
  return t ? t.name : `#${value}`;
}

function roleLabel(id: number | null): string {
  if (id == null) return "-";
  const role = props.roleOptions.find(r => r.id === id);
  return role ? role.name : `#${id}`;
}

async function onDetect() {
  if (mode.value === "ROLE_MUTEX") {
    if (form.firstAbstractRoleId == null || form.secondAbstractRoleId == null) {
      message("请选择两个角色");
      return;
    }
    if (form.firstAbstractRoleId === form.secondAbstractRoleId) {
      message("两个角色不能相同");
      return;
    }
  } else {
    if (
      form.firstOperationPermissionId == null ||
      form.secondOperationPermissionId == null
    ) {
      message("请选择两个操作权限");
      return;
    }
    if (form.firstOperationPermissionId === form.secondOperationPermissionId) {
      message("两个操作权限不能相同");
      return;
    }
  }
  detecting.value = true;
  result.value = null;
  try {
    result.value = await detectConflictRule(
      mode.value === "ROLE_MUTEX"
        ? {
            firstAbstractRoleId: form.firstAbstractRoleId,
            secondAbstractRoleId: form.secondAbstractRoleId
          }
        : {
            firstOperationPermissionId: form.firstOperationPermissionId,
            secondOperationPermissionId: form.secondOperationPermissionId,
            resourceTypeValue: form.resourceTypeValue
          }
    );
  } catch (e: any) {
    message(e.message || "检测失败", { type: "error" });
  } finally {
    detecting.value = false;
  }
}
</script>

<template>
  <div class="detect-dialog">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="检测冲突（操作权限对 / 角色对二选一）"
      description="操作权限对检测权限互斥规则（双向匹配：A-B 与 B-A 视为同一冲突）；角色对检测当前同时持有两角色的用户（立规前预检——非空即创建/更新该互斥规则将被拒绝）。"
      class="detect-alert"
    />

    <el-form label-width="100px" class="detect-form">
      <el-form-item label="检测形态">
        <el-radio-group v-model="mode">
          <el-radio-button value="PERM_MUTEX">操作权限对</el-radio-button>
          <el-radio-button value="ROLE_MUTEX">角色对</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="mode === 'ROLE_MUTEX'">
        <el-form-item label="角色 A" required>
          <el-select
            v-model="form.firstAbstractRoleId"
            placeholder="选择角色"
            filterable
            clearable
            class="w-full!"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色 B" required>
          <el-select
            v-model="form.secondAbstractRoleId"
            placeholder="选择角色"
            filterable
            clearable
            class="w-full!"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="操作权限 A" required>
          <el-select
            v-model="form.firstOperationPermissionId"
            placeholder="选择操作权限"
            filterable
            clearable
            class="w-full!"
          >
            <el-option-group
              v-for="grp in operationGroups"
              :key="grp.label"
              :label="grp.label"
            >
              <el-option
                v-for="op in grp.options"
                :key="op.id"
                :label="`${op.name}（${op.code}）`"
                :value="op.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="操作权限 B" required>
          <el-select
            v-model="form.secondOperationPermissionId"
            placeholder="选择操作权限"
            filterable
            clearable
            class="w-full!"
          >
            <el-option-group
              v-for="grp in operationGroups"
              :key="grp.label"
              :label="grp.label"
            >
              <el-option
                v-for="op in grp.options"
                :key="op.id"
                :label="`${op.name}（${op.code}）`"
                :value="op.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select
            v-model="form.resourceTypeValue"
            placeholder="全部资源类型"
            clearable
            class="w-full!"
          >
            <el-option
              v-for="t in resourceTypeOptions"
              :key="t.typeValue"
              :label="t.name"
              :value="t.typeValue"
            />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item>
        <el-button type="primary" :loading="detecting" @click="onDetect">
          {{ detecting ? "检测中…" : "开始检测" }}
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 检测结果 -->
    <div v-if="result" class="detect-result">
      <el-divider content-position="left">检测结果</el-divider>
      <el-alert
        :type="result.conflictDetected ? 'error' : 'success'"
        :closable="false"
        show-icon
        :title="
          result.conflictDetected
            ? mode === 'ROLE_MUTEX'
              ? `检测到冲突（${result.conflictedUserIds.length} 个用户同时持有两角色）`
              : `检测到冲突（匹配 ${result.matchedRules.length} 条规则）`
            : '未检测到冲突'
        "
        class="detect-result-alert"
      />
      <div v-if="mode === 'ROLE_MUTEX'" class="holder-list">
        <template v-if="result.conflictedUserIds.length">
          <span class="holder-label"
            >存量持有用户（内部 id，需先解绑才能立规）：</span
          >
          <el-tag
            v-for="userId in result.conflictedUserIds"
            :key="userId"
            size="small"
            type="danger"
            effect="plain"
            class="holder-tag"
          >
            #{{ userId }}
          </el-tag>
        </template>
        <span v-else class="holder-empty">
          无用户同时持有两角色——可安全创建/更新该互斥规则
        </span>
      </div>
      <div v-if="result.matchedRules.length" class="matched-list">
        <div
          v-for="rule in result.matchedRules"
          :key="rule.id"
          class="matched-item"
        >
          <div class="matched-pair">
            <span class="matched-op">{{
              mode === "ROLE_MUTEX"
                ? roleLabel(rule.firstAbstractRoleId)
                : opLabel(rule.firstOperationPermissionId)
            }}</span>
            <span class="matched-sep">↔</span>
            <span class="matched-op">{{
              mode === "ROLE_MUTEX"
                ? roleLabel(rule.secondAbstractRoleId)
                : opLabel(rule.secondOperationPermissionId)
            }}</span>
          </div>
          <div class="matched-meta">
            <el-tag size="small" type="info" effect="plain">
              资源类型：{{ resourceTypeLabel(rule.resourceTypeValue) }}
            </el-tag>
            <span v-if="rule.description" class="matched-desc">
              {{ rule.description }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.detect-dialog {
  .detect-alert {
    margin-bottom: var(--space-3);
  }
}

.detect-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

.detect-result {
  margin-top: var(--space-2);
}

.detect-result-alert {
  margin-bottom: var(--space-3);
}

.holder-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-3);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.holder-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.holder-empty {
  font-size: 13px;
  color: var(--el-color-success);
}

.matched-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.matched-item {
  padding: var(--space-2) var(--space-3);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.matched-pair {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-1);
}

.matched-op {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.matched-sep {
  font-size: 13px;
  color: var(--el-color-danger);
}

.matched-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.matched-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
