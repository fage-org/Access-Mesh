import { getToken, setToken, removeToken } from "@/utils/auth";
import { refreshToken as refreshTokenApi } from "@/api/admin/oauth2";
import { router } from "@/router";
import { message } from "@/utils/message";
import {
  isTokenRefreshing,
  setTokenRefreshing
} from "@/utils/http/refreshLock";

/**
 * Token 定时刷新调度器
 * - 在 accessToken 有效期 50% 时触发刷新
 * - refreshToken 一次性使用,刷新后返回新的 refreshToken
 * - 刷新失败时清除 token 并跳转登录页
 * - 使用共享锁与 HTTP 拦截器协调,避免并发刷新
 */
class TokenRefreshScheduler {
  private timer: ReturnType<typeof setTimeout> | null = null;
  /** Token 有效期阈值 - 在 50% 时刷新 */
  private readonly REFRESH_THRESHOLD = 0.5;

  /**
   * 启动定时刷新
   */
  start() {
    // 清除旧定时器,避免多个定时器同时运行
    this.stop();

    const tokenData = getToken();
    if (!tokenData?.refreshToken) return;

    const expires = tokenData.expires;
    const now = Date.now();
    const remaining = expires - now;

    // Token 已过期,不启动调度器
    if (remaining <= 0) {
      this.stop();
      return;
    }

    // 计算刷新时间点 (有效期的一半)
    const refreshTime = remaining * this.REFRESH_THRESHOLD;

    // 设置定时器
    this.timer = setTimeout(async () => {
      await this.doRefresh();
    }, refreshTime);
  }

  /**
   * 执行刷新
   */
  private async doRefresh() {
    // 检查共享刷新锁,避免与 HTTP 拦截器并发刷新
    if (isTokenRefreshing()) {
      // 正在刷新,延迟后重新尝试
      this.timer = setTimeout(async () => {
        await this.doRefresh();
      }, 1000);
      return;
    }

    const tokenData = getToken();
    if (!tokenData?.refreshToken) {
      this.stop();
      return;
    }

    // 设置刷新锁
    setTokenRefreshing(true);

    try {
      const res = await refreshTokenApi(tokenData.refreshToken);

      if (res.code === 200 && res.data) {
        // 计算新的过期时间
        const newExpires = new Date(Date.now() + res.data.expires_in * 1000);

        // 存储新 Token (包含新的 refreshToken)
        setToken({
          accessToken: res.data.access_token,
          refreshToken: res.data.refresh_token,
          expires: newExpires,
          isOAuth2: tokenData.isOAuth2,
          username: tokenData.username,
          nickname: tokenData.nickname,
          avatar: tokenData.avatar,
          roles: tokenData.roles,
          permissions: tokenData.permissions,
          tenantId: tokenData.tenantId
        });

        // 递归启动下一次刷新
        this.start();
      } else {
        // 刷新失败
        this.handleRefreshFailure(
          `Token refresh returned code ${res.code}: ${res.message}`
        );
      }
    } catch (error) {
      this.handleRefreshFailure(error);
    } finally {
      // 释放刷新锁
      setTokenRefreshing(false);
    }
  }

  /**
   * 处理刷新失败
   */
  private handleRefreshFailure(error: unknown) {
    console.error("[TokenRefreshScheduler] Refresh failed:", error);
    message("会话已过期，请重新登录", { type: "warning" });
    this.stop();
    removeToken();
    router.push("/login");
  }

  /**
   * 停止调度器
   */
  stop() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}

// 单例导出
export const tokenRefreshScheduler = new TokenRefreshScheduler();

// 便捷函数
export function startTokenRefreshScheduler() {
  tokenRefreshScheduler.start();
}

export function stopTokenRefreshScheduler() {
  tokenRefreshScheduler.stop();
}
