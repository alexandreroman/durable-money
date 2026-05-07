---
name: "Spring Boot 4 HttpClientSettings rename and separate artifact"
description: "In Boot 4, ClientHttpRequestFactorySettings was renamed to HttpClientSettings and lives in a separate spring-boot-http-client artifact"
type: project
---

# Spring Boot 4 HttpClientSettings rename and separate artifact

In Spring Boot 4, the `ClientHttpRequestFactorySettings` class
from Boot 3 was renamed to `HttpClientSettings` (same package
`org.springframework.boot.http.client`). The fluent API
(`defaults()`, `withConnectTimeout(...)`, `withReadTimeout(...)`)
is preserved.

Also: the `spring-boot-http-client` artifact is no longer pulled
in transitively by `spring-boot-starter-webmvc` — it must be
declared explicitly when configuring `RestClient` timeouts via
`ClientHttpRequestFactoryBuilder`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-http-client</artifactId>
</dependency>
```

**Why:** Discovered when adding connect/read timeouts to the
`RestClient` beans in `2-microservices/transfer-service` and
`5-temporal/workflow`. Most online docs/snippets still reference
the Boot 3 class name, which compiles against `spring-boot`
3.x but not 4.x.

**How to apply:** When wiring `RestClient` (or `RestTemplate`)
timeouts in any module of this project, use `HttpClientSettings`
not `ClientHttpRequestFactorySettings`, and ensure the module's
pom declares `spring-boot-http-client`.
