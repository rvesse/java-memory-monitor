# java-loiter
ARG JDK_VERSION=21
FROM eclipse-temurin:${JDK_VERSION}-jdk-ubi9-minimal

RUN mkdir /app
COPY *.java loiter.sh /app/
RUN javac /app/*.java

ENTRYPOINT ["/app/loiter.sh"]