# java-memory-monitor
ARG JDK_VERSION=21
FROM eclipse-temurin:${JDK_VERSION}-jdk-ubi9-minimal

RUN mkdir /scripts
COPY jvmMemoryMonitor.sh /scripts/

ENTRYPOINT ["/scripts/jvmMemoryMonitor.sh"]