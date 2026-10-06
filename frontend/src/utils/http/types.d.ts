import type { AxiosError, AxiosResponse, AxiosRequestConfig } from "axios";

export type resultType = {
  accessToken?: string;
};

export type RequestMethods = "post";

export interface PureHttpError extends AxiosError {
  isCancelRequest?: boolean;
}

export interface PureHttpResponse extends AxiosResponse {
  config: PureHttpRequestConfig;
}

export interface PureHttpRequestConfig<D = any> extends AxiosRequestConfig<D> {
  beforeRequestCallback?: (request: PureHttpRequestConfig) => void;
  beforeResponseCallback?: (response: PureHttpResponse) => void;
}

/** 对外业务请求只允许 POST；Axios 拦截器仍使用其原生传输配置。 */
export type PostRequestConfig<D = any> = Omit<
  PureHttpRequestConfig<D>,
  "method"
> & { method?: RequestMethods };

export default class PureHttp {
  request<T>(
    method: RequestMethods,
    url: string,
    param?: PostRequestConfig,
    axiosConfig?: PostRequestConfig
  ): Promise<T>;
  post<T, P>(
    url: string,
    params?: PostRequestConfig<P>,
    config?: PostRequestConfig
  ): Promise<T>;
}
