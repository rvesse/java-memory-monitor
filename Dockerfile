FROM eclipse-temurin:21-jdk-ubi9-minimal

RUN mkdir /scripts
COPY jvmMemoryMonitor.sh /scripts/

ENTRYPOINT ["/scripts/jvmMemoryMonitor.sh"]