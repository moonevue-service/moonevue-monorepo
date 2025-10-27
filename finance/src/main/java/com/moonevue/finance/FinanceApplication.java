package com.moonevue.finance;

import com.moonevue.finance.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        scanBasePackages = {
                "com.moonevue.finance",
                "com.moonevue.core"
        }
)
@EntityScan(basePackages = {
        "com.moonevue.core.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.moonevue.core.repository"
})
public class FinanceApplication {
	public static void main(String[] args) {
		SpringApplication.run(FinanceApplication.class, args);
	}
}
