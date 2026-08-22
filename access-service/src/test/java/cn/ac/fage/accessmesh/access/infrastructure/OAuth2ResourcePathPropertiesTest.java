package cn.ac.fage.accessmesh.access.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OAuth2 资源服务器开放路径配置测试（T-ACCESS-013）。
 * <p>
 * 覆盖：默认值（仅 /auth/oauth2/userinfo）、Ant 通配匹配、启动防护 fail-fast
 * （覆盖 /auth/** 会话端点 / /api/perm/** 内部凭证路径 / 空模式 → 启动失败）。
 * </p>
 */
class OAuth2ResourcePathPropertiesTest {

    @Test
    @DisplayName("默认配置：仅 /auth/oauth2/userinfo（T-ACCESS-004 口径保持）")
    void defaultConfig_containsOnlyUserinfo() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.afterPropertiesSet();

        assertThat(props.getResourcePaths()).hasSize(1);
        assertThat(props.getResourcePaths().get(0).getPath())
            .isEqualTo(OAuth2ResourcePathProperties.DEFAULT_USERINFO_PATH);
        assertThat(props.match("/auth/oauth2/userinfo")).isPresent();
        assertThat(props.match("/auth/oauth2/authorize")).isEmpty();
        assertThat(props.match("/user/page")).isEmpty();
        assertThat(props.match("/api/example/resource/action")).isEmpty();
    }

    @Test
    @DisplayName("Ant 通配：/api/example/** 命中子路径（多条规则按配置顺序）")
    void antPattern_matchesSubPaths() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule wildcard =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/example/**");
        wildcard.getRequiredScopes().add("example:read");
        wildcard.setAudience("example-service");
        props.setResourcePaths(new ArrayList<>(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/auth/oauth2/userinfo"), wildcard)));
        props.afterPropertiesSet();

        assertThat(props.match("/api/example/resource/action")).isPresent();
        assertThat(props.match("/api/example/other")).isPresent();
        assertThat(props.match("/api/admin/user/page")).isEmpty();
    }

    @Test
    @DisplayName("启动防护：模式覆盖 /auth/oauth2/authorize（会话端点）→ 启动失败")
    void guardRejects_patternCoveringAuthorize() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/auth/oauth2/**")));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/auth/oauth2/authorize");
    }

    @Test
    @DisplayName("启动防护：模式覆盖 /auth/userinfo（会话端点）→ 启动失败")
    void guardRejects_patternCoveringSessionUserinfo() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/auth/**")));

        // Set.of 保留端点遍历顺序不定，断言不绑定具体端点，命中任一保留端点即视为防护生效
        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("覆盖平台会话端点")
            .hasMessageContaining("（JWT 分支不得覆盖）");
    }

    @Test
    @DisplayName("启动防护：模式覆盖 /api/perm/**（内部凭证路径）→ 启动失败")
    void guardRejects_patternCoveringInternalApi() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/perm/**")));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/api/perm");
    }

    @Test
    @DisplayName("评审 P2：/api/**/sync（不匹配字面量样本但命中 /api/perm/abstract-user/sync）→ 启动失败")
    void guardRejects_midPatternWildcardCoveringInternalApi() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/**/sync");
        rule.getRequiredScopes().add("example:read");
        rule.setAudience("example-service");
        props.setResourcePaths(List.of(rule));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/api/perm");
    }

    @Test
    @DisplayName("评审 P2：宽通配 /** 与 /api/* → 启动失败（可能覆盖内部凭证空间）")
    void guardRejects_broadWildcardsCoveringInternalApi() {
        // /** 同时覆盖平台会话端点，先被会话端点防护拒绝（同为启动失败，防护目标一致）
        OAuth2ResourcePathProperties rootWildcard = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule root =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/**");
        root.getRequiredScopes().add("example:read");
        root.setAudience("example-service");
        rootWildcard.setResourcePaths(List.of(root));
        assertThatThrownBy(rootWildcard::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class);

        // /api/* 静态前缀 /api 是 /api/perm 的字符前缀 → 内部凭证防护拒绝
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/*");
        rule.getRequiredScopes().add("example:read");
        rule.setAudience("example-service");
        props.setResourcePaths(List.of(rule));
        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/api/perm");
    }

    @Test
    @DisplayName("评审 P2：精确/深层深入内部空间（/api/perm/abstract-user/**、/api/per?/**）→ 启动失败")
    void guardRejects_deepOrQuestionMarkCoveringInternalApi() {
        for (String pattern : List.of("/api/perm/abstract-user/**", "/api/perm/abstract-user/sync", "/api/per?/**")) {
            OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
            OAuth2ResourcePathProperties.ResourcePathRule rule =
                OAuth2ResourcePathProperties.ResourcePathRule.exactPath(pattern);
            rule.getRequiredScopes().add("example:read");
            rule.setAudience("example-service");
            props.setResourcePaths(List.of(rule));

            assertThatThrownBy(props::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/perm");
        }
    }

    @Test
    @DisplayName("复评 P2：URI 模板变量 /api/{module}/abstract-user/sync（运行时命中内部端点）→ 启动失败")
    void guardRejects_uriTemplateCoveringInternalApi() {
        for (String pattern : List.of("/api/{module}/abstract-user/sync", "/api/{module}/**",
            "/api/{m:[a-z]+}/**")) {
            OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
            OAuth2ResourcePathProperties.ResourcePathRule rule =
                OAuth2ResourcePathProperties.ResourcePathRule.exactPath(pattern);
            rule.getRequiredScopes().add("example:read");
            rule.setAudience("example-service");
            props.setResourcePaths(List.of(rule));

            assertThatThrownBy(props::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/perm");
        }
    }

    @Test
    @DisplayName("复评 P2：无关节务路径的 URI 模板变量（/example/{id}）→ 通过防护且运行时匹配有效")
    void guardAccepts_unrelatedUriTemplate() {
        // 文档性锚点：AntPathMatcher 段内正则支持 {var}（该形态是有效配置，不是误禁对象）
        assertThat(new org.springframework.util.AntPathMatcher()
            .match("/example/{id}", "/example/123")).isTrue();

        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule template =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/example/{id}");
        template.getRequiredScopes().add("example:read");
        template.setAudience("example-service");
        props.setResourcePaths(List.of(template));
        props.afterPropertiesSet();

        assertThat(props.match("/example/123")).isPresent();
        assertThat(props.match("/api/perm/abstract-user/sync")).isEmpty();
    }

    @Test
    @DisplayName("评审 P2：无关节务通配（/api/example/**、/example/**）→ 通过启动防护（静态前缀无 /api/perm 前缀关系）")
    void guardAccepts_unrelatedBusinessPatterns() {
        for (String pattern : List.of("/api/example/**", "/example/**", "/api/example/**/action")) {
            OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
            OAuth2ResourcePathProperties.ResourcePathRule business =
                OAuth2ResourcePathProperties.ResourcePathRule.exactPath(pattern);
            business.getRequiredScopes().add("example:read");
            business.setAudience("example-service");
            props.setResourcePaths(List.of(business));

            props.afterPropertiesSet();
            // 启动防护通过即为本用例目标（规则可正常装载）
            assertThat(props.getResourcePaths()).hasSize(1);
        }
    }

    @Test
    @DisplayName("启动防护：空路径模式 → 启动失败")
    void guardRejects_blankPattern() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("")));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("空路径");
    }

    @Test
    @DisplayName("合法业务路径配置：声明 requiredScopes + audience → 通过启动防护")
    void guardAccepts_legitimateBusinessPattern() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule business =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/example/**");
        business.getRequiredScopes().add("example:read");
        business.setAudience("example-service");
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/auth/oauth2/userinfo"), business));
        props.afterPropertiesSet();

        assertThat(props.match("/api/example/resource/action")).isPresent();
    }

    @Test
    @DisplayName("启动防护（评审 P1）：业务路径缺失 requiredScopes → 启动失败（防静默放行）")
    void guardRejects_businessPathWithoutScopes() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule business =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/example/**");
        business.setAudience("example-service");
        props.setResourcePaths(List.of(business));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("requiredScopes");
    }

    @Test
    @DisplayName("启动防护（评审 P1）：业务路径缺失 audience → 启动失败（业务路径强制受众）")
    void guardRejects_businessPathWithoutAudience() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule business =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/example/**");
        business.getRequiredScopes().add("example:read");
        props.setResourcePaths(List.of(business));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("userinfo 豁免路径：不声明 requiredScopes/audience → 通过启动防护（旧令牌兼容）")
    void guardAccepts_userinfoWithoutGates() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/auth/oauth2/userinfo")));
        props.afterPropertiesSet();

        assertThat(props.match("/auth/oauth2/userinfo")).isPresent();
    }

    @Test
    @DisplayName("显式空清单合法（运维显式关闭全部开放路径，含 userinfo）")
    void emptyList_isLegal() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(new ArrayList<>());
        props.afterPropertiesSet();

        assertThat(props.match("/auth/oauth2/userinfo")).isEmpty();
    }
}
