<script setup lang="ts">
import { ref, reactive, watch, computed } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  createUser,
  updateUser,
  getUserDetail,
  type UserCreateRequest,
  type UserUpdateRequest
} from "@/api/admin/user";
import { getOrgTree, type OrgNode } from "@/api/admin/org";

defineOptions({
  name: "UserForm"
});

const props = defineProps<{
  editId: number | null;
}>();

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();
const orgTreeData = ref<Array<OrgNode>>([]);

const form = reactive({
  username: "",
  name: "",
  phone: "",
  email: "",
  status: 1,
  primaryOrgId: null as number | null
});

const isEdit = computed(() => props.editId !== null);

// ========== 表单验证规则 ==========

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 32, message: "用户名长度3-32位", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_]+$/,
      message: "用户名只能包含字母、数字和下划线",
      trigger: "blur"
    }
  ],
  name: [
    { required: true, message: "请输入姓名", trigger: "blur" },
    { max: 50, message: "姓名最多50个字符", trigger: "blur" }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: "请输入正确的手机号", trigger: "blur" }
  ],
  email: [{ type: "email", message: "请输入正确的邮箱地址", trigger: "blur" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }]
};

// ========== 数据加载 ==========

const loadOrgTree = async () => {
  try {
    const res = await getOrgTree();
    if (res.success) {
      orgTreeData.value = res.data;
    }
  } catch (error) {
    ElMessage.error("加载组织树失败");
  }
};

const loadUserDetail = async () => {
  if (!props.editId) return;
  loading.value = true;
  try {
    const res = await getUserDetail({ id: props.editId });
    if (res.success) {
      const data = res.data;
      form.username = data.username;
      form.name = data.name;
      form.phone = data.phone || "";
      form.email = data.email || "";
      form.status = data.status;
      // 设置主组织
      const primaryOrg = data.orgs?.find(o => o.isPrimary);
      form.primaryOrgId = primaryOrg?.orgId || null;
    }
  } catch (error) {
    ElMessage.error("加载用户信息失败");
  } finally {
    loading.value = false;
  }
};

// ========== 打开弹窗 ==========

const openDialog = () => {
  dialogVisible.value = true;
  resetForm();
  loadOrgTree();
  if (isEdit.value) {
    loadUserDetail();
  }
};

const resetForm = () => {
  form.username = "";
  form.name = "";
  form.phone = "";
  form.email = "";
  form.status = 1;
  form.primaryOrgId = null;
  formRef.value?.resetFields();
};

// ========== 提交 ==========

const handleSubmit = async () => {
  const valid = await formRef.value?.validate();
  if (!valid) return;

  loading.value = true;
  try {
    if (isEdit.value) {
      // 编辑
      const params: UserUpdateRequest = {
        id: props.editId!,
        name: form.name,
        phone: form.phone || undefined,
        email: form.email || undefined,
        status: form.status
      };
      const res = await updateUser(params);
      if (res.success) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        emit("success");
      }
    } else {
      // 创建
      const params: UserCreateRequest = {
        username: form.username,
        name: form.name,
        phone: form.phone || undefined,
        email: form.email || undefined,
        status: form.status
      };
      const res = await createUser(params);
      if (res.success) {
        ElMessage.success("创建成功");
        dialogVisible.value = false;
        emit("success");
      }
    }
  } catch (error) {
    ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
  } finally {
    loading.value = false;
  }
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑用户' : '新增用户'"
    width="500px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          placeholder="请输入用户名"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item label="姓名" prop="name">
        <el-input v-model="form.name" placeholder="请输入姓名" />
      </el-form-item>

      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="请输入手机号" />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="主组织">
        <el-tree-select
          v-model="form.primaryOrgId"
          :data="orgTreeData"
          :props="{
            children: 'children',
            label: 'orgName',
            value: 'id'
          }"
          node-key="id"
          check-strictly
          placeholder="请选择主组织"
          clearable
          class="w-full"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isEdit ? "保存" : "创建" }}
      </el-button>
    </template>
  </el-dialog>
</template>
