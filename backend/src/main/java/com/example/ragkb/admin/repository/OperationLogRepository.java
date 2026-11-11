package com.example.ragkb.admin.repository;

import com.example.ragkb.admin.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationLogRepository
        extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {
}
