package com.kad.jet.user;

import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner adminInsert(UserRepository userRepository,
                                  PasswordEncoder encoder,
                                  @Value("${admin.password}") String password,
                                  @Value("${admin.email}") String email){

        return args -> {
            if (!userRepository.findByEmailIgnoreCase(email).isPresent()){
                userRepository.save(
                        _User
                                .builder()
                                .email(email)
                                .passwordHashed(encoder.encode(password))
                                .createdAt(LocalDateTime.now())
                                .fullName("admin jet")
                                .isActive(true)
                                .isBanned(false)
                                .userRole(UserRole.SUPER_ADMIN)
                                .build()
                );
            }
        };
    }
}
