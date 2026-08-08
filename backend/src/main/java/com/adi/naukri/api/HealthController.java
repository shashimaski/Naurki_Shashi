package com.adi.naukri.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Author: Adikarthik Gupta C B
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String VERSION = "0.1.0";
    private static final String AUTHOR  = "Adikarthik Gupta C B";

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "version", VERSION, "author", AUTHOR);
    }
}
