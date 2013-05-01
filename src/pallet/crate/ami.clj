(ns pallet.crate.ami
  "A [pallet](https://palletops.com/) crate to create S3 backed AMI images.

By default, installs ruby using rbenv in the ami-tools install-dir.  We use this
instead of the native package so we can exclude the ruby from the ami easily."
  (:require
   [amazonica.core]
   [amazonica.aws.ec2 :as ec2]
   [clj-schema.schema :refer [constraints def-map-schema map-schema
                              optional-path predicate-schema seq-schema
                              sequence-of set-of wild]]
   [clojure.string :as string]
   [clojure.tools.logging :refer [infof]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions
    :refer [as-action directory exec-checked-script packages remote-directory
            remote-file update-settings]
    :rename {update-settings update-settings-action}
    :as actions]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.compute :refer [service-properties]]
   [pallet.contracts :refer [any-value check-spec]]
   [pallet.crate :refer [admin-user assoc-settings compute-service
                         defmethod-plan defplan get-settings]]
   [pallet.crate.rbenv :as rbenv]
   [pallet.crate-install :as crate-install]
   [pallet.stevedore :refer [fragment]]
   [pallet.script.lib :refer [config-root file mv rm state-root]]
   [pallet.utils :refer [apply-map]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))

(def-map-schema ami-settings-schema
  [[:image-name] string?
   [:image-description] string?
   [:access-key] string?
   [:secret-key] string?
   [:user-id] string?
   [:user] string?
   [:exclusions] (sequence-of string?)
   (optional-path [:extra-exclusions]) (sequence-of string?)
   [:install-strategy] keyword?
   [:install-dir] string?
   [:destination-dir] string?
   [:install-source] (map-schema
                      :loose
                      [(optional-path [:local-file]) string?
                       (optional-path [:url]) string?])
   [:credential-dir] string?
   [:ami-tools-url] string?
   [:private-key-path] string?
   [:certificate-path] string?
   [:private-key-source] (map-schema
                          :loose
                          [(optional-path [:local-file]) string?])
   [:certificate-source] (map-schema
                          :loose
                          [(optional-path [:local-file]) string?])
   [:arch] string?
   [:s3-bucket] string?
   [:s3-path] string?
   [:s3-manifest-path] string?
   [:manifest-path] string?
   [:ruby-version] string?
   (optional-path [:install-ruby]) any-value])

