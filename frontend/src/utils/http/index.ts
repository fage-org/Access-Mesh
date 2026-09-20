import Axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type CustomParamsSerializer
} from "axios";
import type {
  PureHttpError,
  RequestMethods,
  PureHttpResponse,
  PureHttpRequestConfig
} from "./types.d";
import { stringify } from "qs";
import { getToken, formatToken, isSessionTerminated } from "@/utils/auth";
import { useUserStoreHook } from "@/store/modules/user";
import { refreshSessionCapability } from "@/router/utils";
import {
  terminateLocalSession,
  notifySessionExpiredOnce
} from "@/utils/session-expired";

// 相关配置请参考：www.axios-js.com/zh-cn/docs/#axios-request-config-1
const defaultConfig: AxiosRequestConfig = {
  // 请求超时时间
  timeout: 10000,
  headers: {
    Accept: "application/json, text/plain, */*",
    "Content-Type": "application/json",
    "X-Requested-With": "XMLHttpRequest"
  },
  // 数组格式参数序列化（https://github.com/axios/axios/issues/5142）
  paramsSerializer: {
    serialize: stringify as unknown as CustomParamsSerializer
  }
};

/** 403 触发能力刷新的去重窗口（T-FE-048，2026-09-20 用户拍板 10s）：窗口起算于触发时刻 */
export const CAPABILITY_REFRESH_403_WINDOW_MS = 10_000;

/** 窗口截止时刻与在途标记（模块级单例；手动入口与授予页重试直连能力刷新入口，不经本通道） */
let capabilityRefreshWindowUntil = 0;
let capabilityRefreshInFlight: Promise<void> | null = null;

/**
 * 403 触发会话能力刷新（T-FE-048）：被授权人会话侧「下一个动作即自愈」——管理员
 * 授权发生在管理员自己的浏览器，刷新机制必须落在被授权人会话侧。403 ≠ 必然权限
 * 变更（可能是配错/越权访问），刷新无害（多一次 user-menu 请求），按钮显隐/侧栏
 * 以最新事实为准。
 * <ul>
 *   <li>窗口去重：短时间内多次 403 只刷一次（窗口内在途双保险）</li>
 *   <li>刷新失败静默：按钮/侧栏维持旧态、门禁状态机维持 loaded（failed 仅由首次
 *       加载失败产生，与 T-FE-056 状态迁移表同 owner 一致；后端 fail-closed 兜底）</li>
 *   <li>不自动重放原请求（防循环，用户重新点击即可）</li>
 *   <li>排除 user-menu 自身：刷新入口即该请求，其 403 下重发无自愈可能</li>
 * </ul>
 */
function triggerCapabilityRefreshOn403(url: string | undefined) {
  if (url?.endsWith("/api/access/auth/user-menu")) return;
  const now = Date.now();
  if (capabilityRefreshInFlight || now < capabilityRefreshWindowUntil) return;
  capabilityRefreshWindowUntil = now + CAPABILITY_REFRESH_403_WINDOW_MS;
  capabilityRefreshInFlight = refreshSessionCapability()
    .catch((err: unknown) => {
      console.warn("[http] 403 触发的会话能力刷新失败，维持旧态", err);
    })
    .finally(() => {
      capabilityRefreshInFlight = null;
    });
}

class PureHttp {
  constructor() {
    this.httpInterceptorsRequest();
    this.httpInterceptorsResponse();
  }

  /** 初始化配置对象 */
  private static initConfig: PureHttpRequestConfig = {};

  /** 保存当前`Axios`实例对象 */
  private static axiosInstance: AxiosInstance = Axios.create(defaultConfig);

