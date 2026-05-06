package io.temporal.demos.durablemoney.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
class TransferController {

    private final WorkflowClient workflowClient;

    TransferController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> startTransfer(@RequestBody @Valid TransferRequest request) {
        var transferId = UUID.randomUUID();
        var input = new TransferInput(
            transferId,
            request.sourceAccountId(),
            request.targetAccountId(),
            request.amount()
        );
        var options = WorkflowOptions.newBuilder()
            .setWorkflowId(transferId.toString())
            .setTaskQueue(TemporalConfig.TASK_QUEUE)
            .build();
        var stub = workflowClient.newWorkflowStub(TransferWorkflow.class, options);
        WorkflowClient.start(stub::execute, input);
        return Map.of("transferId", transferId.toString());
    }

    @GetMapping("/{workflowId}")
    ResponseEntity<Map<String, Object>> getTransfer(@PathVariable String workflowId) {
        try {
            var stub = workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty());
            var desc = stub.describe();
            return ResponseEntity.ok(Map.of(
                "workflowId", workflowId,
                "status", desc.getStatus().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
