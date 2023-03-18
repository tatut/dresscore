(ns dresscore.main ^:dev/always
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.api :as hf]
            [hyperfiddle.history :as router]
            [dresscore.service :as service]
            [dresscore.routes :as routes]
            [dresscore.db-connection :as db-connection]
            [dresscore.page.program :as page.program])
    (:require-macros
     [contrib.element-syntax :refer [<%]]))

(e/defn Navbar []
  (<% :div.navbar.bg-base-100
      (<% :div.flex-1
          (<% :a.btn.btn-ghost.normal-case.text-xl "Dresscore"))
      (<% :div.flex-none)))


(e/defn Content []
  (let [route (get-in router/route [:data :name])
        params (:path-params router/route)]
    (<% :div.content
        (case route
          ::routes/program (page.program/Program. params)
          ::routes/new-score (page.program/ProgramScore. params)
          (dom/text "ei oo routea! " (pr-str router/route))))))

(e/defn Menu []
  (<% :div.drawer.drawer-mobile
      (<% :input.drawer-toggle {:type :checkbox})
      (<% :div.drawer-content ;.flex.flex-col.items-center.justify-center
          (Content.))
      (<% :div.drawer-side
          (<% :label.drawer-overlay)
          (<% :ul.menu.bg-base-100.w-56
              ;; (e/for [[label route] [["Dashboard" [::routes/dashboard]]
              ;;                        ["Organizations" [::routes/organizations]]
              ;;                        ["Contacts" [::routes/contacts]]
              ;;                        ["Reports" [::routes/reports]]]]
              ;;   (let [href (routes/path route)]
              ;;     (<% :li
              ;;         (<% :a {:href href
              ;;                 :on-click (e/fn [e]
              ;;                             (.preventDefault e)
              ;;                             (router/navigate! router/!history route))}
              ;;             (dom/text label)))))


              ))))


(e/defn Main []
  (router/router
   (router/HTML5-History.)

   (Navbar.)
   (service/ServiceError.)
   (Menu.)))



(def electric-main
  (hyperfiddle.electric/boot ; Electric macroexpansion - Clojure to signals compiler
   (binding [hyperfiddle.electric-dom2/node (.getElementById js/document "app")
             router/encode :path ;;routes/path
             router/decode routes/decode
             service/error-atom (atom nil)
             ;;db-connection/db (db-connection/get-db)
             ]
     (Main.))))

(defonce reactor nil)

(defn ^:dev/after-load ^:export start! []
  (assert (nil? reactor) "reactor already running")
  (set! reactor (electric-main
                  #(js/console.log "Reactor success:" %)
                  #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop! []
  (when reactor (reactor)) ; teardown
  (set! reactor nil))
