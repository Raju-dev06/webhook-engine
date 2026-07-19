package com.webhook.engine.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {
    Page<DeliveryRecord> findByEventMessageId(Long eventMessageId, Pageable pageable);
    Page<DeliveryRecord> findByEndpointId(Long endpointId, Pageable pageable);
}
