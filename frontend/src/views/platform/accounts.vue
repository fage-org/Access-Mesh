<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import {
  platformAccountPage,
  createPlatformAccount,
  renamePlatformAccount,
  setPlatformAccountStatus,
  resetPlatformAccount,
  type PlatformAccount
} from "@/api/platform";
import { usePagedList } from "@/utils/list-load";
import { toErrorMessage } from "@/api/_envelope";
import { message } from "@/utils/message";
defineOptions({ name: "PlatformAccounts" });
const list = usePagedList<PlatformAccount>({
  fetcher: (pageNum, pageSize) => platformAccountPage({ pageNum, pageSize }),
  errorText: "平台账号加载失败"
});
const { tableData, loading, error, pagination } = list;
const busy = ref(false);
const visible = ref(false);
const form = reactive({ username: "", name: "" });
const delivery = ref<{ username: string; password: string } | null>(null);
async function create() {
  if (busy.value || !form.username.trim() || !form.name.trim()) return;
  busy.value = true;
  try {
    const result = await createPlatformAccount({ ...form });
    delivery.value = {
      username: result.account.username,
      password: result.initialPassword
    };
    visible.value = false;
    form.username = "";
    form.name = "";
    await list.onSearch();
  } catch (e) {
    message(
      toErrorMessage(e, "创建失败") +
        "。若响应中断，请先查询账号，再通过重置密码恢复交付。",
      { type: "error" }
    );
  } finally {
    busy.value = false;
  }
}
async function action(
  row: PlatformAccount,
  kind: "rename" | "status" | "reset"
) {
  if (busy.value) return;
  busy.value = true;
  try {
    if (kind === "rename") {
      const { value } = await ElMessageBox.prompt(
        "修改平台账号显示名称",
        "修改名称",
        {
          inputValue: row.name,
          inputValidator: value =>
            (!!value?.trim() && value.length <= 128) ||
            "名称不能为空且最多 128 字符"
        }
      );
      await renamePlatformAccount({ id: row.id, name: value });
    } else if (kind === "status") {
      await ElMessageBox.confirm(
        `${row.status === 1 ? "停用" : "恢复"}平台账号 ${row.username}？停用会使旧会话失效。`,
        "账号状态",
        { type: "warning" }
      );
      await setPlatformAccountStatus({
        id: row.id,
        status: row.status === 1 ? 0 : 1
      });
    } else {
      await ElMessageBox.confirm(
        `重置 ${row.username} 的密码并撤销旧会话？操作将被审计。`,
        "重置密码",
        { type: "warning" }
      );
      const result = await resetPlatformAccount(row.id);
      delivery.value = { username: row.username, password: result.password };
      return; // 自身重置已撤销会话，先完成一次展示，不能刷新触发跳转丢失凭据。
    }
    await list.loadTable();
  } catch (e) {
    if (e !== "cancel" && e !== "close")
      message(toErrorMessage(e, "操作失败"), { type: "error" });
  } finally {
    busy.value = false;
  }
}
onMounted(list.loadTable);
</script>
<template>
  <el-card>
    <template #header><h1>平台账号</h1></template>
    <el-alert
      title="平台账号具有相同运营权限。最后一个启用平台管理员受保护（临时登录锁定不计入）；所有管理操作均留审计。"
      type="info"
      :closable="false"
    />
    <el-button type="primary" :disabled="busy" @click="visible = true"
      >新增平台账号</el-button
    >
    <el-button :loading="loading" @click="list.loadTable">刷新</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="tableData" row-key="id">
      <el-table-column prop="username" label="账号" /><el-table-column
        prop="name"
        label="名称"
      />
      <el-table-column label="状态"
        ><template #default="{ row }">{{
          row.status === 1 ? "启用" : "停用"
        }}</template></el-table-column
      >
      <el-table-column label="改密要求"
        ><template #default="{ row }">{{
          row.forceResetPwd ? "首次登录需改密" : "已设置密码"
        }}</template></el-table-column
      >
      <el-table-column label="操作" min-width="250"
        ><template #default="{ row }">
          <el-button link :disabled="busy" @click="action(row, 'rename')"
            >改名称</el-button
          >
          <el-button link :disabled="busy" @click="action(row, 'status')">{{
            row.status === 1 ? "停用" : "恢复"
          }}</el-button>
          <el-button link :disabled="busy" @click="action(row, 'reset')"
            >重置密码</el-button
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
    v-model="visible"
    title="新增平台账号"
    :close-on-click-modal="false"
    :before-close="
      done => {
        if (!busy) done();
      }
    "
  >
    <el-form label-position="top" @submit.prevent="create">
      <el-form-item label="账号"
        ><el-input v-model="form.username" maxlength="64" :disabled="busy"
      /></el-form-item>
      <el-form-item label="名称"
        ><el-input v-model="form.name" maxlength="128" :disabled="busy"
      /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy"
        >创建并生成密码</el-button
      >
    </el-form>
  </el-dialog>
  <el-dialog
    :model-value="!!delivery"
    title="凭据仅展示一次"
    :close-on-click-modal="false"
    @update:model-value="delivery = null"
  >
    <template v-if="delivery"
      ><el-alert
        title="关闭后无法再次查看。首次登录必须设置不同的新密码。"
        type="warning"
        :closable="false"
      />
      <el-descriptions :column="1" border
        ><el-descriptions-item label="账号">{{
          delivery.username
        }}</el-descriptions-item
        ><el-descriptions-item label="初始密码"
          ><code>{{ delivery.password }}</code></el-descriptions-item
        ></el-descriptions
      >
    </template>
    <template #footer
      ><el-button type="primary" @click="delivery = null"
        >已记录，关闭</el-button
      ></template
    >
  </el-dialog>
</template>
