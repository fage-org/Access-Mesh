import { createSessionBoundary } from "@/utils/session-boundary";
const platform = createSessionBoundary();
export const platformSessionBlocked = platform.blocked;
export const blockPlatformSession = platform.block;
export const assertPlatformSessionCurrent = platform.assertCurrent;
export const capturePlatformRequest = platform.captureRequest;
export const assertPlatformRequest = platform.assertRequest;
export const acceptLocalPlatformSession = platform.acceptLocal;
export const startPlatformSessionSync = platform.start;
