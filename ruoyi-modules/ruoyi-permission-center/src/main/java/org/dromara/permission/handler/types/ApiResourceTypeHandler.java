package org.dromara.permission.handler.types;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.ResourceTypeConstants;
import org.dromara.permission.handler.ResourceTypeHandler;
import org.dromara.permission.operation.custom.ApiGrantValidator;
import org.dromara.permission.operation.custom.ApiPermissionMatcher;
import org.dromara.permission.operation.custom.ApiSnapshotAssembler;
import org.dromara.permission.operation.ConditionEvaluator;
import org.dromara.permission.operation.ConflictDetector;
import org.dromara.permission.operation.DependencyChecker;
import org.dromara.permission.operation.GrantValidator;
import org.dromara.permission.operation.InheritanceExpander;
import org.dromara.permission.operation.PermissionMatcher;
import org.dromara.permission.operation.SnapshotAssembler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiResourceTypeHandler implements ResourceTypeHandler {

    @Qualifier("defaultInheritanceExpander")
    private final InheritanceExpander inheritanceExpander;
    @Qualifier("defaultConditionEvaluator")
    private final ConditionEvaluator conditionEvaluator;
    @Qualifier("defaultConflictDetector")
    private final ConflictDetector conflictDetector;
    @Qualifier("defaultDependencyChecker")
    private final DependencyChecker dependencyChecker;
    private final ApiPermissionMatcher apiPermissionMatcher;
    private final ApiGrantValidator apiGrantValidator;
    private final ApiSnapshotAssembler apiSnapshotAssembler;

    @Override
    public String getResourceType() {
        return ResourceTypeConstants.API;
    }

    @Override
    public PermissionMatcher getPermissionMatcher() {
        return apiPermissionMatcher;
    }

    @Override
    public InheritanceExpander getInheritanceExpander() {
        return inheritanceExpander;
    }

    @Override
    public ConditionEvaluator getConditionEvaluator() {
        return conditionEvaluator;
    }

    @Override
    public ConflictDetector getConflictDetector() {
        return conflictDetector;
    }

    @Override
    public DependencyChecker getDependencyChecker() {
        return dependencyChecker;
    }

    @Override
    public GrantValidator getGrantValidator() {
        return apiGrantValidator;
    }

    @Override
    public SnapshotAssembler getSnapshotAssembler() {
        return apiSnapshotAssembler;
    }
}
