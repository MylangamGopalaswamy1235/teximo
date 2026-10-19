package com.teximo;

import com.teximo.model.Role;
import com.teximo.model.User;
import com.teximo.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Objects;

@SpringBootApplication
public class TeximoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeximoApplication.class, args);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Keeps the admin bootstrap account synchronized with its configured credentials. */
    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder,
                                @Value("${teximo.admin.email}") String email,
                                @Value("${teximo.admin.password}") String password) {
        return args -> {
            String configuredEmail = email.toLowerCase(Locale.ROOT);
            User admin = users.findFirstByRole(Role.ADMIN).orElseGet(() -> {
                User created = new User();
                created.setName("Placement Admin");
                created.setRole(Role.ADMIN);
                return created;
            });
            User existingAccount = users.findByEmail(configuredEmail).orElse(null);
            if (existingAccount != null && !Objects.equals(existingAccount.getId(), admin.getId())) {
                throw new IllegalStateException("Configured admin email is already used by another account.");
            }

            boolean changed = !configuredEmail.equalsIgnoreCase(admin.getEmail())
                    || !encoder.matches(password, admin.getPasswordHash());
            if (changed || admin.getId() == null) {
                admin.setEmail(configuredEmail);
                admin.setPasswordHash(encoder.encode(password));
                users.save(admin);
                System.out.println("Synchronized configured admin account.");
            }
        };
    }
}
