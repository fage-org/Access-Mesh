package cn.ac.fage.accessmesh.example.task;

import cn.ac.fage.accessmesh.example.task.controller.DemoExecutionController;
import cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService;
import cn.ac.fage.accessmesh.common.exception.GlobalExceptionHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DemoExecutionControllerTest {
    @Test void shouldReturnCsvWithNoStoreAndKeepFailuresInEnvelope() throws Exception {
        var service=mock(DemoExecutionAppService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DemoExecutionController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        var key=UUID.randomUUID(); var body="{\"executionKey\":\""+key+"\"}";
        when(service.download(any(),eq(key))).thenReturn(new byte[]{1,2,3});
        mvc.perform(post("/api/example/demo-execution/download").header("X-Tenant-Id","1").header("X-User-Id","42")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        when(service.download(any(),eq(key))).thenThrow(new BizException(30107,"Result unavailable"));
        mvc.perform(post("/api/example/demo-execution/download").header("X-Tenant-Id","1").header("X-User-Id","42")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(jsonPath("$.code").value(30107));
    }
    @Test void shouldValidateNestedSelectionAndPagingBeforeInvokingService() throws Exception {
        var service=mock(DemoExecutionAppService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DemoExecutionController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/example/demo-snapshot/submit").contentType(MediaType.APPLICATION_JSON)
            .content("{\"triggerKey\":\""+UUID.randomUUID()+"\",\"selection\":{\"reportCode\":\"r1\",\"fieldIds\":[]}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/example/demo-execution/page").contentType(MediaType.APPLICATION_JSON).content("{\"pageSize\":201}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
