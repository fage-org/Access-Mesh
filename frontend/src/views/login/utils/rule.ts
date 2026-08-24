import { reactive } from "vue";
import type { FormRules } from "element-plus";

/**
 * 登录校验（T-FE-041 评审修复：对齐后端契约）。
 * 密码仅做非空校验——登录不是设置密码的场景，长度/复杂度约束属改密流程
 * （后端 LoginReq.password 仅 @NotBlank，ResetPasswordReq 才有 8-32 位约束）；
 * 模板遗留的 8-18 位两类字符正则会拒绝后端合法密码，已删除。
 */
const loginRules = reactive<FormRules>({
  password: [
    {
      required: true,
      message: "请输入密码",
      trigger: "blur"
    }
  ],
  captchaCode: [
    {
      required: true,
      message: "请输入验证码",
      trigger: "blur"
    }
  ]
});

export { loginRules };
