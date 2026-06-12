package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgUserItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgSyncHandler;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缁勭粐绠＄悊鏈嶅姟瀹炵幇绫? * <p>
 * 鎻愪緵缁勭粐鐨凜RUD鎿嶄綔銆佹爲褰㈡煡璇㈢瓑鍔熻兘銆? * 瀹炵幇璺ㄦ湇鍔℃暟鎹悓姝ユ満鍒讹紝閫氳繃Outbox Pattern纭繚缁勭粐鍒涘缓涓庡悓姝ヤ换鍔¤褰曞師瀛愭€с€? * 鏀寔缁勭粐灞傜骇娣卞害闄愬埗锛堟渶澶?0绾э級銆佺粍缁囩紪鐮佸敮涓€鎬ф牎楠屻€? * 浣跨敤OrgDomainService澶勭悊缁勭粐鏁版嵁鏌ヨ銆? * 璁捐绾︽潫锛氱粍缁?宀椾綅鍦?permission-center 涓湁涓ょ被浜嬪疄锛? * ADMIN_ORG resource_entity 鐢ㄤ簬瀹炰緥绾х鐞嗘潈闄愶紝
 * ORG/POSITION abstract_role 鐢ㄤ簬瑙掕壊瀹瑰櫒鍜?user_role 璁＄畻銆? * 鍧囦娇鐢ㄤ笟鍔￠敭瀹氫綅锛屼笉瀛樺唴閮?ID銆? * </p>
 */
