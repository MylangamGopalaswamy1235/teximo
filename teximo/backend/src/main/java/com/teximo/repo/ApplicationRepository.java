package com.teximo.repo;

import com.teximo.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByStudentIdOrderByAppliedAtDesc(Long studentId);
    boolean existsByStudentIdAndDriveId(Long studentId, Long driveId);
    List<Application> findByDriveId(Long driveId);
}
