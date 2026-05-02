package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserQuery;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.UserService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef.SYS_USER;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;

    public UserServiceImpl(SysUserMapper userMapper, SysUserOrgMapper userOrgMapper) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
    }

    @Override
    @Transactional
    public Long createUser(UserCreateReq req) {
        SysUser existing = userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_USER.USERNAME.eq(req.username()))
                .and(SYS_USER.DELETE_FLAG.eq(0))
        );
        if (existing != null) {
            throw new BizException(AdminErrorCode.USER_ALREADY_EXISTS.getCode(), AdminErrorCode.USER_ALREADY_EXISTS.getMessage());
        }
        if (req.phone() != null) {
            SysUser phoneUser = userMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(SYS_USER.PHONE.eq(req.phone()))
                    .and(SYS_USER.DELETE_FLAG.eq(0))
            );
            if (phoneUser != null) {
                throw new BizException(AdminErrorCode.PHONE_ALREADY_EXISTS.getCode(), AdminErrorCode.PHONE_ALREADY_EXISTS.getMessage());
            }
        }

        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setPassword(BCrypt.hashpw("123456"));
        user.setStatus(req.status() != null ? req.status() : 1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleteFlag(0L);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(UserUpdateReq req) {
        SysUser user = userMapper.selectOneById(req.id());
        if (user == null || user.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setStatus(req.status());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    @Override
    @Transactional
    public void deleteUser(IdsReq req) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            if (id.equals(currentUserId)) {
                throw new BizException(AdminErrorCode.CANNOT_DELETE_SELF.getCode(), AdminErrorCode.CANNOT_DELETE_SELF.getMessage());
            }
            SysUser user = userMapper.selectOneById(id);
            if (user == null || user.getDeleteFlag() != 0L) continue;
            user.setDeleteFlag(1L);
            user.setDeletedAt(now);
            userMapper.update(user);
        }
    }

    @Override
    @Transactional
    public void enableUser(IdsReq req) {
        for (Long id : req.ids()) {
            SysUser user = userMapper.selectOneById(id);
            if (user == null || user.getDeleteFlag() != 0L) continue;
            user.setStatus(1);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.update(user);
        }
    }

    @Override
    public UserResp getUser(Long id) {
        SysUser user = userMapper.selectOneById(id);
        if (user == null || user.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        List<UserResp.OrgBrief> orgs = getUserOrgs(id);
        return new UserResp(
            user.getId(), user.getUsername(), user.getName(), user.getPhone(),
            user.getEmail(), user.getStatus(), orgs, user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    @Override
    public PaginatedResult<UserPageItemResp> pageUsers(PageReq pageReq, UserQuery query) {
        QueryWrapper<?> qw = QueryWrapper.create()
            .where(SYS_USER.DELETE_FLAG.eq(0));

        if (query != null) {
            if (query.username() != null) qw.and(SYS_USER.USERNAME.like(query.username()));
            if (query.name() != null) qw.and(SYS_USER.NAME.like(query.name()));
            if (query.phone() != null) qw.and(SYS_USER.PHONE.eq(query.phone()));
            if (query.email() != null) qw.and(SYS_USER.EMAIL.eq(query.email()));
            if (query.status() != null) qw.and(SYS_USER.STATUS.eq(query.status()));
        }

        qw.orderBy(SYS_USER.CREATED_AT.desc());

        Page<SysUser> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysUser> result = userMapper.paginate(page, qw);

        List<UserPageItemResp> items = result.getRecords().stream()
            .map(u -> {
                List<UserPageItemResp.OrgBrief> orgs = getUserPageOrgs(u.getId());
                return new UserPageItemResp(
                    u.getId(), u.getUsername(), u.getName(), u.getPhone(),
                    u.getEmail(), u.getStatus(), orgs, u.getCreatedAt()
                );
            })
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(
            items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages)
        );
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = userMapper.selectOneById(userId);
        if (user == null || user.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    private List<UserResp.OrgBrief> getUserOrgs(Long userId) {
        return userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        ).stream()
            .map(uo -> new UserResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
    }

    private List<UserPageItemResp.OrgBrief> getUserPageOrgs(Long userId) {
        return userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        ).stream()
            .map(uo -> new UserPageItemResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
    }
}
