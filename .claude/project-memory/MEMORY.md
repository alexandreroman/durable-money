# Project Memory

> When a new decision **contradicts** an existing
> memory note, do NOT silently override it.
> Instead: surface the conflict, quote the
> existing memory, explain how the new decision
> differs, and ask for explicit confirmation
> before updating. **Do NOT take any action** —
> no tool calls, no file writes — until confirmed.

- [Localized @ExceptionHandler over @RestControllerAdvice](references/feedback_exception_handlers.md) — prefer per-controller exception handlers; a global advice was tried and rejected
- [Nest types inside their owner class](references/feedback_nested_types.md) — narrowest scope for everything; nest records/enums inside the single class that uses them, top-level only when shared
- [RabbitMQ under rootless podman: pin user: rabbitmq](references/feedback_rabbitmq_rootless_podman.md) — set user: rabbitmq on rabbitmq:4-management-alpine to skip the entrypoint's root chown+gosu branch
- [Spring Boot 4 HttpClientSettings rename](references/project_spring_boot_4_http_client.md) — Boot 4 renamed ClientHttpRequestFactorySettings to HttpClientSettings; needs explicit spring-boot-http-client dep
