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
- [Temporal Spring Boot starter: activities need @Component AND register-activity-beans: true](references/feedback_temporal_starter_activity_beans.md) — @ActivityImpl alone does not register activities under the starter
- [Slim jlink JREs for Temporal apps need jdk.management](references/feedback_jlink_temporal_modules.md) — Worker static initializer needs com.sun.management.OperatingSystemMXBean from jdk.management
- [Do not add CDS to Dockerfiles](references/feedback_no_cds.md) — skip Spring Boot CDS training runs in module Dockerfiles; tutorial clarity beats marginal startup gains
- [Keep application-dev.yaml in every module](references/feedback_keep_application_dev_yaml.md) — preserve per-module dev profile files even if no automation activates them
- [Keep spring-boot-devtools in every module](references/feedback_keep_devtools.md) — devtools stays as runtime/optional in every pom.xml for local IDE workflows
- [Keep workflow-packages: io.temporal.demos broad](references/feedback_temporal_workflow_packages_broad.md) — use the wider scan root for Temporal workflow auto-discovery, not a tighter sub-package
- [Reference nested types by simple name](references/feedback_unqualified_nested_types.md) — import nested records/enums so they read as DebitInput, not AccountActivities.DebitInput; inherited interface members need no import
- [Migration from JPA/Hibernate to Spring JdbcClient](references/project_jdbc_client_migration.md) — all modules migrated to JdbcClient + records + schema.sql as of 2026-05-06
- [Bind OffsetDateTime, not Instant, with JdbcClient on PostgreSQL](references/feedback_jdbc_instant_binding.md) — pgjdbc cannot infer SQL type for Instant; convert via .atOffset(ZoneOffset.UTC) at bind site, keep domain types as Instant
- [Register Saga compensations AFTER the activity completes](references/feedback_saga_register_after_activity.md) — Temporal: pre-registering reverseDebit caused a failed 5000 transfer to credit the source 800→5800
- [Update README LOC table after code/config changes](references/feedback_update_loc_table.md) — refresh the 'Lines of code' table in README.md after editing Java sources or config files in any module
- [Spring Boot 4 ships Jackson 3 (tools.jackson.*)](references/project_spring_boot_4_jackson_3.md) — import tools.jackson.databind.ObjectMapper; com.fasterxml.jackson.databind is NOT on the classpath
