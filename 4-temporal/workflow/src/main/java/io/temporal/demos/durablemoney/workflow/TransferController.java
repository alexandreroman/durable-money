package io.temporal.demos.durablemoney.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    Map<String, String> startTransfer(@RequestBody @Valid NewTransfer request) {
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
    Map<String, Object> getTransfer(@PathVariable String workflowId) {
        var stub = workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty());
        try {
            var desc = stub.describe();
            return Map.of(
                "workflowId", workflowId,
                "status", desc.getStatus().toString()
            );
        } catch (io.temporal.client.WorkflowNotFoundException e) {
            throw new WorkflowNotFoundException("Workflow not found: " + workflowId);
        }
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    ProblemDetail handleNotFound(WorkflowNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Workflow not found");
        return problem;
    }

    record NewTransfer(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}
}