(defmacro check-ami-settings
  [m]
  (check-spec m `ami-settings-schema &form))

;;; # Settings
(defn default-settings
  "Provides default settings, that are merged with any user supplied settings."
  []
  (let [{:keys [identity credential]} (service-properties (compute-service))]
    {:user (:username (admin-user))
     :access-key identity
     :secret-key credential
     :private-key-path nil                ; private key
     :certificate-path nil                ; certificate source
     :user-id nil                         ; aws user id
     :ami-tools-url "http://s3.amazonaws.com/ec2-downloads/ec2-ami-tools.zip"
     :install-ruby true
     :ruby-version "1.9.3-p392"
     :install-dir "/opt/ami-tools"
     :destination-dir "/tmp"
     :credential-dir "/opt/ami-tools/credentials"
     :arch "x86_64"}))

(def rbenv-options {:instance-id ::ami})

(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (merge {:install-strategy ::unzip
                 :install-source {:url (:ami-tools-url settings)}}
                settings)))

(defn finalise-settings
  "Fill in any blanks for the settings"
  [{:keys [credential-dir destination-dir image-name install-dir instance-id
           s3-bucket s3-path]
    :as settings}]
  (let [properties (service-properties (compute-service))
        settings (->
                  settings
                  (update-in
                   [:s3-manifest-path]
                   #(or %           ; <your-s3-bucket>/<path>/image.manifest.xml
                        (format "%s/%s/image.manifest.xml" s3-bucket s3-path)))
                  (update-in
                   [:manifest-path]
                   #(or % (format "%s/image.manifest.xml" destination-dir)))
                  (update-in [:access-key] #(or % (:identity properties)))
                  (update-in [:secret-key] #(or % (:credential properties)))
                  ;; <cert_location>, ruby, pallet md5s, etc
                  (update-in [:exclusions]
                             #(or %
                                  (concat
                                   [install-dir
                                    (fragment (file (state-root) "pallet"))]
                                   (:extra-exclusions settings))))
                  (update-in [:private-key-path]
                             #(or % (str credential-dir "/pk")))
                  (update-in [:certificate-path]
                             #(or % (str credential-dir "/cert"))))]
    (check-ami-settings settings)
    settings))

(defplan settings
  "Settings for ami-crate.

## Options

`:image-name`
: image name

`:image-description`
: image description

`:install-ruby`
: flag to install ruby (defaults to true).  Ruby must be present for the
  ami-tools.

`:ruby-version`
: version of ruby to install (defaults 1.9.3-p392).

`:s3-bucket`
: s3 bucket to upload the bundle to

`:s3-path`
: s3 path to upload the bundle to (relative to the bucket)

`:user-id`
: your AWS user ID (without any dashes)

`:private-key-source`
: a remote-file source map for the AWS private key file

`:certificate-source`
: a remote-file source map for the AWS cert file

`:access-key`
: AWS access key (defaults to the access key for the compute service)

`:secret-key`
: AWS secret key (defaults to the secret key for the compute service)

`:destination-dir`
: directory on the node in which the image bundle should be built (default /tmp)

`:install-dir`
: directory on the node for the ami-tools (default /opt/ami-tools)

`:credential-dir`
: directory on the node for the credential files (default
  /opt/ami-tools/credentials)

`:arch`
: the AMI architecture (default x86_64)"
  [{:keys [image-name s3-bucket s3-path instance-id] :as settings}]
  (let [settings (merge (default-settings) settings)
        settings (settings-map (:version settings) settings)
        settings (finalise-settings settings)]
    (when (:install-ruby settings)
      (rbenv/settings (assoc rbenv-options
                        :install-dir (str (:install-dir settings) "/.rbenv")
                        :user (:user settings))))
    (assoc-settings :ami-crate settings {:instance-id instance-id})))

;;; # Install
(defmethod-plan crate-install/install ::unzip
  [facility instance-id]
  (let [{:keys [install-dir install-source user] :as settings}
        (get-settings facility {:instance-id instance-id})]
    (check-ami-settings settings)
    (packages :apt ["unzip"])
    (apply-map
     remote-directory install-dir
     :unpack :unzip
     :owner user
     install-source)
    (with-action-options {:script-dir install-dir}
      (exec-checked-script
       "remove top level folder"
       (if (not (directory? "ec2-ami-tools"))
         (mv "ec2-ami-tools*" "ec2-ami-tools" :force true))))))

(defplan install
  "Install AMI tools"
  [{:keys [instance-id] :as options}]
  (let [{:keys [credential-dir install-dir install-ruby ruby-version user]
         :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (crate-install/install :ami-crate instance-id)
    (with-action-options {:sudo-user user}
      (directory credential-dir))
    (when install-ruby
      (rbenv/install rbenv-options)
      (apply-map rbenv/install-ruby ruby-version rbenv-options)
      (with-action-options {:script-dir install-dir}
        (apply-map rbenv/local ruby-version rbenv-options)))))

;;; # Configure
(def ^{:doc "Flag for recognising changes to configuration"}
  ami-crate-config-changed-flag "ami-crate-config")

(defplan credential-file
  "Helper to write credential files"
  [path file-source]
  (apply-map
   remote-file path
   :flag-on-changed ami-crate-config-changed-flag
   file-source))

(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [certificate-path certificate-source credential-dir
                private-key-path private-key-source
                user]
         :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (with-action-options {:sudo-user user}
      (credential-file certificate-path certificate-source)
      (credential-file private-key-path private-key-source))))

(defplan ami-bundle
  "Build an AMI bundle."
  [{:keys [instance-id] :as options}]
  (let [{:keys [install-dir install-ruby] :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (with-action-options
      {:script-dir install-dir
       :script-env {"EC2_HOME" (fragment (file ~install-dir "ec2-ami-tools"))}}
      (exec-checked-script
       "Bundle volume"
       ~(if install-ruby
          (rbenv/rbenv-init {:instance-id ::ami})
          ":")
       ((file ~install-dir "ec2-ami-tools" "bin" "ec2-bundle-vol")
        -k ~(:private-key-path settings)
        -c ~(:certificate-path settings)
        -u ~(:user-id settings)
        -e ~(string/join "," (:exclusions settings))
        -r ~(:arch settings))))))

(defplan ami-upload
  "Upload a AMI bundle to S3."
  [{:keys [instance-id] :as options}]
  (let [{:keys [install-dir install-ruby] :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (with-action-options
      {:script-dir install-dir
       :script-env {"EC2_HOME" (fragment (file ~install-dir "ec2-ami-tools"))}}
      (exec-checked-script
       "Upload bundle"
       ~(if install-ruby
          (rbenv/rbenv-init {:instance-id ::ami})
          ":")
       ((file ~install-dir "ec2-ami-tools" "bin" "ec2-upload-bundle")
        -b ~(str (:s3-bucket settings)
                 (if-let [p (:s3-path settings)] (str "/" p) ""))
        -m ~(:manifest-path settings)
        -a ~(:access-key settings)
        -s ~(:secret-key settings))))))

(defplan ami-register
  "Register an S3 backed AMI."
  [{:keys [instance-id] :as options}]
  (let [{:keys [install-dir install-ruby] :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (let [rv (as-action
              (let [response (ec2/register-image
                              (select-keys settings [:access-key :secret-key])
                              :name (:image-name settings)
                              :image-location (:s3-manifest-path settings)
                              :description (:image-description settings))]
                (infof "ami-register image-id %s"
                       (pr-str (:image-id response)))
                response))]
      (update-settings-action :ami-crate merge rv))))

(defplan cleanup
  "Remove ruby and ami-tools and bundle files."
  [{:keys [instance-id] :as options}]
  (let [{:keys [install-dir destination-dir credential-dir] :as settings}
        (get-settings :ami-crate options)]
    (check-ami-settings settings)
    (exec-checked-script
     "Remove bundle image files"
     (rm ~credential-dir :recursive true :force true)
     (rm ~install-dir :recursive true :force true)
     (rm (file ~destination-dir "image.part.*"))
     (rm (file ~destination-dir "image.manifest.xml")))))

;;; # Server spec
(defn server-spec
  "Returns a server-spec that installs and configures ami-crate."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
                (pallet.crate.ami/settings (merge settings options)))
    :install (plan-fn
               (install options))
    :configure (plan-fn
                 (configure options))
    :ami-bundle (plan-fn
                  (ami-bundle options))
    :ami-upload (plan-fn
                  (ami-upload options))
    :ami-register (plan-fn
                    (ami-register options))
    :cleanup (plan-fn
               (cleanup options))}))
