package com.moonevue.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = {
                "com.moonevue.gateway",
                "com.moonevue.core"
        }
)
@EntityScan(basePackages = {
        "com.moonevue.core.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.moonevue.core.repository"
})
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
