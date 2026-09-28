package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.engine.controller.PermAuthController;
import cn.ac.fage.accessmesh.access.engine.service.PermissionAdmissionAppService;
import cn.ac.fage.accessmesh.access.engine.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.engine.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.resource.controller.ServiceConfigController;
import cn.ac.fage.accessmesh.access.resource.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceSyncAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyInterfaceRetirementTest {
    @Test
    void shouldReturn404WithoutExecutingServices_whenLegacyEndpointsAreCalled() throws Exception {
        var checks = mock(PermissionCheckAppService.class);
        var queries = mock(PermissionQueryAppService.class);
        var admission = mock(PermissionAdmissionAppService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PermAuthController(checks, queries, admission)).build();
        String request = """
            {"subjectTypeCode":"USER","subjectExternalId":"100","serviceCode":"example-service",
             "httpMethod":"POST","path":"/api/example/report/view"}
            """;
        mvc.perform(post("/api/access/auth/check-interface").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/access/auth/interface-snapshot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectTypeCode\":\"USER\",\"subjectExternalId\":\"100\",\"serviceCode\":\"example-service\"}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(checks, queries, admission);
    }

    @Test
    void shouldRejectRemovedModeField_whenSavingServiceConfiguration() {
        assertThatThrownBy(() -> new ObjectMapper().readValue("""
            {"serviceCode":"example-service","name":"Example","apiAuthMode":"LEGACY_API"}
            """, ServiceConfigReq.class)).isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void shouldReturn404_whenLegacyInterfaceSyncIsCalled() throws Exception {
        var config = mock(ServiceConfigAppService.class);
        var sync = mock(ServiceSyncAppService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ServiceConfigController(config, sync)).build();
        mvc.perform(post("/api/access/service-config/sync").contentType(MediaType.APPLICATION_JSON)
            .content("{\"serviceCode\":\"example-service\",\"syncMode\":\"FULL\",\"groups\":[]}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(config, sync);
    }
}
