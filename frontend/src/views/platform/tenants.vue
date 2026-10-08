<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import {
  tenantPage,
  tenantDetail,
  createTenant,
  renameTenant,
  setTenantStatus,
  resetTenantAdmin,
  type Tenant
} from "@/api/platform";
import { usePagedList } from "@/utils/list-load";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
defineOptions({ name: "PlatformTenants" });
const keyword = ref("");
const list = usePagedList<Tenant>({
  fetcher: (pageNum, pageSize) =>
    tenantPage({ pageNum, pageSize, keyword: keyword.value || undefined }),
  errorText: "租户列表加载失败"
});
const { tableData, loading, error, pagination } = list;
const busy = ref(false);
const createVisible = ref(false);
const form = reactive({ code: "", name: "" });
const detail = ref<Tenant | null>(null);
const delivery = ref<{
  code: string;
  username: string;
  password: string;
  userStatus?: number;
} | null>(null);
async function create() {
  if (busy.value) return;
  if (!/^[a-z][a-z0-9-]{0,63}$/.test(form.code) || !form.name.trim()) {
    message("请填写有效租户编码和名称", { type: "warning" });
    return;
  }
  busy.value = true;
  try {
    const result = await createTenant({ ...form });
    delivery.value = {
      code: result.tenant.code,
      username: result.adminUsername,
      password: result.initialPassword
    };
    createVisible.value = false;
    form.code = "";
    form.name = "";
    await list.onSearch();
  } catch (e) {
    message(
      toErrorMessage(e, "开通失败") +
        "。若响应中断，请先查询编码是否已存在，再使用首管理员密码重置恢复交付。",
      { type: "error" }
    );
  } finally {
    busy.value = false;
  }
}
async function action(
  row: Tenant,
  kind: "detail" | "rename" | "status" | "reset"
) {
  if (busy.value) return;
  busy.value = true;
  try {
    if (kind === "detail") {
      detail.value = await tenantDetail(row.id);
      return;
    }
    if (kind === "rename") {
      const { value } = await ElMessageBox.prompt(
        "租户编码保持不变",
        "修改租户名称",
        {
          inputValue: row.name,
          inputValidator: value =>
            (!!value?.trim() && value.length <= 128) ||
            "名称不能为空且最多 128 字符"
        }
      );
      await renameTenant({ id: row.id, name: value });
    } else if (kind === "status") {
      await ElMessageBox.confirm(
        row.status === 1
          ? `停用 ${row.code} 后，新请求和任务将被阻断。`
          : `恢复 ${row.code} 后，用户需要重新登录，有效服务凭证恢复可用。`,
        row.status === 1 ? "停用租户" : "恢复租户",
        { type: "warning" }
      );
      await setTenantStatus({ id: row.id, status: row.status === 1 ? 0 : 1 });
    } else {
      await ElMessageBox.confirm(
        `重置 ${row.code} 的首管理员密码会撤销其会话并要求首次改密，不恢复账号状态或角色。此操作将记入平台审计。`,
        "重置首管理员密码",
        { type: "warning" }
      );
      const result = await resetTenantAdmin(row.id);
      delivery.value = { code: row.code, ...result };
    }
    await list.loadTable();
  } catch (e) {
    if (e !== "cancel" && e !== "close")
      message(toErrorMessage(e, "操作失败，请刷新租户状态核对结果"), {
        type: "error"
      });
  } finally {
    busy.value = false;
  }
}
onMounted(list.loadTable);
</script>
<template>
  <el-card>
    <template #header><h1>租户</h1></template>
    <el-alert
      title="平台负责租户开通与启停。客户内部的用户、组织和授权由租户自行管理。"
      type="info"
      :closable="false"
    />
    <el-form inline @submit.prevent="list.onSearch">
      <el-form-item label="编码或名称"
        ><el-input v-model="keyword" clearable
      /></el-form-item>
      <el-form-item
        ><el-button native-type="submit" :loading="loading">查询</el-button
        ><el-button
          type="primary"
          :disabled="busy"
          @click="createVisible = true"
          >开通租户</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="tableData" row-key="id">
      <el-table-column prop="code" label="租户编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="登记状态"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
            row.status === 1 ? "启用" : "停用"
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="访问状态"
        ><template #default="{ row }"
          ><el-tag
            :type="row.accessState === 'ENABLED' ? 'success' : 'warning'"
            >{{
              row.accessState === "ENABLED"
                ? "可访问"
                : row.accessState === "DISABLED"
                  ? "已阻断"
                  : "门禁未就绪"
            }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column label="操作" min-width="330"
        ><template #default="{ row }">
          <el-button link :disabled="busy" @click="action(row, 'detail')"
            >详情</el-button
          >
          <el-button link :disabled="busy" @click="action(row, 'rename')"
            >改名称</el-button
          >
          <el-button link :disabled="busy" @click="action(row, 'status')">{{
            row.status === 1 ? "停用" : "恢复"
          }}</el-button>
          <el-button link :disabled="busy" @click="action(row, 'reset')"
            >重置首管理员密码</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <el-pagination
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total"
      layout="total, prev, pager, next"
      @current-change="list.onPageChange"
    />
  </el-card>
  <el-dialog
    v-model="createVisible"
    title="开通租户"
    :close-on-click-modal="false"
    :before-close="
      done => {
        if (!busy) done();
      }
    "
  >
    <el-form label-position="top" @submit.prevent="create">
      <el-form-item label="租户编码（创建后不可修改或复用）"
        ><el-input
          v-model="form.code"
          maxlength="64"
          placeholder="如 customer-a"
          :disabled="busy"
      /></el-form-item>
      <el-form-item label="租户名称"
        ><el-input v-model="form.name" maxlength="128" :disabled="busy"
      /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy"
        >开通并生成首管理员密码</el-button
      >
    </el-form>
  </el-dialog>
  <el-dialog
    :model-value="!!delivery"
    title="凭据仅展示一次"
    :close-on-click-modal="false"
    @update:model-value="delivery = null"
  >
    <template v-if="delivery">
      <el-alert
        title="请妥善交付给客户。关闭后无法再次查看；客户首次登录必须更换密码。"
        type="warning"
        :closable="false"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="租户编码">{{
          delivery.code
        }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{
          delivery.username
        }}</el-descriptions-item>
        <el-descriptions-item label="初始密码"
          ><code>{{ delivery.password }}</code></el-descriptions-item
        >
      </el-descriptions>
      <el-alert
        v-if="delivery.userStatus === 0"
        title="该账号仍处于停用状态；本次仅重置凭据。"
        type="warning"
        :closable="false"
      />
    </template>
    <template #footer
      ><el-button type="primary" @click="delivery = null"
        >已记录，关闭</el-button
      ></template
    >
  </el-dialog>
  <el-dialog
    :model-value="!!detail"
    title="租户详情"
    @update:model-value="detail = null"
  >
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="租户 ID">{{
        detail.id
      }}</el-descriptions-item>
      <el-descriptions-item label="租户编码">{{
        detail.code
      }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{
        detail.name
      }}</el-descriptions-item>
      <el-descriptions-item label="首管理员 ID">{{
        detail.adminUserId
      }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{
        detail.createdAt
      }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
