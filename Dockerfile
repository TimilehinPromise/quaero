# Consumes a jar built by CI rather than running Maven here: the deployment host shares its
# cores with Kokoro, and a build competing with synthesis is what stalls a streamed response.
# Layer order is cache order: dependencies change rarely, application classes change every
# build. Extracting the layered jar keeps a code-only change to a few megabytes of push.
FROM eclipse-temurin:21-jdk-jammy AS extractor
WORKDIR /build
COPY target/know-commute-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --launcher \
        --layers dependencies,spring-boot-loader,snapshot-dependencies,application \
        --destination extracted

FROM eclipse-temurin:21-jre-jammy

# curl is here only for the container healthcheck; the JRE image ships without one.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 1001 readcast \
    && mkdir -p /data/figure-debug \
    && chown -R readcast:readcast /data
USER readcast
WORKDIR /app

COPY --from=extractor --chown=readcast:readcast /build/extracted/dependencies/ ./
COPY --from=extractor --chown=readcast:readcast /build/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=readcast:readcast /build/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=readcast:readcast /build/extracted/application/ ./

EXPOSE 8020

# Container memory, not the host's: the JVM otherwise sizes its heap against the whole box and
# is killed when the cgroup limit bites. Synthesis holds whole MP3s in memory, so this matters.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://localhost:8020/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
