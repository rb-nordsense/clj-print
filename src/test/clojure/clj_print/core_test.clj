(ns clj-print.core-test
  (:require [clojure.test :refer :all]
            [clj-print.core :refer :all]))

;; TODO: Test status
;; TODO: Test attributes
;; TODO: Test trays

(deftest printers-test
  (testing "printer returns the default printer when :default is provided"
    (is (= (printer :default) (javax.print.PrintServiceLookup/lookupDefaultPrintService))))
  ;; (testing "Printers works with all possible arugment arities"
  ;;   (seq (printers))
  ;;   (seq (printers :flavor javax.print.DocFlavor$INPUT_STREAM/AUTOSENSE))
  ;;   (seq (printers :attrs (doto (javax.print.attribute.HashAttributeSet.)
  ;;                           (.add (javax.print.attribute.standard.MediaTray/MAIN)))))
  ;;   (seq (printers :flavor javax.print.DocFlavor$INPUT_STREAM/AUTOSENSE
  ;;                  :attrs (doto (javax.print.attribute.HashAttributeSet.)
  ;;                           (.add (javax.print.attribute.standard.MediaTray/MAIN))))))
  )

;; TODO Test make-doc in this fashion (varying arities)
;; clj-print.core> (make-doc {:source "/Users/Roberto/Desktop/test.txt"
;;                            :flavor (:autosense flavors/input-streams)
;;                            :attrs #{}})
;; ;; => #<SimpleDoc javax.print.SimpleDoc@b76d58f>
;; clj-print.core> (make-doc {:source "/Users/Roberto/Desktop/test.txt"
;;                            :flavor (:autosense flavors/input-streams)
;;                            :attrs nil})
;; ;; => #<SimpleDoc javax.print.SimpleDoc@173c0e5c>
;; clj-print.core> (doc empty?)
;; -------------------------
;; clojure.core/empty?
;; ([coll])
;;   Returns true if coll has no items - same as (not (seq coll)).
;;   Please use the idiom (seq x) rather than (not (empty? x))
;; ;; => nil
;; clj-print.core> (make-doc {:source "/Users/Roberto/Desktop/test.txt"
;;                            :flavor (:autosense flavors/input-streams)
;;                            :attrs nil})
;; ;; => #<SimpleDoc javax.print.SimpleDoc@748c9a21>
;; clj-print.core> (make-doc {:source "/Users/Roberto/Desktop/test.txt"
;;                            :flavor (:autosense flavors/input-streams)})
;; ;; => #<SimpleDoc javax.print.SimpleDoc@a992139>
;; clj-print.core> (make-doc {:source "/Users/Roberto/Desktop/test.txt"})
;; ;; => #<SimpleDoc javax.print.SimpleDoc@6a2e603a>
;; clj-print.core> 
