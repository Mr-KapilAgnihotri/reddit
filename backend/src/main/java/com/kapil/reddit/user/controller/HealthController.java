package com.kapil.reddit.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/users/health")
    public String health(){
        return "Security installed and running fine";
    }
}
