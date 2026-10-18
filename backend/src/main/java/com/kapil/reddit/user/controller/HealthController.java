package com.kapil.reddit.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/users/health")
    public String health(){
        return "Security installed and running fine";
    }

    /** Public liveness probe used by the Docker Compose healthcheck and the frontend. */
    @GetMapping("/api/health")
    public java.util.Map<String, String> publicHealth() {
        return java.util.Map.of("status", "UP");
    }
}
