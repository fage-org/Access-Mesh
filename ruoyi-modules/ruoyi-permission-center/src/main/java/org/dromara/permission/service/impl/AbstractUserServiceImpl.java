package org.dromara.permission.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.UserPageReq;
import org.dromara.permission.domain.dto.UserSaveReq;
import org.dromara.permission.domain.vo.AbstractUserVo;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.service.AbstractUserService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.TypeDefinitionReader;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbstractUserServiceImpl implements AbstractUserService {

    private final PcAbstractUserMapper mapper;
    private final TypeDefinitionReader typeDefinitionReader;

    @Override
    public TableDataInfo<AbstractUserVo> page(UserPageReq req) {
        if (req == null || req.getTenantId() == null) {
            return TableDataInfo.build();
        }
        int pageNum = req.getPageNum() != null && req.getPageNum() > 0 ? req.getPageNum() : 1;
        int pageSize = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        LambdaQueryWrapper<PcAbstractUser> q = new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, req.getTenantId())
            .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (req.getUserType() != null) {
            q.eq(PcAbstractUser::getUserType, req.getUserType());
        }
        if (StrUtil.isNotBlank(req.getExternalId())) {
            q.like(PcAbstractUser::getExternalId, req.getExternalId());
        }
        if (StrUtil.isNotBlank(req.getName())) {
            q.like(PcAbstractUser::getName, req.getName());
        }
        q.orderByDesc(PcAbstractUser::getId);
        Page<PcAbstractUser> page = mapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<AbstractUserVo> rows = page.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return new TableDataInfo<>(rows, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UserSaveReq req) {
        if (req == null || req.getTenantId() == null) {
            return;
        }
        typeDefinitionReader.assertTypeValueExists(req.getTenantId(), null, "user_type", req.getUserType(), "无效的用户类型");
        LocalDateTime now = LocalDateTime.now();
        if (req.getId() != null) {
            PcAbstractUser entity = mapper.selectOne(new LambdaQueryWrapper<PcAbstractUser>()
                .eq(PcAbstractUser::getId, req.getId())
                .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null) {
                entity.setUserType(req.getUserType());
                entity.setExternalId(req.getExternalId());
                entity.setName(req.getName());
                entity.setExtra(req.getExtra() != null ? req.getExtra() : "{}");
                entity.setUpdatedAt(now);
                mapper.updateById(entity);
            }
        } else {
            PcAbstractUser entity = new PcAbstractUser();
            entity.setTenantId(req.getTenantId());
            entity.setUserType(req.getUserType());
            entity.setExternalId(req.getExternalId());
            entity.setName(req.getName());
            entity.setExtra(req.getExtra() != null ? req.getExtra() : "{}");
            entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insert(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.getIds()) {
            PcAbstractUser entity = mapper.selectById(id);
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
            }
        }
    }

    private AbstractUserVo toVo(PcAbstractUser e) {
        AbstractUserVo vo = new AbstractUserVo();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
