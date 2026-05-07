<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  createOrg,
  updateOrg,
  type OrgNode,
  type OrgCreateRequest,
  type OrgUpdateRequest
} from "@/api/admin/org";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "OrgForm"
});

const props = defineProps<{
  orgDetail: OrgNode | null;
  parentId: number | null;
}>();

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const formRef = ref<FormInstance>();
const loading = ref(false);
const isEdit = ref(false);

const form = reactive({
  id: undefined as number | undefined,
  parentOrgId: undefined as string | undefined,
  orgName: "",
  orgType: 1,
  code: "",
  phone: "",
  email: "",
  sort: 0,
  status: 1
});

// ========== 权限计算 ==========

const canUpdate = hasPerms(PERM_CODES.SYS_ORG_UPDATE);
const canCreate = hasPerms(PERM_CODES.SYS_ORG_CREATE);

// ========== 表单验证规则 ==========

const rules: FormRules = {
  orgName: [{ required: true, message: "请输入组织名称", trigger: "blur" }],
  orgType: [{ required: true, message: "请选择组织类型", trigger: "change" }],
  code: [
    {
      pattern: /^[A-Z0-9_]+$/,
      message: "组织编码只能包含大写字母、数字和下划线",
      trigger: "blur"
    }
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$|^$/,
      message: "请输入正确的手机号",
      trigger: "blur"
    }
  ],
  email: [
    {
      type: "email",
      message: "请输入正确的邮箱地址",
      trigger: "blur"
    }
  ]
};

// ========== 监听 orgDetail 变化 ==========

watch(
  () => props.orgDetail,
  detail => {
    if (detail) {
      isEdit.value = true;
      form.id = detail.id;
      form.parentOrgId = detail.parentOrgId;
      form.orgName = detail.orgName;
      form.orgType = detail.orgType;
      form.code = detail.code || "";
      form.phone = detail.phone || "";
      form.email = detail.email || "";
      form.sort = detail.sort || 0;
      form.status = detail.status;
    } else {
      isEdit.value = false;
      resetForm();
      // 设置父组织ID（新增子节点时）
      form.parentOrgId = props.parentId ? String(props.parentId) : undefined;
    }
  },
  { immediate: true }
);

// ========== 重置表单 ==========

const resetForm = () => {
  form.id = undefined;
  form.parentOrgId = undefined;
  form.orgName = "";
  form.orgType = 1;
  form.code = "";
  form.phone = "";
  form.email = "";
  form.sort = 0;
  form.status = 1;
};

// ========== 提交表单 ==========

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
  } catch {
    return;
  }

  loading.value = true;
  try {
    if (isEdit.value && form.id) {
      const updateData: OrgUpdateRequest = {
        id: form.id,
        orgName: form.orgName,
        orgType: form.orgType,
        code: form.code,
        phone: form.phone,
        email: form.email,
        sort: form.sort,
        status: form.status
      };
      const res = await updateOrg(updateData);
      if (res.success) {
        ElMessage.success("更新成功");
        emit("success");
      } else {
        ElMessage.error("更新失败");
      }
    } else {
      const createData: OrgCreateRequest = {
        orgName: form.orgName,
        orgType: form.orgType,
        parentOrgId: form.parentOrgId,
        code: form.code,
        phone: form.phone,
        email: form.email,
        sort: form.sort,
        status: form.status
      };
      const res = await createOrg(createData);
      if (res.success) {
        ElMessage.success("创建成功");
        emit("success");
      } else {
        ElMessage.error("创建失败");
      }
    }
  } catch (error) {
    console.error("[OrgForm] Submit failed:", error);
    ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
  } finally {
    loading.value = false;
  }
};

// ========== 取消 ==========

const handleCancel = () => {
  resetForm();
};
</script>

<template>
  <div class="org-form">
    <el-alert
      v-if="!props.orgDetail && !props.parentId"
      type="info"
      title="新增根节点"
      class="mb-4"
      :closable="false"
    />
    <el-alert
      v-if="!props.orgDetail && props.parentId"
      type="info"
      title="新增子节点"
      class="mb-4"
      :closable="false"
    />
    <el-alert
      v-if="props.orgDetail"
      type="success"
      :title="`编辑组织: ${props.orgDetail.orgName}`"
      class="mb-4"
      :closable="false"
    />

    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item v-if="!isEdit" label="上级组织">
        <el-input
          :value="props.parentId ? '已选择父节点' : '根节点'"
          disabled
        />
      </el-form-item>

      <el-form-item label="组织名称" prop="orgName">
        <el-input v-model="form.orgName" placeholder="请输入组织名称" />
      </el-form-item>

      <el-form-item label="组织编码" prop="code">
        <el-input
          v-model="form.code"
          placeholder="请输入组织编码"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item label="组织类型" prop="orgType">
        <el-select v-model="form.orgType" placeholder="请选择组织类型">
          <el-option label="公司" :value="1" />
          <el-option label="部门" :value="2" />
          <el-option label="小组" :value="3" />
        </el-select>
      </el-form-item>

      <el-form-item label="联系电话">
        <el-input v-model="form.phone" placeholder="请输入联系电话" />
      </el-form-item>

      <el-form-item label="邮箱">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sort" :min="0" :max="999" />
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="isEdit ? !canUpdate : !canCreate"
          @click="handleSubmit"
        >
          保存
        </el-button>
        <el-button @click="handleCancel">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.org-form {
  max-width: 600px;
}
</style>
