package com.kad.jet.config.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KeepAliveController {

    @GetMapping("api/ping")
    public ResponseEntity<?> ping(){
        return ResponseEntity.ok("ok");
    }
}
