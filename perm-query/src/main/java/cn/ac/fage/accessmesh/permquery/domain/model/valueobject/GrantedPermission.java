package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

/**
 * 权限条目值对象
 * <p>
 * 表示单个已授予的权限配置，包含资源、操作、条件等完整信息。
 * 调用方可以根据这些信息自行判断权限结果。
 * </p>
 */
public record GrantedPermission(
    Long permissionId,
    Long roleId,
    Integer resourceType,
    Long resourceEntityId,
    long grantedBits,
    String operationCode,
    long effectiveBits,
    String grantSource,
    boolean canGrant,
    Long conditionId,
    Long dependOn,
    Boolean conditionMet,
    boolean hasConflict
) {

    /**
     * 判断是否为类型级权限（scopeAll）
     */
    public boolean isScopeAll() {
        return resourceEntityId == null;
    }

    /**
     * 判断是否匹配指定目标位
     */
    public boolean matchesBit(long targetBit) {
        return (grantedBits & targetBit) != 0;
    }

    /**
     * 判断是否匹配指定目标位掩码
     */
    public boolean matchesBit(BitMask target) {
        return (grantedBits & target.raw()) != 0;
    }

    /**
     * 判断是否有条件约束
     */
    public boolean hasCondition() {
        return conditionId != null;
    }

    /**
     * 判断条件是否满足（已评估）
     */
    public boolean isConditionSatisfied() {
        return conditionId == null || Boolean.TRUE.equals(conditionMet);
    }

    /**
     * 判断是否可授权他人
     */
    public boolean isGrantable() {
        return canGrant;
    }

    /**
     * 判断是否有依赖权限
     */
    public boolean hasDependOn() {
        return dependOn != null;
    }

    /**
     * 判断是否有冲突标记
     */
    public boolean isInConflict() {
        return hasConflict;
    }

    /**
     * 判断权限是否有效（条件满足 + 无冲突）
     */
    public boolean isEffective() {
        return isConditionSatisfied() && !hasConflict;
    }

    // ===== Builder 方法（用于填充评估结果）=====

    /**
     * 设置条件评估结果
     */
    public GrantedPermission withConditionMet(Boolean met) {
        return new GrantedPermission(
            permissionId, roleId, resourceType, resourceEntityId,
            grantedBits, operationCode, effectiveBits, grantSource,
            canGrant, conditionId, dependOn,
            met, hasConflict
        );
    }

    /**
     * 设置冲突标记
     */
    public GrantedPermission withConflict(boolean conflict) {
        return new GrantedPermission(
            permissionId, roleId, resourceType, resourceEntityId,
            grantedBits, operationCode, effectiveBits, grantSource,
            canGrant, conditionId, dependOn,
            conditionMet, conflict
        );
    }

    /**
     * 填充操作编码和有效位（从 OperationPermission 反查）
     */
    public GrantedPermission withOperationInfo(String opCode, long effBits) {
        return new GrantedPermission(
            permissionId, roleId, resourceType, resourceEntityId,
            grantedBits, opCode, effBits, grantSource,
            canGrant, conditionId, dependOn,
            conditionMet, hasConflict
        );
    }
}