package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.engine.query.AdmissionConfigurationException;
import cn.ac.fage.accessmesh.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdmissionConfigurationExceptionHandlerTest {
    @RestController
    static class StubController {
        @PostMapping("/stub/admission")
        public void trigger() { throw new AdmissionConfigurationException("unknown operation"); }
    }

    @Test
    void should_reportConfigCodeInServiceEnvelope_whenAdmissionRequirementIsInvalid() throws Exception {
        MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler(), new AdmissionConfigurationExceptionHandler()).build()
            .perform(post("/stub/admission").contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20071));
    }
}
