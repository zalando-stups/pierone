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
You will need a PostgreSQL database (database schemas are created automatically on first start).

.. code-block:: bash

    # run Pier One locally with file backend and connect to localhost PostgreSQL
    # NOTE: we simply use the "host" networking hack here to connect to the localhost DB
    $ docker run -it -p 8080:8080 --net=host stups/pierone

``DB_SUBNAME``
    Postgres connection string, e.g "//pierone.foo.eu-west-1.rds.amazonaws.com:5432/pierone?ssl=true". Default is "//localhost:5432/pierone"
``DB_PASSWORD``
    Postgres password. Default is "postgres".
``DB_USER``
    Postgres user name. Default is "postgres".
``HTTP_ALLOW_PUBLIC_READ``
    Allow Docker image downloads without authentication (e.g. to run Pier One as a registry for open source projects). Default is "false".
``HTTP_TEAM_SERVICE_URL``
    URL to get team membership information by user's UID.
``HTTP_TOKENINFO_URL``
    OAuth2 token info URL (e.g. https://example.org/oauth2/tokeninfo)
``PGSSLMODE``
    Set to "verify-full" in order to fully verify the Postgres SSL cert.
``STORAGE_S3_BUCKET``
    Only for S3 backend: the Amazon S3 bucket name.

See the `STUPS Installation Guide section on Pier One`_ for details about deploying Pier One into your AWS account.

.. _Leiningen: http://leiningen.org/
.. _STUPS Installation Guide section on Pier One: http://docs.stups.io/en/latest/installation/service-deployments.html#pier-one

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
