(ns ^{:doc "PrintJobListeners to react to PrintJobEvents."
      :author "Roberto Acevedo"}
  clj-print.listeners
  (:import (javax.print.event PrintJobListener))
  (:gen-class))

(def counter (atom 0))

(def ^{:doc "Basic PrintJobListener implementation."}
  basic
  (reify PrintJobListener
    (printDataTransferCompleted [this pje]
      (println (str "Transfer complete: " pje))
      (println @counter)
      (swap! counter inc))
    (printJobCompleted [this pje]
      (println (str "Job complete: " pje)))
    (printJobFailed [this pje]
      (println (str "Job failed: " pje)))
    (printJobCanceled [this pje]
      (println (str "Job canceled: " pje)))
    (printJobNoMoreEvents [this pje]
      (println (str "No more events: " pje)))
    (printJobRequiresAttention [this pje]
      (println (str "Job requires attention: " pje)))))
