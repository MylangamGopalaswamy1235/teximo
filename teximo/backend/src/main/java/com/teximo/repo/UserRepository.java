package com.teximo.repo;

import com.teximo.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findFirstByRole(Role role);
    List<User> findByRole(Role role);
    boolean existsByEmail(String email);
}
