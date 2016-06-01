(defproject anim-demo "0.1.0-SNAPSHOT"
  :description "ClojureScript in PyQt4 Testbed"
  :url "https://www.github.com/kitsu/PyQt4_CLJS"
  :license {:name "BSD simplified 3-clause License"
            :url "https://www.opensource.org/licenses/BSD-3-Clause"}

  :min-lein-version "2.6.1"
  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [prismatic/dommy "1.1.0"]]
  
  :plugins [[lein-cljsbuild "1.1.3" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["target"]

  :cljsbuild {:builds
              [{:id "dev"
                     :source-paths ["src"]
                     :compiler {:main anim-demo.core
                                :asset-path "lib"
                                :output-to "demo/main.js"
                                :output-dir "demo/lib"
                                :source-map-timestamp true}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                     :source-paths ["src"]
                     :compiler {:main anim-demo.core
                                :output-to "demo/main.js"
                                :optimizations :advanced
                                :pretty-print false}}]})
