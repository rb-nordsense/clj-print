(ns ^{:doc "Core print logic."
      :author "Roberto Acevedo"}
  clj-print.core
  (:require [clj-print [doc-flavors :as flavors]
             [listeners :as listeners]]
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
                                  HashPrintRequestAttributeSet
                                  HashPrintServiceAttributeSet
                                  Attribute
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
  ([] (printer :default)) ;; TODO: If called with nil or "", should printer return (printer :default) or nil?
  ([name]
     (cond
      (= :default name) (PrintServiceLookup/lookupDefaultPrintService)
      (seq name) (let [pname-obj (PrinterName. name nil)
                       attrs  (make-set #{pname-obj} :attr)] 
                   (some (fn [^PrintService p]
                           (when (= name (.. p getName)) p))
                         (printers nil attrs)))
      :else nil)))

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

(defn lazy-map
  "A lazier version of map so that calling (take 1 (map submit! multidoc))
  on a lazy-seq made by multimethod make-job :docs doesn't cause
  print events to be realized sooner than they should be due to
  chunking."
  {:since "0.0.1"}
  [f coll]
  (if (seq coll)
    (lazy-seq (cons (f (first coll)) (lazy-map f (rest coll))))
    nil))

(defn- choose-source-key
  "Returns a keyword representative of the document source's
   type."
  {:since "0.0.1"}
  [source]
  (cond (try (.. (io/file source) exists) (catch Throwable t)) :file
        (try (URL. source) (catch Throwable t)) :url
        :else nil))

(defn- choose-flavor
  "Attempts to guess the appropriate DocFlavor for the document.
   Returns nil if no suitable DocFlavor is found."
  {:since "0.0.1"}
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
      :attr (HashAttributeSet.
                #^"[Ljavax.print.attribute.Attribute;"
                (into-array Attribute s))
      :service (HashPrintServiceAttributeSet.
                #^"[Ljavax.print.attribute.PrintServiceAttribute;"
                (into-array PrintServiceAttribute s))
      :job (HashPrintJobAttributeSet.
            #^"[Ljavax.print.attribute.PrintJobAttribute;"
            (into-array PrintJobAttribute s))
      :doc (HashDocAttributeSet.
            #^"[Ljavax.print.attribute.DocAttribute;"
            (into-array DocAttribute s))
      :request (HashPrintRequestAttributeSet.
                #^"[Ljavax.print.attribute.PrintRequestAttribute;"
                (into-array PrintRequestAttribute s))
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

(defprotocol IJobSpec
  "Protocol for print job data structures"
  (submit! [this]) 
  (add-listener! [this listener-fn]))

(defrecord JobSpec [doc printer job attrs]
  IJobSpec
  (submit! [this]
    (if-let [{listener-fn :listener-fn} this]
      ;; Listeners are not added until right before submission
      (add-listener! this listener-fn)) 
    (let [{{doc-obj :obj} :doc} this
          {:keys [^DocPrintJob job attrs]} this
          attr-set (make-set attrs :request)]
      (.. job (print @doc-obj attr-set))))
  (add-listener! [this listener-fn]
    (let [{:keys [^DocPrintJob job listener-fn]} this
          listener (listener-fn)]
      (doto job (.addPrintJobListener listener)))))

;;  A JobSpec might look something like this:
;; {:doc {:source "/path/to/doc.pdf"
;;        :flavor (:autosense flavors/input-streams)
;;        :attrs #{Chromaticity/MONOCHROME
;;                 PrintQuality/HIGH
;;                 OrientationRequested/PORTRAIT}}
;;  :printer (printer "HP_Color_LaserJet_CP3505")
;;  :attrs #{(Copies. 5) MediaTray/MAIN}
;;  :listener (listeners/basic-listener)}

;; Or
;; {:docs [{:source "/path/to/doc1.pdf"
;;          :flavor (:autosense flavors/input-streams)
;;          :attrs #{Chromaticity/MONOCHROME}}
;;         {:source "http://somesite.com/doc2.pdf"
;;          :flavor (:autosense flavors/urls)
;;          :attrs #{Chromaticity/COLOR}}]
;;  :printer (printer :default)
;;  :attrs #{MediaTray/MAIN}
;;  :listener (listeners/basic-listener)}

(defmulti make-job
  "Returns a JobSpec map that is the result of
   assoc'ing required values to it. The values are:

   1. A SimpleDoc object (wrapped in a Delay) made from
   the map keyed at :doc.
   2. A DocPrintJob object that is retrieved from the
   PrintService.

   The following defaults are also assoc'd to the job-spec
   if no values are supplied for them:

   1. The default system printer.
   2. An attribute set with the single attribute MediaTray/MAIN.
   3. A basic PrintJobListener that prints any events that occur
   on the DocPrintJob."
  {:since "0.0.1"}
  (fn [spec] (apply (some-fn #{:doc} #{:docs}) (keys spec))))

(defmethod make-job :doc [spec]
  (let [{:keys [^PrintService printer ^PrintJobListener listener doc attrs]} spec
        {doc-attrs :attrs} doc]
    (if (and (valid-attrs? doc-attrs :doc)
             (valid-attrs? attrs :job))
      (letfn [(add-doc [spec]
                (assoc-in spec [:doc :obj] (delay (make-doc doc))))
              (add-job [spec]
                (assoc-in spec [:job] (.. printer createPrintJob)))]
        (-> spec add-doc add-job map->JobSpec)))))

(defmethod make-job :docs [spec]
  (let [{docs :docs} spec]
    (for [doc docs]
      (-> spec (dissoc :docs) (assoc :doc doc) make-job))))

;; Old method of encapsulating a 'MultiJobSpec', most
;; values in a JobSpec will be singleton instances
;; anyways, so structural sharing may not be necessary,
;; though I'll throw this in here anyways:
;;
;; TODO: Analyze memory consumption in JVisualVM for:
;; 1. maps vs records being used for job specs in general
;; 2. Using MultiJobSpec vs just using 
;;
;; (defmethod make-job :docs [spec]
;;   (let [{:keys [^PrintService printer
;;                 ^PrintJobListener listener
;;                 docs attrs]} spec]
;;     (if (and (every? (fn [d] (valid-attrs? d :doc)) (map :attrs docs))
;;              (valid-attrs? attrs :job))
;;       (letfn [(add-doc [doc-map]
;;                 (assoc-in doc-map [:obj] (delay (make-doc doc-map))))
;;               (add-docs [spec]
;;                 (update-in spec [:docs] (fn [doc-list] (map add-doc  doc-list))))
;;               (add-jobs [spec]
;;                 (assoc-in spec [:jobs] (for [doc docs] (.. printer createPrintJob))))]
;;         (-> spec add-docs add-jobs map->MultiJobSpec)))))

(defn -main
  "Main method, expects each value in args to be the string
   representation of a clojure map to be read by the reader."
  {:since "0.0.1"}
  [& args]
  (if (seq args)
    (doseq [v args]
      (let [spec (read-string v)]
        (-> (make-job spec) submit!)))))
