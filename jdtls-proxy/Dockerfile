# stage 1: JDT LS
FROM eclipse-temurin:21-jre-alpine AS jdtls
RUN wget -q \
     https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz \
     -O /tmp/jdtls.tgz \
 && mkdir /jdtls \
 && tar -xzf /tmp/jdtls.tgz -C /jdtls --strip-components=1


# stage 2: proxy
FROM eclipse-temurin:21-jre-alpine
COPY --from=jdtls /jdtls /jdtls
COPY target/jdtls-proxy.jar /app/app.jar
WORKDIR /workspace
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
