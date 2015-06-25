FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

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

CMD /run.sh
