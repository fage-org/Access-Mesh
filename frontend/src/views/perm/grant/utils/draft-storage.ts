import { storageLocal } from "@pureadmin/utils";
import { userKey, type DataInfo } from "@/utils/auth";
import { FIXED_TENANT_ID } from "@/api/auth";
import type { DraftChange, GrantContext } from "./types";

export type DraftOwner = { tenantId: string; userId: string };
export type SavedGrantDraft = {
  version: 1;
  changes: DraftChange[];
  unknownOutcome: boolean;
};

export function readDraftOwner(): DraftOwner | null {
  try {
    const user = storageLocal().getItem<DataInfo<number>>(userKey);
    if (user?.userId == null) return null;
    return {
      userId: String(user.userId),
      tenantId: String(user.tenantId ?? FIXED_TENANT_ID)
    };
  } catch {
    return null;
  }
}

export function draftKey(
  owner: DraftOwner,
  context: Pick<GrantContext, "roleTypeCode" | "roleExternalId">,
  resourceTypeCode: string
): string {
  return (
    "permission-grant:draft:" +
    JSON.stringify([
      owner.tenantId,
      owner.userId,
      context.roleTypeCode,
      context.roleExternalId,
      resourceTypeCode
    ])
  );
}

export function readDraft(
  storage: Storage,
  key: string
): SavedGrantDraft | null {
  try {
    const draft = JSON.parse(storage.getItem(key) ?? "null");
    if (
      draft?.version !== 1 ||
      !Array.isArray(draft.changes) ||
      !draft.changes.length
    )
      return null;
    if (
      !draft.changes.every(
        (c: DraftChange) =>
          c &&
          typeof c.changeId === "string" &&
          c.summary &&
          (c.kind === "add"
            ? c.recordKey
            : c.kind === "update"
              ? c.before && c.after
              : c.kind === "remove"
                ? Array.isArray(c.records)
                : c.kind === "replace" &&
                  c.newKey &&
                  Array.isArray(c.removedRecords))
      )
    )
      return null;
    return draft;
  } catch {
    return null;
  }
}

export function writeDraft(
  storage: Storage,
  key: string,
  changes: DraftChange[],
  unknownOutcome: boolean
): boolean {
  try {
    storage.setItem(
      key,
      JSON.stringify({
        version: 1,
        changes,
        unknownOutcome
      })
    );
    return true;
  } catch {
    return false;
  }
}

export function removeDraft(storage: Storage, key: string) {
  try {
    storage.removeItem(key);
  } catch {
    /* 禁用存储时保持页面可用。 */
  }
}
