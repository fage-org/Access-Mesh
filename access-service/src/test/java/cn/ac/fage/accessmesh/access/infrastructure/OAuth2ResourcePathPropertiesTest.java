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
 * 覆盖：默认值（仅 userinfo）、Ant 通配匹配、启动防护 fail-fast
 * （覆盖平台会话端点 / 空模式 / 业务路径缺门禁 → 启动失败）。
 * T-ACCESS-042：原「内部凭证路径重叠」防护退役——URL 单命名空间后开放路径与内部
 * 凭证路径同住 /api/access/**，合法流量恒经 Gateway 注入密钥，OAuth2 JWT 验证在
 * InternalApiSecretInterceptor 之后独立执行，双凭证并存不构成机制冲突；本类
 * 「深路径/中段通配/宽通配/URI 模板」四组用例随之改锁新语义（曾为旧防护拒绝项，
 * 退役后必须通过——旧实现下必红，为退役回归锁）。
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
        assertThat(props.match("/api/access/auth/oauth2/userinfo")).isPresent();
        assertThat(props.match("/api/access/auth/oauth2/authorize")).isEmpty();
        assertThat(props.match("/api/access/user/page")).isEmpty();
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
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/userinfo"), wildcard)));
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
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/**")));

        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/api/access/auth/oauth2/authorize");
    }

    @Test
    @DisplayName("启动防护：模式覆盖 /auth/userinfo（会话端点）→ 启动失败")
    void guardRejects_patternCoveringSessionUserinfo() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/**")));

        // Set.of 保留端点遍历顺序不定，断言不绑定具体端点，命中任一保留端点即视为防护生效
        assertThatThrownBy(props::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("覆盖平台会话端点")
            .hasMessageContaining("（JWT 分支不得覆盖）");
    }

    @Test
    @DisplayName("T-ACCESS-042 退役回归锁：中段通配 /api/**/sync 通过防护并命中内部深路径（旧防护必拒）")
    void guardAccepts_midPatternWildcard_afterInternalOverlapGuardRetired() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/**/sync");
        rule.getRequiredScopes().add("example:read");
        rule.setAudience("example-service");
        props.setResourcePaths(List.of(rule));
        props.afterPropertiesSet();

        assertThat(props.match("/api/access/abstract-user/sync")).isPresent();
    }

    @Test
    @DisplayName("T-ACCESS-042 退役回归锁：/api/* 宽通配通过；/** 因覆盖会话端点仍拒")
    void guardAccepts_apiSingleWildcard_butRootWildcardStillRejected() {
        // /** 同时覆盖平台会话端点，被会话端点防护拒绝（防护目标不变）
        OAuth2ResourcePathProperties rootWildcard = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule root =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/**");
        root.getRequiredScopes().add("example:read");
        root.setAudience("example-service");
        rootWildcard.setResourcePaths(List.of(root));
        assertThatThrownBy(rootWildcard::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("覆盖平台会话端点");

        // /api/* 单段通配不覆盖任何保留端点 → 通过（旧「内部凭证前缀」防护的拒绝项退役）
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/*");
        rule.getRequiredScopes().add("example:read");
        rule.setAudience("example-service");
        props.setResourcePaths(List.of(rule));
        props.afterPropertiesSet();
        assertThat(props.getResourcePaths()).hasSize(1);
    }

    @Test
    @DisplayName("T-ACCESS-042 退役回归锁：精确/深层/单字符通配深入命名空间（旧防护必拒项）→ 通过")
    void guardAccepts_deepOrQuestionMarkPatterns_afterInternalOverlapGuardRetired() {
        for (String pattern : List.of("/api/access/abstract-user/**", "/api/access/abstract-user/sync", "/api/per?/**")) {
            OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
            OAuth2ResourcePathProperties.ResourcePathRule rule =
                OAuth2ResourcePathProperties.ResourcePathRule.exactPath(pattern);
            rule.getRequiredScopes().add("example:read");
            rule.setAudience("example-service");
            props.setResourcePaths(List.of(rule));

            props.afterPropertiesSet();
            assertThat(props.getResourcePaths()).hasSize(1);
        }
    }

    @Test
    @DisplayName("T-ACCESS-042 退役回归锁：URI 模板变量模式通过（覆盖保留端点的形态仍拒）")
    void guardAccepts_uriTemplatePatterns_butReservedCoverageStillRejected() {
        // 深层精确模板不覆盖任何保留端点 → 通过（旧「内部凭证前缀」防护的拒绝项退役）
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        OAuth2ResourcePathProperties.ResourcePathRule deep =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/{module}/abstract-user/sync");
        deep.getRequiredScopes().add("example:read");
        deep.setAudience("example-service");
        props.setResourcePaths(List.of(deep));
        props.afterPropertiesSet();
        assertThat(props.getResourcePaths()).hasSize(1);

        // 宽模板 /api/{module}/**、/api/{m:[a-z]+}/** 运行时命中 /api/access/auth/userinfo
        // 等平台会话端点 → 仍被会话端点防护拒绝（防护语义与新旧前缀无关）
        for (String pattern : List.of("/api/{module}/**", "/api/{m:[a-z]+}/**")) {
            OAuth2ResourcePathProperties reserved = new OAuth2ResourcePathProperties();
            OAuth2ResourcePathProperties.ResourcePathRule coversReserved =
                OAuth2ResourcePathProperties.ResourcePathRule.exactPath(pattern);
            coversReserved.getRequiredScopes().add("example:read");
            coversReserved.setAudience("example-service");
            reserved.setResourcePaths(List.of(coversReserved));
            assertThatThrownBy(reserved::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("覆盖平台会话端点");
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
        assertThat(props.match("/api/access/abstract-user/sync")).isEmpty();
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
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/userinfo"), business));
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
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/userinfo")));
        props.afterPropertiesSet();

        assertThat(props.match("/api/access/auth/oauth2/userinfo")).isPresent();
    }

    @Test
    @DisplayName("显式空清单合法（运维显式关闭全部开放路径，含 userinfo）")
    void emptyList_isLegal() {
        OAuth2ResourcePathProperties props = new OAuth2ResourcePathProperties();
        props.setResourcePaths(new ArrayList<>());
        props.afterPropertiesSet();

        assertThat(props.match("/api/access/auth/oauth2/userinfo")).isEmpty();
    }
}
