<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { platformCaptcha } from "@/api/platform";
import { toErrorMessage } from "@/api/_envelope";
import { usePlatformStoreHook } from "@/store/modules/platform";
import { message } from "@/utils/message";
defineOptions({ name: "PlatformLogin" });
const router = useRouter();
const form = reactive({
  username: "",
  password: "",
  captchaId: "",
  captchaCode: ""
});
const image = ref("");
const busy = ref(false);
async function refresh() {
  form.captchaId = "";
  form.captchaCode = "";
  try {
    const captcha = await platformCaptcha();
    form.captchaId = captcha.captchaId;
    image.value = captcha.image;
  } catch (error) {
    message(toErrorMessage(error, "验证码获取失败"), { type: "error" });
  }
}
async function login() {
  if (
    busy.value ||
    !form.username ||
    !form.password ||
    !form.captchaCode ||
    !form.captchaId
  )
    return;
  busy.value = true;
  try {
    const account = await usePlatformStoreHook().login({ ...form });
    form.password = "";
    await router.replace(
      account.forceResetPwd ? "/platform/change-password" : "/platform/tenants"
    );
  } catch (error) {
    message(toErrorMessage(error, "登录失败"), { type: "error" });
    await refresh();
  } finally {
    busy.value = false;
  }
}
onMounted(refresh);
</script>
<template>
  <el-main>
    <el-row justify="center"
      ><el-col :xs="24" :sm="16" :md="10" :lg="8">
        <el-card>
          <template #header><h1>平台运营登录</h1></template>
          <p>管理租户开通、启停及平台账号。</p>
          <el-form label-position="top" @submit.prevent="login">
            <el-form-item label="平台账号" required
              ><el-input v-model="form.username" autocomplete="username"
            /></el-form-item>
            <el-form-item label="密码" required
              ><el-input
                v-model="form.password"
                type="password"
                show-password
                autocomplete="current-password"
            /></el-form-item>
            <el-form-item label="验证码" required
              ><el-input v-model="form.captchaCode" maxlength="4" /><el-button
                text
                @click="refresh"
                ><img v-if="image" :src="image" alt="点击刷新验证码" /><span
                  v-else
                  >刷新验证码</span
                ></el-button
              ></el-form-item
            >
            <el-button type="primary" native-type="submit" :loading="busy"
              >登录平台</el-button
            >
            <el-button link @click="router.push('/login')">租户登录</el-button>
          </el-form>
        </el-card>
      </el-col></el-row
    >
  </el-main>
</template>
