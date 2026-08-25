<script setup lang="ts">
import Motion from "./utils/motion";
import { useRouter } from "vue-router";
import { message } from "@/utils/message";
import { loginRules } from "./utils/rule";
import { ref, reactive, toRaw, onMounted } from "vue";
import { debounce } from "@pureadmin/utils";
import { useNav } from "@/layout/hooks/useNav";
import { useEventListener } from "@vueuse/core";
import type { FormInstance } from "element-plus";
import { useLayout } from "@/layout/hooks/useLayout";
import { getCaptcha } from "@/api/auth";
import { useUserStoreHook } from "@/store/modules/user";
import { initRouter, getTopMenu } from "@/router/utils";
import { bg, avatar, illustration } from "./utils/static";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { useDataThemeChange } from "@/layout/hooks/useDataThemeChange";

import dayIcon from "@/assets/svg/day.svg?component";
import darkIcon from "@/assets/svg/dark.svg?component";
import Lock from "~icons/ri/lock-fill";
import User from "~icons/ri/user-3-fill";
import Cyanpass from "~icons/ri/shield-keyhole-line";

defineOptions({
  name: "Login"
});

const router = useRouter();
const loading = ref(false);
const disabled = ref(false);
const ruleFormRef = ref<FormInstance>();

const { initStorage } = useLayout();
initStorage();

const { dataTheme, overallStyle, dataThemeChange } = useDataThemeChange();
dataThemeChange(overallStyle.value);
const { title } = useNav();

const ruleForm = reactive({
  username: "",
  password: "",
  captchaCode: ""
});

/** 当前验证码 ID（后端一次性消费，登录失败后必须刷新） */
const captchaId = ref("");
/** 验证码图片（base64，后端已含 data:image/png;base64, 前缀） */
const captchaImage = ref("");

/** 拉取/刷新验证码：页面加载与每次登录失败后调用（发起即失效旧验证码——后端一次性消费） */
const refreshCaptcha = async () => {
  captchaId.value = "";
  ruleForm.captchaCode = "";
  try {
    const { captchaId: id, image } = await getCaptcha();
    captchaId.value = id;
    captchaImage.value = image;
  } catch {
    message("验证码获取失败，请检查服务后点击图片重试", { type: "error" });
  }
};

onMounted(refreshCaptcha);

const onLogin = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate(valid => {
    if (valid) {
      loading.value = true;
      useUserStoreHook()
        .loginByUsername({
          username: ruleForm.username,
          password: ruleForm.password,
          captchaId: captchaId.value,
          captchaCode: ruleForm.captchaCode
        })
        .then(loginData => {
          // 获取后端路由
          return initRouter().then(() => {
            disabled.value = true;
            router
              .push(getTopMenu(true).path)
              .then(() => {
                message("登录成功", { type: "success" });
                // 初始密码/管理员重置后待改密：系统暂无自助改密通道，
                // 非阻断提示引导联系管理员（T-ADMIN-022，归属自 T-FE-041 移入）
                if (loginData?.forceResetPwd) {
                  message("当前密码为初始密码，请联系管理员重置", {
                    type: "warning",
                    duration: 6000
                  });
                }
              })
              .finally(() => (disabled.value = false));
          });
        })
        .catch((error: Error) => {
          // 业务失败（HTTP 200 + code≠200，经 unwrap 抛 RequestError）展示后端 message；
          // 验证码一次性消费，无论何种失败均刷新——返回 promise 使 loading 覆盖刷新过程，
          // 期间按钮不可重复提交（评审修复：避免慢网下用已消费旧码立即重试）
          message(error?.message || "登录失败", { type: "error" });
          return refreshCaptcha();
        })
        .finally(() => (loading.value = false));
    }
  });
};

const immediateDebounce: any = debounce(
  formRef => onLogin(formRef),
  1000,
  true
);

useEventListener(document, "keydown", ({ code }) => {
  if (
    ["Enter", "NumpadEnter"].includes(code) &&
    !disabled.value &&
    !loading.value
  )
    immediateDebounce(ruleFormRef.value);
});
</script>

<template>
  <div class="select-none">
    <img :src="bg" class="wave" />
    <div class="flex-c absolute right-5 top-3">
      <!-- 主题 -->
      <el-switch
        v-model="dataTheme"
        inline-prompt
        :active-icon="dayIcon"
        :inactive-icon="darkIcon"
        @change="dataThemeChange"
      />
    </div>
    <div class="login-container">
      <div class="img">
        <component :is="toRaw(illustration)" />
      </div>
      <div class="login-box">
        <div class="login-form">
          <avatar class="avatar" />
          <Motion>
            <h2 class="outline-hidden">{{ title }}</h2>
          </Motion>

          <el-form
            ref="ruleFormRef"
            :model="ruleForm"
            :rules="loginRules"
            size="large"
          >
            <Motion :delay="100">
              <el-form-item
                :rules="[
                  {
                    required: true,
                    message: '请输入账号',
                    trigger: 'blur'
                  }
                ]"
                prop="username"
              >
                <el-input
                  v-model="ruleForm.username"
                  clearable
                  placeholder="账号"
                  :prefix-icon="useRenderIcon(User)"
                />
              </el-form-item>
            </Motion>

            <Motion :delay="150">
              <el-form-item prop="password">
                <el-input
                  v-model="ruleForm.password"
                  clearable
                  show-password
                  placeholder="密码"
                  :prefix-icon="useRenderIcon(Lock)"
                />
              </el-form-item>
            </Motion>

            <Motion :delay="200">
              <el-form-item prop="captchaCode">
                <el-input
                  v-model="ruleForm.captchaCode"
                  clearable
                  maxlength="4"
                  placeholder="验证码"
                  :prefix-icon="useRenderIcon(Cyanpass)"
                >
                  <template #append>
                    <img
                      v-if="captchaImage"
                      :src="captchaImage"
                      alt="验证码"
                      title="点击刷新验证码"
                      class="cursor-pointer select-none"
                      style=" width: 120px;height: 40px"
                      @click="refreshCaptcha"
                    />
                    <span
                      v-else
                      class="cursor-pointer"
                      style="
                        display: inline-block;
                        width: 120px;
                        height: 40px;
                        font-size: 12px;
                        line-height: 40px;
                        color: var(--el-text-color-secondary);
                        text-align: center;
                      "
                      @click="refreshCaptcha"
                    >
                      点击加载
                    </span>
                  </template>
                </el-input>
              </el-form-item>
            </Motion>

            <Motion :delay="250">
              <el-button
                class="w-full mt-4!"
                size="default"
                type="primary"
                :loading="loading"
                :disabled="disabled"
                @click="onLogin(ruleFormRef)"
              >
                登录
              </el-button>
            </Motion>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url("@/style/login.css");
</style>

<style lang="scss" scoped>
:deep(.el-input-group__append, .el-input-group__prepend) {
  padding: 0;
}
</style>
