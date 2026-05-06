---
name: "RabbitMQ under rootless podman: pin user: rabbitmq"
description: "When running rabbitmq:4-management-alpine under rootless podman, set user: rabbitmq in compose so the entrypoint skips the chown+gosu branch"
type: feedback
---

# RabbitMQ under rootless podman: pin user: rabbitmq

In any compose file in this repo that runs
`rabbitmq:4-management-alpine`, set
`user: rabbitmq` on the service.

**Why:** the default entrypoint runs as root,
writes `/var/lib/rabbitmq/.erlang.cookie`,
`chown`s it to user `rabbitmq`, then `gosu`-switches.
Under rootless podman the UID remapping makes that
ownership unreadable to the runtime user, and the
container fails with
`Error when reading /var/lib/rabbitmq/.erlang.cookie: eacces`.
Starting as `rabbitmq` makes the entrypoint's
`if [ "$(id -u)" = '0' ]` branch a no-op — no chown,
no gosu, the cookie is created and read by the same
user.

**How to apply:** whenever adding or editing a
RabbitMQ service in any module's `compose.yaml`,
include `user: rabbitmq` next to `image:`. Same
treatment likely applies to any other Alpine
official image whose entrypoint does the
root-then-gosu dance (postgres in this repo runs
fine, so leave it alone unless it breaks).
