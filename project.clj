(defproject clj-print "0.1.0-SNAPSHOT"
  :description "A javax.print wraper"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :resource-paths ["src/main/resources"]
  :main clj-print.core
  :profiles {:uberjar {:aot [clj-print.core]}}
  :dependencies [[org.clojure/clojure "1.5.1"]])
