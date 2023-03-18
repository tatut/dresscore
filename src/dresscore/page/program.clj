(ns dresscore.page.program
  (:require [dresscore.db :refer [fetch-source]]
            [dresscore.ui :as ui]
            [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.live.protocols :as p]
            [ripley.js :as js]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [specql.core :as specql]))

(defn program [{db :db {name :name} :path-params}]
  (h/html
   [:span
    [::h/live (fetch-source {:db db} :ds/program #{:ds/name :ds/id :ds/parts} {:ds/name name})
     (fn [{:ds/keys [name id parts]}]
       (h/html
        [:div.card.w-96.bg-base-100.shadow-xl
         [:div.card-body
          [:h2.card-title name]
          [:p
           [:ul
            [::h/for [{:ds/keys [name]} parts]
             [:li name]]]]
          [:div.card-actions.justify-end
           [:a {:href (str "/program/" name "/new-score")} "Kirjaa tulos!"]]]]))]]))

(defn if-changed [key-fn]
  (let [val (atom {})]
    (fn [new]
      (let [old @val]
        ;;(println (pr-str old)  "  =>  " (pr-str new))
        (reset! val new)
        (not= (key-fn old) (key-fn new))))))

(defn- ->long [x]
  (try (Long/parseLong (str/trim x))
       (catch NumberFormatException _nfe
         nil)))

(defn- ->decimal [x]
  (try (BigDecimal. (-> x str/trim (str/replace "," ".")))
       (catch NumberFormatException _nfe
         nil)))

(defn- ->date [x]
  (try (java.time.LocalDate/parse (str/trim x) java.time.format.DateTimeFormatter/ISO_DATE)
       (catch java.time.format.DateTimeParseException _dtpe
         nil)))

(defn save-score! [db {:keys [program form] :as save}]
  (let [program-id (:ds/id program)
        {:keys [rider horse date judges]} form]
    (when (and program-id rider horse date)
      (jdbc/with-db-transaction [db db]
        (dotimes [j judges]
          (let [record #:ds {:program-id program-id
                             :date (-> date (.atStartOfDay (java.time.ZoneId/systemDefault)) .toInstant java.util.Date/from)
                             :rider-id rider
                             :horse-id horse
                             :judge-id (get-in form [j :id])
                             :scores (vec
                                      (for [{id :ds/id} (:ds/parts program)
                                            :let [score (get-in form [j id])]
                                            :when score]
                                        #:ds{:part-id id :score score}))}]
            (specql/upsert! db :ds/scoring #{:ds/program-id :ds/date :ds/judge-id :ds/horse-id :ds/rider-id}
                            record))))
      (def *save save))))

(defn calculate-percentage [{:keys [program form] :as a}]
  (def *a a)
  (println "A: " (pr-str a))
  (with-precision 5
    (let [max-points (* 10M (count (:ds/parts program)))
          judges (:judges form)
          judge-points (for [j (range judges)]
                         (reduce + (keep #(get-in form [j %])
                                         (map :ds/id (:ds/parts program)))))
          avg-points (/ (reduce + judge-points) (count judge-points))]
      {:max-points max-points
       :average-points avg-points
       :percentage (/ (* 100M avg-points) max-points)})))

(defn program-score [{db :db {name :name} :path-params}]
  (let [program-source (fetch-source {:db db :process-results first}
                                     :ds/program
                                     #{:ds/name :ds/id :ds/parts}
                                     {:ds/name name})
        [form _ update-form!] (source/use-state {:judges 1})
        assoc-form! (fn [path val]
                      (update-form! assoc-in path val))
        source (source/computed #(merge {:program %1 :form %2}) program-source form)
        save-listen! (p/listen! source (partial save-score! db))]

    (h/html
     [:div

      [:div.flex.w-full
       [::h/live
        {:source source
         :should-update? (if-changed (juxt :date :rider :horse))
         :component
         (fn [{{:ds/keys [name id parts]} :program
               {:keys [judges] :as f} :form :as val}]
           (let [{:keys [percentage max-points average-points]}
                 (calculate-percentage val)]
             (h/html
              [:div.flex.w-full
               [:div
                [:div.divider "Suorituksen tiedot"]

                [:div.form-control.m-2
                 [:label.input-group
                  [:span {:class "w-1/6"} "Ohjelma"]
                  [:input.input.input-bordered.w-full.max-w-xs
                   {:disabled true :value name}]]]

                [:div.form-control.m-2
                 [:label.input-group
                  [:span {:class "w-1/6"} "Päivämäärä"]
                  [:input.input.input-bordered.w-full.max-w-xs
                   {:type :date :placeholder "pp.kk.vvvv"
                    :value (str (:date f))
                    :on-change (js/js #(some->> % ->date (assoc-form! [:date])) js/change-value)}]]]

                [:div.form-control.m-2
                 [:label.input-group
                  [:span  {:class "w-1/6"} "Ratsastaja"]
                  (ui/select-or-create {:db db :table :ds/person :where {:ds/rider? true}
                                        :value (:rider f)
                                        :on-change #(some->> % ->long (assoc-form! [:rider]))
                                        :template (fn [name] {:ds/name name :ds/rider? true})
                                        :empty-option "Valitse ratsastaja"
                                        :create-option "Uusi ratsastaja..."})]]

                [:div.form-control.m-2
                 [:label.input-group
                  [:span {:class "w-1/6"} "Hevonen"]
                  (ui/select-or-create {:db db :table :ds/horse :where {}
                                        :value (:horse f)
                                        :on-change #(some->> % ->long (assoc-form! [:horse]))
                                        :template (fn [name] {:ds/name name})
                                        :empty-option "Valitse hevonen"
                                        :create-option "Uusi hevonen..."})]]]])))}]

       [:div.divider.divider-horizontal]

       [::h/live source
        (fn [value]
          (let [{:keys [percentage max-points average-points]} (calculate-percentage value)]
            (let [style (str "--value:" percentage ";--size:35vw;")]
              (h/html
               [:div.stat
                [:div.stat-title "Prosentit"]
                [:div.stat-value
                 [:div.radial-progress {:style style}
                  (h/out! (format "%.3f%%" percentage))]]
                [:div.stat-desc.my-1 average-points " pistettä (" max-points " maksimi)"]]))))]]

      [:div.divider "Arviointi"]

      [::h/live
       {:source source
        :should-update? (if-changed (comp :judges :form))
        :component
        (fn [{{:ds/keys [name parts]} :program
              {:keys [judges] :as f} :form}]
          (h/html
           [:table.table.table-zebra.table-compact
            [:thead
             [:tr
              [:td "#"]
              [:td "Kohta"]
              [::h/for [j (range 0 judges)]
               [:td
                (ui/select-or-create {:db db
                                      :name (str "j" j)
                                      :table :ds/person
                                      :where {:ds/judge? true}
                                      :template (fn [val] #:ds {:name val :judge? true})
                                      :empty-option "Tuomari"
                                      :create-option "Uusi tuomari..."
                                      :value (get-in f [j :id])
                                      :on-change #(some->> % ->long (assoc-form! [j :id]))
                                      :style "width: 20vw;"})]]
              [:td
               [:button.btn.btn-ghost {:on-click [js/prevent-default #(update-form! update :judges inc)]}
                "+ tuomari"]]]]

            [:tbody
             [::h/for [{:ds/keys [id name]} parts]
              [:tr
               [:td id]
               [:td name]

              ;; jokaiselle tuomarille oma
               [::h/for [j (range 0 judges)]
                [:td [:input.input.input-sm.w-full.max-w-xs
                      {:name (str "j" j "_" id) :placeholder "10" :type "number"
                       :min "0" :max "10" :step ".1"
                       :value (str (get-in f [j id]))
                       :on-change (js/js #(some->> % ->decimal (assoc-form! [j id])) js/change-value)
                       :tabindex (+ 5 (* j 100) id)}]]]]]]]))}]])))
