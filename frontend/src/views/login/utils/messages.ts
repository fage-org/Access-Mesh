export type LoginToast = {
  type: "success" | "warning";
  text: string;
};

/**
 * 登录成功后的提示语义（T-FE-049 半成功诚实提示，纯函数提取便于回归锁）：
 *  - 菜单/权限加载成功（menusCount>0）：绿色「登录成功」（既有行为）
 *  - 半成功——menus 为空且最近一次拉取失败（含 initRouter 隐式重试仍失败）：
 *    不弹 success，一条 warning 如实告知加载失败 + 指引侧栏占位项重试
 *  - 拉取成功但账号无菜单（零权限，如建号未配角色）：一条 warning 引导联系管理员
 * forceResetPwd 提示分支已删（T-FE-046，2026-09-19）：强制改密改为路由守卫
 * 阻断（登录后只放行改密页，见 router/index.ts forceResetAllowPaths），登录页
 * 不再弹「请联系管理员重置」warning——本函数是登录提示语义的唯一出口，勿散落组件内。
 */
export function resolveLoginMessages(input: {
  menusCount: number;
  menuLoadFailed: boolean | undefined;
}): LoginToast[] {
  const toasts: LoginToast[] = [];
  if (input.menusCount > 0) {
    toasts.push({ type: "success", text: "登录成功" });
  } else if (input.menuLoadFailed) {
    toasts.push({
      type: "warning",
      // 「进入系统后」限定（claude 外评 P3）：强制改密阻断人群登录后被拦在
      // /change-password 全屏页（无侧栏），指引动作在改密进入系统后才可达
      text: "登录成功，但菜单与权限加载失败，进入系统后请点击侧栏占位项重试"
    });
  } else {
    toasts.push({
      type: "warning",
      text: "登录成功，当前账号无可用菜单，请联系管理员分配权限"
    });
  }
  return toasts;
}
