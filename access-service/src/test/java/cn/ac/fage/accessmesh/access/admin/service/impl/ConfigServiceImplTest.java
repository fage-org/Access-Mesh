package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.ConfigUpdateReq;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * ConfigServiceImpl.updateConfig 服务端权威审计脱敏测试。
 * <p>
 * 验证按 ID 更新配置时，脱敏掩码判定完全基于入库实体的真实 configKey（服务端权威），
 * 而非客户端请求字段：实体键为密钥类（如 {@code admin.SIGNING_KEY}）时登记调用作用域
 * 使切面掩码 configValue；实体键非密钥类时不登记（保留审计可追溯性）。
 * 旧客户端只提交 id/configValue/remark、以及客户端伪造键，都无法影响该判定。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceImplTest {

    @Mock private SystemConfigMapper configMapper;
    @Mock private AdminPermissionValidator permissionValidator;

    private ConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConfigServiceImpl(configMapper, permissionValidator);
    }

    @Test
    @DisplayName("实体 configKey 为密钥类 → 登记调用作用域掩码 configValue")
    void shouldMarkConfigValueSensitive_whenEntityKeyIsSecret() {
        try (var tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);
            OperationLogRuntimeContext.clear();
            try {
                SystemConfig cfg = new SystemConfig();
                cfg.setId(42L);
                cfg.setConfigKey("admin.SIGNING_KEY");
                cfg.setConfigValue("old-secret");
                when(configMapper.selectOneByIdAndTenantId(1L, 42L)).thenReturn(cfg);

                // 模拟旧客户端：请求体只含 id/configValue/remark，无 configKey
                service.updateConfig(new ConfigUpdateReq(42L, "new-signing-secret", null));

                var snap = OperationLogRuntimeContext.snapshot();
                assertTrue(snap.sensitiveFields().contains("configvalue"),
                    "密钥类配置应在调用作用域登记掩码 configValue（不依赖客户端传键）");
            } finally {
                OperationLogRuntimeContext.clear();
            }
        }
    }

    @Test
    @DisplayName("实体 configKey 非密钥类 → 不登记，保留审计可追溯性")
    void shouldNotMarkConfigValueSensitive_whenEntityKeyIsNotSecret() {
        try (var tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);
            OperationLogRuntimeContext.clear();
            try {
                SystemConfig cfg = new SystemConfig();
                cfg.setId(7L);
                cfg.setConfigKey("admin.LOGIN_CAPTCHA_ENABLED");
                cfg.setConfigValue("true");
                when(configMapper.selectOneByIdAndTenantId(1L, 7L)).thenReturn(cfg);

                service.updateConfig(new ConfigUpdateReq(7L, "false", null));

                var snap = OperationLogRuntimeContext.snapshot();
                // 非密钥类配置未登记任何敏感字段，sensitiveFields() 为 null（空集合不落 ThreadLocal）
                assertTrue(snap.sensitiveFields() == null || !snap.sensitiveFields().contains("configvalue"),
                    "非密钥类配置不应掩码 configValue");
            } finally {
                OperationLogRuntimeContext.clear();
            }
        }
    }
}
