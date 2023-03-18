(ns dresscore.routes
  (:require [reitit.ring :as r]
            [dresscore.page :refer [->page]]
            [dresscore.page.program :as page.program]
            ripley.live.context))

(def routes
  (r/router
   [["/program/:name" {:get (->page #'page.program/program)}]
    ["/program/:name/new-score" {:get (->page #'page.program/program-score)}]
    ["/__ripley-live" {:get  (ripley.live.context/connection-handler "/__ripley-live"
                                                                     :ping-interval 45)}]]))
