FROM eclipse-temurin:21-jdk-ubi9-minimal

RUN mkdir /app
COPY *.java loiter.sh /app/
RUN javac /app/*.java

ENTRYPOINT ["/app/loiter.sh"]