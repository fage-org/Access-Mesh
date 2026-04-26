package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.*;
import cn.ac.fage.accessmesh.admin.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.service.OrgService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef.SYS_ORG;

@Service
public class OrgServiceImpl implements OrgService {

    private final SysOrgMapper orgMapper;

    public OrgServiceImpl(SysOrgMapper orgMapper) {
        this.orgMapper = orgMapper;
    }

    @Override
    @Transactional
    public Long createOrg(OrgCreateReq req) {
        SysOrg existing = orgMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.CODE.eq(req.code()))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
        if (existing != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(), AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }

        int level = 1;
        if (req.parentOrgId() != null) {
            SysOrg parent = orgMapper.selectOneById(Long.parseLong(req.parentOrgId()));
            if (parent != null) {
                level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
            }
        }
        if (level > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }

        SysOrg org = new SysOrg();
        org.setParentId(req.parentOrgId() != null ? Long.parseLong(req.parentOrgId()) : 0L);
        org.setOrgType(String.valueOf(req.orgType()));
        org.setCode(req.code());
        org.setName(req.orgName());
        org.setStatus(req.status() != null ? req.status() : 1);
        org.setSortOrder(req.sort());
        org.setLevel(level);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        org.setDeleteFlag(0L);
        orgMapper.insert(org);
        return org.getId();
    }

    @Override
    @Transactional
    public void updateOrg(OrgUpdateReq req) {
        SysOrg org = orgMapper.selectOneById(req.id());
        if (org == null || org.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        long newParentId = Long.parseLong(req.parentOrgId());
        if (newParentId != org.getParentId()) {
            SysOrg newParent = orgMapper.selectOneById(newParentId);
            int newLevel = newParent != null ? (newParent.getLevel() != null ? newParent.getLevel() + 1 : 1) : 1;
            if (newLevel > 10) {
                throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(), AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
            }
        }
        org.setName(req.orgName());
        org.setParentId(req.parentOrgId() != null ? Long.parseLong(req.parentOrgId()) : org.getParentId());
        org.setCode(req.code());
        org.setStatus(req.status());
        org.setUpdatedAt(LocalDateTime.now());
        orgMapper.update(org);
    }

    @Override
    @Transactional
    public void deleteOrg(Long id) {
        SysOrg org = orgMapper.selectOneById(id);
        if (org == null || org.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        long childCount = orgMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.PARENT_ID.eq(id))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
        if (childCount > 0) {
            throw new BizException(AdminErrorCode.ORG_HAS_CHILDREN.getCode(), AdminErrorCode.ORG_HAS_CHILDREN.getMessage());
        }
        org.setDeleteFlag(1L);
        org.setDeletedAt(LocalDateTime.now());
        orgMapper.update(org);
    }

    @Override
    public OrgResp getOrg(Long id) {
        SysOrg org = orgMapper.selectOneById(id);
        if (org == null || org.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        return toResp(org, List.of());
    }

    @Override
    public PaginatedResult<OrgResp> pageOrgs(PageReq pageReq, OrgQuery query) {
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_ORG.DELETE_FLAG.eq(0));
        if (query != null) {
            if (query.orgName() != null) qw.and(SYS_ORG.NAME.like(query.orgName()));
            if (query.orgType() != null) qw.and(SYS_ORG.ORG_TYPE.eq(String.valueOf(query.orgType())));
            if (query.status() != null) qw.and(SYS_ORG.STATUS.eq(query.status()));
        }
        qw.orderBy(SYS_ORG.SORT_ORDER.asc(), SYS_ORG.CREATED_AT.asc());

        Page<SysOrg> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysOrg> result = orgMapper.paginate(page, qw);

        List<OrgResp> items = result.getRecords().stream()
            .map(o -> toResp(o, List.of()))
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    @Override
    public List<OrgResp> treeOrgs(OrgQuery query) {
        QueryWrapper qw = QueryWrapper.create()
            .where(SYS_ORG.DELETE_FLAG.eq(0));
        if (query != null) {
            if (query.orgType() != null) qw.and(SYS_ORG.ORG_TYPE.eq(String.valueOf(query.orgType())));
            if (query.status() != null) qw.and(SYS_ORG.STATUS.eq(query.status()));
        }
        qw.orderBy(SYS_ORG.SORT_ORDER.asc(), SYS_ORG.CREATED_AT.asc());

        List<SysOrg> all = orgMapper.selectListByQuery(qw);
        return buildTree(all, 0L);
    }

    private OrgResp toResp(SysOrg org, List<OrgResp> children) {
        return new OrgResp(
            org.getId(), Integer.parseInt(org.getOrgType()), org.getName(),
            String.valueOf(org.getParentId()), org.getCode(), null, null,
            org.getStatus(), org.getSortOrder(), org.getCreatedAt(), org.getUpdatedAt(), children
        );
    }

    private List<OrgResp> buildTree(List<SysOrg> all, Long parentId) {
        return all.stream()
            .filter(o -> parentId.equals(o.getParentId()))
            .map(o -> new OrgResp(
                o.getId(), Integer.parseInt(o.getOrgType()), o.getName(),
                String.valueOf(o.getParentId()), o.getCode(), null, null,
                o.getStatus(), o.getSortOrder(), o.getCreatedAt(), o.getUpdatedAt(),
                buildTree(all, o.getId())
            ))
            .collect(Collectors.toList());
    }
}
