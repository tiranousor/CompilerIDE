
FROM openjdk:21-jdk-slim

RUN groupadd -r appgroup --gid=1000 && \
    useradd -r -g appgroup --uid=1000 appuser

WORKDIR /app

COPY oc-agent-javac.jar worker.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "worker.jar"]
