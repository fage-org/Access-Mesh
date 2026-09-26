package cn.ac.fage.accessmesh.access.engine.query;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构校验（T-PERM-082，设计 §2.5/§2.6/§3.4）。
 * <p>
 * 合法组合表＋首版混批约束在执行前整体拒绝（零权限 I/O 抛 {@link QueryValidationException}）；
 * 格式正确但不存在的 type/operation/code 不在校验范围——那是运行时按 item 的普通未命中，
 * 不能让一个未知对象变成全批技术故障。空实例集合或解析失败永不自动升级为 TYPE_LEVEL。
 * </p>
 */
final class QueryRequestValidator {

    private QueryRequestValidator() {
    }

    static void validate(QueryRequest request) {
        if (request == null) {
            throw new QueryValidationException("请求为空");
        }
        if (request.tenantId() <= 0) {
            throw new QueryValidationException("tenantId 必须为正数: " + request.tenantId());
        }
        validateSubject(request.subject());
        if (request.context() == null) {
            throw new QueryValidationException("context 不能为空");
        }
        if (request.reads() == null) {
            throw new QueryValidationException("reads 不能为空");
        }
        validateItems(request);
    }

    private static void validateSubject(Subject subject) {
        if (subject == null) {
            throw new QueryValidationException("subject 不能为空");
        }
        if (subject instanceof User user && user.userId() <= 0) {
            throw new QueryValidationException("User.userId 必须为正数: " + user.userId());
        }
        if (subject instanceof Roles roles) {
            for (Long roleId : roles.roleIds()) {
                if (roleId <= 0) {
                    throw new QueryValidationException("Roles.roleIds 必须全为正数: " + roleId);
                }
            }
        }
    }

    private static void validateItems(QueryRequest request) {
        List<QueryItem> items = request.items();
        Set<String> keys = new HashSet<>();
        boolean hasGrantList = false;
        boolean hasAdmission = false;
        Set<ResultForm> admissionForms = EnumSet.noneOf(ResultForm.class);
        Set<ParentRequirement> parents = new HashSet<>();
        for (QueryItem item : items) {
            String key = item.key();
            if (key == null || key.isBlank()) {
                throw new QueryValidationException("item key 不能为空");
            }
            String at = "item[" + key + "]";
            if (!keys.add(key)) {
                throw new QueryValidationException("重复 item key: " + key);
            }
            if (item.selection() == null) {
                throw new QueryValidationException(at + " selection 不能为空");
            }
            if (item.evaluation() == null) {
                throw new QueryValidationException(at + " evaluation 不能为空");
            }
            if (item.resultForm() == null) {
                throw new QueryValidationException(at + " resultForm 不能为空");
            }
            if (item.output() == null) {
                throw new QueryValidationException(at + " output 不能为空");
            }
            validateSelection(at, item.selection());
            validateCombination(at, item);
            if (item.output().factDetail() == null) {
                throw new QueryValidationException(at + " OutputSpec.factDetail 不能为空");
            }
            for (TypeOperation extra : item.output().extraOperationKeys()) {
                validateTypeOperation(at + " extraOperationKeys", extra);
            }
            if (item.resultForm() == ResultForm.FACTS && item.output().factDetail() == FactDetail.NONE) {
                throw new QueryValidationException(at + " FACTS 结果的事实档至少为 KEPT");
            }
            if (item.selection() instanceof GrantList grantList) {
                hasGrantList = true;
                collectParent(at, grantList.requiredParent(), parents);
            } else if (item.selection() instanceof TargetSet targetSet && targetSet.parent() != null) {
                collectParent(at, targetSet.parent(), parents);
            } else if (item.selection() instanceof OperationAdmission) {
                hasAdmission = true;
                admissionForms.add(item.resultForm());
            }
        }
        if (hasGrantList && items.size() > 1) {
            throw new QueryValidationException("GRANT_LIST 单项独占请求（首版混批约束）");
        }
        if (hasAdmission) {
            for (QueryItem item : items) {
                if (!(item.selection() instanceof OperationAdmission)) {
                    throw new QueryValidationException(
                        "OPERATION_ADMISSION 首版不与普通目标/GRANT_LIST 混批: item[" + item.key() + "]");
                }
            }
            if (admissionForms.size() > 1) {
                throw new QueryValidationException(
                    "OPERATION_ADMISSION 同批必须使用同一结果形式: " + admissionForms);
            }
        }
        if (parents.size() > 1) {
            throw new QueryValidationException("父要求至多一个不同值（首版混批约束），实际不同值数: " + parents.size());
        }
    }

