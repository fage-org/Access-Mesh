package org.dromara.permission.handler;

import org.dromara.permission.operation.ConditionEvaluator;
import org.dromara.permission.operation.ConflictDetector;
import org.dromara.permission.operation.DependencyChecker;
import org.dromara.permission.operation.GrantValidator;
import org.dromara.permission.operation.InheritanceExpander;
import org.dromara.permission.operation.PermissionMatcher;
import org.dromara.permission.operation.SnapshotAssembler;

public interface ResourceTypeHandler {

    String getResourceType();

    PermissionMatcher getPermissionMatcher();

    InheritanceExpander getInheritanceExpander();

    ConditionEvaluator getConditionEvaluator();

    ConflictDetector getConflictDetector();

    DependencyChecker getDependencyChecker();

    GrantValidator getGrantValidator();

    SnapshotAssembler getSnapshotAssembler();
}
