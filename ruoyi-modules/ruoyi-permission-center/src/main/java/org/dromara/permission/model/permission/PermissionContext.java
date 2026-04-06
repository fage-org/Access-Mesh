package org.dromara.permission.model.permission;

import lombok.Data;
import org.dromara.permission.condition.PermissionConditionContextSupport;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.operation.ConditionEvaluator;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class PermissionContext {
    private final Long tenantId;
    private final Long userId;
    private final Long bizDomainId;
    private final InheritMode inheritMode;
    private final Map<String, Object> baseEvalContext;
    private final Map<String, Object> evalContext;

    private List<ResolvedRole> roles = new ArrayList<>();
    private PcResourceEntity resource;
    private PcOperationPermission operation;
    private Map<Long, PcResourceEntity> resources = new HashMap<>();
    private Map<Long, PcOperationPermission> operations = new HashMap<>();
    private Map<Long, PcPermissionCondition> conditions = new HashMap<>();
    private Set<Long> expandedResourceIds = new HashSet<>();
    private List<MatchedPermission> matchedPermissions = new ArrayList<>();
    private List<ConflictDetail> detectedConflicts = new ArrayList<>();
    private Function<Integer, ConditionEvaluator> conditionEvaluatorResolver;
    private String action;
    private String requestId;
    private String changeSource;
    private String versionRemark;

    public PermissionContext(Long tenantId, Long userId, Long bizDomainId, InheritMode inheritMode, Map<String, Object> evalContext) {
        this(tenantId, userId, bizDomainId, inheritMode, evalContext, null);
    }

    public PermissionContext(Long tenantId, Long userId, Long bizDomainId, InheritMode inheritMode,
                             Map<String, Object> evalContext, Map<String, Object> trustedContext) {
        Map<String, Object> normalizedContext = new HashMap<>(PermissionConditionContextSupport.normalizeBaseContext(
            tenantId, userId, bizDomainId, evalContext, trustedContext));
        this.tenantId = tenantId;
        this.userId = userId;
        this.bizDomainId = bizDomainId;
        this.inheritMode = inheritMode == null ? InheritMode.NONE : inheritMode;
        this.baseEvalContext = normalizedContext;
        this.evalContext = new HashMap<>(normalizedContext);
    }

    private PermissionContext(Long tenantId, Long userId, Long bizDomainId, InheritMode inheritMode,
                              Map<String, Object> baseEvalContext, Map<String, Object> evalContext,
                              boolean normalized) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.bizDomainId = bizDomainId;
        this.inheritMode = inheritMode == null ? InheritMode.NONE : inheritMode;
        this.baseEvalContext = baseEvalContext;
        this.evalContext = evalContext;
    }

    public void bindResourceContext() {
        this.evalContext.clear();
        this.evalContext.putAll(this.baseEvalContext);
        PermissionConditionContextSupport.bindResourceContext(this.evalContext, this.resource, this.operation);
    }

    public PermissionContext scopedFor(PcResourceEntity resource, PcOperationPermission operation) {
        PermissionContext scoped = new PermissionContext(
            this.tenantId,
            this.userId,
            this.bizDomainId,
            this.inheritMode,
            new HashMap<>(this.baseEvalContext),
            new HashMap<>(this.baseEvalContext),
            true
        );
        scoped.setRoles(this.roles);
        scoped.setResources(this.resources);
        scoped.setOperations(this.operations);
        scoped.setConditions(this.conditions);
        scoped.setExpandedResourceIds(this.expandedResourceIds);
        scoped.setMatchedPermissions(this.matchedPermissions);
        scoped.setDetectedConflicts(this.detectedConflicts);
        scoped.setConditionEvaluatorResolver(this.conditionEvaluatorResolver);
        scoped.setAction(this.action);
        scoped.setRequestId(this.requestId);
        scoped.setChangeSource(this.changeSource);
        scoped.setVersionRemark(this.versionRemark);
        scoped.setResource(resource);
        scoped.setOperation(operation);
        scoped.bindResourceContext();
        return scoped;
    }
}
