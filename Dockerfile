FROM zalando/openjdk:8u40-b09-4
MAINTAINER Henning Jacobs <henning.jacobs@zalando.de>

COPY target/pierone.jar /

RUN mkdir /data
RUN chmod 777 /data

EXPOSE 8080

ENV PORT=8080

CMD java $(java-dynamic-memory-opts) -jar /pierone.jar
