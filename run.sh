#!/bin/bash

if [ "$HTTP_ALLOW_PUBLIC_READ" = "true" ]; then
    echo 'INFO: Allowing image downloads without authentication!'
    extraopt="-Dhttp.api.definition.suffix=-allow-public-read"
fi

java $JAVA_OPTS $(java-dynamic-memory-opts) $(newrelic-agent) $(appdynamics-agent) -Dhystrix.command.default.execution.timeout.enabled=false -Dhystrix.threadpool.default.coreSize=50 $extraopt -jar /pierone.jar
