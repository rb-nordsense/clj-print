clj-print
=======

A Clojure library that wraps the [javax.print](http://docs.oracle.com/javase/7/docs/api/javax/print/package-summary.html) API, because sometimes we
need dead tree repesentations of our data.

## TODO
- Consider whether job-map contains sufficient data
- Cut down on type hinting where possible
- Establish workers for the queue
- Create mappings for the myriad of DocFlavors/Attributes that
- Java's printing API uses so that clients don't need to type out
  extremely long names to specify the attributes of a document/print request

## Usage

``` clojure

(require '(clj-print (core :refer :all)))

;; Assumes printer name is known and that path leads to a file
(let [job (job-map "/path/to/file" "ACME Co Printer 9000")]
  (send-job job))

;; When no printer is specified, the system default is used. Also
;; works with URLs.
(let [job (job-map "http://coolwebsite.com/my_neat_document.pdf")]
  (send-job job))

```

More options can be passed to job-map to configure the job before it
is sent off.

## License

Copyright © 2014 Roberto Acevedo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
