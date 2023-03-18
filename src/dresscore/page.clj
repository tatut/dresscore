(ns dresscore.page
  (:require [ripley.html :as h]))

(defn render-page [page-content-fn req]
  (h/render-response
   #(do
      (h/out! "<!DOCTYPE html>\n")
      (h/html
       [:html {:data-theme "cmyk"}
        [:head
         [:meta {:charset "UTF-8"}]
         [:link {:rel "stylesheet" :href "/app.css"}]
         (h/live-client-script "/__ripley-live")]
        [:body
         [:div.page
          (page-content-fn req)]]]))))

(defn ->page [page-content-fn]
  (partial render-page page-content-fn))
