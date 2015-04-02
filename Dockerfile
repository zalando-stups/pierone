FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

COPY target/pierone.jar /

RUN mkdir /data
RUN chmod 0777 /data

EXPOSE 8080

CMD java $(java-dynamic-memory-opts) -jar /pierone.jar
