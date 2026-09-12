package cn.ac.fage.accessmesh.access.permission.service.domain;

import java.util.Collection;
import java.util.Set;

/**
 * 类型授权根领域服务（T-PERM-062）：自定义 resource_type 首授基座的生命周期维护。
 * <p>
 * 自定义类型在租户内初始可转授行数为 0，apply-grant-plan 委托校验（严格无旁路）下
 * 无人能完成首笔授权——本服务在类型生命周期写路径（createType/createOperation/
 * 所有者变更）同事务落 AUTHORITY_ROOT 种子行，把固定图「建库时点自举」（API:ACCESS，
 * T-API-001）推广到「类型生命周期」。checkCanGrant 与通用授权链零改动（§14.1 红线维持）。
 * </p>
 * <p>
 * 所有者指针持久化于 {@code type_definition.extra.grantOriginRole}
 * （{"roleTypeCode":..,"roleExternalId":..}），缺省引导角色 BASIC_ROLE/bootstrap-admin
 * （与 BootstrapGraphDefinition.ADMIN_ROLE_* 同值；permission 域不反向依赖
 * application 包，取值一致性由 CustomResourceTypeSlicePgIT 行为锁钉住）。
 * </p>
 */
public interface GrantOriginDomainService {

    /** extra 内所有者指针键名 */
    String EXTRA_KEY_GRANT_ORIGIN_ROLE = "grantOriginRole";

    /** 缺省引导角色（类型未显式指定所有者时落点；与 BootstrapGraphDefinition.ADMIN_ROLE_* 同值） */
    String DEFAULT_OWNER_ROLE_TYPE_CODE = "BASIC_ROLE";
    String DEFAULT_OWNER_ROLE_EXTERNAL_ID = "bootstrap-admin";

    /**
     * 解析 extra 内所有者指针（仅解析结构，不解析角色行）。
     *
     * @param extraJson type_definition.extra，可选
     * @return 指针值；无指针返回 null
     * @throws cn.ac.fage.accessmesh.common.exception.BizException 指针结构非法（20044：非对象/
     *             字段缺失/空白/显式 null——fail-closed，不把坏指针静默按缺省处理）
     */
    GrantOriginRole parseGrantOriginPointer(String extraJson);

    /**
     * 解析所有者角色 id：指针缺省落引导角色；角色不存在（20001）/停用（20003）整单回滚。
     *
     * @param tenantId  租户ID
     * @param extraJson type_definition.extra，可选（无指针按缺省引导角色）
     * @return 所有者角色 id
     */
    Long resolveOwnerRoleId(Long tenantId, String extraJson);

    /**
     * createType 服务端注入所有者指针（typeKey=resource_type 专用）。
     * <p>
     * 指针是服务端管理键：客户端在 extra 内自带 grantOriginRole 一律拒绝
     * （合法输入通道是请求体 ownerRoleTypeCode/ownerRoleExternalId 字段）。
     * </p>
     *
     * @param clientExtraJson 客户端 extra，可选（经所有权声明校验后的合法 JSON）
     * @param roleTypeCode    所有者角色类型编码
     * @param roleExternalId  所有者角色外部ID
     * @return 合并指针后的 extra JSON
     */
    String mergeGrantOriginPointer(String clientExtraJson, String roleTypeCode, String roleExternalId);

    /**
     * extra 是否携带 grantOriginRole 键（键存在性探测，非结构校验）。
     * <p>
     * createType 对任意 typeKey 统一拒绝客户端自带该键（服务端管理键不得经非 resource_type
     * 类型绕道入库成脏键）；坏 JSON 返回 false——存在性探测前置于调用方的 JSON 合法性校验，
     * 坏 JSON 由 {@code validateExtraDeclaration} 先行拒绝。
     * </p>
     *
     * @param extraJson 客户端 extra，可选
     * @return 携带该键返回 true
     */
    boolean hasGrantOriginPointerKey(String extraJson);

    /**
     * 落授权根种子行（幂等 insert-if-absent；同事务随类型生命周期写路径回滚）。
     *
     * @param tenantId      租户ID
     * @param ownerRoleId   所有者角色 id
     * @param typeValue     resource_type 内部类型值
     * @param operationBits 操作位集合（单 bit；AUTHORITY_ROOT 形状由 DDL CHECK 焊死）
     * @param operatorId    操作者ID（created_by）
     */
    void seedAuthorityRootGrants(Long tenantId, Long ownerRoleId, Integer typeValue,
                                 Collection<Long> operationBits, Long operatorId);

    /**
     * 所有者变更迁移（updateType 指针变更时同事务调用）：先清后种重整化——软删该类型
     * 全部 AUTHORITY_ROOT 行（含已删角色/误配旧 owner 的残留），再向新所有者补齐该类型
     * 全部有效操作位种子（用户定案 2026-09-12：变更=转移而非叠加，杜绝误配 owner 的一次性
     * 永久扩权——只补不迁时旧 owner 无产品内移除通道）。
     *
     * @param tenantId     租户ID
     * @param typeValue    resource_type 内部类型值
     * @param newOwnerRoleId 新所有者角色 id（调用方已解析有效）
     * @param operatorId   操作者ID
     * @return 被清理行涉及的角色 id 集合（旧 owner 们；供调用方 markRoles 失效快照）
     */
    Set<Long> rematerializeAuthorityRootGrants(Long tenantId, Integer typeValue,
                                               Long newOwnerRoleId, Long operatorId);

    /** 所有者指针值对象 */
    record GrantOriginRole(String roleTypeCode, String roleExternalId) {}
}
