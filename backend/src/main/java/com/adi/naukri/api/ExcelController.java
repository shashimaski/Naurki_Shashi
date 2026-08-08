package com.adi.naukri.api;

import com.adi.naukri.excel.EmailExcelParser;
import com.adi.naukri.excel.ExcelFormatException;
import com.adi.naukri.excel.ParsedEmailRow;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * POST /api/parse-excel — accepts an xlsx upload and returns parsed email rows.
 *
 * Created by: Adikarthik Gupta C B
 */
@RestController
@RequestMapping("/api")
public class ExcelController {

    private final EmailExcelParser parser;

    public ExcelController(EmailExcelParser parser) {
        this.parser = parser;
    }

    @PostMapping(value = "/parse-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<ParsedEmailRow> parseExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return parser.parse(file.getInputStream());
    }

    @ExceptionHandler(ExcelFormatException.class)
    public ResponseEntity<Map<String, String>> handleExcelFormatException(ExcelFormatException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", ex.getMessage()));
    }
}