  /** 请求拦截 */
  private httpInterceptorsRequest(): void {
    PureHttp.axiosInstance.interceptors.request.use(
      async (config: PureHttpRequestConfig): Promise<any> => {
        // 优先判断post/get等方法是否传入回调，否则执行初始化设置等回调
        if (typeof config.beforeRequestCallback === "function") {
          config.beforeRequestCallback(config);
          return config;
        }
        if (PureHttp.initConfig.beforeRequestCallback) {
          PureHttp.initConfig.beforeRequestCallback(config);
          return config;
        }
        /** 请求白名单，放置不经拦截器附加/判定`token`的接口（通过设置请求白名单，防止`token`过期后再请求造成的死循环问题）。
         *  logout 由调用方显式携带 Authorization 头（T-FE-045）：本端点入白名单是为跳过
         *  过期分支（过期时本分支会嵌套触发 logOut 并免头发请求，注销将空转），令牌仍照常送达服务端 */
        const whiteList = [
          "/api/access/auth/captcha",
          "/api/access/auth/login",
          "/api/access/auth/logout"
        ];
        return whiteList.some(url => config.url.endsWith(url))
          ? config
          : new Promise(resolve => {
              const data = getToken();
              if (isSessionTerminated(data)) {
                // 会话终结短路（T-FE-054，2026-09-20 拍板）：不再无令牌放行（原
                // T-FE-041 口径由 Gateway 401 兜底，多一次必然 401 的往返）。处置
                // 经 terminateLocalSession 三件套单源（提示+logOut+抛 SessionExpiredError
                // ——T-FE-056 复评 P3-1 统一；message 与提示同源，授予页
                // classifySaveError 按 name 识别为「未发出可重试」）；executor 内
                // throw 被 Promise 构造转为 reject，与原 reject 形态等价。判据单源
                // isSessionTerminated（无凭证 ∨ 本地 expires 已到期——cookie 被清、
                // userKey 残留形态同判终结）；无凭证形态同样短路——并发短路首个
                // logOut 同步清令牌后，后续请求不得无头放行再跑一次必然 401 的往返
                terminateLocalSession();
              } else {
                config.headers["Authorization"] = formatToken(data.accessToken);
                resolve(config);
              }
            });
      },
      error => {
        return Promise.reject(error);
      }
    );
  }

  /** 响应拦截 */
  private httpInterceptorsResponse(): void {
    const instance = PureHttp.axiosInstance;
    instance.interceptors.response.use(
      (response: PureHttpResponse) => {
        const $config = response.config;
        // 优先判断post/get等方法是否传入回调，否则执行初始化设置等回调
        if (typeof $config.beforeResponseCallback === "function") {
          $config.beforeResponseCallback(response);
          return response.data;
        }
        if (PureHttp.initConfig.beforeResponseCallback) {
          PureHttp.initConfig.beforeResponseCallback(response);
          return response.data;
        }
        return response.data;
      },
      (error: PureHttpError) => {
        const $error = error;
        $error.isCancelRequest = Axios.isCancel($error);
        // HTTP 401 统一窄处理（T-FE-041）：收到 401 即清除本地会话并跳转登录页；
        // 不增加 refresh-token、自动续期或重试体系。后端业务失败为 HTTP 200 + code≠200，
        // 不经过此分支。会话已过期提示（T-FE-054 双分支统一，2026-09-20 拍板）：
        // 服务端判定失效（30min 冻结/他端登出）与本地过期短路同语义同文案，共用去重
        // 窗口。**令牌仍在才提示**（claude 外评 P3 处置）：主动登出（logOut 已清令牌）
        // 后在途请求的 401 不弹「会话已过期」误导——退出意图是用户自己的动作
        if ($error?.response?.status === 401) {
          if (getToken()) notifySessionExpiredOnce();
          useUserStoreHook().logOut();
        }
        // 403 触发会话权限热刷新（T-FE-048）：窗口去重 + 失败静默 + 不重放原请求；
        // 错误本身仍原样 reject 由页面层处理展示。503 等其余状态码由页面层自行处理
        if ($error?.response?.status === 403) {
          triggerCapabilityRefreshOn403($error?.config?.url);
        }
        // 所有的响应异常 区分来源为取消请求/非取消请求
        return Promise.reject($error);
      }
    );
  }

  /** 通用请求工具函数 */
  public request<T>(
    method: RequestMethods,
    url: string,
    param?: AxiosRequestConfig,
    axiosConfig?: PureHttpRequestConfig
  ): Promise<T> {
    const config = {
      method,
      url,
      ...param,
      ...axiosConfig
    } as PureHttpRequestConfig;

    // 单独处理自定义请求/响应回调
    return new Promise((resolve, reject) => {
      PureHttp.axiosInstance
        .request(config)
        .then((response: undefined) => {
          resolve(response);
        })
        .catch(error => {
          reject(error);
        });
    });
  }

  /** 单独抽离的`post`工具函数 */
  public post<T, P>(
    url: string,
    params?: AxiosRequestConfig<P>,
    config?: PureHttpRequestConfig
  ): Promise<T> {
    return this.request<T>("post", url, params, config);
  }

  /** 单独抽离的`get`工具函数 */
  public get<T, P>(
    url: string,
    params?: AxiosRequestConfig<P>,
    config?: PureHttpRequestConfig
  ): Promise<T> {
    return this.request<T>("get", url, params, config);
  }
}

export const http = new PureHttp();
