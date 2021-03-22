FROM navikt/java:15
COPY target/*-jar-with-dependencies.jar app.jar

USER root
RUN apt-get install -y curl

RUN mkdir -p /opt/kafka
RUN cd /opt/kafka && wget -O kafka.tgz https://downloads.apache.org/kafka/2.7.0/kafka_2.13-2.7.0.tgz
RUN cd /opt/kafka && tar --strip-components=1 -xzf kafka.tgz
RUN chmod +x /opt/kafka/bin/*
ENV PATH="/opt/kafka/bin:${PATH}"

USER apprunner
