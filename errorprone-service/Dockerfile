# stage 1: JDT LS
FROM eclipse-temurin:21-jre-alpine
RUN wget -q \
     https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz \
     -O /tmp/jdtls.tgz \
 && mkdir /jdtls \
 && tar -xzf /tmp/jdtls.tgz -C /jdtls --strip-components=1


# stage 2: proxy
FROM eclipse-temurin:21-jre-alpine
COPY target/oc-errorprone-analyzer-1.0.0.jar app.jar
COPY --from=build /app/target/errorprone-service-*.jar /service/app.jar
WORKDIR /service
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
