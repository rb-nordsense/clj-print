(ns ^{:doc "Core print logic."
      :author "Roberto Acevedo"}
  clj-print.core
  (:require [clj-print [doc-flavors :as doc-flavors]])
  (:import (java.io File FileInputStream FileNotFoundException)
           (java.net URL)
           (javax.print Doc
                        DocPrintJob
                        PrintException
                        PrintService
                        PrintServiceLookup
                        SimpleDoc)
           (javax.print.attribute AttributeSet
                                  HashAttributeSet
                                  HashPrintRequestAttributeSet)
           (javax.print.attribute.standard MediaTray
                                           PrinterName)))

;; TODO: Resume at CURSOR
;; TODO: Use a bounded/concurrent queue structure
;; TODO: Move queuing to a separate ns

;; ;; Naive unbounded job queue
;; (def print-queue (Stack.))

;; (defn add-job
;;   "Adds the job map to the job queue. Adds a map entry :queue-time
;;    that represents the point in time at which the job was queued."
;;   [job]
;;   (doto ^Stack print-queue
;;         (.push (-> job (assoc :queue-time (System/currentTimeMillis))))))


(defn printers
  "Returns a seq of printers that supports the specified
   DocFlavor and Attributes acquired from PrintServiceLookup.
   With no arguments, returns a seq of all printers that
   PrintServiceLookup is aware of."
  {:added "1.0"}
  ([& {:keys [^DocFlavor flavor ^AttributeSet attrs] :or [flavor nil attrs nil]}] 
     (seq (PrintServiceLookup/lookupPrintServices flavor attrs))))

(defn printer 
  "Returns the printer with the specified name from PrintServiceLookup.
   With no arguments, returns the system default printer."
  {:added "1.0"}
  ([] (PrintServiceLookup/lookupDefaultPrintService))
  ([name]
     (let [attrs (doto (HashAttributeSet.) (.add (PrinterName. name nil)))] 
       (some (fn [^PrintService p] (if (= name (.getName p)) p)) (printers nil attrs)))))

(defn status
  "Returns a seq of this PrintService's status attributes."
  {:added "1.0"}
  [^PrintService p]
  (-> p .getAttributes .toArray seq))

(defn attributes
  "Returns a flattened seq of this PrintService's supported
  atributes, for all of its supported Attribute classes."
  {:added "1.0"}
  [^PrintService p]
  ;; We don't care about DocFlavor or passing in AttributeSets, just use filter
  (let [unflattened (for [c (.getSupportedAttributeCategories p)]
                      (.getSupportedAttributeValues p c nil nil))] 
    (->> unflattened (map (fn [e] (if (.isArray (type e)) (seq e) e))) flatten)))

(defn trays
  "Returns a seq of this PrintService's MediaTrays"
  {:added "1.0"}
  [^PrintService p]
  (filter (fn [attr] (instance? MediaTray attr)) (attributes p)))

;; TODO: Catch exception when not a PrintService or let it bubble up?
(defn- job
  "Returns a DocPrintJob object bound to the PrintService
   specified by p"
  {:added "1.0"}
  [^PrintService p]
  (.. p createPrintJob))

;; CURSOR

(defn job-map
  "Returns a job-map that encapsulates the constituent parts of a print job.

   :doc-source  - The source of the data being
                  printed (supplied)
   :printer     - The name of the PrintService
                  returned for p-name
   :job-attrs   - The HashPrintRequestAttributeSet
                  that defines the attributes of
                  the PrintJob
   :job         - The PrintJob object returned from
                  the PrintService
   :doc-flavor  - The DocFlavor to use for for this
                  document
   :doc-attrs   - The attributes to be applied to
                  the Doc object when it is is
                  instantiated (when the job is
                  submitted)"
  [doc-source p-name & {:keys [doc-flavor doc-attrs job-attrs]
                        :or {doc-flavor nil
                             doc-attrs nil
                             job-attrs (doto (HashPrintRequestAttributeSet.) (.add MediaTray/MAIN))}}]
  (let [^PrintService p (printer p-name)
        job (job p)]
    {:doc-source doc-source
     :printer (.getName p)
     :job-attrs job-attrs
     :job job
     :doc-flavor doc-flavor
     :doc-attrs doc-attrs}))

;; TODO: Supersedes below, Maybe wrap get-doc in a delay and
;; dereference it when the job is submitted? That should work...
;; TODO: Finish this, one should be able to create a SimpleDoc without
;; tying up the resource to be printed until the job is actually sent
;; to the print service. Delay? For InputStreams, need to
;; enclose (.print job doc job-attrs) in a (with-open) macro...

(defn- get-doc
  "Returns a javax.print.Doc object for the print data in this
   job map."
  [j-map]
  (let [{:keys [^String doc-source doc-flavor doc-attrs]
         :as stuff} j-map]
    (try (SimpleDoc. (FileInputStream. doc-source) doc-flavor doc-attrs)
         (catch Exception e
           (SimpleDoc. (URL. doc-source) doc-flavor doc-attrs)))))

(defn submit
  "Requests a dead tree representation of the document specified
   in the job map (sends the print job to the printer)."
  [j-map]
  (let [{:keys [^DocPrintJob job ^String doc-source doc-flavor doc-attrs job-attrs]} j-map]
    (try
      ;; TODO: WRONG, data may not always come from a File.
      ;; (with-open [stream (FileInputStream. doc-source)]) 
      (do
        (.print job (get-doc j-map) job-attrs)
        (println (str "Job: " j-map "\nhas been submitted.")))
      (catch PrintException pe (.printStackTrace pe)))))

(defn -main [& args]
  (println "Hello world!"))

;; Le deprecated
(comment (defn print-doc
           "Prints the document in the specified file using the specified printer"
           [f-path p-name]
           (if (not (.exists (File. f-path)))
             (throw (FileNotFoundException. "The file cannot be found."))
             (with-open [f-stream (FileInputStream. f-path)]
               (let [p (printer p-name)
                     doc (SimpleDoc. f-stream (:autosense doc-flavors/input-streams) nil)
                     attrs (doto (HashPrintRequestAttributeSet.)
                             (.add MediaTray/MAIN))
                     job (.createPrintJob p)]
                 (.print job doc attrs)
                 (println (str "Job " job " sent to")))))))

;; This also works!!! Sort of, it causes my printer at home to start
;; spitting out a bunch of blank pages, not sure why that is yet. 
(comment (let [f (File. "/Users/Roberto/Dropbox/docs/pdf/semi-log-graph-paper.pdf")
               b (byte-array (.length f))]
           (with-open [s (Socket. "10.0.1.14" 9100)
                       s-stream (.getOutputStream s)
                       f-stream (FileInputStream. f)]
             (.read f-stream b)
             (.write s-stream b)
             (.flush s-stream))))
