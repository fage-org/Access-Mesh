package org.dromara.gateway.config.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionAuthzProperties 单元测试
 */
@DisplayName("PermissionAuthzProperties Tests")
@Tag("dev")
class PermissionAuthzPropertiesTest {

    private PermissionAuthzProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PermissionAuthzProperties();
    }

    @Test
    @DisplayName("isInGrayscale should return true when both lists are empty")
    void isInGrayscaleShouldReturnTrueWhenBothListsAreEmpty() {
        assertTrue(properties.isInGrayscale("tenant1", "/api/orders"));
        assertTrue(properties.isInGrayscale("tenant2", "/api/users"));
    }

    @Test
    @DisplayName("isInGrayscale should match tenant in grayscale list")
    void isInGrayscaleShouldMatchTenantInGrayscaleList() {
        properties.setGrayscaleTenants(Arrays.asList("tenant1", "tenant2"));

        assertTrue(properties.isInGrayscale("tenant1", "/api/any"));
        assertTrue(properties.isInGrayscale("tenant2", "/api/any"));
        assertFalse(properties.isInGrayscale("tenant3", "/api/any"));
    }

    @Test
    @DisplayName("isInGrayscale should match route in grayscale list")
    void isInGrayscaleShouldMatchRouteInGrayscaleList() {
        properties.setGrayscaleRoutes(Arrays.asList("/api/orders/**", "/api/users"));

        assertTrue(properties.isInGrayscale("any-tenant", "/api/orders/create"));
        assertTrue(properties.isInGrayscale("any-tenant", "/api/orders/query"));
        assertTrue(properties.isInGrayscale("any-tenant", "/api/users"));
        assertFalse(properties.isInGrayscale("any-tenant", "/api/products"));
    }

    @Test
    @DisplayName("isInGrayscale should require both tenant and route match when both lists are set")
    void isInGrayscaleShouldRequireBothTenantAndRouteMatch() {
        properties.setGrayscaleTenants(Arrays.asList("tenant1"));
        properties.setGrayscaleRoutes(Arrays.asList("/api/orders/**"));

        // Both match
        assertTrue(properties.isInGrayscale("tenant1", "/api/orders/create"));

        // Tenant matches but route doesn't
        assertFalse(properties.isInGrayscale("tenant1", "/api/users"));

        // Route matches but tenant doesn't
        assertFalse(properties.isInGrayscale("tenant2", "/api/orders/create"));

        // Neither matches
        assertFalse(properties.isInGrayscale("tenant2", "/api/users"));
    }

    @Test
    @DisplayName("isInGrayscale should handle wildcard route patterns")
    void isInGrayscaleShouldHandleWildcardRoutePatterns() {
        properties.setGrayscaleRoutes(Arrays.asList("/api/v1/**", "/api/health"));

        assertTrue(properties.isInGrayscale("any", "/api/v1/users"));
        assertTrue(properties.isInGrayscale("any", "/api/v1/orders/123"));
        assertTrue(properties.isInGrayscale("any", "/api/health"));
        assertFalse(properties.isInGrayscale("any", "/api/v2/users"));
    }

    @Test
    @DisplayName("default values should be correct")
    void defaultValuesShouldBeCorrect() {
        assertFalse(properties.isEnabled());
        assertTrue(properties.isFailOpen());
        assertEquals(10000, properties.getCacheMaxSize());
        assertEquals(5, properties.getCacheExpireMinutes());
        assertFalse(properties.isHttpClientEnabled());
    }
}
