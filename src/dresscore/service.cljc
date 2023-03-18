(ns dresscore.service
  "Middleware that handles logging and database connection for
  service layer.

  Wraps service calls to a threadpool, and catches exceptions, which
  are shown in an alert message."
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-svg :as svg]
            [contrib.element-syntax :refer-macros [<%]]
            [missionary.core :as m])
  (:import (hyperfiddle.electric Pending)))

(defmulti invoke
  "Multimethod to actually invoke a service call.
  Services must define an implementation for this multimethod.

  The ctx is a map containing the XTDB node (:xtdb) and
  user info (:user).

  The service name is the keyword registered for the service.
  Parameters is a map of keyword parameters for the service.
  "
  (fn [_ctx service-name _service-parameters] service-name))

(e/def ctx {})

;; Bound in app root
(e/def error-atom nil)

(e/defn ServiceError []
  (let [error (e/watch error-atom)]
    (when error
      (<% :div.alert.alert-error.shadow-lg
          (<% :div
              (svg/svg
               (dom/props
                {:class "stroke-current flex-shrink-0 h-6 w-6"
                 :fill :none :viewBox "0 0 24 24"})
               (svg/path
                (dom/props
                 {:stroke-linecap "round" :stroke-linejoin "round"
                  :stroke-width "2"
                  :d "M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"})))
              (<% :span (dom/text error)))
          (<% :div.flex-none
              (<% :button.btn.btn-sm.btn-ghost
                  {:on-click (e/fn [_]
                               (println "klikattu")
                               (reset! error-atom nil))}
                  "Dismiss"))))))

#?(:clj (defn describe-error [t]
          ;; Could use ex-data to get some special key for translation
          ;; or fallback to generic message
          (.getMessage t)))

(e/defn call [name parameters]
  (let [result (e/server
                (e/offload
                 #(try [::result (invoke ctx name parameters)]
                      (catch Throwable t
                        [::error (describe-error t)]))))]
    #?(:cljs (new (m/observe (fn [!]
                               (! nil)
                               (js* "NProgress.start();")
                               #(do (js* "NProgress.done();"))))))
    (println "RESULTTI " (pr-str result))
    (case (first result)
      ::result (second result)
      ::error (do (reset! error-atom (second result))
                  ::error))))

#?(:clj
   (defmacro defservice [fn-name [ctx parameters] & body]
     (let [service-name (-> fn-name name keyword)]
       `(do (defn ~fn-name [~ctx ~parameters]
              ~@body)
            (defmethod invoke ~service-name [ctx# _# parameters#]
              (~fn-name ctx# parameters#))))))
