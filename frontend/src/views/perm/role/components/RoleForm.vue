<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  createRole,
  updateRole,
  ROLE_TYPE_CODES,
  type RoleDetail,
  type RoleCreateRequest,
  type RoleUpdateRequest
} from "@/api/perm/role";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "RoleForm"
});

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const loading = ref(false);
const isEdit = ref(false);
const parentId = ref<number | null>(null);

const form = reactive({
  id: undefined as number | undefined,
  parentId: undefined as number | undefined,
  roleTypeCode: ROLE_TYPE_CODES.BASIC_ROLE as string,
  externalId: "",
  name: "",
  sortOrder: 0,
  status: 1,
  extra: ""
});

// ========== 权限计算 ==========

const canUpdate = hasPerms(PERM_CODES.SYS_ROLE_UPDATE);
const canCreate = hasPerms(PERM_CODES.SYS_ROLE_CREATE);

// ========== 表单验证规则 ==========

const rules: FormRules = {
  name: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  externalId: [
    { required: true, message: "请输入角色编码", trigger: "blur" },
    {
      pattern: /^[A-Z_]+$/,
      message: "编码只能包含大写字母和下划线",
      trigger: "blur"
    }
  ],
  roleTypeCode: [
    { required: true, message: "请选择角色类型", trigger: "change" }
  ]
};

// ========== 打开弹窗 ==========

const openDialog = (parent: number | null, editData?: RoleDetail) => {
  dialogVisible.value = true;
  parentId.value = parent;

  if (editData) {
    isEdit.value = true;
    form.id = editData.id;
    form.parentId = editData.parentId;
    form.roleTypeCode = editData.roleTypeCode;
    form.externalId = editData.externalId;
    form.name = editData.name;
    form.sortOrder = editData.sortOrder;
    form.status = editData.status;
    form.extra = editData.extra || "";
  } else {
    isEdit.value = false;
    resetForm();
    form.parentId = parent;
  }
};

// ========== 重置表单 ==========

const resetForm = () => {
  form.id = undefined;
  form.parentId = undefined;
  form.roleTypeCode = ROLE_TYPE_CODES.BASIC_ROLE;
  form.externalId = "";
  form.name = "";
  form.sortOrder = 0;
  form.status = 1;
  form.extra = "";
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
      const updateData: RoleUpdateRequest = {
        roleId: form.id,
        name: form.name,
        status: form.status,
        sortOrder: form.sortOrder,
        extra: form.extra || undefined
      };
      const res = await updateRole(updateData);
      if (res.success) {
        ElMessage.success("更新成功");
        dialogVisible.value = false;
        emit("success");
      } else {
        ElMessage.error("更新失败");
      }
    } else {
      const createData: RoleCreateRequest = {
        parentId: form.parentId,
        roleTypeCode: form.roleTypeCode,
        externalId: form.externalId,
        name: form.name,
        sortOrder: form.sortOrder,
        extra: form.extra || undefined
      };
      const res = await createRole(createData);
      if (res.success) {
        ElMessage.success("创建成功");
        dialogVisible.value = false;
        emit("success");
      } else {
        ElMessage.error("创建失败");
      }
    }
  } catch {
    ElMessage.error(isEdit.value ? "更新失败" : "创建失败");
  } finally {
    loading.value = false;
  }
};

// ========== 监听弹窗关闭 ==========

watch(dialogVisible, val => {
  if (!val) {
    resetForm();
    formRef.value?.resetFields();
  }
});

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑角色' : '新增角色'"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item v-if="!isEdit" label="上级角色">
        <el-input :value="parentId ? '已选择父节点' : '根角色'" disabled />
      </el-form-item>

      <el-form-item label="角色名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入角色名称" />
      </el-form-item>

      <el-form-item label="角色编码" prop="externalId">
        <el-input
          v-model="form.externalId"
          placeholder="请输入角色编码(大写字母+下划线)"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item v-if="!isEdit" label="角色类型" prop="roleTypeCode">
        <el-radio-group v-model="form.roleTypeCode">
          <el-radio :value="ROLE_TYPE_CODES.GROUP_ROLE">分组角色</el-radio>
          <el-radio :value="ROLE_TYPE_CODES.BASIC_ROLE">基本角色</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="扩展信息">
        <el-input
          v-model="form.extra"
          placeholder="可选扩展信息"
          type="textarea"
          :rows="2"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="isEdit ? !canUpdate : !canCreate"
        @click="handleSubmit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
