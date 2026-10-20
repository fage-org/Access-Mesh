package cn.ac.fage.accessmesh.example.task;

import cn.ac.fage.accessmesh.example.task.controller.DemoTaskController;
import cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService;
import cn.ac.fage.accessmesh.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DemoTaskControllerTest {
    @ParameterizedTest @ValueSource(strings={"* * * * * *","0 70 * * * *","@daily","0 * * * *"})
    void shouldRejectNonMinuteOrMalformedCronBeforeBusinessExecution(String cron) throws Exception {
        var service=mock(DemoTaskAppService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DemoTaskController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/example/demo-task/create").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"task\",\"cron\":\""+cron+"\",\"selection\":{\"reportCode\":\"r1\"}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(90001));
        verifyNoInteractions(service);
    }
}
