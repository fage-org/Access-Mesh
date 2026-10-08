<script setup lang="ts">
import { reactive, ref } from "vue";
import { changePlatformPassword } from "@/api/platform";
import { toErrorMessage } from "@/api/_envelope";
import { usePlatformStoreHook } from "@/store/modules/platform";
import { message } from "@/utils/message";
defineOptions({ name: "PlatformChangePassword" });
const form = reactive({ oldPassword: "", newPassword: "", confirm: "" });
const busy = ref(false);
async function save() {
  if (busy.value) return;
  if (
    !form.oldPassword ||
    !/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.newPassword) ||
    form.newPassword !== form.confirm
  ) {
    message("请填写原密码及一致的新密码（8–32 位，包含字母和数字）", {
      type: "warning"
    });
    return;
  }
  busy.value = true;
  try {
    await changePlatformPassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    });
    form.oldPassword = "";
    form.newPassword = "";
    form.confirm = "";
    message("密码已修改，请重新登录", { type: "success" });
    usePlatformStoreHook().logout();
  } catch (error) {
    message(toErrorMessage(error, "修改失败"), { type: "error" });
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <el-main
    ><el-row justify="center"
      ><el-col :xs="24" :sm="16" :md="10">
        <el-card header="修改平台账号密码">
          <el-alert
            title="首次改密必须使用不同的新密码。修改完成后需要重新登录。"
            type="info"
            :closable="false"
          />
          <el-form label-position="top" @submit.prevent="save">
            <el-form-item label="当前密码"
              ><el-input
                v-model="form.oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
            /></el-form-item>
            <el-form-item label="新密码"
              ><el-input
                v-model="form.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
            /></el-form-item>
            <el-form-item label="确认新密码"
              ><el-input
                v-model="form.confirm"
                type="password"
                show-password
                autocomplete="new-password"
            /></el-form-item>
            <el-button native-type="submit" type="primary" :loading="busy"
              >保存并重新登录</el-button
            >
            <el-button @click="usePlatformStoreHook().logout()"
              >退出平台</el-button
            >
          </el-form>
        </el-card>
      </el-col></el-row
    ></el-main
  >
</template>
