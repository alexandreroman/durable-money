FROM eclipse-temurin:25-jdk-noble AS builder
RUN rm -f /etc/apt/apt.conf.d/docker-clean && \
    echo 'Binary::apt::APT::Keep-Downloaded-Packages "true";' > /etc/apt/apt.conf.d/keep-cache
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends maven
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -q
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -q

FROM eclipse-temurin:25-jre-noble
RUN addgroup --system app && adduser --system --ingroup app --uid 1001 app
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
