package com.kad.jet;

import com.kad.jet.config.PaystackConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableConfigurationProperties(PaystackConfig.class)
public class JetApplication {

	public static void main(String[] args) {
		SpringApplication.run(JetApplication.class, args);
	}

}
