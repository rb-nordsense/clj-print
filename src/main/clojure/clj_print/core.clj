(ns ^{:doc "Core print logic."
      :author "Roberto Acevedo"}
  clj-print.core
  (:require [clj-print [doc-flavors :as flavors]]
            [clojure.java [io :as io]]
            [clojure.pprint :refer [pprint]])
  (:import (java.io File FileInputStream FileNotFoundException)
           (java.net URL)
           (javax.print DocPrintJob
                        DocFlavor 
                        PrintException
                        PrintService
                        PrintServiceLookup
                        SimpleDoc)
           (javax.print.attribute AttributeSet
                                  HashAttributeSet
                                  HashDocAttributeSet
                                  HashPrintJobAttributeSet
                                  HashPrintServiceAttributeSet
                                  DocAttribute
                                  PrintJobAttribute
                                  PrintRequestAttribute
                                  PrintServiceAttribute)
           (javax.print.attribute.standard Copies
                                           Chromaticity
                                           MediaTray
                                           OrientationRequested
                                           PrinterName
                                           PrintQuality))
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Printers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; TODO: Is this the best way to do this?
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

(defn setify
  "Takes a Clojure set and returns an Attribute"
  [^clojure.lang.IPersistentSet s bound]
  (if (seq s)
    (let [el (first s)
          t (type el)]
      (condp = bound
        :service (HashPrintServiceAttributeSet. (into-array t s))
        :job (HashPrintJobAttributeSet. (into-array t s))
        :doc (HashDocAttributeSet. (into-array t s))
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Job ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IPrintable 
  (submit [this & {:keys [s-attrs]}] "Submits the IPrintable for printing, with optional service attributes."))

;; TODO: Only allow maps
(defrecord JobMap [doc printer job] 
  IPrintable
  (submit [this & {:keys [s-attrs]}]
    (let [{:keys [attrs obj]} job
          sdoc (make-doc (:source doc) (:flavor doc) (:attrs doc))]
      (.. obj (print @sdoc s-attrs)))))

;; TODO: Should printer be optional?
(defn make-jobmap [doc & {:keys [printer attrs]
                          :or {printer (printer)
                               attrs (setify #{MediaTray/MAIN} :job)}}]
  (->JobMap doc printer {:obj (.. printer createPrintJob)
                         :attrs attrs}))

(comment "A JobMap might look something like this:"
         {:doc {:source "path"
                :flavor (:autosense flavors/input-streams)
                :attrs #{Chromaticity/MONOCHROME
                         PrintQuality/HIGH
                         OrientationRequested/PORTRAIT}}
          :printer (printer "\\\\srqprint\\2WSouth-Prt3")
          :attrs #{(Copies. 5) MediaTray/MAIN}})

(defn make-doc
  "Returns a javax.print.Doc object for the print data in this
   job map. SimpleDoc will throw an IllegalArgumentException if
   the doc-flavor is not representative of the data pointed to
   by doc-source."
  {:added "1.0"}
  [source & {:keys [flavor attrs]
             :or {flavor (flavor source)
                  attrs (-> #{MediaTray/MAIN} (setify :doc))}}] ;; Pretty sure I need this
  (let [resource (condp = (doc-source-type source)
                   :file (FileInputStream. source)
                   :url (URL. source)
                   nil)]
    (delay (SimpleDoc. resource flavor attrs))))

;; TODO: Win32PrintService.java will close the streams when it's done,
;; check IPPrintService.java
;; (defn- print-action
;;   "Calls print on the job, but considers the doc-source-type."
;;   [j-map]
;;   (let [{:keys [^DocPrintJob job
;;                 ^String doc-source
;;                 doc-flavor
;;                 doc-attrs
;;                 job-attrs]} j-map]
;;     (condp = (doc-source-type doc-source)
;;       :file (with-open [fis (FileInputStream. doc-source)] ;; Close anyways
;;               (.. job (print (make-doc fis doc-flavor doc-attrs) job-attrs)))
;;       :url (.. job (print (make-doc (URL. doc-source) doc-flavor doc-attrs) job-attrs))
;;       nil (throw (RuntimeException. "Unrecognized document source")))))

;; (defn submit
;;   "Requests a dead tree representation of the document specified
;;    in the job map (sends the print job to the printer)."
;;   [j-map]
;;   (let [action (delay (print-action j-map))]
;;     (try
;;       @action
;;       (catch PrintException pe (.. pe printStackTrace)))))

;; (defn -main [& args]
;;   (if (seq args)
;;     (doseq [v args]
;;       (let [{:keys [doc-source p-name]} (read-string v)
;;             job (job-map doc-source :p-name p-name)]
;;         (-> job submit)))))

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
