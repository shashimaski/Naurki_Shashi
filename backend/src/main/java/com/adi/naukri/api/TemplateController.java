package com.adi.naukri.api;

import com.adi.naukri.excel.TemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TemplateController {
    private final TemplateBuilder builder;
    public TemplateController(TemplateBuilder builder) { this.builder = builder; }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = builder.build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"naukri-emails-template.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }
}
