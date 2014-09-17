clj-print
=======

A Clojure library that wraps the
[javax.print](http://docs.oracle.com/javase/7/docs/api/javax/print/package-summary.html)
API.

This project is unfinished, it was originally intended to be used at
my work, but ended up just being a fun exercise for me. It was an
excuse for me to try out records, multi-methods and Clojure in
general.

## TODO
- [ ] Tests
- [ ] Logging
- [ ] Map vs Record performance (structural sharing vs compiled Java classes with final member variables).
- [ ] API is still a bit unwieldy (but at least it's REPL-y!)
- [ ] Update README

## Usage

``` clojure
(require '(clj-print (core :refer :all)))
(require '(clj-print (doc-flavors :as flavors)))

;; You can get all the printers registered on your machine
;; that Java recognizes:
(printers)
;; (#<Win32PrintService Win32 Printer : Microsoft XPS Document Writer>
;;  #<Win32PrintService Win32 Printer : ImageRight Printer>
;;  #<Win32PrintService Win32 Printer : Fax>
;;  #<Win32PrintService Win32 Printer : Adobe PDF>
;;  #<Win32PrintService Win32 Printer : \\srqprint\2WSouth-Prt3>
;;  #<Win32PrintService Win32 Printer : \\SRQPRINT\2WColor-PRT2_SA>)

;; Or a specific printer: 
(printer "\\\\SRQPRINT\\2WColor-PRT2_SA")
;; #<Win32PrintService Win32 Printer : \\SRQPRINT\2WColor-PRT2_SA>

;; You can also get the system default printer by calling
;; `printer` with the :default key
(printer :default)
;; #<Win32PrintService Win32 Printer : \\srqprint\2WSouth-Prt3>

;; You can get a status seq for any registered printer:
(status (printer :default)) 
;; (#<PrinterIsAcceptingJobs accepting-jobs>
;;  #<ColorSupported supported>
;;  #<QueuedJobCount 0>
;;  #<PrinterName \\srqprint\2WSouth-Prt3>)

;; As well as the various attributes that the printer supports:
(attributes (printer :default))
;; (#<JobName Java Printing>
;;  #<RequestingUserName racevedo>
;;  #<CopiesSupported 1-9999>
;;  #<Destination file:/c:/Users/racevedo/git/p3_clj/out.prn>
;;  #<OrientationRequested portrait>
;;  #<OrientationRequested landscape>
;;  #<OrientationRequested reverse-landscape>
;;  #<PageRanges 1-2147483647>
;;  #<MediaSizeName na-letter>
;;  #<MediaSizeName na-legal>
;;  #<MediaSizeName invoice>
;;  #<MediaSizeName executive>
;;  #<MediaSizeName folio>
;;  #<MediaSizeName b>
;;  #<MediaSizeName na-9x11-envelope>
;;  #<MediaSizeName na-5x7>
;;  #<MediaSizeName na-8x10>
;;  #<Win32MediaSize Postcard (4.5 x 6")>
;;  #<Win32MediaSize 5.5 x 7">
;;  #<MediaSizeName iso-a4>
;; ... too many to list

;; Or be more specific (more coming soon), you can of
;; course just do this with `filter`:
(trays (printer :default)) 
;; (#<Win32MediaTray Form-Source>
;;  #<MediaTray manual>
;;  #<Win32MediaTray Tray 4>
;;  #<Win32MediaTray Tray 3>
;;  #<Win32MediaTray Tray 2>
;;  #<Win32MediaTray Tray 1>
;;  #<Win32MediaTray Transparency>
;;  #<Win32MediaTray Bond>
;;  #<Win32MediaTray Labels>
;;  #<Win32MediaTray Custom Type 1>
;;  #<Win32MediaTray Custom Type 2>
;;  #<Win32MediaTray Custom Type 3>
;;  #<Win32MediaTray Custom Type 4>
;;  #<Win32MediaTray Custom Type 5>
;;  #<Win32MediaTray Custom Type 6>
;;  #<Win32MediaTray Custom Type 7>
;;  #<Win32MediaTray Plain>
;;  #<Win32MediaTray Envelope>
;;  #<Win32MediaTray Pre-Cut Tab>
;;  #<Win32MediaTray Other Type>
;;  #<Win32MediaTray Heavyweight>
;;  #<Win32MediaTray Rough Surface>
;;  #<Win32MediaTray Pre-Printed>
;;  #<Win32MediaTray Hole Punched>
;;  #<Win32MediaTray Printer Default Type>
;;  #<Win32MediaTray Recycled>
;;  #<Win32MediaTray Letterhead>)

;; Actually printing things is easy. `make-job` is a multi-method
;; that expects a nested map formatted as shown below (some parameters
;; are optional, see docs):
(-> (make-job {:doc {:source "/Users/Roberto/Desktop/test.pdf"
                     :flavor (:autosense flavors/input-streams)
                     :attrs #{Chromaticity/MONOCHROME
                              PrintQuality/HIGH
                              OrientationRequested/PORTRAIT}}
               :printer (printer :default)
               :attrs #{MediaTray/MAIN}
               :listener-fn listeners/basic-listener})
    (submit!))
;; 2014-Apr-25 16:28:02 -0400 rxacevedo INFO [clj-print.listeners] - Transfer complete: PrintEvent on
;; sun.print.UnixPrintJob@3a29bb76
;; 2014-Apr-25 16:28:02 -0400 rxacevedo INFO [clj-print.listeners] - No more events: PrintEvent on
;; sun.print.UnixPrintJob@3a29bb76
;; 2014-Apr-25 16:28:02 -0400 rxacevedo INFO [clj-print.listeners] - Job complete: PrintEvent on
;; sun.print.UnixPrintJob@3a29bb76

;; Or as a 'multi-job':
(let [jobs (make-job {:docs [{:source "/Users/Roberto/Desktop/test.pdf"
                              :flavor (:autosense flavors/input-streams)
                              :attrs #{Chromaticity/MONOCHROME
                                       PrintQuality/HIGH
                                       OrientationRequested/PORTRAIT}}
                             {:source "/Users/Roberto/Desktop/parabolla.pdf"
                              :flavor (:autosense flavors/input-streams)
                              :attrs #{Chromaticity/MONOCHROME
                                       PrintQuality/HIGH
                                       OrientationRequested/PORTRAIT}}]
                      :printer (printer :default)
                      :attrs #{MediaTray/MAIN}
                      :listener-fn listeners/basic-listener})
      job-pipe (lazy-map submit! jobs)]
  (take 2 job-pipe)) 
;; (2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - Transfer complete: PrintEvent on
;; sun.print.UnixPrintJob@1cdace08
;; 2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - No more events: PrintEvent on
;; sun.print.UnixPrintJob@1cdace08
;; 2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - Job complete: PrintEvent on
;; sun.print.UnixPrintJob@1cdace08
;; 2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - Transfer complete: PrintEvent on
;; sun.print.UnixPrintJob@7b256a62
;; 2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - No more events: PrintEvent on
;; sun.print.UnixPrintJob@7b256a62
;; 2014-Apr-25 16:28:04 -0400 rxacevedo INFO [clj-print.listeners] - Job complete: PrintEvent on
;; sun.print.UnixPrintJob@7b256a62
;; nil nil)
  
;; Because lazy-map is truly lazy (not chunked like map), submit! is
;; not computed until a job is taken from the "pipe." This allows for
;; print jobs to be "consumed." 

```

[Job pipe idea](http://oobaloo.co.uk/clojure-from-callbacks-to-sequences)    
[Lazy-map idea](http://isti.bitbucket.org/2012/04/01/pipes-clojure-choco-1.html)

## License

Copyright © 2014 Roberto Acevedo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
