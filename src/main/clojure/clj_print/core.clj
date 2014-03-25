(ns ^{:doc "Core print logic."
      :author "Roberto Acevedo"}
  clj-print.core
  (:require [clj-print [doc-flavors :as flavors]]
            [clojure.java [io :as io]]
            [clojure.pprint :refer [pprint]])
  (:import (java.io File FileInputStream FileNotFoundException)
           (java.net URL)
           (javax.print Doc
                        DocPrintJob
                        DocFlavor ;; Remove me!
                        PrintException
                        PrintService
                        PrintServiceLookup
                        SimpleDoc)
           (javax.print.attribute Attribute
                                  AttributeSet
                                  DocAttributeSet
                                  HashAttributeSet
                                  HashDocAttributeSet
                                  HashPrintJobAttributeSet
                                  HashPrintRequestAttributeSet
                                  HashPrintServiceAttributeSet
                                  DocAttribute
                                  PrintJobAttribute
                                  PrintRequestAttribute
                                  PrintServiceAttribute)
           (javax.print.attribute.standard MediaTray
                                           PrinterName)
           (javax.print.event PrintJobListener))
  (:gen-class))

(def ^{:doc "Basic PrintJobListener implementation."}
  basic-job-listener
  (reify PrintJobListener
    (printDataTransferCompleted [this pje]
      (println (str "Transfer complete: " pje)))
    (printJobCompleted [this pje]
      (println (str "Job complete: " pje)))
    (printJobFailed [this pje]
      (println (str "Job failed: " pje)))
    (printJobCanceled [this pje]
      (println (str "Job canceled: " pje)))
    (printJobNoMoreEvents [this pje]
      (println (str "No more events for: " pje)))
    (printJobRequiresAttention [this pje]
      (println (str "Job requires attention: " pje)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Printers;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn printers
  "Returns a seq of printers that supports the specified
   DocFlavor and Attributes acquired from PrintServiceLookup.
   With no arguments, returns a seq of all printers that
   PrintServiceLookup is aware of."
  {:added "1.0"}
  ([& {:keys [^DocFlavor flavor ^AttributeSet attrs]}]
     (seq (PrintServiceLookup/lookupPrintServices flavor attrs))))

(defn printer 
  "Returns the printer with the specified name from PrintServiceLookup.
   With no arguments, returns the system default printer."
  {:added "1.0"}
  ([] (PrintServiceLookup/lookupDefaultPrintService))
  ([name]
     (if (seq name)
       (let [attrs (doto (HashAttributeSet.) (.add (PrinterName. name nil)))] 
         (some (fn [^PrintService p] (when (= name (.. p getName)) p)) (printers nil attrs)))
       (printer))))

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
  (let [unflattened (for [c (.. p getSupportedAttributeCategories)]
                      (.. p (getSupportedAttributeValues c nil nil)))] 
    (->> unflattened
         (map (fn [e] (if (.. (type e) isArray) (seq e) e)))
         flatten))) ;; TODO: Reflection

(defn trays
  "Returns a seq of this PrintService's MediaTrays"
  {:added "1.0"}
  [^PrintService p]
  (filter (fn [attr] (instance? MediaTray attr)) (attributes p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Util ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- doc-source-type
  "Returns a keyword representative of the document source's
   type."
  {:since "1.0"}
  [doc-source]
  (cond (try (.. (io/file doc-source) exists) (catch Throwable t)) :file
        (try (URL. doc-source) (catch Throwable t)) :url
        :else nil))

(defn- flavor
  "Attempts to guess the appropriate DocFlavor for the document.
   Returns nil if no suitable DocFlavor is found."
  {:since "1.0"}
  [doc-source]
  (condp = (doc-source-type doc-source)
    :file (:autosense flavors/input-streams)
    :url (:autosense flavors/urls)
    nil))

;; TODO: Classes like javax.print.attribute.standard.Chromaticity
;; implement more than one of the below interfaces, so wat do in that
;; scenario?
(defn setify [^clojure.lang.IPersistentSet s]
  (if (seq s)
    (let [el (first s)
          t (type el)]
      (cond
       (instance? PrintRequestAttribute el) (HashPrintRequestAttributeSet. (into-array t s))
       (instance? PrintServiceAttribute el) (HashPrintServiceAttributeSet. (into-array t s))
       (instance? PrintJobAttribute el) (HashPrintJobAttributeSet. (into-array t s))
       (instance? DocAttribute el) (HashDocAttributeSet. (into-array t s))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Job ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Make the various AttributeSet implementations seqable, thus
;; removing the need to have these things keyed in the map, since they
;; can then be pretty printed from the SimpleDoc.
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
  ;; TODO: This throws a nullpointer exception if p-name cannot be
  ;; resolved (since `printer` returns nil when it can't find a
  ;; printer).
  [doc-source & {:keys [p-name
                        doc-flavor
                        doc-attrs
                        job-attrs]
                 :or {doc-flavor (flavor doc-source)
                      job-attrs (doto (HashPrintRequestAttributeSet.) (.add MediaTray/MAIN))}}]
  (let [^PrintService p (printer p-name)
        job (.. p createPrintJob)]
    {:doc-source doc-source
     :printer p
     :job-attrs job-attrs
     :job job
     :doc-flavor doc-flavor
     :doc-attrs doc-attrs}))

(defn make-doc
  "Returns a javax.print.Doc object for the print data in this
   job map. SimpleDoc will throw an IllegalArgumentException if
   the doc-flavor is not representative of the data pointed to
   by doc-source."
  {:added "1.0"}
  [doc-source doc-flavor doc-attrs]
  (SimpleDoc. doc-source doc-flavor doc-attrs))

;; TODO: Win32PrintService.java will close the streams when it's done,
;; check IPPrintService.java
(defn- print-action
  "Calls print on the job, but considers the doc-source-type."
  [j-map]
  (let [{:keys [^DocPrintJob job
                ^String doc-source
                doc-flavor
                doc-attrs
                job-attrs]} j-map]
    (condp = (doc-source-type doc-source)
      :file (with-open [fis (FileInputStream. doc-source)] ;; Close anyways
              (.. job (print (make-doc fis doc-flavor doc-attrs) job-attrs)))
      :url (.. job (print (make-doc (URL. doc-source) doc-flavor doc-attrs) job-attrs))
      nil (throw (RuntimeException. "Unrecognized document source")))))

(defn submit
  "Requests a dead tree representation of the document specified
   in the job map (sends the print job to the printer)."
  [j-map]
  (let [action (delay (print-action j-map))]
    (try
      @action
      (catch PrintException pe (.. pe printStackTrace)))))

(defn -main [& args]
  (if (seq args)
    (doseq [v args]
      (let [{:keys [doc-source p-name]} (read-string v)
            job (job-map doc-source :p-name p-name)]
        (-> job submit)))))

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
