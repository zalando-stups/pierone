#!/bin/bash

if [ "$HTTP_ALLOW_PUBLIC_READ" = "true" ]; then
    echo 'INFO: Allowing image downloads without authentication!'
    extraopt="-Dhttp.api.definition.suffix=-allow-public-read"
fi

java $(java-dynamic-memory-opts) -Dhystrix.command.default.execution.timeout.enabled=false $extraopt -jar /pierone.jar
