FROM openjdk:21-jdk-slim

RUN apt-get update && apt-get install -y python3 python3-pip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN groupadd -r appgroup --gid=1000 && \
    useradd -r -g appgroup --uid=1000 appuser

WORKDIR /app

COPY oc-agent-python.jar worker.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 6000

ENTRYPOINT ["java", "-jar", "worker.jar"]
