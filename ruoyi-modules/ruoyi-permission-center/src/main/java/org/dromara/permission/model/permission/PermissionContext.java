package org.dromara.permission.model.permission;

import lombok.Data;
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
        this.tenantId = tenantId;
        this.userId = userId;
        this.bizDomainId = bizDomainId;
        this.inheritMode = inheritMode == null ? InheritMode.NONE : inheritMode;
        this.evalContext = evalContext == null ? new HashMap<>() : new HashMap<>(evalContext);
    }
}
