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
import { useUserStoreHook } from "@/store/modules/user";
import { useTenantStoreHook } from "@/store/modules/tenant";
import { getTenantList } from "@/api/admin/tenant";
import { initRouter, getTopMenu } from "@/router/utils";
import { bg, avatar, illustration } from "./utils/static";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { useDataThemeChange } from "@/layout/hooks/useDataThemeChange";
import {
  startOAuth2Flow,
  isOAuth2Callback,
  handleOAuth2Callback
} from "@/utils/oauth2";
import Captcha from "./components/Captcha.vue";

import dayIcon from "@/assets/svg/day.svg?component";
import darkIcon from "@/assets/svg/dark.svg?component";
import Lock from "~icons/ri/lock-fill";
import User from "~icons/ri/user-3-fill";

defineOptions({
  name: "Login"
});

const router = useRouter();
const loading = ref(false);
const disabled = ref(false);
const ruleFormRef = ref<FormInstance>();
const tenantStore = useTenantStoreHook();
const tenantLoading = ref(false);
const selectedTenantId = ref<number>();
const captchaRef = ref();

// OAuth2 是否启用
const oauth2Enabled = import.meta.env.VITE_OAUTH2_ENABLED === "true";

const { initStorage } = useLayout();
initStorage();

const { dataTheme, overallStyle, dataThemeChange } = useDataThemeChange();
dataThemeChange(overallStyle.value);
const { title } = useNav();

const ruleForm = reactive({
  username: "admin",
  password: "admin123",
  tenantId: null as number | null
});

// 初始化租户列表
onMounted(async () => {
  // OAuth2 模式: 检查是否是回调
  if (oauth2Enabled && isOAuth2Callback()) {
    loading.value = true;
    await handleOAuth2Callback();
    loading.value = false;
    return;
  }

  // 加载租户列表
  tenantLoading.value = true;
  try {
    const res = await getTenantList();
    if (res?.success) {
      tenantStore.SET_TENANT_LIST(res.data.items);
      // 默认选择第一个租户
      if (res.data.items.length > 0) {
        selectedTenantId.value = res.data.items[0].id;
        ruleForm.tenantId = res.data.items[0].id;
        tenantStore.SET_CURRENT_TENANT(res.data.items[0].id);
      }
    }
  } catch (error) {
    message("获取租户列表失败", { type: "error" });
  } finally {
    tenantLoading.value = false;
  }
});

// OAuth2 登录: 启动授权码流程
const onOAuth2Login = async () => {
  if (!ruleForm.tenantId) {
    message("请选择租户", { type: "warning" });
    return;
  }

  loading.value = true;
  tenantStore.SET_CURRENT_TENANT(ruleForm.tenantId);

  try {
    await startOAuth2Flow(ruleForm.tenantId);
  } catch (error) {
    console.error("OAuth2 flow start failed:", error);
    message("登录失败", { type: "error" });
  } finally {
    loading.value = false;
  }
};

// 简化登录: 直接 POST 登录接口
const onSimpleLogin = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate(valid => {
    if (valid) {
      if (!ruleForm.tenantId) {
        message("请选择租户", { type: "warning" });
        return;
      }
      loading.value = true;
      useUserStoreHook()
        .loginByUsername({
          username: ruleForm.username,
          password: ruleForm.password,
          tenantId: ruleForm.tenantId!
        })
        .then(res => {
          if (res?.success) {
            return initRouter().then(() => {
              disabled.value = true;
              router
                .push(getTopMenu(true).path)
                .then(() => {
                  message("登录成功", { type: "success" });
                })
                .finally(() => (disabled.value = false));
            });
          } else {
            message("登录失败", { type: "error" });
          }
        })
        .finally(() => (loading.value = false));
    }
  });
};

// 登录按钮处理
const onLogin = async (formEl: FormInstance | undefined) => {
  if (oauth2Enabled) {
    await onOAuth2Login();
  } else {
    await onSimpleLogin(formEl);
  }
};

// 租户切换
const onTenantChange = (tenantId: number) => {
  ruleForm.tenantId = tenantId;
  tenantStore.SET_CURRENT_TENANT(tenantId);
  // 切换租户时重新加载验证码
  captchaRef.value?.loadCaptcha();
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
            <Motion :delay="50">
              <el-form-item
                :rules="[
                  {
                    required: true,
                    message: '请选择租户',
                    trigger: 'change'
                  }
                ]"
                prop="tenantId"
              >
                <el-select
                  v-model="selectedTenantId"
                  placeholder="选择租户"
                  size="large"
                  :loading="tenantLoading"
                  clearable
                  @change="onTenantChange"
                >
                  <el-option
                    v-for="tenant in tenantStore.tenantList"
                    :key="tenant.id"
                    :label="tenant.name"
                    :value="tenant.id"
                  />
                </el-select>
              </el-form-item>
            </Motion>

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

            <!-- OAuth2 模式下显示验证码 -->
            <Motion v-if="oauth2Enabled" :delay="200">
              <el-form-item prop="captcha">
                <Captcha ref="captchaRef" />
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
