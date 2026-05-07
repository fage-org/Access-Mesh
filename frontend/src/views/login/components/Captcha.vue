<script setup lang="ts">
import { ref, onMounted } from "vue";
import { getCaptcha } from "@/api/admin/oauth2";
import { useTenantStoreHook } from "@/store/modules/tenant";
import { Loading } from "@element-plus/icons-vue";

defineOptions({
  name: "Captcha"
});

const tenantStore = useTenantStoreHook();

const captchaId = ref<string>("");
const captchaImage = ref<string>("");
const captchaCode = ref<string>("");
const loading = ref(false);

// 加载验证码
const loadCaptcha = async () => {
  if (!tenantStore.currentTenantId) {
    return;
  }

  loading.value = true;
  try {
    const res = await getCaptcha(tenantStore.currentTenantId);
    if (res?.success) {
      captchaId.value = res.data.captchaId;
      captchaImage.value = res.data.captchaImage;
    }
  } catch (error) {
    console.error("Load captcha failed:", error);
  } finally {
    loading.value = false;
  }
};

// 刷新验证码(点击图片)
const refreshCaptcha = () => {
  captchaCode.value = "";
  loadCaptcha();
};

// 暴露给父组件
defineExpose({
  captchaId,
  captchaCode,
  loadCaptcha
});

onMounted(() => {
  loadCaptcha();
});
</script>

<template>
  <div class="captcha-container">
    <el-input
      v-model="captchaCode"
      placeholder="请输入验证码"
      size="large"
      class="captcha-input"
      maxlength="4"
    />
    <div class="captcha-image-wrapper" @click="refreshCaptcha">
      <img
        v-if="captchaImage"
        :src="`data:image/png;base64,${captchaImage}`"
        alt="验证码"
        class="captcha-image"
      />
      <el-icon v-else class="captcha-loading">
        <Loading />
      </el-icon>
    </div>
  </div>
</template>

<style scoped lang="scss">
.captcha-container {
  display: flex;
  align-items: center;
  gap: 10px;
}

.captcha-input {
  flex: 1;
}

.captcha-image-wrapper {
  width: 120px;
  height: 40px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-loading {
  font-size: 20px;
  color: #999;
}
</style>
