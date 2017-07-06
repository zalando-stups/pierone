FROM registry.opensource.zalan.do/stups/openjdk:latest

MAINTAINER Zalando SE

# this is a hack to put the filtered YAML file into pierone.jar
RUN apt-get update && apt-get install -y -q zip

RUN mkdir /data
RUN chmod 0777 /data
ENV STORAGE_DIRECTORY /data

EXPOSE 8080
ENV HTTP_PORT 8080

COPY run.sh /

COPY target/pierone.jar /
COPY target/scm-source.json /

COPY resources/ /resources/

RUN cat /resources/api/pierone-api.yaml | grep -v 'remove for HTTP_ALLOW_PUBLIC_READ' > /resources/api/pierone-api-allow-public-read.yaml
RUN cd /resources && zip /pierone.jar api/pierone-api-allow-public-read.yaml

CMD /run.sh
