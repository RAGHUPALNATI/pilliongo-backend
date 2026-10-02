package com.raghu.pilliongo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// A tiny "are you alive?" page for the server, monitoring tools and you.
// No login and no database, so it's cheap to call as often as needed.
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
