<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { storageLocal } from "@pureadmin/utils";
import type { FormInstance } from "element-plus";
import { message } from "@/utils/message";
import { resetUserPassword } from "@/api/user-manage";
import { type DataInfo, userKey } from "@/utils/auth";
import { useUserStoreHook } from "@/store/modules/user";
import { buildChangePasswordRules } from "./utils/rules";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";

import Lock from "~icons/ri/lock-fill";

defineOptions({
  name: "ChangePassword"
});

/** 首次改密成功后旧会话由后端撤销，回到登录页。 */
const route = useRoute();
const loading = ref(false);
const ruleFormRef = ref<FormInstance>();

const ruleForm = reactive({
  newPassword: "",
  confirmPassword: ""
});
const rules = buildChangePasswordRules(() => ruleForm.newPassword);

// 自身 userId：登录时由 loginByUsername 从 LoginResp 写入 userKey（T-FE-046）。
// 改密前旧会话无 userId（升级前登录的存量会话）时无法构造请求——自助通道以
// userId 定位自身，缺失即提示重新登录，不做静默兜底
const userId = storageLocal().getItem<DataInfo<number>>(userKey)?.userId;

const onSubmit = async (formEl: FormInstance | undefined) => {
  if (!formEl || !userId) return;
  await formEl.validate(async valid => {
    if (!valid) return;
    loading.value = true;
    try {
      await resetUserPassword({ userId, newPassword: ruleForm.newPassword });
      message("密码修改成功，请重新登录", { type: "success" });
      await useUserStoreHook().logOut(route.query.returnTo);
    } catch (error) {
      if ((error as Error)?.name === "SessionExpiredError") return;
      message((error as Error)?.message || "密码修改失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  });
};
</script>

<template>
  <div class="change-password-container">
    <div class="change-password-card">
      <h2 class="change-password-title">设置新密码</h2>
      <el-alert
        title="当前密码为初始密码，为保障账号安全，请设置新密码后继续使用系统"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="!userId"
        class="mt-2"
        title="会话信息缺失，无法定位当前用户，请退出后重新登录再修改密码"
        type="error"
        :closable="false"
        show-icon
      />
      <el-form
        ref="ruleFormRef"
        :model="ruleForm"
        :rules="rules"
        size="large"
        :disabled="!userId"
      >
        <el-form-item prop="newPassword">
          <el-input
            v-model="ruleForm.newPassword"
            clearable
            show-password
            placeholder="新密码（8-32 位，包含字母和数字）"
            :prefix-icon="useRenderIcon(Lock)"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="ruleForm.confirmPassword"
            clearable
            show-password
            placeholder="确认新密码"
            :prefix-icon="useRenderIcon(Lock)"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            class="w-full"
            type="primary"
            :loading="loading"
            :disabled="!userId"
            @click="onSubmit(ruleFormRef)"
          >
            提交新密码
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.change-password-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
  background: var(--el-fill-color-light);
}

.change-password-card {
  width: min(420px, calc(100vw - var(--space-6)));
  padding: var(--space-6);
  background: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
}

.change-password-title {
  margin: 0 0 var(--space-4);
  font-size: var(--el-font-size-large);
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-align: center;
}
</style>
