=================================
Pier One - STUPS' Docker Registry
=================================

.. image:: https://travis-ci.org/zalando-stups/pierone.svg?branch=master
   :target: https://travis-ci.org/zalando-stups/pierone
   :alt: Travis CI build status

.. image:: https://coveralls.io/repos/zalando-stups/pierone/badge.svg
   :target: https://coveralls.io/r/zalando-stups/pierone
   :alt: Coveralls status

Docker registry with immutable tags, repo permissions, S3 backend and OAuth.

Development
===========

The service is written in Clojure. You need Leiningen_ installed to build or develop.

To start a web server for the application, run:

.. code-block:: bash

    $ lein repl
    (go)

The web server will run on port 8080.

Testing
=======

Running unit tests with code coverage report:

.. code-block:: bash

    $ lein cloverage

.. code-block:: bash

    $ docker pull busybox
    $ docker tag busybox localhost:8080/example/foobar:1.0
    $ docker push localhost:8080/example/foobar:1.0
    $ docker pull localhost:8080/example/foobar:1.0

Building
========

.. code-block:: bash

    $ lein uberjar
    $ docker build -t stups/pierone .

Running
=======

Pier One supports a number of environment variables to use the Amazon S3 backend.

.. code-block:: bash

    $ docker run -it -p 8080:8080 -e BACKEND=s3 -e S3_BUCKET_NAME=my-bucket -e AWS_REGION_ID=eu-central-1 stups/pierone

``BACKEND``
    The backend to use, can be "file" or "s3".
``S3_BUCKET_NAME``
    Only for S3 backend: the Amazon S3 bucket name.
``AWS_REGION_ID``
    Only for S3 backend: the AWS region ID (e.g. "eu-central-1").

.. _Leiningen: http://leiningen.org/
