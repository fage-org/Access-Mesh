<script setup lang="ts">
/**
 * 授撤影响预览抽屉（T-PERM-073，M5 定案：预览仅供参考）。
 *
 * 展示三组计划影响（removed/added/retained，含根显式来源）+ 独立提示：
 * - 参考横幅（保存按最新事实重算，影响变化不要求重新确认）；
 * - truncated（totalCount>输出数——空数组不能独立解释为无影响）；
 * - driftDetected（现有漂移，非计划导致，保存不修复也不受其阻碍）；
 * - M5 边界说明（独立目标权限 / 类型级与父继承不触发 / 资源停用不暂停）。
 * 加载中 / 失败态显式呈现（失败=无法预览，不展示为零影响）。
 */
import { computed } from "vue";
import type { GrantPlanPreviewResp } from "@/api/permission-grant";
import {
  previewFactLabel,
  seedLabel,
  hasNoImpact,
  previewFailureText,
  PREVIEW_BOUNDARY_NOTES
} from "../utils/preview-impact";

const props = defineProps<{
  modelValue: boolean;
  loading: boolean;
  resp: GrantPlanPreviewResp | null;
  error: string | null;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value)
});

const noImpact = computed(() => hasNoImpact(props.resp));
const failureText = computed(() =>
  props.error ? previewFailureText({ message: props.error }) : null
);

interface GroupView {
  key: "removed" | "added" | "retained";
  title: string;
  tagType: "danger" | "success" | "info";
  hint: string;
  items: GrantPlanPreviewResp["removed"];
}

const groups = computed<GroupView[]>(() => {
  const resp = props.resp;
  if (!resp) return [];
  return [
    {
      key: "removed",
      title: "将被回收",
      tagType: "danger",
      hint: "无其他显式来源支持，随本计划回收",
      items: resp.removed
    },
    {
      key: "added",
      title: "将新增",
      tagType: "success",
      hint: "本计划授予触发的自动权限",
      items: resp.added
    },
    {
      key: "retained",
      title: "仍被其他来源保留",
      tagType: "info",
      hint: "受本计划影响但仍有其他显式来源支持",
      items: resp.retained
    }
  ];
});
</script>

<template>
  <el-drawer
    v-model="visible"
    title="授撤影响预览（仅供参考）"
    size="560px"
    append-to-body
  >
    <div v-loading="props.loading" class="preview-drawer">
      <!-- 失败态：无法预览，不展示为零影响 -->
      <el-alert
        v-if="failureText && !props.loading"
        :title="failureText"
        type="error"
        :closable="false"
      />

      <template v-if="props.resp && !props.loading">
        <el-alert
          type="warning"
          :closable="false"
          title="预览仅供参考"
          description="结果可能因并发授权或依赖声明变更而不同；保存时服务端按最新事实重新校验与重算，不影响保存。"
        />

        <el-alert
          v-if="props.resp.truncated"
          class="mt-2"
          type="info"
          :closable="false"
          :title="`结果超出展示预算（共 ${props.resp.totalCount} 条），以下仅展示部分影响——空分组不代表无影响`"
        />

        <el-alert
          v-if="props.resp.driftDetected"
          class="mt-2"
          type="warning"
          :closable="false"
          title="检测到既有漂移"
          description="该角色现有自动授权与按声明推导的应有结果不一致（非本计划导致）；保存按最新事实重算后收敛。"
        />

        <el-empty
          v-if="noImpact"
          description="本计划不改变任何自动权限"
          :image-size="80"
          class="mt-4"
        />

        <div
          v-for="group in groups"
          v-show="group.items.length > 0"
          :key="group.key"
          class="impact-group"
        >
          <div class="group-head">
            <el-tag :type="group.tagType" effect="dark" size="small">
              {{ group.title }}
            </el-tag>
            <span class="group-count">{{ group.items.length }}</span>
            <span class="group-hint">{{ group.hint }}</span>
          </div>
          <div
            v-for="(element, index) in group.items"
            :key="`${group.key}-${index}`"
            class="impact-item"
          >
            <div class="item-fact">{{ previewFactLabel(element) }}</div>
            <div class="item-seeds">
              来源：
              <span
                v-for="(seed, seedIndex) in element.seeds"
                :key="seedIndex"
                class="seed-chip"
              >
                {{ seedLabel(seed) }}
              </span>
              <span v-if="element.seeds.length === 0" class="seed-empty">
                （来源未知——可与既有漂移相关）
              </span>
            </div>
          </div>
        </div>

        <el-divider />
        <div class="boundary-notes">
          <div class="notes-title">边界说明</div>
          <ul>
            <li v-for="(note, index) in PREVIEW_BOUNDARY_NOTES" :key="index">
              {{ note }}
            </li>
          </ul>
        </div>
        <div class="viewed-at">
          读取时间：{{ props.resp.viewedAt }}（不构成保存时点承诺）
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.preview-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2, 8px);
}

.impact-group {
  margin-top: var(--space-3, 12px);
}

.group-head {
  display: flex;
  gap: var(--space-2, 8px);
  align-items: center;
  margin-bottom: var(--space-1, 4px);

  .group-count {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .group-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.impact-item {
  padding: var(--space-2, 8px);
  margin-bottom: var(--space-1, 4px);
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base, 4px);

  .item-fact {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .item-seeds {
    margin-top: 2px;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    .seed-chip {
      margin-right: var(--space-1, 4px);
    }

    .seed-empty {
      font-style: italic;
    }
  }
}

.boundary-notes {
  font-size: 12px;
  color: var(--el-text-color-secondary);

  .notes-title {
    margin-bottom: var(--space-1, 4px);
    font-weight: 600;
  }

  ul {
    padding-left: 16px;
    margin: 0;

    li {
      margin-bottom: 2px;
      line-height: 1.5;
    }
  }
}

.viewed-at {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
