<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import {
  approveAuthorization,
  previewAuthorization,
  type AuthorizationPreview,
  type OAuthAuthorizeReq
} from "@/api/oauth-consent";
import { toErrorMessage } from "@/api/_envelope";
import { authorizationRequest, consentCallback } from "./flow";

defineOptions({ name: "OAuthConsent" });
const route = useRoute();
const loading = ref(true);
const submitting = ref(false);
const error = ref("");
const preview = ref<AuthorizationPreview | null>(null);
let request: OAuthAuthorizeReq;

onMounted(async () => {
  try {
    request = authorizationRequest(route.query);
    preview.value = await previewAuthorization(request);
  } catch (e) {
    error.value = toErrorMessage(e, "无法核验授权请求，请返回应用重新发起");
  } finally {
    loading.value = false;
  }
});

async function decide(approved: boolean) {
  if (!preview.value || submitting.value) return;
  submitting.value = true;
  error.value = "";
  try {
    const callback = await consentCallback(
      approved,
      request,
      preview.value,
      approveAuthorization
    );
    window.location.assign(callback);
  } catch (e) {
    error.value = toErrorMessage(e, "授权未完成，请重试或返回应用重新发起");
    submitting.value = false;
  }
}
</script>

<template>
  <main class="min-h-screen flex items-center justify-center p-6">
    <el-card v-loading="loading" class="w-full max-w-xl">
      <template #header
        ><h1 class="text-xl font-semibold">应用授权</h1></template
      >
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <template v-if="preview">
        <p class="my-4 text-lg">
          {{ preview.clientName || preview.clientId }} 请求你的授权
        </p>
        <p class="mb-4 text-sm">
          应用可读取你的基础用户信息。请确认你信任此应用后继续。
        </p>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="应用标识">{{
            preview.clientId
          }}</el-descriptions-item>
          <el-descriptions-item label="请求范围">{{
            preview.scopes.length
              ? preview.scopes.join("、")
              : "未请求额外权限范围"
          }}</el-descriptions-item>
          <el-descriptions-item label="返回地址"
            ><span class="break-all">{{
              preview.redirectUri
            }}</span></el-descriptions-item
          >
        </el-descriptions>
        <div class="mt-6 flex justify-end gap-3">
          <el-button :disabled="submitting" @click="decide(false)"
            >拒绝</el-button
          >
          <el-button type="primary" :loading="submitting" @click="decide(true)"
            >同意授权</el-button
          >
        </div>
      </template>
      <p v-else-if="!loading" class="mt-4 text-sm">
        未签发授权码。请关闭此页并从应用重新发起授权。
      </p>
    </el-card>
  </main>
</template>
