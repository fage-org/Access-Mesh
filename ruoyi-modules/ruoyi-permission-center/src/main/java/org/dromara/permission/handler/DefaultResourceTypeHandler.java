package org.dromara.permission.handler;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.operation.ConditionEvaluator;
import org.dromara.permission.operation.ConflictDetector;
import org.dromara.permission.operation.DependencyChecker;
import org.dromara.permission.operation.GrantValidator;
import org.dromara.permission.operation.InheritanceExpander;
import org.dromara.permission.operation.PermissionMatcher;
import org.dromara.permission.operation.SnapshotAssembler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultResourceTypeHandler implements ResourceTypeHandler {

    private final PermissionMatcher permissionMatcher;
    private final InheritanceExpander inheritanceExpander;
    private final ConditionEvaluator conditionEvaluator;
    private final ConflictDetector conflictDetector;
    private final DependencyChecker dependencyChecker;
    private final GrantValidator grantValidator;
    private final SnapshotAssembler snapshotAssembler;

    @Override
    public String getResourceType() {
        return null;
    }

    @Override
    public PermissionMatcher getPermissionMatcher() {
        return permissionMatcher;
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
        return grantValidator;
    }

    @Override
    public SnapshotAssembler getSnapshotAssembler() {
        return snapshotAssembler;
    }
}
