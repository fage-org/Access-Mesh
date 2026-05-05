package cn.ac.fage.accessmesh.permission.dto.query;

import java.util.*;

/**
 * Unified permission query input.
 *
 * <p>All query scenarios (check / batchCheck / checkInterface / queryResources /
 * queryScopes / canGrant / view) use this single DTO.
 * Callers use one of the 8 static factory methods; custom fields can be set after creation.
 */
public class PermQuery {

    // ── who ──
    private final Long tenantId;
    private Long userId;
    private Set<Long> roleIds;
    private Long bizDomainId;

    // ── what resource ──
    private Set<String> resourceTypeCodes;
    private Set<String> resourceCodes;
    private String codeType;
    private Set<Long> resourceEntityIds;
    private String inheritMode;

    // ── what operation ──
    private Set<String> operationCodes;
    private Set<Long> operationPermissionIds;

    // ── query behaviour ──
    private boolean queryScopeAll = true;
    private boolean queryInstance = true;
    private boolean earlyReturnOnScopeAll = true;
    private boolean inheritParents;
    private boolean inheritChildren;
    private boolean evaluateConditions = true;
    private boolean evaluateConflicts = true;
    private boolean evaluateMatchesBit = true;

    // ── return content ──
    private boolean includeResources;
    private boolean includeOperations;
    private boolean includeRoles;
    private boolean includeDomains;
    private boolean includeConditions;

    // ── cache ──
    private boolean useRoleCache = true;

    // ── context ──
    private Map<String, Object> context;

