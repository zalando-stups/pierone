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

    $ lein do uberjar, docker build

Running
=======

Pier One supports a number of environment variables to use the Amazon S3 backend.

.. code-block:: bash

    $ docker run -it -p 8080:8080 -e BACKEND_S3_BUCKET_NAME=my-bucket stups/pierone

``BACKEND_S3_BUCKET_NAME``
    Only for S3 backend: the Amazon S3 bucket name.

.. _Leiningen: http://leiningen.org/

License
=======

Copyright © 2015 Zalando SE

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
