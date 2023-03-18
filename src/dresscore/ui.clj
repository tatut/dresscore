(ns dresscore.ui
  "Generic UI utils"
  (:require [ripley.html :as h]
            [ripley.js :as js]
            [ripley.live.source :as source]
            [dresscore.db :refer [fetch-source insert!]]))

(defn input-group [{:keys [label name placeholder type on-enter] :or {type :text}}]
  (h/html
   [:div.form-control
    [:label.label
     [:span.label-text label]]
    [:label.input-group
     [:span name]
     [:input.input.input-bordered.w-full.max-w-xs
      {:on-keydown (js/js-when js/enter-pressed? on-enter js/change-value)
       :type type :placeholder placeholder}]]]))

(defn input [{:keys [name type placeholder value on-change] :or {type :text placeholder ""}}]
  (h/html
   [:input.input.input-sm.w-full.max-w-xs
    {:name name :placeholder placeholder :type type
     :value (str value)
     :on-change (js/js on-change js/change-value)}]))

(defn select-or-create [{:keys [db table where display-key id-key empty-option create-option template
                                value name on-change style]
                         :or {display-key :ds/name
                              id-key :ds/id
                              style "width: 33vw;"}}]
  (let [options (fetch-source {:db db} table #{id-key display-key} where)
        create-val (str (gensym "new"))
        [creating? set-creating!] (source/use-state false)]
    (h/html
     [:div.relative
      [::h/live options
       (fn [options]
         (h/html
          [:select.select.select-primary
           {:style style
            :name name :on-change (js/js #(if (= % create-val)
                                            (set-creating! true)
                                            (on-change %)) js/change-value)}
           [::h/when empty-option
            [:option {:disabled true :selected (nil? value)} empty-option]]
           [::h/for [item options
                     :let [option-value (get item id-key)
                           label (get item display-key)]]
            [:option {:value option-value :selected (= value option-value)} label]]
           [::h/when create-option
            [:option {:value create-val} create-option]]]))]

      [::h/when creating?
       [:div.absolute.bottom-0.left-0.m-2.shadow-lg {:style "width: 33vw;"}
        (input-group {:label create-option
                      :name "Luo"
                      :placeholder create-option
                      :on-enter (fn [val]
                                  (println "Luodaan....")
                                  (set-creating! false)
                                  (insert! db table (template val)))})]]])))
