package com.teximo.repo;

import com.teximo.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByStudentIdOrderByScheduledAtAsc(Long studentId);
    boolean existsByStudentIdAndDriveId(Long studentId, Long driveId);
}
