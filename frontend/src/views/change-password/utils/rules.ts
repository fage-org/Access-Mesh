import type { FormRules } from "element-plus";

/**
 * 强制改密页表单规则（T-FE-046）。
 * 长度边界 8-32 对齐后端 ResetPasswordReq（契约 §7.7 @Size(8,32)）；
 * 不设旧密码字段——后端旧密码校验未实现（T-PERM-066 明确另立任务），
 * 前端收集不校验的字段是假安全。
 * confirmPassword 校验器经 getPassword 取实时新密码（工厂注入，纯函数可测）。
 */
export function buildChangePasswordRules(getPassword: () => string): FormRules {
  return {
    newPassword: [
      { required: true, message: "请输入新密码", trigger: "blur" },
      { min: 8, max: 32, message: "密码长度为 8-32 位", trigger: "blur" }
    ],
    confirmPassword: [
      { required: true, message: "请再次输入新密码", trigger: "blur" },
      {
        validator: (_rule, value: string, callback) => {
          value === getPassword()
            ? callback()
            : callback(new Error("两次输入的密码不一致"));
        },
        trigger: "blur"
      }
    ]
  };
}
