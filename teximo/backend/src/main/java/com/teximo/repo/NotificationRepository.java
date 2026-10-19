package com.teximo.repo;

import com.teximo.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
