package com.kad.jet.config.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class KeepAliveService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.public-url}")
    private String appUrl;

    @Scheduled(fixedRate = 840000)
    public void pingSelf(){
        try {
            String url = appUrl + "/api/ping";
            restTemplate.getForObject(url, String.class);
            log.info("keep-alive ping successful. Application is awake");
        }catch (Exception e){
            log.error("failed to ping self {}", e.getMessage());
        }
    }
}
