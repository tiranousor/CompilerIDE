
FROM openjdk:21-jdk-slim

RUN groupadd -r appgroup --gid=1000 && \
    useradd -r -g appgroup --uid=1000 appuser

WORKDIR /app

COPY target/dispatcher.jar dispatcher.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "dispatcher.jar"]
