FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

RUN mkdir /data
RUN chmod 0777 /data
ENV STORAGE_DIRECTORY /data

EXPOSE 8080
ENV HTTP_PORT 8080

COPY target/pierone.jar /
CMD java $(java-dynamic-memory-opts) -jar /pierone.jar
