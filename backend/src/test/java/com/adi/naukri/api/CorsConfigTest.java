package com.adi.naukri.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired MockMvc mvc;

    @Test
    void preflight_from_file_origin_is_allowed() throws Exception {
        mvc.perform(options("/api/jobs")
                .header("Origin", "null")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
           .andExpect(status().isOk())
           .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_from_localhost_is_allowed() throws Exception {
        mvc.perform(options("/api/jobs")
                .header("Origin", "http://127.0.0.1:12345")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
           .andExpect(status().isOk())
           .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
