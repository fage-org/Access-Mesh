/**
 * Token 刷新锁 - 用于调度器和 HTTP 拦截器之间的协调
 *
 * refreshToken 是一次性使用的,并发刷新会导致其中一个失败。
 * 此锁确保只有一个组件在同一时间进行刷新操作。
 */

let isRefreshing = false;

/** 检查是否正在刷新 Token */
export function isTokenRefreshing(): boolean {
  return isRefreshing;
}

/** 设置刷新锁状态 */
export function setTokenRefreshing(value: boolean): void {
  isRefreshing = value;
}
