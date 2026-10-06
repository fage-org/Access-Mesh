<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import {
  getServiceConfigList,
  type ServiceConfigResp
} from "@/api/service-interface";
import {
  issueServiceCredential,
  listServiceCredentials,
  removeServiceCredential,
  updateServiceCredential,
  type IssuedCredential,
  type ServiceCredential
} from "@/api/service-credential";
import { useListLoad } from "@/utils/list-load";
import { hasPerms } from "@/utils/auth";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import { PERMISSION_CODE } from "@/constants/access";
defineOptions({ name: "SystemServiceCredential" });
const services = ref<ServiceConfigResp[]>([]);
const filter = ref("");
const { list, loading, error, load } = useListLoad<ServiceCredential>({
  fetcher: async () =>
    (await listServiceCredentials(filter.value || undefined)).items,
  contextKey: () => filter.value,
  errorText: "服务凭证加载失败"
});
const canManage = computed(() => hasPerms(PERMISSION_CODE.SERVICE_MANAGE));
const issuing = ref(false);
const formVisible = ref(false);
const issued = ref<IssuedCredential | null>(null);
const form = reactive({ serviceCode: "", expiresAt: "" });
const editing = ref<ServiceCredential | null>(null);
const expiry = ref("");
const updating = ref(false);
onMounted(async () => {
  load();
  try {
    services.value = (await getServiceConfigList()).items;
  } catch (e) {
    message(toErrorMessage(e, "服务列表加载失败"), { type: "error" });
  }
});

async function issue() {
  if (!form.serviceCode || issuing.value) return;
  issuing.value = true;
  try {
    issued.value = await issueServiceCredential({
      serviceCode: form.serviceCode,
      expiresAt: form.expiresAt || null
    });
    formVisible.value = false;
    await load();
  } catch (e) {
    message(toErrorMessage(e, "签发失败"), { type: "error" });
  } finally {
    issuing.value = false;
  }
}
async function toggle(row: ServiceCredential) {
  const action = row.status === 1 ? "停用" : "启用";
  try {
    await ElMessageBox.confirm(
      `确认${action}凭证 ${row.credentialId}？停用后调用立即被拒绝。`,
      action,
      { type: "warning" }
    );
  } catch {
    return;
  }
  try {
    await updateServiceCredential({
      id: row.id,
      status: row.status === 1 ? 0 : 1
    });
    await load();
  } catch (e) {
    message(toErrorMessage(e, `${action}失败`), { type: "error" });
  }
}
async function remove(row: ServiceCredential) {
  try {
    await ElMessageBox.confirm(
      `删除凭证 ${row.credentialId} 后立即失效，无法恢复。是否继续？`,
      "删除凭证",
      { type: "warning" }
    );
  } catch {
    return;
  }
  try {
    await removeServiceCredential(row.id);
    await load();
  } catch (e) {
    message(toErrorMessage(e, "删除失败"), { type: "error" });
  }
}
async function saveExpiry() {
  if (!editing.value || !expiry.value || updating.value) return;
  updating.value = true;
  try {
    await updateServiceCredential({
      id: editing.value.id,
      expiresAt: expiry.value
    });
    editing.value = null;
    await load();
  } catch (e) {
    message(toErrorMessage(e, "修改有效期失败"), { type: "error" });
  } finally {
    updating.value = false;
  }
}
async function copySecret() {
  if (!issued.value) return;
  try {
    await navigator.clipboard.writeText(issued.value.secret);
    message("密钥已复制", { type: "success" });
  } catch {
    message("复制失败，请手动复制并妥善保存", { type: "warning" });
  }
}
function state(row: ServiceCredential) {
  if (row.status === 0) return "停用";
  return row.expiresAt && Date.parse(`${row.expiresAt}Z`) <= Date.now()
    ? "已过期"
    : "启用";
}
</script>

