package io.temporal.demos.durablemoney.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.temporal.client.WorkflowClient.start;

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
        var input = new TransferWorkflow.Input(
            transferId,
            request.sourceAccountId(),
            request.targetAccountId(),
            request.amount()
        );
        var options = WorkflowOptions.newBuilder()
            .setWorkflowId(transferId.toString())
            .setTaskQueue(TransferWorkflow.TASK_QUEUE)
            .build();
        var stub = workflowClient.newWorkflowStub(TransferWorkflow.class, options);
        start(stub::execute, input);
        return Map.of("transferId", transferId.toString());
    }

    @GetMapping("/{workflowId}")
    Map<String, Object> getTransfer(@PathVariable String workflowId) {
        var stub = workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty());
        var desc = stub.describe();
        return Map.of(
            "workflowId", workflowId,
            "status", desc.getStatus().toString()
        );
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    ProblemDetail handleNotFound(WorkflowNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Workflow not found: " + e.getExecution().getWorkflowId()
        );
        problem.setTitle("Workflow not found");
        return problem;
    }

    record NewTransfer(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}
}
