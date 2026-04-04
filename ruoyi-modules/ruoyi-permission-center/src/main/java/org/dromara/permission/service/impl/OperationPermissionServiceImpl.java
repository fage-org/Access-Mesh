package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.OperationListReq;
import org.dromara.permission.domain.dto.OperationSaveReq;
import org.dromara.permission.domain.vo.OperationPermissionVo;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.service.OperationPermissionService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationPermissionServiceImpl implements OperationPermissionService {

    private final PcOperationPermissionMapper mapper;
    private final TypeDefinitionReader typeDefinitionReader;

    @Override
    public List<OperationPermissionVo> list(OperationListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcOperationPermission> q = new LambdaQueryWrapper<PcOperationPermission>()
            .eq(PcOperationPermission::getTenantId, req.getTenantId())
            .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getResourceType() != null) {
            q.eq(PcOperationPermission::getResourceType, req.getResourceType());
        }
        q.orderByAsc(PcOperationPermission::getId);
        return mapper.selectList(q).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(OperationSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        if (req.getResourceType() != null) {
            typeDefinitionReader.assertTypeValueExists(req.getTenantId(), null, "resource_type", req.getResourceType(), "无效的资源类型");
        }
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcOperationPermission entity = mapper.selectOne(new LambdaQueryWrapper<PcOperationPermission>()
                .eq(PcOperationPermission::getId, req.getId())
                .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                entity.setResourceType(req.getResourceType());
                entity.setCode(req.getCode());
                entity.setName(req.getName());
                entity.setBinaryBit(req.getBinaryBit());
                entity.setInheritMask(req.getInheritMask());
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            }
        } else {
            PcOperationPermission entity = new PcOperationPermission();
            entity.setTenantId(req.getTenantId());
            entity.setResourceType(req.getResourceType());
            entity.setCode(req.getCode());
            entity.setName(req.getName());
            entity.setBinaryBit(req.getBinaryBit() != null ? req.getBinaryBit() : 0L);
            entity.setInheritMask(req.getInheritMask() != null ? req.getInheritMask() : 0L);
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
        List<PcOperationPermission> entities = mapper.selectList(new LambdaQueryWrapper<PcOperationPermission>()
            .eq(PcOperationPermission::getTenantId, req.getTenantId())
            .in(PcOperationPermission::getId, req.getIds())
            .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcOperationPermission entity : entities) {
            PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
            mapper.updateById(entity);
        }
    }

    private OperationPermissionVo toVo(PcOperationPermission e) {
        OperationPermissionVo vo = new OperationPermissionVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
