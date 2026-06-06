import type { UserItem, UserRoleItem } from "@/api/user-manage";

export interface UserFormData {
  username: string;
  name: string;
  phone: string;
  email: string;
  orgId: number | null;
}

export type { UserItem, UserRoleItem };
