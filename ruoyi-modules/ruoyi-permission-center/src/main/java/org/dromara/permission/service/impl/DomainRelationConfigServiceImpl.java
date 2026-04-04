package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcDomainRelationConfig;
import org.dromara.permission.domain.dto.DomainRelationListReq;
import org.dromara.permission.domain.dto.DomainRelationSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.DomainRelationConfigVo;
import org.dromara.permission.mapper.PcDomainRelationConfigMapper;
import org.dromara.permission.service.DomainRelationConfigService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainRelationConfigServiceImpl implements DomainRelationConfigService {

    private final PcDomainRelationConfigMapper mapper;

    @Override
    public List<DomainRelationConfigVo> list(DomainRelationListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcDomainRelationConfig> q = new LambdaQueryWrapper<PcDomainRelationConfig>()
            .eq(PcDomainRelationConfig::getTenantId, req.getTenantId())
            .eq(PcDomainRelationConfig::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcDomainRelationConfig::getBizDomainId, req.getBizDomainId());
        }
        if (StrUtil.isNotBlank(req.getRelationType())) {
            q.eq(PcDomainRelationConfig::getRelationType, req.getRelationType());
        }
        q.orderByAsc(PcDomainRelationConfig::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(DomainRelationSaveReq req) {
        if (req == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DomainRelationSaveReq.DomainRelationItem item : req.getItems()) {
            if (item.getTenantId() == null) {
                continue;
            }
            if (item.getId() != null) {
                PcDomainRelationConfig entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainRelationConfig>()
                    .eq(PcDomainRelationConfig::getTenantId, item.getTenantId())
                    .eq(PcDomainRelationConfig::getId, item.getId())
                    .eq(PcDomainRelationConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
                if (entity != null) {
                    entity.setBizDomainId(item.getBizDomainId());
                    entity.setRelationType(item.getRelationType());
                    entity.setLeftRefId(item.getLeftRefId());
                    entity.setRightRefId(item.getRightRefId());
                    entity.setDefaultConditionId(item.getDefaultConditionId());
                    entity.setUpdatedAt(now);
                    mapper.updateById(entity);
                }
            } else {
                PcDomainRelationConfig entity = new PcDomainRelationConfig();
                entity.setTenantId(item.getTenantId());
                entity.setBizDomainId(item.getBizDomainId());
                entity.setRelationType(item.getRelationType());
                entity.setLeftRefId(item.getLeftRefId());
                entity.setRightRefId(item.getRightRefId());
                entity.setDefaultConditionId(item.getDefaultConditionId());
                entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                mapper.insert(entity);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.getIds()) {
            PcDomainRelationConfig entity = mapper.selectOne(new LambdaQueryWrapper<PcDomainRelationConfig>()
                .eq(PcDomainRelationConfig::getTenantId, req.getTenantId())
                .eq(PcDomainRelationConfig::getId, id)
                .eq(PcDomainRelationConfig::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private DomainRelationConfigVo toVo(PcDomainRelationConfig e) {
        DomainRelationConfigVo vo = new DomainRelationConfigVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
