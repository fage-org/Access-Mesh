package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcBizDomain;
import org.dromara.permission.domain.dto.DomainPageReq;
import org.dromara.permission.domain.dto.DomainSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.BizDomainVo;
import org.dromara.permission.mapper.PcBizDomainMapper;
import org.dromara.permission.service.BizDomainService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizDomainServiceImpl implements BizDomainService {

    private final PcBizDomainMapper mapper;

    @Override
    public TableDataInfo<BizDomainVo> page(DomainPageReq req) {
        if (req == null || req.getTenantId() == null) {
            return TableDataInfo.build();
        }
        int pageNum = req.getPageNum() != null && req.getPageNum() > 0 ? req.getPageNum() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        LambdaQueryWrapper<PcBizDomain> q = new LambdaQueryWrapper<PcBizDomain>()
            .eq(PcBizDomain::getTenantId, req.getTenantId())
            .eq(PcBizDomain::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (StrUtil.isNotBlank(req.getCode())) {
            q.like(PcBizDomain::getCode, req.getCode());
        }
        if (StrUtil.isNotBlank(req.getName())) {
            q.like(PcBizDomain::getName, req.getName());
        }
        q.orderByAsc(PcBizDomain::getId);
        Page<PcBizDomain> page = mapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<BizDomainVo> rows = page.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return new TableDataInfo<>(rows, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(DomainSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcBizDomain entity = mapper.selectOne(new LambdaQueryWrapper<PcBizDomain>()
                .eq(PcBizDomain::getId, req.getId())
                .eq(PcBizDomain::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                entity.setCode(req.getCode());
                entity.setName(req.getName());
                entity.setDescription(req.getDescription());
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            }
        } else {
            PcBizDomain entity = new PcBizDomain();
            entity.setTenantId(req.getTenantId());
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setDescription(req.getDescription());
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcBizDomain> entities = mapper.selectList(new LambdaQueryWrapper<PcBizDomain>()
            .eq(PcBizDomain::getTenantId, req.getTenantId())
            .in(PcBizDomain::getId, req.getIds())
            .eq(PcBizDomain::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcBizDomain entity : entities) {
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
        }
    }

    private BizDomainVo toVo(PcBizDomain e) {
        BizDomainVo vo = new BizDomainVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
