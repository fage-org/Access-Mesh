package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.Oauth2ClientService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.dev33.satoken.secure.BCrypt;
import com.mybatisflex.core.paginate.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OAuth2客户端管理服务实现类
 * <p>
 * 提供OAuth2客户端的CRUD操作和分页查询功能。
 * OAuth2客户端是接入系统的第三方应用，需要注册客户端ID和密钥。
 * 客户端密钥使用BCrypt加密存储，确保安全性。
 * 支持配置授权类型、回调地址、授权范围、令牌有效期等。
 * </p>
 */
@Service
public class Oauth2ClientServiceImpl implements Oauth2ClientService {

    private final SysOauth2ClientMapper oauth2ClientMapper;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param oauth2ClientMapper OAuth2客户端数据访问Mapper
     * @param permissionValidator 权限校验器，校验客户端操作权限
     */
    public Oauth2ClientServiceImpl(SysOauth2ClientMapper oauth2ClientMapper,
                                   AdminPermissionValidator permissionValidator) {
        this.oauth2ClientMapper = oauth2ClientMapper;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 创建OAuth2客户端
     * <p>
     * 注册新的OAuth2客户端应用，设置客户端ID、密钥、回调地址等。
     * 客户端密钥使用BCrypt加密存储。
     * 校验客户端ID唯一性。
     * </p>
     *
     * @param req 客户端创建请求，包含客户端基本信息
     * @return 创建成功的客户端ID
     * @throws BizException 客户端ID已存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClient(Oauth2ClientCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.OAUTH2_CLIENT, AdminOperationCode.CREATE);

        // 检查clientId是否已存在
        SysOauth2Client existing = oauth2ClientMapper.selectByClientId(TenantContextHolder.getTenantId(), req.clientId());
        if (existing != null) {
            throw new BizException(AdminErrorCode.CLIENT_ID_EXISTS.getCode(), AdminErrorCode.CLIENT_ID_EXISTS.getMessage());
        }

        SysOauth2Client client = new SysOauth2Client();
        client.setTenantId(TenantContextHolder.getTenantId());
        client.setClientId(req.clientId());
        // clientSecret 应加密存储
        client.setClientSecret(BCrypt.hashpw(req.clientSecret()));
        client.setClientName(req.clientName());
        client.setGrantTypes(req.grantTypes());
        client.setRedirectUris(req.redirectUris());
        client.setScopes(req.scopes());
        client.setAccessTokenTtl(req.accessTokenTtl() != null ? req.accessTokenTtl() : 3600);
        client.setRefreshTokenTtl(req.refreshTokenTtl() != null ? req.refreshTokenTtl() : 86400);
        client.setStatus(req.status() != null ? req.status() : 1);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        client.setDeleteFlag(0L);

        oauth2ClientMapper.insert(client);
        return client.getId();
    }

    /**
     * 更新OAuth2客户端配置
     * <p>
     * 更新客户端的名称、密钥、回调地址、授权范围等属性。
     * 执行实例级权限校验。
     * 只更新请求中非null的字段，密钥更新时重新BCrypt加密。
     * </p>
     *
     * @param req 客户端更新请求，包含客户端ID和新属性值
     * @throws BizException OAuth2客户端不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Oauth2ClientUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.OAUTH2_CLIENT,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        SysOauth2Client existing = oauth2ClientMapper.selectByIdSafe(TenantContextHolder.getTenantId(), req.id());
        if (existing == null) {
            throw new BizException(AdminErrorCode.CLIENT_NOT_FOUND.getCode(), "OAuth2客户端不存在");
        }

        // 只更新非null字段
        if (req.clientSecret() != null) {
            existing.setClientSecret(BCrypt.hashpw(req.clientSecret()));
        }
        if (req.clientName() != null) {
            existing.setClientName(req.clientName());
        }
        if (req.grantTypes() != null) {
            existing.setGrantTypes(req.grantTypes());
        }
        if (req.redirectUris() != null) {
            existing.setRedirectUris(req.redirectUris());
        }
        if (req.scopes() != null) {
            existing.setScopes(req.scopes());
        }
        if (req.accessTokenTtl() != null) {
            existing.setAccessTokenTtl(req.accessTokenTtl());
        }
        if (req.refreshTokenTtl() != null) {
            existing.setRefreshTokenTtl(req.refreshTokenTtl());
        }
        if (req.status() != null) {
            existing.setStatus(req.status());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        oauth2ClientMapper.update(existing);
    }

    /**
     * 批量删除OAuth2客户端
     * <p>
     * 执行批量实例级权限校验后软删除客户端。
     * 使用单条批量SQL提高性能。
     * </p>
     *
     * @param req ID集合请求，包含待删除的客户端ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClients(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.OAUTH2_CLIENT, resourceCodes, AdminOperationCode.DELETE);

        // Batch soft delete (performance fix: use single SQL instead of loop)
        LocalDateTime now = LocalDateTime.now();
        oauth2ClientMapper.softDeleteBatch(TenantContextHolder.getTenantId(), req.ids(), now);
    }

    /**
     * 获取OAuth2客户端详情响应
     * <p>
     * 根据客户端主键ID查询客户端，转换为API响应格式。
     * 不包含客户端密钥（敏感信息）。
     * </p>
     *
     * @param id 客户端主键ID
     * @return 客户端详情响应
     * @throws BizException OAuth2客户端不存在
     */
    @Override
    public Oauth2ClientResp getClientResp(Long id) {
        SysOauth2Client entity = oauth2ClientMapper.selectByIdSafe(TenantContextHolder.getTenantId(), id);
        if (entity == null) {
            throw new BizException(AdminErrorCode.CLIENT_NOT_FOUND.getCode(), "OAuth2客户端不存在");
        }
        return Oauth2ClientResp.fromEntity(entity);
    }

    /**
     * 分页查询OAuth2客户端列表
     * <p>
     * 查询系统中注册的OAuth2客户端列表，支持按客户端名称和状态过滤。
     * 按创建时间倒序排列。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页客户端列表结果
     */
    @Override
    public PaginatedResult<Oauth2ClientResp> pageClientResps(Oauth2ClientPageReq req) {
        Page<SysOauth2Client> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysOauth2Client> result = oauth2ClientMapper.paginateByCondition(
            page, TenantContextHolder.getTenantId(), req.clientName(), req.status());

        List<Oauth2ClientResp> items = result.getRecords().stream()
            .map(Oauth2ClientResp::fromEntity)
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + req.getPageSize() - 1) / req.getPageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), req.getPageNum(), req.getPageSize(), (int) totalPages));
    }
}
