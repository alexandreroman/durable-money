package io.temporal.demos.durablemoney.account;

import io.temporal.demos.durablemoney.account.DlqMessage.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/admin/dlq")
class DlqAdminController {
    private final DlqAdminService dlqAdminService;

    DlqAdminController(DlqAdminService dlqAdminService) {
        this.dlqAdminService = dlqAdminService;
    }

    @GetMapping
    List<DlqMessageView> list(@RequestParam Optional<Status> status) {
        var messages = status.map(dlqAdminService::listByStatus).orElseGet(dlqAdminService::list);
        return messages.stream().map(DlqMessageView::from).toList();
    }

    @GetMapping("/{id}")
    DlqMessageView get(@PathVariable UUID id) {
        return DlqMessageView.from(dlqAdminService.get(id));
    }

    @PostMapping("/{id}/replay")
    ResponseEntity<DlqMessageView> replay(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(DlqMessageView.from(dlqAdminService.replay(id)));
    }

    @DeleteMapping("/{id}")
    DlqMessageView discard(@PathVariable UUID id) {
        return DlqMessageView.from(dlqAdminService.discard(id));
    }

    @ExceptionHandler(DlqMessageNotFoundException.class)
    ProblemDetail handleNotFound(DlqMessageNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("DLQ message not found");
        return problem;
    }

    @ExceptionHandler(DlqAlreadyResolvedException.class)
    ProblemDetail handleAlreadyResolved(DlqAlreadyResolvedException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("DLQ message already resolved");
        return problem;
    }

    record DlqMessageView(
            UUID id,
            UUID transferId,
            String originalExchange,
            String originalRoutingKey,
            String failureReason,
            int failureCount,
            String status,
            String contentType,
            String payload,
            Instant parkedAt,
            Instant updatedAt
    ) {
        static DlqMessageView from(DlqMessage m) {
            return new DlqMessageView(m.id(), m.transferId(), m.originalExchange(), m.originalRoutingKey(),
                    m.failureReason(), m.failureCount(), m.status().name(), m.contentType(),
                    m.payload(), m.parkedAt(), m.updatedAt());
        }
    }
}
