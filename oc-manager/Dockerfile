# Используем базовый образ с JDK
FROM openjdk:21-jdk-slim

# Создаём группу и пользователя
RUN groupadd -r appgroup --gid=1000 && \
    useradd -r -g appgroup --uid=1000 appuser

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем собранный JAR-файл в образ
COPY target/oc-manager.jar app.jar

# Создаём необходимые директории и устанавливаем права доступа
RUN mkdir -p /app/uploads /tmp/git-clones /app/user-photos && \
    chown -R appuser:appgroup /app/uploads /tmp/git-clones /app/user-photos

# Переключаемся на пользователя appuser
USER appuser

# Открываем порт, на котором работает приложение
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]
