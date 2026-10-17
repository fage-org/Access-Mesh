/** 去重后的请求目标结果；级联后代仅在被请求时列入明细。 */
export type BatchDeleteResp<T> = {
  deleted: T[];
  denied: T[];
  notFound: T[];
};
