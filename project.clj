(defproject navis/untangled-spec "0.3.10-SNAPSHOT"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url ""
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[cljsjs/react-with-addons "15.0.1-1" :scope "provided"]
                 [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [kibu/pushy "0.3.6"]
                 [lein-doo "0.1.6" :scope "test"]
                 [navis/untangled-client "0.6.0"]
                 [navis/untangled-server "0.7.0-SNAPSHOT" :exclusions [com.taoensso/timbre]]
                 [navis/untangled-websockets "0.3.3-SNAPSHOT"]
                 [org.clojure/clojure "1.9.0-alpha14" :scope "provided"]
                 [org.clojure/clojurescript "1.9.293" :scope "provided"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 [org.omcljs/om "1.0.0-alpha47" :scope "provided" :exclusions [cljsjs/react]]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.18.1" :exclusions [org.clojure/tools.namespace]]
            [lein-cljsbuild "1.1.5"]
            [lein-doo "0.1.6"] ;; for cljs CI tests
            [lein-figwheel "0.5.8" :exclusions [ring/ring-core commons-fileupload clj-time joda-time org.clojure/clojure org.clojure/tools.reader]]
            [lein-shell "0.5.0"]]

  :release-tasks [["shell" "bin/release" "all_tasks"]]

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["src" "resources"]

  ;; this for backwards compatability, should now use untangled-spec.suite/test-suite & see dev/clj/user.clj for an example
  :test-refresh {:report untangled-spec.reporters.terminal/untangled-report
                 :changes-only true
                 :with-repl true}

  ;; CI tests: Set up to support karma runner. Recommend running against chrome. See README
  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}

  :clean-targets ^{:protect false} [:target-path "target" "resources/public/js" "resources/private/js"]

  :cljsbuild {:test-commands {"unit-tests" ["phantomjs" "run-tests.js" "resources/private/unit-tests.html"]}
              :builds        [{:id           "test"
                               :jar          true
                               :source-paths ["src" "dev" "test"]
                               :figwheel     {:on-jsload cljs.user/on-load}
                               :compiler     {:main          cljs.user
                                              :output-to     "resources/public/js/test/test.js"
                                              :output-dir    "resources/public/js/test/out"
                                              :asset-path    "js/test/out"
                                              :optimizations :none}}
                              {:id           "server-tests"
                               :source-paths ["src" "dev" "test"]
                               :compiler     {:main          cljs.server-tests
                                              :output-to     "resources/public/js/test/server-tests.js"
                                              :output-dir    "resources/public/js/test/server-tests"
                                              :asset-path    "js/test/server-tests"
                                              :optimizations :none}}
                              ;; FOR CI tests. Runs via doo
                              {:id           "automated-tests"
                               :source-paths ["src" "test"]
                               :compiler     {:output-to     "resources/private/js/unit-tests.js"
                                              :main          untangled-spec.all-tests
                                              :optimizations :none}}]}

  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]

  :figwheel {:nrepl-port 7888
             :server-port 3457}
  :profiles {:dev {:source-paths ["src" "test" "dev"]
                   :repl-options {:init-ns clj.user
                                  :port    7007
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :dependencies [[figwheel-sidecar "0.5.8" :exclusions [ring/ring-core http-kit joda-time]]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [org.clojure/test.check "0.9.0"]]}})
