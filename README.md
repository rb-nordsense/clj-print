clj-print
=======

A Clojure library that wraps the [javax.print](http://docs.oracle.com/javase/7/docs/api/javax/print/package-summary.html) API.

## TODO
- Make jobs identifiable/comparable so that they can be searched for
- Cut down on type hinting where possible


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
(status (printer)) 
;; (#<PrinterIsAcceptingJobs accepting-jobs>
;;  #<ColorSupported supported>
;;  #<QueuedJobCount 0>
;;  #<PrinterName \\srqprint\2WSouth-Prt3>)

;; As well as the various attributes that the printer supports:
(attributes (printer))
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
(trays (printer)) 
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

;; Actually printing things is easy. `job-map` Expects a nested map
;; formatted as shown below (some parameters are optional, see docs):
(-> (job {:doc {:source "/path/to/doc.pdf"
                :flavor (flavors/input-streams :autosense)}
          :printer (printer :default)
          :attrs #{MediaTray/MAIN
                   Chromaticity/MONOCHROME
                   PrintQuality/HIGH
                   OrientationRequested/PORTRAIT}
          :listener listeners/basic}) submit)

```

## License

Copyright © 2014 Roberto Acevedo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
