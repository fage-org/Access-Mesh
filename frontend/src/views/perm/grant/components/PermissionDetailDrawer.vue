<script setup lang="ts">
/**
 * 权限详情（只读）。
 * 同一角色、资源/范围、操作及父权限下最多一条 MANUAL 直接授权；
 * MANUAL 与 AUTO_DEP 可并列展示，授权属性与子权限均在授权弹窗修改。
 */
import { computed, ref, watch } from "vue";
import type { ConditionResp } from "@/api/permission-condition";
import { summarizeRules } from "@/utils/condition-rules";
import type { EffectiveRecord } from "../utils/grant-plan";

const props = defineProps<{
  modelValue: boolean;
  target: {
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    resourceName: string;
    operationCode: string;
    scopeMode: "INSTANCE" | "ALL";
  } | null;
  /** 目标单元格下的直接记录（最多一条 MANUAL，可与 AUTO_DEP 并存）。 */
  records: EffectiveRecord[];
  childrenProvider: (record: EffectiveRecord) => EffectiveRecord[];
  conditions: ConditionResp[];
  /** 组合位未定义位（只读防御性展示）。 */
  undefinedBitsByRecord: Map<number, string>;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>();

const activeTab = ref("records");
const manualRecord = computed(
  () => props.records.find(record => record.grantSource === "MANUAL") ?? null
);
const childList = computed(() =>
  manualRecord.value ? props.childrenProvider(manualRecord.value) : []
);

watch(
  () => props.modelValue,
  visible => {
    if (visible) activeTab.value = "records";
  }
);

/** 条件名（T-PERM-048 双轨）：草稿内联绑定显示定义名；引用绑定查 conditions（含 INLINE 实体） */
function conditionName(record: EffectiveRecord): string {
  if (record.inlineCondition != null) {
    return `内联：${record.inlineCondition.name}`;
  }
  const code = record.conditionCode;
  if (!code) return "无条件";
  const condition = props.conditions.find(item => item.code === code);
  if (condition?.source === "INLINE") return `内联：${condition.name}`;
  return condition ? `${condition.name}（${code}）` : code;
}

function conditionSummary(code: string | null): string {
  if (!code) return "不附加条件，始终生效";
  const condition = props.conditions.find(item => item.code === code);
  return condition ? summarizeRules(condition.conditionRules) : "";
}

function draftTag(record: EffectiveRecord): {
  text: string;
  type: "success" | "warning" | "danger";
} | null {
  if (record.draftMark === "add") return { text: "待授权", type: "success" };
  if (record.draftMark === "update") return { text: "待更新", type: "warning" };
  if (record.draftMark === "remove") return { text: "待撤销", type: "danger" };
  return null;
}

function resourceLabel(record: EffectiveRecord): string {
  return (
    record.resourceName ??
    record.resourceCode ??
    `全部资源（${record.resourceTypeCode}）`
  );
}

function handleClose() {
  emit("update:modelValue", false);
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="
      target
        ? `权限详情：${target.resourceName} · ${target.operationCode}`
        : '权限详情'
    "
    size="480px"
    class="grant-drawer"
    @update:model-value="handleClose"
  >
    <template v-if="target">
      <div class="main-summary">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="资源">
            {{ target.resourceName }}
          </el-descriptions-item>
          <el-descriptions-item label="操作">
            {{ target.operationCode }}
          </el-descriptions-item>
          <el-descriptions-item label="范围">
            {{ target.scopeMode === "ALL" ? "全量（ALL）" : "实例" }}
          </el-descriptions-item>
          <el-descriptions-item label="资源编码">
            {{ target.resourceCode ?? "—" }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-alert
        v-if="records.length === 0"
        type="info"
        :closable="false"
        title="本单元格权限来自继承，无直接授权记录"
        description="请在来源资源行或操作来源列查看对应的直接授权。"
      />

      <el-tabs v-else v-model="activeTab">
        <el-tab-pane label="授权记录" name="records">
          <div class="record-list">
            <article
              v-for="record in records"
              :key="record.id"
              class="record-item"
              :class="{
                'auto-dep': record.grantSource === 'AUTO_DEP',
                removed: record.draftMark === 'remove'
              }"
            >
              <div class="record-head">
                <div class="record-title">
                  <el-tag
                    size="small"
                    :type="
                      record.grantSource === 'AUTO_DEP' ? 'info' : 'primary'
                    "
                    effect="plain"
                  >
                    {{
                      record.grantSource === "AUTO_DEP"
                        ? "自动补全"
                        : "直接授权"
                    }}
                  </el-tag>
                  <span>{{ conditionName(record) }}</span>
                </div>
                <el-tag
                  v-if="draftTag(record)"
                  size="small"
                  :type="draftTag(record)!.type"
                  effect="plain"
                >
                  {{ draftTag(record)!.text }}
                </el-tag>
              </div>
              <div class="condition-summary">
                {{ conditionSummary(record.conditionCode) }}
              </div>
              <div class="record-meta">
                <span>可转授：{{ record.canGrant ? "是" : "否" }}</span>
                <span v-if="record.childCount > 0"
                  >子权限：{{ record.childCount }}</span
                >
                <span v-if="record.createdAt"
                  >创建：{{ record.createdAt }}</span
                >
                <span v-if="undefinedBitsByRecord.get(record.id)">
                  未定义位：{{ undefinedBitsByRecord.get(record.id) }}
                </span>
              </div>
              <div
                v-if="record.grantSource === 'AUTO_DEP'"
                class="readonly-hint"
              >
                由资源依赖自动补全，只读不可修改
              </div>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="子权限" name="children">
          <el-alert
            v-if="!manualRecord"
            type="info"
            :closable="false"
            title="没有可挂载子权限的直接授权"
            description="自动补全记录只读，不能作为子权限父记录。"
          />
          <div v-else class="children-section">
            <div class="parent-context">
              <span class="parent-mark">主</span>
              <div>
                <strong>{{ resourceLabel(manualRecord) }}</strong>
                <span>{{ conditionName(manualRecord) }}</span>
              </div>
            </div>
            <div class="child-list">
              <el-empty
                v-if="childList.length === 0"
                description="暂无子权限"
                :image-size="48"
              />
              <article
                v-for="child in childList"
                :key="child.id"
                class="child-item"
                :class="{ removed: child.draftMark === 'remove' }"
              >
                <div class="child-head">
                  <span>{{ resourceLabel(child) }}</span>
                  <el-tag
                    v-if="draftTag(child)"
                    size="small"
                    :type="draftTag(child)!.type"
                    effect="plain"
                  >
                    {{ draftTag(child)!.text }}
                  </el-tag>
                </div>
                <div class="record-meta">
                  <span>{{ child.resourceTypeCode }}</span>
                  <span>{{ child.operationCode ?? "未知操作" }}</span>
                  <span>{{ child.scopeMode === "ALL" ? "全量" : "实例" }}</span>
                  <span>
                    来源：{{
                      child.grantSource === "AUTO_DEP" ? "自动补全" : "直接授权"
                    }}
                  </span>
                </div>
              </article>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
:global(.grant-drawer .el-drawer__header) {
  padding: var(--space-4) var(--space-5);
  margin-bottom: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:global(.grant-drawer .el-drawer__header .el-drawer__title) {
  font-size: 15px;
  font-weight: 600;
}

:global(.grant-drawer .el-drawer__body) {
  padding: var(--space-4) var(--space-5);
}

.main-summary {
  padding: var(--space-3);
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);

  :deep(.el-descriptions__body) {
    background: transparent;
  }
}

.record-list,
.child-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.record-item,
.child-item {
  padding: var(--space-3);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);

  &.auto-dep {
    background: var(--el-fill-color-lighter);
    border-style: dashed;
  }

  &.removed {
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-7);
  }
}

.record-head,
.record-title,
.child-head,
.parent-context {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.record-head,
.child-head {
  justify-content: space-between;
}

.record-title,
.child-head {
  font-size: 13px;
  font-weight: 500;
}

.condition-summary,
.readonly-hint {
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  padding-top: var(--space-2);
  margin-top: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border-top: 1px dashed var(--el-border-color-lighter);
}

.readonly-hint {
  color: var(--el-color-warning);
}

.children-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.parent-context {
  padding: var(--space-3);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--radius-md);

  > div {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  strong {
    font-size: 13px;
  }

  span:last-child {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.parent-mark {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  border: 2px solid var(--el-color-primary-light-3);
  border-radius: var(--radius-full);
}
</style>
