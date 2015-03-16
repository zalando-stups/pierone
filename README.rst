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

    $ lein ring server

The web server will run on port 3000.

Testing
=======

.. code-block:: bash

    $ docker pull busybox
    $ docker tag busybox localhost:3000/example/foobar:1.0
    $ docker push localhost:3000/example/foobar:1.0
    $ docker pull localhost:3000/example/foobar:1.0


.. _Leiningen: http://leiningen.org/
