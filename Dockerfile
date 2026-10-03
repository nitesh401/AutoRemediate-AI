FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# Runtime image doubles as the sandbox build environment: JDK + Maven + git + Snyk CLI.
FROM maven:3.9-eclipse-temurin-21
RUN apt-get update \
 && apt-get install -y --no-install-recommends git nodejs npm ca-certificates \
 && npm install -g snyk \
 && rm -rf /var/lib/apt/lists/*
RUN useradd -m -u 1001 remediator && mkdir -p /work && chown remediator /work
COPY --from=build /src/target/auto-remediate-ai-*.jar /app/app.jar
USER remediator
ENV REMEDIATION_WORKSPACE=/work
VOLUME /work
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
