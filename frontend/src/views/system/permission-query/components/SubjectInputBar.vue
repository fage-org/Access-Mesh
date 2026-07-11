<script setup lang="ts">
import { computed } from "vue";
import {
  TARGET_TYPE_OPTIONS,
  SUBJECT_TYPE_OPTIONS,
  ROLE_TYPE_OPTIONS
} from "../utils/types";
import type { TargetType } from "@/api/permission-query";

defineOptions({ name: "PermissionQuerySubjectInputBar" });

const props = defineProps<{
  targetType: TargetType;
  subjectTypeCode: string;
  subjectExternalId: string;
  roleTypeCode: string;
  roleExternalId: string;
  domainCode: string;
}>();

const emit = defineEmits<{
  "update:targetType": [TargetType];
  "update:subjectTypeCode": [string];
  "update:subjectExternalId": [string];
  "update:roleTypeCode": [string];
  "update:roleExternalId": [string];
  "update:domainCode": [string];
  targetTypeChange: [];
}>();

const isRole = computed(() => props.targetType === "ROLE");

/** 当前角色类型是否要求 domainCode 必填（ORG/POSITION 必填） */
const domainRequired = computed(() => {
  if (!isRole.value) return false;
  const rt = ROLE_TYPE_OPTIONS.find(r => r.value === props.roleTypeCode);
  return rt?.domainRequired ?? false;
});

function onTargetTypeChange(v: string | number | boolean | undefined) {
  emit("update:targetType", v as TargetType);
  // 切换 targetType 清空旧结果（hook.onTargetTypeChange）
  emit("targetTypeChange");
}
</script>

<template>
  <div class="subject-bar">
    <el-form-item label="目标类型" class="mb-0!">
      <el-select
        :model-value="targetType"
        class="w-30!"
        @update:model-value="onTargetTypeChange"
      >
        <el-option
          v-for="opt in TARGET_TYPE_OPTIONS"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </el-form-item>

    <!-- USER 分支 -->
    <template v-if="!isRole">
      <el-form-item label="用户类型" class="mb-0!">
        <el-select
          :model-value="subjectTypeCode"
          class="w-40!"
          @update:model-value="(v: string) => emit('update:subjectTypeCode', v)"
        >
          <el-option
            v-for="opt in SUBJECT_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="用户标识" class="mb-0!">
        <el-input
          :model-value="subjectExternalId"
          placeholder="subjectExternalId"
          class="w-44!"
          @update:model-value="
            (v: string) => emit('update:subjectExternalId', v)
          "
        />
      </el-form-item>
    </template>

    <!-- ROLE 分支 -->
    <template v-else>
      <el-form-item label="角色类型" class="mb-0!">
        <el-select
          :model-value="roleTypeCode"
          class="w-44!"
          @update:model-value="(v: string) => emit('update:roleTypeCode', v)"
        >
          <el-option
            v-for="opt in ROLE_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="角色标识" class="mb-0!">
        <el-input
          :model-value="roleExternalId"
          placeholder="roleExternalId"
          class="w-44!"
          @update:model-value="(v: string) => emit('update:roleExternalId', v)"
        />
      </el-form-item>
    </template>

    <el-form-item label="业务域" class="mb-0!">
      <el-input
        :model-value="domainCode"
        :placeholder="domainRequired ? '必填' : '可空(全局域)'"
        class="w-36!"
        @update:model-value="(v: string) => emit('update:domainCode', v)"
      />
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped>
.subject-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
</style>
