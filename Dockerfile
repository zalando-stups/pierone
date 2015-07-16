FROM zalando/openjdk:8u45-b14-5

MAINTAINER Zalando SE

RUN mkdir /data
RUN chmod 0777 /data
ENV STORAGE_DIRECTORY /data

EXPOSE 8080
ENV HTTP_PORT 8080

COPY target/pierone.jar /
COPY target/scm-source.json /

CMD java $(java-dynamic-memory-opts) $(appdynamics-agent) -Dhystrix.command.default.execution.timeout.enabled=false -jar /pierone.jar
