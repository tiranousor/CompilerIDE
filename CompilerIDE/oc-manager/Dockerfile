FROM openjdk:21-jdk-slim

RUN groupadd -r appgroup --gid=1000 && \
    useradd -r -g appgroup --uid=1000 appuser

WORKDIR /app

COPY oc-manager.jar app.jar

RUN mkdir -p /app/uploads /tmp/git-clones /app/user-photos && \
    chown -R appuser:appgroup /app/uploads /tmp/git-clones /app/user-photos

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
