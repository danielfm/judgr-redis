# Redis Support for Judgr

[![Build Status](https://secure.travis-ci.org/danielfm/judgr-redis.png?branch=master)](http://travis-ci.org/danielfm/judgr-redis)

This project adds [Redis](http://redis.io) support for
[Judgr](http://danielfm.github.com/judgr/), a naïve Bayes classifier
library written in Clojure.

## Getting Started

Add the following dependencies to your _project.clj_ file:

````clojure

[judgr "0.1.1"]
[judgr/redis "0.1.0"]
````

Then, require the `judgr.redis.db` module and adjust the settings in
order to create your classifier:

````clojure

(ns your-ns
  (:use [judgr.core]
        [judgr.settings]
        [judgr.redis.db]))

(def new-settings
  (update-settings settings
                   [:database :type] :redis
                   [:database :redis] {:host     "localhost"
                                       :port     6379
                                       :auth?    false
                                       :password ""}))

(def classifier (classifier-from new-settings))

````

Doing this, all data will be stored in the specified Redis instance.

## License

Copyright (C) Daniel Fernandes Martins

Distributed under the New BSD License. See COPYING for further details.
