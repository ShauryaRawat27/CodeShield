package com.codeshield.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class WebSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRenders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(containsString("Application security drift overview")));
    }

    @Test
    void apiStartsWithoutSeededApplications() throws Exception {
        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void repositoryScanPageRenders() throws Exception {
        mockMvc.perform(get("/scans/repository"))
                .andExpect(status().isOk())
                .andExpect(view().name("repository-scan"))
                .andExpect(content().string(containsString("Scan a Git repository")));
    }

    @Test
    void logAnalysisPageRenders() throws Exception {
        mockMvc.perform(get("/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("log-analysis"))
                .andExpect(content().string(containsString("Analyze application logs")));
    }

    @Test
    void historyPageRenders() throws Exception {
        mockMvc.perform(get("/scans"))
                .andExpect(status().isOk())
                .andExpect(view().name("scans"))
                .andExpect(content().string(containsString("Scan history")));
    }
}
