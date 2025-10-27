package com.moonevue.auth;

import com.moonevue.core.entity.User;
import com.moonevue.core.repository.UserRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.moonevue")
@EntityScan(basePackageClasses = {User.class})
@EnableJpaRepositories(basePackageClasses = {UserRepository.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

}
