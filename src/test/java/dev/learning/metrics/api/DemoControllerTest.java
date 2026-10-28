package dev.learning.metrics.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetingReturnsMessage() throws Exception {
        mockMvc.perform(get("/api/greeting").param("name", "Prometheus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, Prometheus!"));
    }

    @Test
    void computeBoundsAndReturnsResult() throws Exception {
        mockMvc.perform(get("/api/compute").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iterations").value(1000))
                .andExpect(jsonPath("$.checksum").isNumber());
    }

    @Test
    void memoryPressureIsBoundedAndCanBeCleared() throws Exception {
        mockMvc.perform(post("/api/memory-pressure").param("megabytes", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocatedMegabytes").value(1))
                .andExpect(jsonPath("$.maxHeapMegabytes").isNumber());

        mockMvc.perform(delete("/api/memory-pressure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retainedMegabytes").value(0));
    }
}
