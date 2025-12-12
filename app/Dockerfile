FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21
COPY target/produsent-api/app.jar app.jar
COPY target/produsent-api/lib lib

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=88"

CMD ["-jar", "app.jar"]
