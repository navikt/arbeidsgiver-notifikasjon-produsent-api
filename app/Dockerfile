FROM navikt/java:15
COPY target/*-jar-with-dependencies.jar app.jar

USER root
RUN apt-get install -y curl

RUN mkdir -p /opt/kafka
RUN cd /opt/kafka && wget -O kafka.tgz https://archive.apache.org/dist/kafka/2.7.1/kafka_2.13-2.7.1.tgz
RUN cd /opt/kafka && tar --strip-components=1 -xzf kafka.tgz
RUN chmod +x /opt/kafka/bin/*
ENV PATH="/opt/kafka/bin:${PATH}"

COPY 99-create-kafka-properties.sh /init-scripts/
RUN chmod +x /init-scripts/99-create-kafka-properties.sh
RUN touch /kafka.properties
RUN chown apprunner /kafka.properties

USER apprunner