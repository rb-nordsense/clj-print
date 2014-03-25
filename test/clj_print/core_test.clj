(ns clj-print.core-test
  (:require [clojure.test :refer :all]
            [clj-print.core :refer :all]))

;; TODO: Test status
;; TODO: Test attributes
;; TODO: Test trays

(deftest printers-test
  (testing "Returns default printer"
    (is (= (printer) (javax.print.PrintServiceLookup/lookupDefaultPrintService)))
    (is (= (printer "") (javax.print.PrintServiceLookup/lookupDefaultPrintService)))
    (is (= (printer nil) (javax.print.PrintServiceLookup/lookupDefaultPrintService))))
  (testing "Printers works with all possible arugment arities"
    (seq (printers))
    (seq (printers :flavor javax.print.DocFlavor$INPUT_STREAM/AUTOSENSE))
    (seq (printers :attrs (doto (javax.print.attribute.HashAttributeSet.)
                            (.add (javax.print.attribute.standard.MediaTray/MAIN)))))
    (seq (printers :flavor javax.print.DocFlavor$INPUT_STREAM/AUTOSENSE
                   :attrs (doto (javax.print.attribute.HashAttributeSet.)
                            (.add (javax.print.attribute.standard.MediaTray/MAIN)))))))
