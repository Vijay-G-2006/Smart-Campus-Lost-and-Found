package com.findit.service;

import com.findit.domain.AuditLog;
import com.findit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records security and workflow relevant events.
 *
 * <p>Only safe metadata is stored - never passwords, tokens, claim texts or
 * private verification answers.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String entityType, Object entityId, Map<String, String> metadata) {
        String safeMetadata = null;
        if (metadata != null && !metadata.isEmpty()) {
            try {
                safeMetadata = objectMapper.writeValueAsString(new LinkedHashMap<>(metadata));
            } catch (JacksonException e) {
                log.warn("Could not serialise audit metadata for action {}", action);
            }
        }
        auditLogRepository.save(new AuditLog(actorId, action, entityType,
                entityId == null ? null : String.valueOf(entityId), safeMetadata));
    }
}
