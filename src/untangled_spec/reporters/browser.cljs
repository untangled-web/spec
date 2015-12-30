(ns untangled-spec.reporters.browser
  (:import [goog Delay])
  (:require
    goog.object
    [om.dom :as dom]
    [goog.dom :as gdom]
    [om.next :as om :refer-macros [defui]]
    [cljs-uuid-utils.core :as uuid]

    [untangled-spec.reporters.impl.browser :as impl]))

(defui ResultLine
       Object
       (initLocalState [this] {:folded? true})
       (render [this]
               (let [{:keys [title value]} (om/props this)
                     {:keys [folded?]} (om/get-state this)]
                 (dom/tr nil
                         (dom/td #js {:className "test-result-title"} title)
                         (dom/td #js {:className "test-result"
                                      :onClick #(om/update-state! this update :folded? not)}
                                 (if (.-stack value)
                                   (dom/code nil
                                             (if folded? \u25BA \u25BC)
                                             (str value)
                                             (dom/div #js {:className (if folded? "hidden" "stack-trace")}
                                                      (some-> value .-stack impl/stack->trace)))
                                   (dom/code nil (str value))))))))

(def <result-line> (om/factory ResultLine))

(defui TestResult
       Object
       (render [this]
               (let [{:keys [message actual expected]} (om/props this)]
                 (->> (dom/tbody nil
                                 (<result-line> {:title "Actual"
                                                     :value actual})
                                 (<result-line> {:title "Expected"
                                                     :value (or expected "")}))
                      (dom/table nil)
                      (dom/div nil (when message (dom/h3 nil message)))
                      (dom/li nil)))))

(def <test-result> (om/factory TestResult {:keyfn :id}))

(declare <test-item>)

(defui TestItem
       Object
       (render [this]
               (let [test-item-data (om/props this)
                     filter (:report/filter test-item-data) ]
                 (dom/li #js {:className "test-item "}
                         (dom/div #js {:className (impl/filter-class test-item-data)}
                                  (dom/span #js {:className (impl/itemclass (:status test-item-data))}
                                            (:name test-item-data))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv <test-result>
                                                (:test-results test-item-data)))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv (comp <test-item> #(assoc % :report/filter filter))
                                                (:test-items test-item-data))))))))

(def <test-item> (om/factory TestItem {:keyfn :id}))

(defui TestNamespace
       Object
       (initLocalState [this] {:folded? false})
       (render
         [this]
         (let [tests-by-namespace (om/props this)
               filter (:report/filter tests-by-namespace)
               {:keys [folded?]} (om/get-state this)]
           (dom/li #js {:className "test-item"}
                   (dom/div #js {:className "test-namespace"}
                            (dom/a #js {:href "#"
                                        :style #js {:textDecoration "none"} ;; TODO: refactor to css
                                        :onClick   #(om/update-state! this update :folded? not)}
                                   (dom/h2 #js {:className (impl/itemclass (:status tests-by-namespace))}
                                           (if folded? \u25BA \u25BC)
                                           " Testing " (:name tests-by-namespace)))
                            (dom/ul #js {:className (if folded? "hidden" "test-list")}
                                    (mapv (comp <test-item> #(assoc % :report/filter filter))
                                          (:test-items tests-by-namespace))))))))

(def <test-namespace> (om/factory TestNamespace {:keyfn :name}))

(defui Filters
       Object
       (render [this]
               (let [{:current-filter :report/filter
                      :keys [set-filter!]} (om/props this)]
                 (dom/div #js {:name "filters" :className "filter-controls"}
                          (dom/label #js {:htmlFor "filters"} "Filter: ")
                          (dom/a #js {:className (if (= current-filter :all) "selected" "")
                                      :onClick   (set-filter! :all)}
                                 "All")
                          (dom/a #js {:className (if (= current-filter :manual) "selected" "")
                                      :onClick   (set-filter! :manual)}
                                 "Manual")
                          (dom/a #js {:className (if (= current-filter :failed) "selected" "")
                                      :onClick   (set-filter! :failed)}
                                 "Failed")))))

(def <filters> (om/factory Filters))

(defn debounce [f interval]
  (let [timeout (atom nil)]
    (fn [& args]
      (when-not (nil? @timeout)
        (.disposeInternal @timeout))
      (reset! timeout (Delay. #(apply f args)))
      (.start @timeout interval))))

(def notification (atom nil))
(defn *notify-failure!
  "for more info: https://developer.mozilla.org/en-US/docs/Web/API/Notification/Notification"
  [[passed failed errors total]]
  (let [notify-str (str (+ failed errors)
                        " tests failed out of " total)]
    (cond
      (= js/Notification.permission "granted")
      (do (when @notification (.close @notification))
          (reset! notification
                  (new js/Notification "cljscript tests failed"
                       #js {:body notify-str})))

      (not= js/Notification.permission "denied")
      (js/Notification.requestPermission
        (fn [perm]
          (when (= perm "granted")
            (new js/Notification
                 "cljscript tests failed" #js {:body notify-str}))))

      :else (println :NO-NOTIFY-PERMISSION))))

(def notify-failure! (debounce *notify-failure! 1000))

(defui TestCount
       Object
       (render [this]
               (let [{:keys [passed failed error namespaces]} (om/props this)
                     total (+ passed failed error)]
                 (if (< 0 (+ failed error))
                   (do (impl/change-favicon-to-color "#d00")
                       (notify-failure! [passed failed error total]))
                   (impl/change-favicon-to-color "#0d0"))
                 (dom/div #js {:className "test-count"}
                          (dom/h2 nil
                                  (str "Tested " (count namespaces) " namespaces containing "
                                       total  " assertions. "
                                       passed " passed "
                                       failed " failed "
                                       error  " errors"))))))

(def <test-count> (om/factory TestCount))

(defui TestReport
       static om/IQuery
       (query [this] [:top :report/filter])
       Object
       (render [this]
               (let [props (om/props this)
                     test-report-data (-> props :top)
                     current-filter (-> props :report/filter)
                     set-filter! (fn [new-filter]
                                   #(om/transact! this `[(~'set-filter {:new-filter ~new-filter})]))]
                 (dom/section #js {:className "test-report"}
                              (<filters> {:report/filter current-filter
                                          :set-filter! set-filter!})
                              (dom/ul #js {:className "test-list"}
                                      (mapv (comp <test-namespace>
                                                  #(assoc % :report/filter current-filter))
                                            (:namespaces test-report-data)))
                              (<test-count> test-report-data)))))
