FROM maven:3-eclipse-temurin-25-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn dependency:go-offline -q
COPY src ./src
RUN --mount=type=cache,id=maven,target=/root/.m2 mvn package -DskipTests -q && \
    cp target/*.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Build a minimal JRE with only the modules a Spring Boot + JPA + JDBC app needs.
RUN jlink \
        --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.security.jgss,java.sql,java.sql.rowset,java.xml,jdk.crypto.ec,jdk.unsupported \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=zip-6 \
        --output /opt/jre

FROM alpine:3.21
RUN apk add --no-cache curl && \
    addgroup -S app && adduser -S -G app -u 1001 app
ENV JAVA_HOME=/opt/jre
ENV PATH=$JAVA_HOME/bin:$PATH
COPY --from=builder /opt/jre /opt/jre
WORKDIR /app
COPY --from=builder --chown=app:app /app/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/application/ ./
USER app
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
