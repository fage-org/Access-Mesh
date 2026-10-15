import { reactive } from "vue";
import { message } from "@/utils/message";

export interface ResetPasswordTarget {
  id: number;
  name: string;
}

export interface ResetPasswordFlowOptions {
  /** 确认框：resolve=确认提交；reject（含 "cancel"）=放弃 */
  confirmReset: (target: ResetPasswordTarget) => Promise<unknown>;
  /** 重置请求 */
  requestReset: (userId: number) => Promise<{ newPassword: string }>;
  /** 新密码交付弹窗 */
  deliverPassword: (password: string) => Promise<unknown>;
  /** 确认框期间上下文核对（T-FE-059 既有守卫）：选中组织 getter */
  currentOrgId: () => unknown;
  /** 上下文漂移提示（确认后核对不一致时） */
  notifyContextDrift: () => void;
}

export interface ResetPasswordFlow {
  /** 同用户重置在途登记：按钮 disabled 与 handler 守卫同源（reactive Set，渲染直接可读） */
  isPending: (userId: number) => boolean;
  /** 重置密码入口：同用户互斥 + 上下文核对 + 一次性交付 */
  reset: (target: ResetPasswordTarget) => Promise<void>;
}

/**
 * 成员表「重置密码」入口流程（T-FE-068 自 MemberTab 抽出）：
 * 确认 → 上下文核对（T-FE-059）→ 请求 → 新密码一次性交付。
 *
 * 同用户互斥（探针实证：确认→请求→弹密码全程无防重入，两请求并发写服务端时
 * 迟到响应交付已失效密码）：pending 覆盖整条流程（入口登记/finally 复位），
 * 在途期间同用户再触发入口即拒——同 tab 内任一时刻至多一条在途请求，其响应
 * 必为该用户最新凭据。跨标签页/跨操作员形态超出前端可检测范围（服务端响应
 * 不含代际标识），登记 Q 待盘点。
 */
export function createResetPasswordFlow(
  options: ResetPasswordFlowOptions
): ResetPasswordFlow {
  const pendingUserIds = reactive(new Set<number>());

  function isPending(userId: number) {
    return pendingUserIds.has(userId);
  }

  async function reset(target: ResetPasswordTarget) {
    if (pendingUserIds.has(target.id)) {
      message("该用户的密码重置正在进行中，请稍候", { type: "warning" });
      return;
    }
    // 入口捕获当前选中组织（T-FE-059）：确认框期间上下文可能已变，提交前核对
    const openedAtOrgId = options.currentOrgId();
    pendingUserIds.add(target.id);
    try {
      await options.confirmReset(target);
      if (!Object.is(openedAtOrgId, options.currentOrgId())) {
        options.notifyContextDrift();
        return;
      }
      const result = await options.requestReset(target.id);
      await options.deliverPassword(result.newPassword);
    } catch (e) {
      if (e !== "cancel") {
        message("重置密码失败", { type: "error" });
      }
    } finally {
      pendingUserIds.delete(target.id);
    }
  }

  return { isPending, reset };
}
