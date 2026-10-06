import type PureHttp from "./types";
import type { http } from "./index";
import type { RequestMethods } from "./types";

/** 编译期回归锁，不执行请求；放开非 POST 或恢复 get 通道会导致未使用的 expect-error 报错。 */
export function verifyPostOnly(
  declared: PureHttp,
  implementation: typeof http
) {
  const post: RequestMethods = "post";
  declared.request(post, "/api/access/auth/login");
  // @ts-expect-error GET 不属于业务请求协议
  declared.request("get", "/api/access/auth/login");
  // @ts-expect-error PUT 不属于业务请求协议
  implementation.request("put", "/api/access/auth/login");
  // @ts-expect-error 配置参数不能覆盖为非 POST
  implementation.request("post", "/api/access/auth/login", { method: "get" });
  // @ts-expect-error 具名 GET 通道已退役
  declared.get("/api/access/auth/login");
  // @ts-expect-error 实现不得另留 GET 通道
  implementation.get("/api/access/auth/login");
}
