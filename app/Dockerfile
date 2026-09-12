# The build stage runs on the BUILD platform: a bootJar is bytecode and resources with no
# native code, so it is architecture-independent. This keeps a cross-architecture build
# (for example linux/amd64 from an arm64 machine) from running Gradle under emulation.
# On a native build BUILDPLATFORM == TARGETPLATFORM, so nothing changes.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /src

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY common common
COPY modules modules
COPY app app

RUN chmod +x gradlew \
    && ./gradlew :app:bootJar --no-daemon -x test -x integrationTest

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 --shell /usr/sbin/nologin foshol

WORKDIR /app

COPY --from=build --chown=foshol:foshol /src/app/build/libs/foshol-doctor.jar /app/foshol-doctor.jar

USER foshol
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=30 --start-period=90s \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health

ENTRYPOINT ["java", "-jar", "/app/foshol-doctor.jar"]
