package com.adi.naukri.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TemplateControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void download_returns_xlsx_with_attachment_disposition() throws Exception {
        mvc.perform(get("/api/template"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition",
               "attachment; filename=\"naukri-emails-template.xlsx\""))
           .andExpect(content().contentType(
               "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
