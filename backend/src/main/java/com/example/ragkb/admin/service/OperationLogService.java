package com.example.ragkb.admin.service;

import com.example.ragkb.admin.dto.OperationLogResponse;
import com.example.ragkb.admin.entity.OperationLog;
import com.example.ragkb.admin.model.OperationAction;
import com.example.ragkb.admin.model.OperationResourceType;
import com.example.ragkb.admin.repository.OperationLogRepository;
import com.example.ragkb.common.page.PageRequest;
import com.example.ragkb.common.page.PageResponse;
import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    public OperationLogService(
            OperationLogRepository operationLogRepository,
            UserRepository userRepository) {
        this.operationLogRepository = operationLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void record(
            Long userId,
            OperationAction action,
            OperationResourceType resourceType,
            Long resourceId) {
        operationLogRepository.save(new OperationLog(userId, action, resourceType, resourceId, currentIp()));
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationLogResponse> search(
            Long userId,
            OperationAction action,
            OperationResourceType resourceType,
            Long resourceId,
            LocalDate from,
            LocalDate to,
            PageRequest pageRequest) {
        OffsetDateTime fromTime = from == null ? null : from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Specification<OperationLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (resourceType != null) {
                predicates.add(criteriaBuilder.equal(root.get("resourceType"), resourceType));
            }
            if (resourceId != null) {
                predicates.add(criteriaBuilder.equal(root.get("resourceId"), resourceId));
            }
            if (fromTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), toTime));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<OperationLog> page = operationLogRepository.findAll(specification, pageable);

        Set<Long> userIds = page.getContent().stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> usernames = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        Page<OperationLogResponse> responses = page.map(log -> new OperationLogResponse(
                log.getId(),
                log.getUserId(),
                log.getUserId() == null ? null : usernames.get(log.getUserId()),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getIp(),
                log.getCreatedAt()));
        return PageResponse.from(responses);
    }

    private String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            String ip = forwarded != null && !forwarded.isBlank()
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();
            if (ip != null && ip.length() > 45) {
                return ip.substring(0, 45);
            }
            return ip;
        }
        return null;
    }
}