@Service
public class OrgServiceImpl implements OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgServiceImpl.class);

    private final SysOrgMapper orgMapper;
    private final OrgDomainService orgDomainService;
    private final OrgSyncHandler orgSyncHandler;
    private final AdminPermissionValidator permissionValidator;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;

    /**
     * 鏋勯€犲嚱鏁版敞鍏ヤ緷璧?     *
     * @param orgMapper 缁勭粐鏁版嵁璁块棶Mapper
     * @param orgDomainService 缁勭粐棰嗗煙鏈嶅姟锛屽鐞嗙粍缁囨暟鎹煡璇㈠拰鎵归噺鎿嶄綔
     * @param orgSyncHandler 缁勭粐鍚屾澶勭悊鍣紝鍚屾缁勭粐鏁版嵁鍒皃ermission-center
     * @param permissionValidator 鏉冮檺鏍￠獙鍣紝鏍￠獙缁勭粐鎿嶄綔鏉冮檺
     * @param syncRetryService 鍚屾閲嶈瘯鏈嶅姟锛岃褰曞悓姝ュけ璐ヤ换鍔?     * @param objectMapper JSON搴忓垪鍖栧伐鍏?     * @param userOrgMapper 鐢ㄦ埛缁勭粐鍏宠仈Mapper锛屾煡璇㈢粍缁囦笅鐢ㄦ埛鍏宠仈
     * @param userDomainService 鐢ㄦ埛棰嗗煙鏈嶅姟锛屾壒閲忔煡璇㈢敤鎴蜂俊鎭?     */
    public OrgServiceImpl(SysOrgMapper orgMapper, OrgDomainService orgDomainService,
                          OrgSyncHandler orgSyncHandler, AdminPermissionValidator permissionValidator,
                          SyncRetryService syncRetryService, ObjectMapper objectMapper,
                          SysUserOrgMapper userOrgMapper,
                          UserDomainService userDomainService) {
        this.orgMapper = orgMapper;
        this.orgDomainService = orgDomainService;
        this.orgSyncHandler = orgSyncHandler;
        this.permissionValidator = permissionValidator;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
    }

    /**
     * 鍒涘缓缁勭粐
     * <p>
     * 鍒涘缓鏂扮粍缁囷紝鏍￠獙缂栫爜鍞竴鎬у拰缁勭粐灞傜骇娣卞害锛堜笉瓒呰繃10绾э級銆?     * 鍒涘缓鎴愬姛鍚庤褰曞悓姝ヤ换鍔★紝寮傛鍚屾鍒皃ermission-center锛圤utbox Pattern锛夈€?     * 鎵ц绫诲瀷绾ф潈闄愭牎楠?CREATE)銆?     * 鍚庣画瀹炵幇缁勭粐鍚屾鏃堕渶鍚屾椂钀藉湴 ADMIN_ORG resource_entity 涓?     * ORG/POSITION abstract_role锛屽潎浣跨敤涓氬姟閿畾浣嶃€?     * </p>
     *
     * @param req 缁勭粐鍒涘缓璇锋眰锛屽寘鍚粍缁囧悕绉般€佺紪鐮併€佺被鍨嬨€佺埗缁勭粐ID绛?     * @return 鏂扮粍缁嘔D
     * @throws BizException 缁勭粐缂栫爜宸插瓨鍦ㄣ€佺粍缁囧眰绾ц秴闄愩€佸悓姝ヤ换鍔¤褰曞け璐ョ瓑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrg(OrgCreateReq req) {
        // 鏉冮檺妫€鏌?鈥?绫诲瀷绾?CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.ORG, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 浣跨敤 DomainService 妫€鏌ョ紪鐮侀噸澶?
        SysOrg existing = orgDomainService.findByCode(tenantId, req.code());
        if (existing != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(), AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }

        int level = 1;
        if (req.parentOrgId() != null) {
            Long parentId = req.parentOrgId();
            SysOrg parent = orgDomainService.selectValidById(tenantId, parentId);
            if (parent != null) {
                level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
            }
        }
        if (level > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }

        SysOrg org = new SysOrg();
        org.setTenantId(tenantId);
        org.setParentId(req.parentOrgId() != null ? req.parentOrgId() : 0L);
        org.setOrgType(String.valueOf(req.orgType()));
        org.setCode(req.code());
        org.setName(req.orgName());
        org.setStatus(req.status() != null ? req.status() : 1);
        org.setSortOrder(req.sort());
        org.setLevel(level);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        org.setDeleteFlag(0L);
        // 浜嬪姟鍐咃細鎻掑叆缁勭粐 + 璁板綍鍚屾浠诲姟锛堝師瀛愭€э紝Outbox Pattern锛?
        orgMapper.insert(org);

        // 鍚屼竴浜嬪姟鍐呰褰曞悓姝ヤ换鍔★紝纭繚缁勭粐鍒涘缓涓庝换鍔¤褰曞師瀛愭€?
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "orgId", org.getId(),
                "orgName", org.getName(),
                "tenantId", tenantId
            ));
            syncRetryService.recordSyncFailure(
                "org:create:" + org.getId(),
                "permission-center",
                "abstract_org",
                String.valueOf(org.getId()),
                "create",
                payload,
                null
            );
            log.info("Recorded sync task for org creation: orgId={}", org.getId());
        } catch (Exception e) {
            log.error("Failed to serialize sync payload for org creation: orgId={}", org.getId(), e);
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "org sync task record failed");
        }

        return org.getId();
    }

    /**
     * 鏇存柊缁勭粐
     * <p>
     * 鏇存柊缁勭粐鐨勫悕绉般€佺紪鐮併€佺埗缁勭粐銆佺姸鎬佺瓑灞炴€с€?     * 鎵ц瀹炰緥绾ф潈闄愭牎楠?UPDATE)銆?     * 鏍￠獙缂栫爜鍞竴鎬у拰缁勭粐灞傜骇娣卞害銆?     * 濡傛灉缁勭粐宸插悓姝ュ埌permission-center锛岃褰曟洿鏂板悓姝ヤ换鍔★紙Outbox Pattern锛夈€?     * </p>
     *
     * @param req 缁勭粐鏇存柊璇锋眰锛屽寘鍚粍缁嘔D鍜屾柊灞炴€у€?     * @throws BizException 缁勭粐涓嶅瓨鍦ㄣ€佺紪鐮佸凡瀛樺湪銆佸眰绾ц秴闄愩€佸悓姝ヤ换鍔¤褰曞け璐ョ瓑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrg(OrgUpdateReq req) {
        // 鏉冮檺妫€鏌?鈥?瀹炰緥绾?UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 浣跨敤 DomainService 鑾峰彇缁勭粐
        SysOrg org = orgDomainService.selectValidById(tenantId, req.id());
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        // 浠呭湪鎸囧畾鏂扮埗绾ф椂楠岃瘉鐖剁骇鍙樻洿
        if (req.parentOrgId() != null) {
            long newParentId = req.parentOrgId();
            if (newParentId != org.getParentId()) {
                SysOrg newParent = orgDomainService.selectValidById(tenantId, newParentId);
                int newLevel = newParent != null ? (newParent.getLevel() != null ? newParent.getLevel() + 1 : 1) : 1;
                if (newLevel > 10) {
                    throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
                }
            }
        }

        // 濡傛灉淇敼浜嗙紪鐮侊紝妫€鏌ユ柊缂栫爜鏄惁閲嶅
        if (!req.code().equals(org.getCode())) {
            SysOrg codeExisting = orgDomainService.findByCode(tenantId, req.code());
            if (codeExisting != null) {
                throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(), AdminErrorCode.ORG_CODE_EXISTS.getMessage());
            }
        }

        org.setName(req.orgName());
        org.setParentId(req.parentOrgId() != null ? req.parentOrgId() : org.getParentId());
        org.setCode(req.code());
        org.setStatus(req.status());
        org.setUpdatedAt(LocalDateTime.now());
        orgMapper.update(org);

        // 鍚屾鏇存柊鍒版潈闄愪腑蹇?- 鍚屼竴浜嬪姟鍐呰褰曞悓姝ヤ换鍔★紙Outbox Pattern锛?
        if (org.getId() != null) {
        try {
                String payload = objectMapper.writeValueAsString(Map.of(
                    "orgId", org.getId(),
                    "name", org.getName(),
                    "code", org.getCode(),
                    "parentId", org.getParentId(),
                    "level", org.getLevel(),
                    "sortOrder", org.getSortOrder()
                ));
                syncRetryService.recordSyncFailure(
                    "org:update:" + org.getId(),
                    "permission-center",
                    "abstract_org",
                    String.valueOf(org.getId()),
                    "update",
                    payload,
                    null
                );
                log.info("Recorded update sync task for org: orgId={}", org.getId());
            } catch (Exception e) {
                log.error("Failed to serialize sync payload for org update: orgId={}", org.getId(), e);
                throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "org sync task record failed");
            }
        }
    }

    /**
     * 鍒犻櫎缁勭粐
     * <p>
     * 杞垹闄ょ粍缁囷紝涓嶅厑璁稿垹闄ゆ湁瀛愮粍缁囩殑缁勭粐銆?     * 鎵ц瀹炰緥绾ф潈闄愭牎楠?DELETE)銆?     * 鍏堟湰鍦拌蒋鍒犻櫎鍐嶈褰曞悓姝ヤ换鍔★紙Outbox Pattern锛夈€?     * </p>
     *
     * @param id 缁勭粐ID
     * @throws BizException 缁勭粐涓嶅瓨鍦ㄣ€佹湁瀛愮粍缁囥€佸悓姝ヤ换鍔¤褰曞け璐ョ瓑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrg(Long id) {
        // 鏉冮檺妫€鏌?鈥?瀹炰緥绾?DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(id),
            AdminOperationCode.DELETE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 浣跨敤 DomainService 鑾峰彇缁勭粐
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        // 浣跨敤 DomainService 妫€鏌ユ槸鍚︽湁瀛愮粍缁?
        if (orgDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.ORG_HAS_CHILDREN.getCode(), AdminErrorCode.ORG_HAS_CHILDREN.getMessage());
        }

        // 浜嬪姟鍐咃細杞垹闄ょ粍缁?+ 璁板綍鍚屾浠诲姟锛堝師瀛愭€э紝Outbox Pattern锛?
        orgDomainService.softDeleteBatch(tenantId, List.of(id));

        // 鍚屼竴浜嬪姟鍐呰褰曞悓姝ヤ换鍔★紝纭繚鍒犻櫎涓庝换鍔¤褰曞師瀛愭€?
        syncRetryService.recordSyncFailure(
            "org:delete:" + id,
            "permission-center",
            "abstract_org",
            String.valueOf(id),
            "delete",
            null,
            null
        );
        log.info("Recorded delete sync task for org: orgId={}", id);
    }

    /**
     * 鑾峰彇缁勭粐璇︽儏
     * <p>
     * 鏍规嵁缁勭粐ID鏌ヨ缁勭粐瀹屾暣淇℃伅銆?     * </p>
     *
     * @param id 缁勭粐ID
     * @return 缁勭粐璇︽儏鍝嶅簲
     * @throws BizException 缁勭粐涓嶅瓨鍦?     */
    @Override
    public OrgResp getOrg(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 浣跨敤 DomainService 鑾峰彇缁勭粐
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        return toResp(org, List.of());
    }

    /**
     * 鍒嗛〉鏌ヨ缁勭粐鍒楄〃
     * <p>
     * 鏀寔鎸夌粍缁囧悕绉般€佺被鍨嬨€佺姸鎬佽繃婊ゃ€?     * 褰?orgId 涓嶄负绌烘椂锛屼粎杩斿洖 orgId 瀛愭爲鍐呯殑缁勭粐锛堝惈鑷韩鍙婃墍鏈夊瓙瀛欙級锛?     * 瀹炵幇宀椾綅 Tab 鎸夐€変腑缁勭粐绛涢€夌殑璇箟銆?     * 鎸夋帓搴忓瓧娈靛拰鍒涘缓鏃堕棿鎺掑簭銆?     * </p>
     *
     * @param req 鍒嗛〉鏌ヨ璇锋眰锛屽寘鍚垎椤靛弬鏁板拰杩囨护鏉′欢
     * @return 鍒嗛〉缁勭粐鍒楄〃缁撴灉
     */
    @Override
    public PaginatedResult<OrgResp> pageOrgs(OrgPageReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        String orgType = req.orgType() != null ? String.valueOf(req.orgType()) : null;
        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();
        Set<Long> orgIds = null;
        if (req.orgId() != null) {
            List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, req.orgId());
            if (subtreeIds.isEmpty()) {
                return new PaginatedResult<>(
                    List.of(),
                    new PaginatedResult.PaginationMeta(0, pageNum, pageSize, 0)
                );
            }
            orgIds = Set.copyOf(subtreeIds);
        }

        Page<SysOrg> result = orgMapper.paginateOrgs(Page.of(pageNum, pageSize), tenantId, req.orgName(), orgType, req.status(), orgIds);

        List<SysOrg> records = result.getRecords();

        // orgId 瀛愭爲璇箟锛氫粎淇濈暀 orgId 瀛愭爲鍐呯殑缁勭粐锛堝惈鑷韩鍙婂瓙瀛欙級
        List<OrgResp> items = records.stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());

        // orgId 杩囨护鍚庢€绘暟闇€閲嶆柊璁＄畻
        long total = result.getTotalRow();
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(total, pageNum, pageSize, (int) totalPages));
    }

    /**
     * 鏌ヨ缁勭粐鏍?     * <p>
     * 鑾峰彇褰撳墠绉熸埛鐨勬墍鏈夌粍缁囷紝鏋勫缓鏍戝舰缁撴瀯杩斿洖銆?     * 鏀寔鎸夌粍缁囩被鍨嬪拰鐘舵€佽繃婊ゃ€?     * 鎸夋帓搴忓瓧娈靛拰鍒涘缓鏃堕棿鎺掑簭銆?     * </p>
     *
     * @param query 缁勭粐鏌ヨ鏉′欢锛屽彲閫?     * @return 缁勭粐鏍戝垪琛?     */
    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
        Long tenantId = TenantContextHolder.getTenantId();
        String orgType = query != null && query.orgType() != null ? String.valueOf(query.orgType()) : null;
        Integer status = query != null ? query.status() : null;

        List<SysOrg> all = orgMapper.selectOrgsForTree(tenantId, orgType, status);
        return buildTree(all, 0L);
    }

    /**
     * 鏌ヨ缁勭粐/宀椾綅涓嬬殑鐢ㄦ埛鍒楄〃
     * <p>
     * 鏌ヨ鎸囧畾缁勭粐鎴栧矖浣嶄笅閫氳繃 user-org 鍏宠仈鐨勭敤鎴枫€?     * 鐢ㄤ簬宀椾綅鍗＄墖灞曞紑鍚庡睍绀哄凡鍒嗛厤鐢ㄦ埛銆?     * </p>
     *
     * @param orgId 缁勭粐鎴栧矖浣岻D
     * @return 鐢ㄦ埛绠€瑕佷俊鎭垪琛?     */
    @Override
    public List<OrgUserItemResp> listOrgUsers(Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 鎸?orgId 鏌ヨ鐢ㄦ埛缁勭粐鍏宠仈锛堜娇鐢?SysUserOrgTableDef锛屼笉闈欐€佸鍏ワ級
        cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef suo = cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;
        com.mybatisflex.core.query.QueryWrapper qw = com.mybatisflex.core.query.QueryWrapper.create()
            .where(suo.TENANT_ID.eq(tenantId))
            .where(suo.ORG_ID.eq(orgId))
            .where(suo.DELETE_FLAG.eq(0L));
        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(qw);

        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 鎵归噺鏌ヨ鐢ㄦ埛淇℃伅
        java.util.Set<Long> userIds = userOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, SysUser> userMap = userDomainService.selectValidByIds(tenantId, userIds).stream()
            .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));

        // 鎸夊叧鑱旈『搴忕粍瑁呭搷搴?
        return userOrgs.stream()
            .map(uo -> {
                SysUser user = userMap.get(uo.getUserId());
                if (user == null) return null;
                return new OrgUserItemResp(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getAvatar(),
                    Boolean.TRUE.equals(uo.getIsPrimary())
                );
            })
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 灏嗙粍缁囧疄浣撹浆鎹负鍝嶅簲瀵硅薄
     * <p>
     * 杞崲缁勭粐瀹炰綋涓篈PI鍝嶅簲鏍煎紡锛屽寘鍚瓙缁勭粐鍒楄〃銆?     * </p>
     *
     * @param org 缁勭粐瀹炰綋
     * @param children 瀛愮粍缁囧搷搴斿垪琛?     * @return 缁勭粐鍝嶅簲瀵硅薄
     */
    private OrgResp toResp(SysOrg org, List<OrgResp> children) {
        return new OrgResp(
            org.getId(), Integer.parseInt(org.getOrgType()), org.getName(),
            org.getParentId(), org.getCode(), null, null,
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

    /**
     * 鏋勫缓缁勭粐鏍?     * <p>
     * 灏嗙粍缁囧垪琛ㄨ浆鎹负鏍戝舰缁撴瀯锛岄€掑綊鏋勫缓瀛愮粍缁囥€?     * </p>
     *
     * @param all 鎵€鏈夌粍缁囧垪琛?     * @param parentId 褰撳墠灞傜骇鐖剁粍缁嘔D锛?琛ㄧず鏍圭骇锛?     * @return 缁勭粐鏍戝垪琛?     */
    private List<OrgResp> buildTree(List<SysOrg> all, Long parentId) {
        return all.stream()
            .filter(o -> parentId.equals(o.getParentId()))
            .map(o -> new OrgResp(
                o.getId(), Integer.parseInt(o.getOrgType()), o.getName(),
                o.getParentId(), o.getCode(), null, null,
                o.getStatus(), o.getSortOrder(), o.getCreatedAt(), o.getUpdatedAt(),
                buildTree(all, o.getId())
            ))
            .collect(Collectors.toList());
    }
}