    private static void validateSelection(String at, Selection selection) {
        switch (selection) {
            case TypeLevel typeLevel -> {
                if (typeLevel.requirements().isEmpty()) {
                    throw new QueryValidationException(at + " TYPE_LEVEL requirements 不能为空");
                }
                for (TypeOperation requirement : typeLevel.requirements()) {
                    validateTypeOperation(at, requirement);
                }
            }
            case TargetSet targetSet -> {
                if (targetSet.clauses().isEmpty()) {
                    throw new QueryValidationException(at + " TARGET_SET clauses 不能为空");
                }
                if (targetSet.inheritance() == null) {
                    throw new QueryValidationException(at + " TARGET_SET inheritance 不能为空");
                }
                if (targetSet.typeFallback() == null) {
                    throw new QueryValidationException(at + " TARGET_SET typeFallback 不能为空");
                }
                for (TargetClause clause : targetSet.clauses()) {
                    validateTypeOperation(at, clause.operation());
                    validateResourceRef(at, clause.resource());
                }
                if (targetSet.parent() != null) {
                    validateParentRequirement(at, targetSet.parent());
                }
            }
            case GrantList grantList -> {
                if (grantList.requiredParent() != null) {
                    validateParentRequirement(at, grantList.requiredParent());
                }
            }
            case OperationAdmission admission -> validateTypeOperation(at, admission.requirement());
        }
    }

    /** 合法组合表（设计 §2.5）：非法组合零授权 I/O 整体拒绝。 */
    private static void validateCombination(String at, QueryItem item) {
        Selection selection = item.selection();
        Evaluation evaluation = item.evaluation();
        switch (item.resultForm()) {
            case DECISION -> {
                if (selection instanceof GrantList || selection instanceof OperationAdmission) {
                    throw new QueryValidationException(at + " DECISION 仅支持 TYPE_LEVEL/TARGET_SET 选择");
                }
                if (!Evaluation.full().equals(evaluation)) {
                    throw new QueryValidationException(at + " DECISION 固定 EVALUATE+ENFORCE，当前: " + evaluation);
                }
            }
            case ADMISSION -> {
                if (!(selection instanceof OperationAdmission)) {
                    throw new QueryValidationException(at + " ADMISSION 仅支持 OPERATION_ADMISSION 选择");
                }
                if (!Evaluation.evaluateSkip().equals(evaluation)) {
                    throw new QueryValidationException(at + " OPERATION_ADMISSION+ADMISSION 固定 EVALUATE+SKIP，当前: " + evaluation);
                }
            }
            case FACTS -> {
                if (selection instanceof OperationAdmission && !Evaluation.preserveSkip().equals(evaluation)) {
                    throw new QueryValidationException(at + " OPERATION_ADMISSION+FACTS 固定 PRESERVE+SKIP，当前: " + evaluation);
                }
            }
        }
    }

    private static void validateTypeOperation(String at, TypeOperation operation) {
        if (operation == null) {
            throw new QueryValidationException(at + " 类型-操作配对不能为空");
        }
        if (isBlank(operation.resourceTypeCode())) {
            throw new QueryValidationException(at + " resourceTypeCode 不能为空");
        }
        if (isBlank(operation.operationCode())) {
            throw new QueryValidationException(at + " operationCode 不能为空");
        }
    }

    private static void validateResourceRef(String at, ResourceRef resource) {
        if (resource == null) {
            throw new QueryValidationException(at + " 资源引用不能为空");
        }
        if (resource instanceof ByCode byCode && isBlank(byCode.code())) {
            throw new QueryValidationException(at + " ByCode.code 不能为空");
        }
        if (resource instanceof ByEntityId byEntityId && byEntityId.entityId() <= 0) {
            throw new QueryValidationException(at + " ByEntityId.entityId 必须为正数: " + byEntityId.entityId());
        }
    }

    private static void validateParentRequirement(String at, ParentRequirement parent) {
        if (isBlank(parent.resourceTypeCode())) {
            throw new QueryValidationException(at + " 父要求 resourceTypeCode 不能为空");
        }
        validateResourceRef(at, parent.resource());
        if (parent.operationCodes().isEmpty()) {
            throw new QueryValidationException(at + " 父操作集不能为空（空集不代表不限操作）");
        }
        for (String operationCode : parent.operationCodes()) {
            if (isBlank(operationCode)) {
                throw new QueryValidationException(at + " 父操作码不能为空");
            }
        }
    }

    private static void collectParent(String at, ParentRequirement parent, Set<ParentRequirement> parents) {
        if (parent != null) {
            validateParentRequirement(at, parent);
            parents.add(parent);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
