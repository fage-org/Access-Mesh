<script setup lang="ts">
import { computed, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { UserItem, UserFormData } from "./utils/types";
import { ReOrgTreePanel } from "@/components/ReOrgTreePanel";

defineOptions({
  name: "UserForm"
});

const props = defineProps<{
  mode: "create" | "edit";
  row?: UserItem | null;
  formData: UserFormData;
  treeConfigId?: number;
}>();
const emit = defineEmits<{
  "update:formData": [value: UserFormData];
}>();

const formRef = ref<FormInstance>();

function updateFormData(patch: Partial<UserFormData>) {
  emit("update:formData", {
    ...props.formData,
    ...patch
  });
}

const usernameModel = computed({
  get: () => props.formData.username,
  set: username => updateFormData({ username })
});

const nameModel = computed({
  get: () => props.formData.name,
  set: name => updateFormData({ name })
});

const phoneModel = computed({
  get: () => props.formData.phone,
  set: phone => updateFormData({ phone })
});

const emailModel = computed({
  get: () => props.formData.email,
  set: email => updateFormData({ email })
});

function onOrgChange(orgId: number | null) {
  updateFormData({ orgId });
}

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 2, max: 64, message: "长度 2-64 字符", trigger: "blur" }
  ],
  name: [
    { required: true, message: "请输入姓名", trigger: "blur" },
    { max: 64, message: "最长 64 字符", trigger: "blur" }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: "请输入有效手机号", trigger: "blur" }
  ],
  email: [{ type: "email", message: "请输入有效邮箱", trigger: "blur" }]
};

async function validate(): Promise<boolean> {
  if (!formRef.value) return false;
  try {
    await formRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

defineExpose({ validate });
</script>

<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="72px">
    <el-form-item v-if="mode === 'create'" label="用户名" prop="username">
      <el-input
        v-model="usernameModel"
        placeholder="登录账号，租户内唯一"
        clearable
      />
    </el-form-item>
    <el-form-item v-else label="用户名">
      <el-input :model-value="row?.username" disabled />
    </el-form-item>

    <el-form-item label="姓名" prop="name">
      <el-input v-model="nameModel" placeholder="请输入姓名" clearable />
    </el-form-item>

    <el-form-item label="手机号" prop="phone">
      <el-input v-model="phoneModel" placeholder="请输入手机号" clearable />
    </el-form-item>

    <el-form-item label="邮箱" prop="email">
      <el-input v-model="emailModel" placeholder="请输入邮箱" clearable />
    </el-form-item>

    <el-form-item v-if="mode === 'create'" label="所属组织">
      <ReOrgTreePanel
        :show-config="false"
        :tree-config-id="treeConfigId"
        compact
        class="w-full!"
        @org-change="onOrgChange"
      />
    </el-form-item>
  </el-form>
</template>
