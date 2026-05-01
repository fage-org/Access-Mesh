package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Result of conflict rule detection (detect-only, does not persist).
 */
public record ConflictDetectResp(
    boolean conflictDetected,
    List<ConflictRuleResp> matchedRules
) {}
