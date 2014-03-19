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
                        PrintException
                        PrintService
                        PrintServiceLookup
                        SimpleDoc)
           (javax.print.attribute AttributeSet
                                  HashAttributeSet
                                  HashPrintRequestAttributeSet)
           (javax.print.attribute.standard MediaTray
                                           PrinterName))
  (:gen-class))

;; TODO: Move queuing to a separate ns

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
     (if (seq name)
       (let [attrs (doto (HashAttributeSet.) (.add (PrinterName. name nil)))] 
         (some (fn [^PrintService p] (if (= name (.getName p)) p)) (printers nil attrs)))
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
  ;; We don't care about DocFlavor or passing in AttributeSets, just use filter
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

;; TODO: Catch exception when not a PrintService or let it bubble up?
(defn- job
  "Returns a DocPrintJob object bound to the PrintService
   specified by p"
  {:added "1.0"}
  [^PrintService p]
  (.. p createPrintJob))

(defn- parse-source
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
  (condp = (parse-source doc-source)
    :file (:autosense flavors/input-streams)
    :url (:autosense flavors/urls)
    nil nil))

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
  [doc-source & {:keys [p-name
                        doc-flavor
                        doc-attrs
                        job-attrs]
                 :or {p-name nil
                      doc-flavor (flavor doc-source)
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

;; TODO: May not need this
(defn- get-doc
  "Returns a javax.print.Doc object for the print data in this
   job map."
  [doc-source doc-flavor doc-attrs]
  (SimpleDoc. doc-source doc-flavor doc-attrs))

(defn- print-action
  "Returns a delay that, when dereferenced, will cause a job to be
   submitted to the print service. This allows for clients to queue
   jobs while delaying resource locking until job submission."
  [j-map]
  (let [{:keys [^DocPrintJob job
                ^String doc-source
                doc-flavor
                doc-attrs
                job-attrs]} j-map]
    (condp = (parse-source doc-source)
      :file (delay (with-open [fis (FileInputStream. doc-source)]
                     (.. job (print (get-doc fis doc-flavor doc-attrs) job-attrs))))
      :url (delay (.. job (print (get-doc (URL. doc-source) doc-flavor doc-attrs) job-attrs)))
      nil (throw (RuntimeException. "Unrecognized document source")))))

(defn submit
  "Requests a dead tree representation of the document specified
   in the job map (sends the print job to the printer)."
  [j-map]
  (let [action (print-action j-map)]
    (try
      @action
      (println (str "Job:\n" (with-out-str (pprint j-map)) "has been submitted."))
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
