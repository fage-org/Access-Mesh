package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.TypeDefinitionListReq;
import org.dromara.permission.domain.dto.TypeDefinitionSaveReq;
import org.dromara.permission.domain.vo.TypeDefinitionVo;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.dromara.permission.service.TypeDefinitionService;
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
public class TypeDefinitionServiceImpl implements TypeDefinitionService {

    private final PcTypeDefinitionMapper mapper;

    @Override
    public List<TypeDefinitionVo> list(TypeDefinitionListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcTypeDefinition> q = new LambdaQueryWrapper<PcTypeDefinition>()
            .eq(PcTypeDefinition::getTenantId, req.getTenantId())
            .eq(PcTypeDefinition::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getBizDomainId() != null) {
            q.eq(PcTypeDefinition::getBizDomainId, req.getBizDomainId());
        }
        if (StrUtil.isNotBlank(req.getTypeKey())) {
            q.eq(PcTypeDefinition::getTypeKey, req.getTypeKey());
        }
        q.orderByAsc(PcTypeDefinition::getTypeKey)
            .orderByAsc(PcTypeDefinition::getSortOrder)
            .orderByAsc(PcTypeDefinition::getTypeValue);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(TypeDefinitionSaveReq req) {
        if (req == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (TypeDefinitionSaveReq.TypeDefinitionItem item : req.getItems()) {
            if (item.getTenantId() == null || StrUtil.isBlank(item.getTypeKey()) || item.getTypeValue() == null) {
                continue;
            }
            if (item.getId() != null) {
                PcTypeDefinition entity = mapper.selectOne(new LambdaQueryWrapper<PcTypeDefinition>()
                    .eq(PcTypeDefinition::getId, item.getId())
                    .eq(PcTypeDefinition::getDeleteFlag, PermissionConstants.NOT_DELETED));
                if (entity == null) {
                    continue;
                }
                entity.setBizDomainId(item.getBizDomainId());
                entity.setTypeKey(item.getTypeKey());
                entity.setTypeValue(item.getTypeValue());
                entity.setName(item.getName());
                entity.setDescription(item.getDescription());
                entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            } else {
                PcTypeDefinition entity = new PcTypeDefinition();
                entity.setTenantId(item.getTenantId());
                entity.setBizDomainId(item.getBizDomainId());
                entity.setTypeKey(item.getTypeKey());
                entity.setTypeValue(item.getTypeValue());
                entity.setName(item.getName());
                entity.setDescription(item.getDescription());
                entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
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
        if (req == null || req.getTenantId() == null || CollUtil.isEmpty(req.getIds())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcTypeDefinition> entities = mapper.selectList(new LambdaQueryWrapper<PcTypeDefinition>()
            .eq(PcTypeDefinition::getTenantId, req.getTenantId())
            .in(PcTypeDefinition::getId, req.getIds())
            .eq(PcTypeDefinition::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcTypeDefinition entity : entities) {
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
        }
    }

    private TypeDefinitionVo toVo(PcTypeDefinition e) {
        TypeDefinitionVo vo = new TypeDefinitionVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
