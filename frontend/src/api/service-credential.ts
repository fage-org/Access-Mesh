import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";
import type { ItemsResp } from "./role-manage";

export type ServiceCredential = {
  id: number;
  credentialId: string;
  serviceCode: string;
  status: number;
  rotatedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
};
export type IssuedCredential = {
  id: number;
  credentialId: string;
  secret: string;
  serviceCode: string;
  status: number;
  expiresAt: string | null;
};
export type ServiceCredentialCreateReq = {
  serviceCode: string;
  expiresAt?: string | null;
};
export type ServiceCredentialUpdateReq = {
  id: number;
  status?: number;
  expiresAt?: string;
};

export const listServiceCredentials = async (serviceCode?: string) =>
  unwrap(
    await http.request<R<ItemsResp<ServiceCredential>>>(
      "post",
      "/api/access/service-credential/list",
      { data: { serviceCode } }
    )
  );
export const issueServiceCredential = async (
  data: ServiceCredentialCreateReq
) =>
  unwrap(
    await http.request<R<IssuedCredential>>(
      "post",
      "/api/access/service-credential/create",
      { data }
    )
  );
export const updateServiceCredential = async (
  data: ServiceCredentialUpdateReq
) =>
  unwrap(
    await http.request<R<ServiceCredential>>(
      "post",
      "/api/access/service-credential/update",
      { data }
    )
  );
export const removeServiceCredential = async (id: number) =>
  unwrap(
    await http.request<R<void>>(
      "post",
      "/api/access/service-credential/remove",
      { data: { id } }
    )
  );
