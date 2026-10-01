package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.model.SupportMessage;
import com.raghu.pilliongo.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    public ResponseEntity<SupportMessage> submitMessage(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(
                supportService.submitMessage(auth.getName(), body.get("subject"), body.get("message")));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyMessages(Authentication auth) {
        return ResponseEntity.ok(supportService.getMyMessages(auth.getName()));
    }
}
