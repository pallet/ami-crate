(ns pallet.crate.ami-test
  (:require
   [amazonica.core]
   [amazonica.aws.ec2 :as ec2]
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-script*]]
   [pallet.api :refer [group-spec plan-fn]]
   [pallet.crate :refer [get-settings]]
   [pallet.crate.ami :as ami]
   [pallet.crate.git :as git]
   [pallet.script-test :refer [is-true testing-script]]))


(def test-spec
  (let [env (System/getenv)]
    (group-spec "ami"
      :extends
      [(git/server-spec {})
       (ami/server-spec
        {:image-name "pallet-test-ami"
         :image-description "Pallet test AMI"
         :s3-bucket (get env "S3_BUCKET")
         :s3-path (get env "S3_PATH")
         :user-id (get env "AWS_USER_ID")
         :private-key-source {:local-file (get env "AWS_PRIVATE_KEY_FILE")}
         :certificate-source {:local-file (get env "AWS_CERT_FILE")}})]
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
