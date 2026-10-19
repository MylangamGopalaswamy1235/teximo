package com.teximo.repo;

import com.teximo.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
