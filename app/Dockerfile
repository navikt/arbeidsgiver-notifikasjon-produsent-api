FROM gcr.io/distroless/java21-debian12
COPY target/produsent-api/app.jar app.jar
COPY target/produsent-api/lib lib

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=88"

CMD ["app.jar"]
