(ns ^{:doc "Core print logic."
      :author "Roberto Acevedo"}
  clj-print.core
  (:require [clj-print [doc-flavors :as flavors]
                       [listeners :as listeners]]
            [clojure.java [io :as io]]
            [clojure.pprint :refer [pprint]]
            [taoensso.timbre :as timbre])
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
                                  HashPrintRequestAttributeSet
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
                                           PrintQuality)
           (javax.print.event PrintJobListener))
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Printers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn printers
  "Returns a seq of printers that supports the specified
   DocFlavor and Attributes acquired from PrintServiceLookup.
   With no arguments, returns a seq of all printers that
   PrintServiceLookup is aware of."
  {:since "0.0.1"}
  ([& {:keys [^DocFlavor flavor ^AttributeSet attrs]}]
     (seq (PrintServiceLookup/lookupPrintServices flavor attrs))))

(defn printer 
  "Returns the printer with the specified name from PrintServiceLookup.
   With no arguments, returns the system default printer."
  {:since "0.0.1"}
  [name]
  (cond
   (= :default name) (PrintServiceLookup/lookupDefaultPrintService)
   (seq name) (let [attrs (doto (HashAttributeSet.) (.add (PrinterName. name nil)))] 
                (some (fn [^PrintService p] (when (= name (.. p getName)) p)) (printers nil attrs)))
   :else nil))

(defn status
  "Returns a seq of this PrintService's status attributes."
  {:since "0.0.1"}
  [^PrintService p]
  (-> p .getAttributes .toArray seq))

;; TODO: Is this the best way to do this?
(defn attributes
  "Returns a flattened seq of this PrintService's supported
  atributes, for all of its supported Attribute classes."
  {:since "0.0.1"}
  [^PrintService p]
  (let [unflattened (for [c (.. p getSupportedAttributeCategories)]
                      (.. p (getSupportedAttributeValues c nil nil)))] 
    (->> unflattened
         (map (fn [e] (if (.. ^Class (type e) isArray) (seq e) e)))
         flatten)))

