FROM navikt/java:15
COPY target/*-jar-with-dependencies.jar app.jar

USER root
RUN apt-get install -y curl

RUN mkdir -p /opt/kafka
RUN cd /opt/kafka && wget -O kafka.tgz https://downloads.apache.org/kafka/2.7.0/kafka_2.13-2.7.0.tgz
RUN cd /opt/kafka && tar --strip-components=1 -xzf kafka.tgz
RUN chmod +x /opt/kafka/bin/*
ENV PATH="/opt/kafka/bin:${PATH}"

COPY 99-create-kafka-properties.sh /init-scripts/
RUN chmod +x /init-scripts/99-create-kafka-properties.sh
RUN touch /kafka.properties
RUN chown apprunner /kafka.properties

USER apprunner

COPY --from=redboxoss/scuttle:latest /scuttle /bin/scuttle
ENV ENVOY_ADMIN_API=http://127.0.0.1:15000
ENV ISTIO_QUIT_API=http://127.0.0.1:15020
ENTRYPOINT ["scuttle", "/dumb-init", "--", "/entrypoint.sh"]