<template>
  <el-card shadow="never">
    <template #header
      ><div class="flex items-center justify-between">
        <span>服务凭证</span
        ><el-button v-if="canManage" type="primary" @click="formVisible = true"
          >签发凭证</el-button
        >
      </div></template
    >
    <el-alert
      title="轮换凭证：先签发新凭证并验证接入，再停用旧凭证"
      description="密钥仅签发时显示一次；到期后即使状态为启用，也不能继续调用。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-form inline class="mt-4"
      ><el-form-item label="服务"
        ><el-select
          v-model="filter"
          clearable
          filterable
          placeholder="全部服务"
          class="min-w-64"
          @change="load"
          ><el-option
            v-for="service in services"
            :key="service.serviceCode"
            :value="service.serviceCode"
            :label="`${service.name}（${service.serviceCode}）`" /></el-select></el-form-item
      ><el-form-item
        ><el-button :loading="loading" @click="load"
          >刷新</el-button
        ></el-form-item
      ></el-form
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="list" row-key="id">
      <el-table-column prop="serviceCode" label="服务编码" min-width="140" />
      <el-table-column
        prop="credentialId"
        label="凭证标识"
        min-width="260"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{
          state(row)
        }}</template></el-table-column
      >
      <el-table-column label="到期时间（UTC）" min-width="180"
        ><template #default="{ row }">{{
          row.expiresAt || "不过期"
        }}</template></el-table-column
      >
      <el-table-column
        prop="createdAt"
        label="签发时间（UTC）"
        min-width="180"
      />
      <el-table-column
        prop="rotatedAt"
        label="最近停用时间（UTC）"
        min-width="180"
      />
      <el-table-column v-if="canManage" label="操作" min-width="240"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="toggle(row)">{{
            row.status === 1 ? "停用" : "启用"
          }}</el-button
          ><el-button
            link
            type="primary"
            @click="
              editing = row;
              expiry = row.expiresAt || '';
            "
            >修改有效期</el-button
          ><el-button link type="danger" @click="remove(row)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-dialog
      v-model="formVisible"
      title="签发服务凭证"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top"
        ><el-form-item label="绑定服务"
          ><el-select v-model="form.serviceCode" filterable class="w-full"
            ><el-option
              v-for="service in services.filter(s => s.status === 1)"
              :key="service.serviceCode"
              :value="service.serviceCode"
              :label="`${service.name}（${service.serviceCode}）`" /></el-select></el-form-item
        ><el-form-item label="到期时间（UTC，不填则不过期）"
          ><el-date-picker
            v-model="form.expiresAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button :disabled="issuing" @click="formVisible = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="issuing"
          :disabled="!form.serviceCode"
          @click="issue"
          >签发</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      :model-value="issued !== null"
      title="请保存新凭证"
      width="600px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <template v-if="issued"
        ><el-alert
          title="密钥仅显示这一次，关闭或离开页面后无法再次查看"
          type="warning"
          :closable="false"
        /><el-form label-position="top" class="mt-4"
          ><el-form-item label="凭证标识"
            ><el-input
              :model-value="issued.credentialId"
              readonly /></el-form-item
          ><el-form-item label="密钥"
            ><el-input
              :model-value="issued.secret"
              type="password"
              show-password
              readonly /></el-form-item></el-form
        ><el-button @click="copySecret">复制密钥</el-button></template
      >
      <template #footer
        ><el-button type="primary" @click="issued = null"
          >已保存，关闭</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      :model-value="editing !== null"
      title="修改有效期"
      width="500px"
      @close="editing = null"
      ><p class="mb-4">请选择未来的 UTC 时间；若需要永不过期，请签发新凭证。</p>
      <el-date-picker
        v-model="expiry"
        type="datetime"
        value-format="YYYY-MM-DDTHH:mm:ss"
        :clearable="false"
      /><template #footer
        ><el-button
          type="primary"
          :loading="updating"
          :disabled="!expiry"
          @click="saveExpiry"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </el-card>
</template>
