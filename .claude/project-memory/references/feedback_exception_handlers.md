---
name: "Localized @ExceptionHandler over @RestControllerAdvice"
description: "User prefers per-controller @ExceptionHandler methods rather than a global @RestControllerAdvice"
type: feedback
---

# Localized @ExceptionHandler over @RestControllerAdvice

In this project, prefer placing `@ExceptionHandler`
methods directly inside each `@RestController`
instead of centralizing them in a global
`@RestControllerAdvice`.

**Why:** The user values locality — each
controller owns the exceptions it can actually
trigger, which keeps the mapping between endpoint
behavior and HTTP error semantics explicit and
discoverable. A global advice was tried and
rejected (refactored back).

**How to apply:**
- When a new exception needs HTTP mapping, add the
  `@ExceptionHandler` method to the controller(s)
  whose endpoints can throw it. Do not add a
  handler for an exception the controller cannot
  produce (e.g. `InsufficientFundsException` does
  not belong on `AccountController`).
- Keep handler methods package-private to match
  the controllers' visibility.
- Return `ProblemDetail` (RFC 7807) built via
  `ProblemDetail.forStatusAndDetail(status,
  e.getMessage())`, and set a human-readable title
  with `setTitle(...)`. The status comes from the
  factory call — do not also annotate the handler
  with `@ResponseStatus`.
- For framework-thrown exceptions (validation,
  type mismatch, body-not-readable, 404, 405…),
  enable `spring.mvc.problemdetails.enabled=true`
  in `application.yaml` so Spring MVC's built-in
  `ResponseEntityExceptionHandler` emits
  ProblemDetail too — preferred over writing a
  per-controller handler for each framework
  exception.
- Do not introduce a `@RestControllerAdvice` in
  these modules without first checking with the
  user.
