(ns dresscore.db
  (:require [specql.rel :as rel]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [specql.core :refer [define-tables] :as specql]
            [ripley.live.source :as source]
            [ripley.live.protocols :as lp]))

;; FIXME: use actual db connection pool
(def db-config {:dbtype "postgresql"
                :dbname "dresscore"})

(defn wrap-db [handler]
  (fn [req]
    (handler (assoc req :db db-config))))

(defonce table-listeners
  (atom {}))

(defn add-table-listener! [table callback]
  (swap! table-listeners
         update table
         (fn [listeners]
           (conj (or listeners #{}) callback)))
  #(swap! table-listeners
          update table
          (fn [listeners]
            (disj listeners callback))))

(defn table-changed! [table]
  (let [now (System/currentTimeMillis)]
    (future
      (doseq [callback (-> table-listeners deref (get table))]
        (callback now)))))

(defn table-change-source
  "Source that updates when any of the given tables changes.
  The value is calculated with `run-q` which receives the db
  connection."
  [db immediate? tables run-q]
  (let [listeners (atom nil)
        last-value (atom (when immediate?
                           (run-q db)))
        [source _listeners]
        (source/source-with-listeners #(deref last-value)
                                      #(doseq [listener @listeners]
                                         (listener)))

        update! #(let [new-value (run-q db)]
                   (reset! last-value new-value)
                   (lp/write! source new-value))]
    (when-not immediate?
      (future
        (update!)))
    (reset! listeners
            (vec
             (for [table tables]
               (add-table-listener! table (fn [_ts] (update!))))))
    source))

(defn fetch-source
  "Return a fetch source that updates when any of the referred tables
  is updated.

  Options must include a `:db` that is the connection (pool) to use.
  If `:immediate?` is true (default) the query is run immediately.
  Otherwise the query is offloaded to a thread pool and results will
  not be available at initial render.

  If `:process-results` function is provided, it will be used to process
  the query results before passing it to the component.
  "
  [{:keys [db immediate? process-results]
    :or {immediate? true
         process-results identity}} table fields where]
  (table-change-source
   db immediate?
   [table]
   (fn [db] (process-results (specql/fetch db table fields where)))))

(defn insert! [db table record]
  (specql/insert! db table record)
  (table-changed! table)
  :ok)

;; PENDING: having trouble on cljs side
(specql/define-tables db-config #_{:specql.core/schema-file "dresscore-schema.edn"}
  ["program_part" :ds/program-part]
  ["program" :ds/program]
  ["score" :ds/score-type {"part_id" :ds/part-id}]
  ["person" :ds/person {"is_rider" :ds/rider?
                        "is_judge" :ds/judge?}]
  ["horse" :ds/horse]
  ["scoring" :ds/scoring {"program_id" :ds/program-id
                          "judge_id" :ds/judge-id
                          "rider_id" :ds/rider-id
                          "horse_id" :ds/horse-id
                          :ds/program (rel/has-one :ds/program-id :ds/program :ds/id)
                          :ds/rider (rel/has-one :ds/rider-id :ds/person :ds/id)
                          :ds/horse (rel/has-one :ds/horse-id :ds/horse :ds/id)
                          :ds/judge (rel/has-one :ds/judge-id :ds/person :ds/id)}]

  ["percentages" :ds/percentages {"program_id" :ds/program-id
                                  "judge_id" :ds/judge-id
                                  "rider_id" :ds/rider-id
                                  "horse_id" :ds/horse-id
                                  "max_points" :ds/max-points
                                  :ds/program (rel/has-one :ds/program-id :ds/program :ds/id)
                                  :ds/rider (rel/has-one :ds/rider-id :ds/person :ds/id)
                                  :ds/horse (rel/has-one :ds/horse-id :ds/horse :ds/id)
                                  :ds/judge (rel/has-one :ds/judge-id :ds/person :ds/id)} ]
  )

(defn sync-programs! [db]
  (let [programs (-> "programs.edn" io/resource slurp read-string)]
    (jdbc/with-db-transaction [db db]
      (doseq [[name parts] programs
              :let [existing-program (first
                                      (specql/fetch db :ds/program
                                                    #{:ds/id :ds/name :ds/parts}
                                                    {:ds/name name}))]]
        (specql/upsert!
         db :ds/program #{:ds/name}
         (merge existing-program
                {:ds/name name
                 :ds/parts (vec (map-indexed
                                 (fn [i p]
                                   {:ds/id (inc i)
                                    :ds/name p}) parts))}))))))

(defn by-name [type db name]
  (first (specql/fetch db type #{:ds/id :ds/name} {:ds/name name})))

(def person-by-name (partial by-name :ds/person))
(def horse-by-name (partial by-name :ds/horse))
(def program-by-name (partial by-name :ds/program))
(def person-ref (comp :ds/id person-by-name))
(def horse-ref (comp :ds/id horse-by-name))
(def program-ref (comp :ds/id program-by-name))

(comment

  (specql/insert! db-config :ds/horse #:ds {:name "Flex"})

    (specql/insert! db-config :ds/horse #:ds {:name "Flex" :gender "stallion"})

  (specql/insert! db-config :ds/person #:ds {:name "Headless Rider" :rider? true})
  (specql/insert! db-config :ds/person #:ds {:name "Dredd" :judge? true})
  (specql/insert! db-config :ds/scoring
                  {:ds/program-id (program-ref db-config "Helppo A")
                   :ds/scores [{:ds/part-id 1 :ds/score 42}]
                   :ds/date (java.util.Date.)
                   :ds/horse-id (horse-ref db-config "Flex")
                   :ds/rider-id (person-ref db-config "Headless Rider")
                   :ds/judge-id (person-ref db-config "Dredd")})


  (specql/fetch db-config :ds/scoring #{:ds/program-id :ds/scores :ds/date} {})

  (specql/fetch db-config
                :ds/scoring

                #{ ;; Fetch data from all 4 linked linked entities
                  [:ds/program #{:ds/name :ds/parts}]
                  [:ds/horse #{:ds/name :ds/id}]
                  [:ds/rider #{:ds/name :ds/id}]
                  [:ds/judge #{:ds/name :ds/id}]

                  :ds/date}
                {})

  (specql/create-schema-file! db-config "resources/dresscore-schema.edn")
  )
