package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.admin.entity.SysDictData;
import cn.ac.fage.accessmesh.admin.entity.SysDictType;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictDataTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictTypeTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysDictDataMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysDictTypeMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.DictService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.common.mybatis.TenantSafeQuery;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysDictDataTableDef.SYS_DICT_DATA;
import static cn.ac.fage.accessmesh.admin.entity.table.SysDictTypeTableDef.SYS_DICT_TYPE;

@Service
public class DictServiceImpl implements DictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final AdminPermissionValidator permissionValidator;

    public DictServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictDataMapper dictDataMapper,
                           AdminPermissionValidator permissionValidator) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public Long createDictType(DictTypeCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.DICT, AdminOperationCode.CREATE);

        SysDictType type = new SysDictType();
        type.setDictType(req.dictType());
        type.setDictName(req.dictName());
        type.setStatus(req.status() != null ? req.status() : 1);
        type.setRemark(req.remark());
        type.setCreatedAt(LocalDateTime.now());
        type.setUpdatedAt(LocalDateTime.now());
        type.setDeleteFlag(0L);
        dictTypeMapper.insert(type);
        return type.getId();
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void deleteDictType(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.DICT, resourceCodes, AdminOperationCode.DELETE);

        // Batch query to check for data and filter valid IDs (performance fix: avoid N+1 queries)
        List<SysDictType> types = dictTypeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_DICT_TYPE.ID.in(req.ids()))
                .and(SYS_DICT_TYPE.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SYS_DICT_TYPE.DELETE_FLAG.eq(0))
        );
        
        // Batch check if any type has associated data (performance fix: single query with GROUP BY)
        if (!types.isEmpty()) {
            List<String> dictTypes = types.stream()
                .map(SysDictType::getDictType)
                .collect(Collectors.toList());

            List<SysDictData> dataWithTypes = dictDataMapper.selectListByQuery(
                QueryWrapper.create()
                    .select(SYS_DICT_DATA.DICT_TYPE)
                    .where(SYS_DICT_DATA.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                    .and(SYS_DICT_DATA.DICT_TYPE.in(dictTypes))
                    .and(SYS_DICT_DATA.DELETE_FLAG.eq(0))
                    .groupBy(SYS_DICT_DATA.DICT_TYPE)
            );

            if (!dataWithTypes.isEmpty()) {
                throw new BizException(AdminErrorCode.DICT_TYPE_HAS_DATA.getCode(), AdminErrorCode.DICT_TYPE_HAS_DATA.getMessage());
            }
        }
        
        // Batch soft delete (performance fix: use single SQL instead of loop)
        if (!types.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = types.stream().map(SysDictType::getId).collect(java.util.stream.Collectors.toList());
            dictTypeMapper.softDeleteBatch(validIds, now);
        }
    }

    @Override
    @Cacheable(value = "dictTypes", key = "'all'")
    public List<DictTypeResp> listDictTypes() {
        List<SysDictType> types = dictTypeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_DICT_TYPE.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SYS_DICT_TYPE.DELETE_FLAG.eq(0))
                .orderBy(SYS_DICT_TYPE.CREATED_AT.asc())
        );
        return types.stream().map(t -> {
            List<DictDataResp> data = listDictData(t.getId());
            return new DictTypeResp(t.getId(), t.getDictName(), t.getDictType(), t.getStatus(), t.getRemark(), t.getCreatedAt(), data);
        }).collect(Collectors.toList());
    }

    @Override
    public PaginatedResult<DictTypeResp> pageDictTypes(PageReq pageReq) {
        Page<SysDictType> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysDictType> result = dictTypeMapper.paginate(page,
            QueryWrapper.create()
                .where(SYS_DICT_TYPE.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SYS_DICT_TYPE.DELETE_FLAG.eq(0))
                .orderBy(SYS_DICT_TYPE.CREATED_AT.asc()));

        List<DictTypeResp> items = result.getRecords().stream()
            .map(t -> new DictTypeResp(t.getId(), t.getDictName(), t.getDictType(), t.getStatus(), t.getRemark(), t.getCreatedAt(), List.of()))
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public Long createDictData(DictDataCreateReq req) {
        // Permission check - type-level CREATE for dict data
        permissionValidator.checkTypeLevel(AdminResourceType.DICT_DATA, AdminOperationCode.CREATE);

        SysDictType type = TenantSafeQuery.selectOneByIdSafe(
            dictTypeMapper, SYS_DICT_TYPE.ID, SYS_DICT_TYPE.TENANT_ID, SYS_DICT_TYPE.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.dictTypeId());
        if (type == null) {
            throw new BizException(AdminErrorCode.DICT_TYPE_NOT_FOUND.getCode(), AdminErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        SysDictData data = new SysDictData();
        data.setDictType(type.getDictType());
        data.setDictLabel(req.dictLabel());
        data.setDictValue(req.dictValue());
        data.setSortOrder(req.sort());
        data.setStatus(req.status() != null ? req.status() : 1);
        data.setRemark(req.remark());
        data.setCreatedAt(LocalDateTime.now());
        data.setUpdatedAt(LocalDateTime.now());
        data.setDeleteFlag(0L);
        dictDataMapper.insert(data);
        return data.getId();
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void updateDictData(DictDataCreateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.DICT_DATA,
            String.valueOf(req.dictTypeId()),
            AdminOperationCode.UPDATE
        );

        SysDictData data = TenantSafeQuery.selectOneByIdSafe(
            dictDataMapper, SYS_DICT_DATA.ID, SYS_DICT_DATA.TENANT_ID, SYS_DICT_DATA.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.dictTypeId());
        if (data == null) {
            throw new BizException(AdminErrorCode.DICT_DATA_NOT_FOUND.getCode(), AdminErrorCode.DICT_DATA_NOT_FOUND.getMessage());
        }
        data.setDictLabel(req.dictLabel());
        data.setDictValue(req.dictValue());
        data.setSortOrder(req.sort());
        data.setStatus(req.status());
        data.setRemark(req.remark());
        data.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.update(data);
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public void deleteDictData(IdReq req) {
        // Permission check - instance-level DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.DICT_DATA,
            String.valueOf(req.id()),
            AdminOperationCode.DELETE
        );

        SysDictData data = TenantSafeQuery.selectOneByIdSafe(
            dictDataMapper, SYS_DICT_DATA.ID, SYS_DICT_DATA.TENANT_ID, SYS_DICT_DATA.DELETE_FLAG,
            TenantContextHolder.getTenantId(), req.id());
        if (data == null) return;
        data.setDeleteFlag(data.getId());
        data.setDeletedAt(LocalDateTime.now());
        dictDataMapper.update(data);
    }

    @Override
    public List<DictDataResp> listDictData(Long dictTypeId) {
        SysDictType type = TenantSafeQuery.selectOneByIdSafe(
            dictTypeMapper, SYS_DICT_TYPE.ID, SYS_DICT_TYPE.TENANT_ID, SYS_DICT_TYPE.DELETE_FLAG,
            TenantContextHolder.getTenantId(), dictTypeId);
        if (type == null) return List.of();
        return dictDataMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_DICT_DATA.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                .and(SYS_DICT_DATA.DICT_TYPE.eq(type.getDictType()))
                .and(SYS_DICT_DATA.DELETE_FLAG.eq(0))
                .orderBy(SYS_DICT_DATA.SORT_ORDER.asc())
        ).stream().map(d -> new DictDataResp(
            d.getId(), dictTypeId, d.getDictLabel(), d.getDictValue(),
            d.getSortOrder(), d.getStatus(), d.getRemark()
        )).collect(Collectors.toList());
    }
}
