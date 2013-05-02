(defproject com.palletops/ami-crate "0.8.0-SNAPSHOT"
  :description "Pallet crate to install, configure and use ami-crate"
  :url "http://palletops.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:pallet/ami-crate.git"}

  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-beta.9"]
                 [com.palletops/rbenv-crate "0.8.0-alpha.1"]
                 [amazonica "0.1.6"]]
  :repositories {"sonatype"
                 {:url "https://oss.sonatype.org/content/repositories/releases/"
                  :snapshots false}}
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/ami_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"]
  :profiles {:pallet {:dependencies
                      [[org.jclouds.provider/aws-ec2 "1.5.5"]
                       [org.jclouds.provider/aws-s3 "1.5.5"]
                       [org.jclouds.driver/jclouds-slf4j "1.5.5"]
                       [org.jclouds.driver/jclouds-sshj "1.5.5"]]}})
