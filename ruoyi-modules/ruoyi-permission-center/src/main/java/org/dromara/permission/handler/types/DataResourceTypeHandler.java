package org.dromara.permission.handler.types;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.ResourceTypeConstants;
import org.dromara.permission.handler.ResourceTypeHandler;
import org.dromara.permission.operation.custom.DataInheritanceExpander;
import org.dromara.permission.operation.custom.DataSnapshotAssembler;
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
public class DataResourceTypeHandler implements ResourceTypeHandler {

    @Qualifier("defaultPermissionMatcher")
    private final PermissionMatcher permissionMatcher;
    private final DataInheritanceExpander dataInheritanceExpander;
    @Qualifier("defaultConditionEvaluator")
    private final ConditionEvaluator conditionEvaluator;
    @Qualifier("defaultConflictDetector")
    private final ConflictDetector conflictDetector;
    @Qualifier("defaultDependencyChecker")
    private final DependencyChecker dependencyChecker;
    @Qualifier("defaultGrantValidator")
    private final GrantValidator grantValidator;
    private final DataSnapshotAssembler dataSnapshotAssembler;

    @Override
    public String getResourceType() {
        return ResourceTypeConstants.DATA;
    }

    @Override
    public PermissionMatcher getPermissionMatcher() {
        return permissionMatcher;
    }

    @Override
    public InheritanceExpander getInheritanceExpander() {
        return dataInheritanceExpander;
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
        return dataSnapshotAssembler;
    }
}
