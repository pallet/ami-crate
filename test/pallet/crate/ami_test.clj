(ns pallet.crate.ami-test
  (:require
   [amazonica.core]
   [amazonica.aws.ec2 :as ec2]
   [clojure.java.io :refer [file resource]]
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-script*]]
   [pallet.api :refer [group-spec plan-fn]]
   [pallet.crate :refer [get-settings]]
   [pallet.crate.ami :as ami]
   [pallet.crate.git :as git]
   [pallet.script-test :refer [is-true testing-script]]))

(defn test-credentials
  []
  (load-file (.getPath (file (resource "test_credentials.clj")))))

(def test-spec
  (let [env (System/getenv)
        creds (test-credentials)]
    (group-spec "ami"
      :extends
      [(git/server-spec {})
       (ami/server-spec
        (merge
         {:image-name "pallet-test-ami"
          :image-description "Pallet test AMI"}
         creds))]
      :phases {:test (plan-fn
                       (let [{:keys [image-id] :as settings}
                             (get-settings :ami-crate {})]
                         (assert image-id "No image-id")
                         (let [images (ec2/describe-images
                                       (select-keys
                                        settings [:access-key :secret-key])
                                       :image-ids [image-id])]
                           (assert (= 1 (count (:images images)))))))}
      :roles #{:live-test :ami})))