(defn trays
  "Returns a seq of this PrintService's MediaTrays"
  {:since "0.0.1"}
  [^PrintService p]
  (filter (fn [attr] (instance? MediaTray attr)) (attributes p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Util ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- choose-source-key
  "Returns a keyword representative of the document source's
   type."
  {:since "1.0"}
  [source]
  (cond (try (.. (io/file source) exists) (catch Throwable t)) :file
        (try (URL. source) (catch Throwable t)) :url
        :else nil))

(defn- choose-flavor
  "Attempts to guess the appropriate DocFlavor for the document.
   Returns nil if no suitable DocFlavor is found."
  {:since "1.0"}
  [source]
  (condp = (choose-source-key source)
    :file (:autosense flavors/input-streams)
    :url (:autosense flavors/urls)
    nil))

(defn- make-set
  "Takes a Clojure set and returns an AttributeSet implementation
   based on bound."
  [^clojure.lang.IPersistentSet s bound]
  (if (seq s)
    (condp = bound        
      :service (HashPrintServiceAttributeSet. (into-array PrintServiceAttribute s))
      :job (HashPrintJobAttributeSet. (into-array PrintJobAttribute s))
      :doc (HashDocAttributeSet. (into-array DocAttribute s))
      :request (HashPrintRequestAttributeSet. (into-array PrintRequestAttribute s))
      nil)))

;; TODO: Validate attrs here?
(defn- make-doc
  "Returns a javax.print.Doc object for the print data in this
   job map. SimpleDoc will throw an IllegalArgumentException if
   the doc-flavor is not representative of the data pointed to
   by doc-source."
  {:since "0.0.1"}
  [doc-map]
  (let [{^String source :source} doc-map
        {:keys [flavor attrs]
         :or {flavor (choose-flavor source)
              attrs #{MediaTray/MAIN}}} doc-map ;; Pretty sure I need this
              resource (condp = (choose-source-key source)
                         :file (FileInputStream. source)
                         :url (URL. source)
                         nil)]
    ;; TODO: Do I need the caching provided by this?
    (delay (SimpleDoc. resource flavor (-> attrs (make-set :doc))))))

(defn- valid-attrs?
  "Returns true if all of the Attribute objects in
   attrs are bounded by the type specified by k.
   Valid keywords for k are:
     :doc
     :job
     :request,
     :service"
  {:since "0.0.1"}
  [attrs k]
  (condp = k
    :doc (every? #(instance? DocAttribute %) attrs)
    :job (every? #(instance? PrintJobAttribute %) attrs)
    :request (every? #(instance? PrintRequestAttribute %) attrs)
    :service (every? #(instance? PrintServiceAttribute %) attrs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Job ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;  A JobSpec might look something like this:
;; 
;; {:doc {:source "/path/to/doc"
;;        :flavor (:autosense flavors/input-streams)
;;        :attrs #{Chromaticity/MONOCHROME
;;                 PrintQuality/HIGH
;;                 OrientationRequested/PORTRAIT}}
;;  :printer (printer "HP_Color_LaserJet_CP3505")
;;  :attrs #{(Copies. 5) MediaTray/MAIN}
;;  :listener SimpleJobListener}

;; (defprotocol ISpoolable
;;   "This protocol defines a contract that states the
;;    responsibilities of any implementation that wishes
;;    to be considered 'spoolable,' which implies that it
;;    can be queued for later processing such that it is
;;    another process' resonsibility to handle the ISpoolable
;;    and block until it has been processed. For an implementation
;;    to be spoolable, it must be able to do the following:"
;;   (submit [this]) ;; Submit the job 
;;   (add-listener [this listener]))
;; Attach an event listener that
                                  ;; returns a value when the process is complete

;; (defrecord JobSpec [doc printer job attrs]
;;   ISpoolable
;;   (submit [this]
;;     (let [{{obj :obj} :doc} this
;;           {:keys [^DocPrintJob job attrs]} this]
;;       (.. job (print @obj (make-set attrs :request)))))
;;   (add-listener [this listener]
;;     (if-let [{^DocPrintJob job :job} this]
;;       (do (doto job (.addPrintJobListener listener))
;;           (assoc this :listener listener))
;;       (throw IllegalStateException "No DocPrintJob keyed at :job."))))

(defn submit [jobspec]
  (let [{{obj :obj} :doc} jobspec
        {:keys [^DocPrintJob job attrs]}jobspec]
    (.. job (print @obj (make-set attrs :request)))))

(defn add-listener [jobspec listener]
    (if-let [{^DocPrintJob job :job} jobspec]
      (do (doto job (.addPrintJobListener listener))
          (assoc jobspec :listener listener))
      (throw IllegalStateException "No DocPrintJob keyed at :job.")))

;; (defn job-seq [job-map]
;;   (let [{docs :docs ^PrintService printer :printer} job-map]
;;     (for [d docs]
;;       [(make-doc d) (.. printer createPrintJob)])))

;; TODO: SEE http://oobaloo.co.uk/clojure-from-callbacks-to-sequences!!!
;;
;; (defprotocol IOperator
;;   "This protocol is to be used by implementations
;;    that wish to handle a print process by coordinating
;;    the processing of multiple documents within an ISpoolable."
;;   (process [this spec]))

;; (defrecord PrintOperator [spec]
;;   IOperator
;;   (process [this spec]
;;     (when-let [docs (seq (:docs spec))]
;;       (let [{^PrintService printer :printer} spec
;;             job (.. printer createPrintJob)]
;;         (submit spec)))))

;; TODO: Make this polymorphic using multimethods to prevent having to
;; create a record type (need to do some performance testing on this,
;; but for now I think it would be better to develop generically and
;; then adopt using records as needed
(defmulti job
  (fn [spec] (let [{:keys [doc docs]} spec]
              (or doc docs))))

(defmethod job :doc [spec]
  (let [{{doc-attrs :attrs} :doc} spec
        {:keys [doc ^PrintService printer attrs ^PrintJobtListener listener]
         :or {printer (printer :default)
              attrs #{MediaTray/MAIN}}} spec
              maybe-listen #(if listener (add-listener % listener) %)]
    (if (and (valid-attrs? doc-attrs :doc)
             (valid-attrs? attrs :job))
      (-> {:doc (assoc doc :obj (make-doc doc))
           :printer printer
           :attrs attrs
           :job (.. printer createPrintJob)}
          maybe-listen))))

(defmethod job :docs [spec]
  (let [{docs :docs} spec
        {:keys [doc ^PrintService printer attrs ^PrintJobtListener listener]
         :or {printer (printer :default)
              attrs #{MediaTray/MAIN}}} spec
              maybe-listen #(if listener (add-listener % listener) %)]
    (println "In :docs")
    (if (and (every? #(valid-attrs? (:attrs %) :doc) docs)
             (valid-attrs? attrs :job))
      (-> {:docs (map #(assoc % :obj (make-doc %)) docs)
           :printer printer
           :attrs attrs
           :job (.. printer createPrintJob)}
          maybe-listen))))

;; (defn make-job
;;   "Returns a JobSpec map that is the result of
;;    assoc'ing required values to it. The values are:

;;    1. A SimpleDoc object (wrapped in a Delay) made from
;;    the map keyed at :doc.
;;    2. A DocPrintJob object that is retrieved from the
;;    PrintService.

;;    The following defaults are also assoc'd to the job-spec
;;    if no values are supplied for them:

;;    1. The default system printer.
;;    2. An attribute set with the single attribute MediaTray/MAIN.
;;    3. A basic PrintJobListener that prints any events that occur
;;    on the DocPrintJob."
;;   {:since "0.0.1"}
;;   [spec]
;;   (cond
;;    (:doc spec) (let [{{doc-attrs :attrs} :doc} spec
;;                      {:keys [doc ^PrintService printer attrs ^PrintJobtListener listener]
;;                       :or {printer (printer :default)
;;                            attrs #{MediaTray/MAIN}}} spec
;;                            maybe-listen #(if listener (add-listener % listener) %)]
;;                  (if (and (valid-attrs? doc-attrs :doc)
;;                           (valid-attrs? attrs :job))
;;                    (-> {:doc (assoc doc :obj (make-doc doc))
;;                         :printer printer
;;                         :attrs attrs
;;                         :job (.. printer createPrintJob)}
;;                        maybe-listen)))
;;    (:docs spec) (let [{docs :docs} spec
;;                      {:keys [doc ^PrintService printer attrs ^PrintJobtListener listener]
;;                       :or {printer (printer :default)
;;                            attrs #{MediaTray/MAIN}}} spec
;;                            maybe-listen #(if listener (add-listener % listener) %)]
;;                   (println "In :docs")
;;                   (if (and (every? #(valid-attrs? (:attrs %) :doc) docs)
;;                            (valid-attrs? attrs :job))
;;                    (-> {:docs (map #(assoc % :obj (make-doc %)) docs)
;;                         :printer printer
;;                         :attrs attrs
;;                         :job (.. printer createPrintJob)}
;;                        maybe-listen)))
;;    :else nil))

(defn -main [& args]
  (if (seq args)
    (doseq [v args]
      (let [spec (read-string v)]
        (-> (make-job spec) submit)))))
