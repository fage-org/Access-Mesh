package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;
import java.util.Set;
import java.util.Map;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.role.entity.AbstractRole;

/**
 * 结果可选块（T-PERM-082，设计 §3.3）。
 * <p>
 * 用 loadedSections 区分「没有请求」与「请求后为空」；不返回 RunState、
 * ORM 可变实体或缓存对象。拒绝项公开命中集保持空。
 * 阶段事实、描述与展示均为不可变快照，不返回 ORM 或缓存对象。
 * </p>
 *
 * @param loadedSections        本次装载的输出块集合；null 归一为空集
 * @param matchedRoleIds        命中角色 id；null 归一为空
 * @param matchedPermissionIds  命中权限 id；null 归一为空
 * @param stageFacts            按实际执行顺序保留的阶段事实；档位由 loadedSections 表达
 * @param descriptions          不可变描述与额外操作定义
 * @param effectiveOperations   评估后授权的有效操作投影
 * @param presentation          评估后授权的展示方向投影
 * @param parentCheck           已执行父判断的命中操作摘要；未执行时为空
 */
public record ResultDetails(Set<DetailSection> loadedSections, List<Long> matchedRoleIds,
                            List<Long> matchedPermissionIds, List<StageFacts> stageFacts,
                            Descriptions descriptions, List<EffectiveOperationEntry> effectiveOperations,
                            List<PresentationEntry> presentation, ParentCheckSummary parentCheck) {

    public ResultDetails {
        loadedSections = loadedSections == null ? Set.of() : Set.copyOf(loadedSections);
        matchedRoleIds = matchedRoleIds == null ? List.of() : List.copyOf(matchedRoleIds);
        matchedPermissionIds = matchedPermissionIds == null ? List.of() : List.copyOf(matchedPermissionIds);
        stageFacts = stageFacts == null ? List.of() : List.copyOf(stageFacts);
        descriptions = descriptions == null ? Descriptions.empty() : descriptions;
        effectiveOperations = effectiveOperations == null ? List.of() : List.copyOf(effectiveOperations);
        presentation = presentation == null ? List.of() : List.copyOf(presentation);
        parentCheck = parentCheck == null ? ParentCheckSummary.empty() : parentCheck;
    }

    public ResultDetails(Set<DetailSection> sections, List<Long> roles, List<Long> permissions, List<StageFacts> facts) {
        this(sections, roles, permissions, facts, Descriptions.empty(), List.of(), List.of(), ParentCheckSummary.empty());
    }

    public ResultDetails(Set<DetailSection> loadedSections, List<Long> matchedRoleIds, List<Long> matchedPermissionIds) {
        this(loadedSections, matchedRoleIds, matchedPermissionIds, List.of());
    }

    /** 空详情（拒绝/短路项缺省形态）。 */
    public static ResultDetails empty() {
        return new ResultDetails(Set.of(), List.of(), List.of());
    }

    /** 父要求中实际命中的操作码；不包含父权限 ID，不重新执行父判断。 */
    public record ParentCheckSummary(List<String> matchedOperationCodes) {
        public ParentCheckSummary { matchedOperationCodes = List.copyOf(matchedOperationCodes); }
        static ParentCheckSummary empty() { return new ParentCheckSummary(List.of()); }
    }

    /** raw 超集的描述与额外要求的操作定义；未找到的要求不在 requestedOperations 中。 */
    public record Descriptions(Map<Long, ResourceDescription> resources, Map<Long, RoleDescription> roles,
                               Map<Long, OperationDefinition> operations,
                               Map<TypeOperation, OperationDefinition> requestedOperations) {
        public Descriptions {
            resources = Map.copyOf(resources); roles = Map.copyOf(roles);
            operations = Map.copyOf(operations); requestedOperations = Map.copyOf(requestedOperations);
        }
        static Descriptions empty() { return new Descriptions(Map.of(), Map.of(), Map.of(), Map.of()); }
    }

    public record ResourceDescription(Long id, Integer resourceType, String code, String codeType, String name,
                                      Long parentId, String path, Integer status, String ownerServiceCode,
                                      String maintainSource, String extra) {
        static ResourceDescription from(ResourceEntity row) {
            return new ResourceDescription(row.getId(), row.getResourceType(), row.getCode(), row.getCodeType(),
                row.getName(), row.getParentId(), row.getPath(), row.getStatus(), row.getOwnerServiceCode(),
                row.getMaintainSource(), row.getExtra());
        }
    }

    public record RoleDescription(Long id, Integer roleType, String externalId, String name) {
        static RoleDescription from(AbstractRole row) {
            return new RoleDescription(row.getId(), row.getRoleType(), row.getExternalId(), row.getName());
        }
    }

    /** 覆盖操作保留真实授权引用；不把不同角色/条件/父绑定的授权合成一行。 */
    public record EffectiveOperationEntry(Long sourcePermissionId, Long sourceRoleId, Long displayedEntityId,
                                          PresentationEntry.Derivation derivation, Integer resourceType,
                                          Long grantedBits, String grantedOperationCode, long effectiveBits,
                                          String operationCode, Long operationBinaryBit) {}

    /** 输出块（与 OutputSpec 请求面对齐）。 */
    public enum DetailSection {

        /** 命中 ID 块。 */
        MATCHED_IDS,

        /** 已执行的父判断摘要；没有该块时见 coverage.parentCheck 的未触发状态。 */
        PARENT_CHECK,

        /** 评估后事实块。 */
        FACTS_KEPT,

        /** 上下文绑定后原始事实块。 */
        FACTS_RAW,

        /** 描述块。 */
        DESCRIPTIONS,

        /** 有效操作展开块。 */
        EFFECTIVE_OPERATIONS,

        /** 展示父/子展开块。 */
        PRESENTATION,

        /** TRACE 块（仅受权诊断）。 */
        TRACE
    }
}
