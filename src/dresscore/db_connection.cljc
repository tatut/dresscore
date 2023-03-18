(ns dresscore.db-connection
  (:require [hyperfiddle.electric :as e]
            [missionary.core :as m]
            #?(:clj [specql.core :as specql])))


#?(:clj )

;; FIXME: use a real connection pool





#_(e/defn latest-table-change> [table]
  (m/observe (fn [!]
               (! nil)
               (add-table-listener! table !))))
