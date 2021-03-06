# Redis Support for Judgr

This project adds [Redis](http://redis.io) support for
[Judgr](http://danielfm.github.com/judgr/), a naïve Bayes classifier
library written in Clojure.

## Getting Started

Add the following dependencies to your _project.clj_ file:

````clojure

[judgr/redis "0.2.2"]
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
                   [:database :redis] {:database 0
                                       :host     "localhost"
                                       :port     6379
                                       :password nil}))

(def classifier (classifier-from new-settings))

````

Doing this, all data will be stored in the specified Redis instance.

## License

Copyright (C) Daniel Fernandes Martins

Distributed under the New BSD License. See COPYING for further details.
