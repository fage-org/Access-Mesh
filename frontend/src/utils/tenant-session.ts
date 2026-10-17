import { createSessionBoundary } from "@/utils/session-boundary";
export type {
  SessionSnapshot as TenantSessionSnapshot,
  SessionRequestStamp as TenantRequestStamp
} from "@/utils/session-boundary";
export {
  SessionChangedError as TenantSessionChangedError,
  reloadSessionPage as reloadTenantSession
} from "@/utils/session-boundary";
const tenant = createSessionBoundary(true);
export const tenantSessionBlocked = tenant.blocked;
export const assertTenantSessionCurrent = tenant.assertCurrent;
export const captureTenantRequest = tenant.captureRequest;
export const assertTenantRequest = tenant.assertRequest;
export const acceptLocalTenantSession = tenant.acceptLocal;
export const startTenantSessionSync = tenant.start;
