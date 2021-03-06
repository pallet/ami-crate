{:dev
 {:dependencies [[com.palletops/pallet "0.8.0-beta.9" :classifier "tests"]
                 [com.palletops/crates "0.1.1-SNAPSHOT"]
                 [com.palletops/git-crate "0.8.0-SNAPSHOT"]
                 [ch.qos.logback/logback-classic "1.0.9"]
                 [org.slf4j/jcl-over-slf4j "1.7.3"]]
  :exclusions [commons-logging]
  :plugins [[lein-set-version "0.3.0"]
            [lein-resource "0.3.2"]]
  :aliases {"live-test-up"
            ["pallet" "up"
             "--phases" "install,configure,test"
             "--selector" "live-test"]
            "live-test-down" ["pallet" "down" "--selector" "live-test"]
            "live-test" ["do" "live-test-up," "live-test-down"]}
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :all (constantly true)}}
 :pallet {:dependencies [[org.cloudhoist/pallet-jclouds "1.5.2"]
                         [org.jclouds.provider/aws-ec2 "1.5.5"]
                         [org.jclouds.provider/aws-s3 "1.5.5"]
                         [org.jclouds.driver/jclouds-slf4j "1.5.5"]
                         [org.jclouds.driver/jclouds-sshj "1.5.5"]]}
 :doc {:dependencies [[com.palletops/pallet-codox "0.1.0-SNAPSHOT"]]
       :plugins [[codox/codox.leiningen "0.6.4"]
                 [lein-marginalia "0.7.1"]]
       :codox {:writer codox-md.writer/write-docs
               :output-dir "doc/0.8/api"
               :src-dir-uri "https://github.com/pallet/ami-crate/blob/develop"
               :src-linenum-anchor-prefix "L"}
       :aliases {"marg" ["marg" "-d" "doc/0.8/annotated"]
                 "codox" ["doc"]
                 "doc" ["do" "codox," "marg"]}}
 :release
 {:set-version
  {:updates [{:path "README.md" :no-snapshot true}]}}}
