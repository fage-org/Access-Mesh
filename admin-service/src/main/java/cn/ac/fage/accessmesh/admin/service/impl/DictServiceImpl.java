package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.DictDataCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.DictTypeCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.resp.DictDataResp;
import cn.ac.fage.accessmesh.admin.dto.resp.DictTypeResp;
import cn.ac.fage.accessmesh.admin.entity.SysDictData;
import cn.ac.fage.accessmesh.admin.entity.SysDictType;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictDataTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysDictTypeTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysDictDataMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysDictTypeMapper;
import cn.ac.fage.accessmesh.admin.service.DictService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
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

    public DictServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictDataMapper dictDataMapper) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
    }

    @Override
    @Transactional
    @CacheEvict(value = "dictTypes", allEntries = true)
    public Long createDictType(DictTypeCreateReq req) {
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
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            SysDictType type = dictTypeMapper.selectOneById(id);
            if (type != null && type.getDeleteFlag() == 0L) {
                long dataCount = dictDataMapper.selectCountByQuery(
                    QueryWrapper.create()
                        .where(SYS_DICT_DATA.DICT_TYPE.eq(type.getDictType()))
                        .and(SYS_DICT_DATA.DELETE_FLAG.eq(0))
                );
                if (dataCount > 0) {
                    throw new BizException(AdminErrorCode.DICT_TYPE_HAS_DATA.getCode(), AdminErrorCode.DICT_TYPE_HAS_DATA.getMessage());
                }
            }
            SysDictType existing = dictTypeMapper.selectOneById(id);
            if (existing == null || existing.getDeleteFlag() != 0L) continue;
            existing.setDeleteFlag(1L);
            existing.setDeletedAt(now);
            dictTypeMapper.update(existing);
        }
    }

    @Override
    @Cacheable(value = "dictTypes", key = "'all'")
    public List<DictTypeResp> listDictTypes() {
        List<SysDictType> types = dictTypeMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_DICT_TYPE.DELETE_FLAG.eq(0))
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
                .where(SYS_DICT_TYPE.DELETE_FLAG.eq(0))
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
        SysDictType type = dictTypeMapper.selectOneById(req.dictTypeId());
        if (type == null || type.getDeleteFlag() != 0L) {
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
        SysDictData data = dictDataMapper.selectOneById(req.dictTypeId());
        if (data == null || data.getDeleteFlag() != 0L) {
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
        SysDictData data = dictDataMapper.selectOneById(req.id());
        if (data == null || data.getDeleteFlag() != 0L) return;
        data.setDeleteFlag(1L);
        data.setDeletedAt(LocalDateTime.now());
        dictDataMapper.update(data);
    }

    @Override
    public List<DictDataResp> listDictData(Long dictTypeId) {
        SysDictType type = dictTypeMapper.selectOneById(dictTypeId);
        if (type == null) return List.of();
        return dictDataMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_DICT_DATA.DICT_TYPE.eq(type.getDictType()))
                .and(SYS_DICT_DATA.DELETE_FLAG.eq(0))
                .orderBy(SYS_DICT_DATA.SORT_ORDER.asc())
        ).stream().map(d -> new DictDataResp(
            d.getId(), dictTypeId, d.getDictLabel(), d.getDictValue(),
            d.getSortOrder(), d.getStatus(), d.getRemark()
        )).collect(Collectors.toList());
    }
}
