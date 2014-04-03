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
  (if (seq attrs)
    (let [mappings {:doc DocAttribute
                    :job PrintJobAttribute
                    :request PrintRequestAttribute
                    :service PrintServiceAttribute}]
      (every? (fn [attr] (instance? (k mappings) attr)) attrs))))


(defn- make-doc
  "Returns a javax.print.Doc object for the print data in this
   job map. SimpleDoc will throw an IllegalArgumentException if
   the doc-flavor is not representative of the data pointed to
   by doc-source."
  {:since "0.0.1"}
  [doc-spec]
  (let [{^String source :source} doc-spec
        {:keys [flavor attrs]
         :or {flavor (choose-flavor source)
              attrs #{MediaTray/MAIN}}} doc-spec
              resource (condp = (choose-source-key source)
                         :file (FileInputStream. source)
                         :url (URL. source)
                         nil)]
    (if (valid-attrs? attrs :doc)
      (SimpleDoc. resource flavor (-> attrs (make-set :doc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Job ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;  A JobSpec might look something like this:
(comment {:doc {:source "/path/to/doc.pdf"
                :flavor (:autosense flavors/input-streams)
                :attrs #{Chromaticity/MONOCHROME
                         PrintQuality/HIGH
                         OrientationRequested/PORTRAIT}}
          :printer (printer "HP_Color_LaserJet_CP3505")
          :attrs #{(Copies. 5) MediaTray/MAIN}
          :listener (listeners/basic-listener)})

;; Or

(comment {:docs [{:source "/path/to/doc1.pdf"
                  :flavor (:autosense flavors/input-streams)
                  :attrs #{Chromaticity/MONOCHROME}}
                 {:source "http://somesite.com/doc2.pdf"
                  :flavor (:autosense flavors/urls)
                  :attrs #{Chromaticity/COLOR}}]
          :printer (printer :default)
          :attrs #{MediaTray/MAIN}
          :listener (listeners/basic-listener)})

(defprotocol IJobSpec
  (submit [this]) 
  (add-listener [this listener-fn]))

;; TODO: Handle exception scenarios
(defrecord JobSpec [doc printer job attrs]
  IJobSpec
  (submit [this]
    (if-let [{listener-fn :listener-fn} this]
      (add-listener this listener-fn))
    (let [doc-obj (make-doc doc)
          {:keys [^DocPrintJob job attrs]} this
          attr-set (make-set attrs :request)]
      (.. job (print doc-obj attr-set))))
  (add-listener [this listener-fn]
    (let [{:keys [^DocPrintJob job listener-fn]} this
          listener (listener-fn)]
      (doto job (.addPrintJobListener listener)))))

(defrecord MultiJobSpec [docs printer jobs attrs]
  IJobSpec
  (submit [this]
    (if-let [{listener-fn :listener-fn} this]
      (add-listener this listener-fn))
    (let [{:keys [docs jobs]} this
          doc-objs (map :obj docs)]
      (doall (map (fn [j d] (.. j (print @d (make-set attrs :request)))) jobs doc-objs)))) ;; TODO: reflection
  (add-listener [this listener-fn]
    (let [{:keys [^DocPrintJob jobs listener-fn]} this]
      (doseq [j jobs]
        (.. j (addPrintJobListener (listener-fn)))))))

;; TODO: SEE http://oobaloo.co.uk/clojure-from-callbacks-to-sequences!!!
(defmulti make-job
  "Dispatch on which key is present in the spec map."
  (fn [spec] (apply (some-fn #{:doc} #{:docs}) (keys spec))))

(defmethod make-job :doc [spec]
  (let [{:keys [^PrintService printer ^PrintJobListener listener doc attrs]} spec
        {doc-attrs :attrs} doc]
    (if (and (valid-attrs? doc-attrs :doc)
             (valid-attrs? attrs :job))
      (letfn [(add-doc [doc-map]
                (assoc-in doc-map [:doc :obj] (delay (make-doc doc-map))))
              (add-job [spec]
                (assoc-in spec [:job] (.. printer createPrintJob)))]
        (-> spec add-doc add-job map->JobSpec)))))

(defmethod make-job :docs [spec]
  (let [{:keys [^PrintService printer
                ^PrintJobListener listener
                docs attrs]} spec]
    (if (and (every? (fn [d] (valid-attrs? d :doc)) (map :attrs docs))
             (valid-attrs? attrs :job))
      (letfn [(add-doc [doc-map]
                (assoc-in doc-map [:obj] (delay (make-doc doc-map))))
              (add-docs [spec]
                (update-in spec [:docs] (fn [doc-list] (map add-doc  doc-list))))
              (add-jobs [spec]
                (assoc-in spec [:jobs] (for [doc docs] (.. printer createPrintJob))))]
        (-> spec add-docs add-jobs map->MultiJobSpec)))))

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
