/**
 * 「服务与接口映射」页权限目录（SSOT）。
 *
 * 所有权限都锚定到 SERVICE 资源类型：服务台账使用类型级 VIEW/MANAGE，
 * 同步与手工 API 映射使用服务实例级的专用操作码。
 */
export const SERVICE_INTERFACE_PERMS = {
  /** 服务列表、详情和接口清单 */
  SERVICE_VIEW: "SERVICE:VIEW",
  /** 登记、编辑与删除服务 */
  SERVICE_MANAGE: "SERVICE:MANAGE",
  /** 以 FULL 清单同步某个服务的接口 */
  SERVICE_SYNC: "SERVICE:SYNC_INTERFACE",
  /** 手工创建、编辑、删除某个服务的 API 映射 */
  MAPPING_MANAGE: "SERVICE:MANAGE_API_MAPPING"
} as const;

export type ServiceInterfacePermKey = keyof typeof SERVICE_INTERFACE_PERMS;
export type ServiceInterfacePermValue =
  (typeof SERVICE_INTERFACE_PERMS)[ServiceInterfacePermKey];

export const SERVICE_INTERFACE_PERM_LIST: ReadonlyArray<ServiceInterfacePermValue> =
  Array.from(new Set(Object.values(SERVICE_INTERFACE_PERMS)));

export const SERVICE_INTERFACE_VIEW_PERMS: ReadonlyArray<ServiceInterfacePermValue> =
  [SERVICE_INTERFACE_PERMS.SERVICE_VIEW];
