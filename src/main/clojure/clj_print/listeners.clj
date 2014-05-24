(ns ^{:doc "PrintJobListeners to react to PrintJobEvents."
      :author "Roberto Acevedo"}
  clj-print.listeners
  (require (taoensso (timbre :as timbre)))
  (:import (javax.print.event PrintJobListener)))


(def ^{:doc "How many jobs we've processed"}
  counter (atom 0))

(defn basic-listener [] 
  (reify
    PrintJobListener
    (printDataTransferCompleted [this pje]
      (timbre/info (str "Transfer complete: " pje)))
    (printJobCompleted [this pje]
      (timbre/info (str "Job complete: " pje)))
    (printJobFailed [this pje]
      (timbre/error (str "Job failed: " pje)))
    (printJobCanceled [this pje]
      (timbre/info (str "Job canceled: " pje)))
    (printJobNoMoreEvents [this pje]
      (timbre/info (str "No more events: " pje))
      (.. this (printJobCompleted pje)))
    (printJobRequiresAttention [this pje]
      (timbre/warn (str "Job requires attention: " pje)))))

(defn multidoc-listener [docs]
  (reify
    PrintJobListener
    (printDataTransferCompleted [this pje]
      (timbre/info (str "Transfer complete: " pje)))
    (printJobCompleted [this pje]
      (timbre/info (str "Job complete: " pje)))
    (printJobFailed [this pje]
      (timbre/error (str "Job failed: " pje)))
    (printJobCanceled [this pje] 
      (timbre/info (str "Job canceled: " pje)))
    (printJobNoMoreEvents [this pje]
      (timbre/info (str "No more events: " pje))
      (if (seq docs)
        ;; Continue printing
        (.. this (printJobCompleted pje))))
    (printJobRequiresAttention [this pje]
      (timbre/warn (str "Job requires attention: " pje)))))
