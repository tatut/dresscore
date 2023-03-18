(ns dresscore.main
  (:require [dresscore.db :as db]
            [dresscore.routes :as routes]
            [org.httpkit.server :as server]
            [reitit.ring :as ring]
            [ripley.html :as h]))

(def http-config
  {:ip "0.0.0.0"
   :port 8080})


(defonce server nil)

(defn start []
  (alter-var-root #'server
                  (fn [old-server]
                    (when old-server
                      (old-server))
                    (server/run-server (ring/ring-handler routes/routes
                                                          (ring/create-resource-handler { :path "/"})
                                                          {:middleware [db/wrap-db]})
                                       http-config))))

(defn main [& _args]
  (start))
