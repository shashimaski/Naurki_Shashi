package com.adi.naukri.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc contract test for POST /api/parse-excel.
 *
 * Created by: Adikarthik Gupta C B
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExcelControllerTest {

    @Autowired MockMvc mvc;

    /** Build a tiny in-memory xlsx with the given rows on the named sheet. */
    private static byte[] buildXlsx(String sheetName, String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * Happy path: upload a small in-memory xlsx with 2 valid + 1 invalid row.
     * Expects a 3-element JSON array with correct valid/invalid flags.
     */
    @Test
    void parseExcel_returns_parsed_rows() throws Exception {
        byte[] xlsx = buildXlsx("Emails", new String[][]{
            { "email",              "remarks"  },   // header row
            { "alice@example.com",  "main"     },   // valid
            { "bob@example.com",    null       },   // valid (no remarks)
            { "not-an-email",       "bad"      },   // invalid
        });

        MockMultipartFile mf = new MockMultipartFile(
            "file", "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsx
        );

        mvc.perform(multipart("/api/parse-excel").file(mf))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(3))
           .andExpect(jsonPath("$[0].email").value("alice@example.com"))
           .andExpect(jsonPath("$[0].valid").value(true))
           .andExpect(jsonPath("$[1].email").value("bob@example.com"))
           .andExpect(jsonPath("$[1].valid").value(true))
           .andExpect(jsonPath("$[2].email").value("not-an-email"))
           .andExpect(jsonPath("$[2].valid").value(false));
    }

    /**
     * Error path: upload a file that is not a valid xlsx.
     * Expects 400 Bad Request (Spring will map ExcelFormatException via default error handler or
     * the uncaught exception propagates to 500; either way, not 200 with garbage data).
     */
    @Test
    void parseExcel_bad_file_returns_error() throws Exception {
        MockMultipartFile bad = new MockMultipartFile(
            "file", "bad.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "this is not an xlsx".getBytes()
        );

        mvc.perform(multipart("/api/parse-excel").file(bad))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }
}