    private PermQuery(Long tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    // ===== 8 factory methods =====

    /** Single permission check. Type+instance, early-return on scopeAll, full evaluation, minimal output. */
    public static PermQuery forAuthCheck(Long tenantId, Long userId,
                                          String resourceTypeCode, String resourceCode,
                                          String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        return q;
    }

    /** Batch permission check. Same defaults as forAuthCheck but accepts multiple resource codes. */
    public static List<PermQuery> forBatchCheck(Long tenantId, Long userId,
                                                 String resourceTypeCode,
                                                 List<String> resourceCodes,
                                                 String operationCode) {
        return resourceCodes.stream()
            .map(rc -> forAuthCheck(tenantId, userId, resourceTypeCode, rc, operationCode))
            .toList();
    }

    /** Interface permission check. Type-first, instance fallback, full evaluation, all ancillary info. */
    public static PermQuery forInterfaceCheck(Long tenantId, Long userId,
                                               Set<String> resourceTypeCodes,
                                               Set<Long> resourceEntityIds,
                                               String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.resourceEntityIds = resourceEntityIds;
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        q.includeDomains = true;
        q.includeConditions = true;
        return q;
    }

    /** Resource filtering. Instance-only, no condition evaluation, includes resource + operation info. */
    public static PermQuery forResourceQuery(Long tenantId, Long userId,
                                              Set<String> resourceTypeCodes,
                                              Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = false;
        q.queryInstance = true;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        return q;
    }

    /** Permission view queries. Instance-only, no evaluation, all ancillary info. */
    public static PermQuery forPermissionView(Long tenantId, Long userId,
                                               Set<Long> roleIds,
                                               Set<String> resourceTypeCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.roleIds = roleIds;
        q.resourceTypeCodes = resourceTypeCodes;
        q.queryScopeAll = false;
        q.queryInstance = true;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        q.includeDomains = true;
        q.includeConditions = true;
        return q;
    }

    /** canGrant authorization check. Type+instance, no shortcut, no evaluation, all ancillary info. */
    public static PermQuery forCanGrant(Long tenantId, Long operatorId,
                                         Set<String> resourceTypeCodes,
                                         Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = operatorId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = false;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        q.includeResources = true;
        q.includeOperations = true;
        return q;
    }

    /** Admin operation validation. Type+instance, early-return, no evaluation, minimal output. */
    public static PermQuery forValidate(Long tenantId, Long operatorId,
                                         String resourceTypeCode, String resourceCode,
                                         String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = operatorId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        return q;
    }

    /** Full query. No shortcut, all evaluation, all ancillary info. */
    public static PermQuery forFullQuery(Long tenantId, Long userId,
                                          Set<String> resourceTypeCodes,
                                          Set<String> resourceCodes,
                                          Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.resourceCodes = resourceCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = false;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        q.includeDomains = true;
        q.includeConditions = true;
        return q;
    }

    /** Scope query. Type+instance both, no shortcut, no evaluation, no matchesBit, all ancillary info. */
    public static PermQuery forScopeQuery(Long tenantId, Long userId,
                                           Set<String> resourceTypeCodes,
                                           Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = false;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        return q;
    }

    // ===== getters / setters =====

    public Long tenantId() { return tenantId; }
    public Long userId() { return userId; }
    public Set<Long> roleIds() { return roleIds; }
    public Long bizDomainId() { return bizDomainId; }
    public Set<String> resourceTypeCodes() { return resourceTypeCodes; }
    public Set<String> resourceCodes() { return resourceCodes; }
    public String codeType() { return codeType; }
    public Set<Long> resourceEntityIds() { return resourceEntityIds; }
    public String inheritMode() { return inheritMode; }
    public Set<String> operationCodes() { return operationCodes; }
    public Set<Long> operationPermissionIds() { return operationPermissionIds; }
    public boolean queryScopeAll() { return queryScopeAll; }
    public boolean queryInstance() { return queryInstance; }
    public boolean earlyReturnOnScopeAll() { return earlyReturnOnScopeAll; }
    public boolean inheritParents() { return inheritParents; }
    public boolean inheritChildren() { return inheritChildren; }
    public boolean evaluateConditions() { return evaluateConditions; }
    public boolean evaluateConflicts() { return evaluateConflicts; }
    public boolean evaluateMatchesBit() { return evaluateMatchesBit; }
    public boolean includeResources() { return includeResources; }
    public boolean includeOperations() { return includeOperations; }
    public boolean includeRoles() { return includeRoles; }
    public boolean includeDomains() { return includeDomains; }
    public boolean includeConditions() { return includeConditions; }
    public boolean useRoleCache() { return useRoleCache; }
    public Map<String, Object> context() { return context; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setRoleIds(Set<Long> roleIds) { this.roleIds = roleIds; }
    public void setBizDomainId(Long bizDomainId) { this.bizDomainId = bizDomainId; }
    public void setResourceTypeCodes(Set<String> resourceTypeCodes) { this.resourceTypeCodes = resourceTypeCodes; }
    public void setResourceCodes(Set<String> resourceCodes) { this.resourceCodes = resourceCodes; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public void setResourceEntityIds(Set<Long> resourceEntityIds) { this.resourceEntityIds = resourceEntityIds; }
    public void setInheritMode(String inheritMode) { this.inheritMode = inheritMode; }
    public void setOperationCodes(Set<String> operationCodes) { this.operationCodes = operationCodes; }
    public void setOperationPermissionIds(Set<Long> operationPermissionIds) { this.operationPermissionIds = operationPermissionIds; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    // delegate-style setters for flags
    public void setQueryScopeAll(boolean v) { this.queryScopeAll = v; }
    public void setQueryInstance(boolean v) { this.queryInstance = v; }
    public void setEarlyReturnOnScopeAll(boolean v) { this.earlyReturnOnScopeAll = v; }
    public void setInheritParents(boolean v) { this.inheritParents = v; }
    public void setInheritChildren(boolean v) { this.inheritChildren = v; }
    public void setEvaluateConditions(boolean v) { this.evaluateConditions = v; }
    public void setEvaluateConflicts(boolean v) { this.evaluateConflicts = v; }
    public void setEvaluateMatchesBit(boolean v) { this.evaluateMatchesBit = v; }
    public void setIncludeResources(boolean v) { this.includeResources = v; }
    public void setIncludeOperations(boolean v) { this.includeOperations = v; }
    public void setIncludeRoles(boolean v) { this.includeRoles = v; }
    public void setIncludeDomains(boolean v) { this.includeDomains = v; }
    public void setIncludeConditions(boolean v) { this.includeConditions = v; }
    public void setUseRoleCache(boolean v) { this.useRoleCache = v; }
}
