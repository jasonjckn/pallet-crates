(ns pallet.crate.java-test
  (:use pallet.crate.java)
  (:require
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.core :as core]
   [pallet.live-test :as live-test]
   [pallet.resource :as resource]
   [pallet.resource.exec-script :as exec-script]
   [pallet.resource.remote-file :as remote-file]
   [pallet.resource.package :as package]
   [pallet.script :as script]
   [pallet.stevedore :as stevedore]
   [pallet.target :as target]
   [pallet.template :as template]
   [pallet.utils :as utils])
  (:use clojure.test
        pallet.test-utils))

(use-fixtures :once with-ubuntu-script-template)

(defn pkg-config [request]
  (-> request
      (package/package-manager :universe)
      (package/package-manager :multiverse)
      (package/package-manager :update)))

(def noninteractive
  (script/with-template [:ubuntu]
    (stevedore/script (package-manager-non-interactive))))

(defn debconf [request pkg]
  (package/package-manager
   request
   :debconf
   (str pkg " shared/present-sun-dlj-v1-1 note")
   (str pkg " shared/accepted-sun-dlj-v1-1 boolean true")))

(deftest java-default-test
  (is (= (first
          (build-resources
           []
           (package/package-source
            "Partner"
            :aptitude {:url ubuntu-partner-url
                       :scopes ["partner"]})
           (pkg-config)
           (package/package-manager :update)
           (debconf "sun-java6-bin")
           (package/package "sun-java6-bin")
           (debconf "sun-java6-jdk")
           (package/package "sun-java6-jdk")))
         (first
          (build-resources
           []
           (java))))))

(deftest java-sun-test
  (is (= (first
          (build-resources
           []
           (package/package-source
            "Partner"
            :aptitude {:url ubuntu-partner-url
                       :scopes ["partner"]})
           (pkg-config)
           (package/package-manager :update)
           (debconf "sun-java6-bin")
           (package/package "sun-java6-bin")
           (debconf "sun-java6-jdk")
           (package/package "sun-java6-jdk")))
         (first
          (build-resources
           []
           (java :sun :bin :jdk))))))

(deftest java-openjdk-test
  (is (= (first
          (build-resources
           []
           (package/package-manager :update)
           (package/package "openjdk-6-jre")))
         (first
          (build-resources
           []
           (java :openjdk :jre)))))
  (is (= (first
          (build-resources
           [:node-type {:image {:packager :pacman}}]
           (package/package-manager :update)
           (package/package "openjdk6")))
         (first
          (build-resources
           [:node-type {:image {:packager :pacman}}]
           (java :openjdk :jre))))))


(deftest invoke-test
  (is
   (build-resources
    []
    (java :openjdk :jdk)
    (jce-policy-file "f" :content ""))))

(deftest live-test
  (doseq [image [{:os-family :ubuntu :os-version-matches "10.04"}
                 {:os-family :ubuntu :os-version-matches "10.10"}
                 {:os-family :debian :os-version-matches "5.0.7"}]]
    (live-test/test-nodes
     [compute node-map node-types]
     {:java
      {:image image
       :count 1
       :phases {:bootstrap (resource/phase
                            (automated-admin-user/automated-admin-user))
                :configure (resource/phase (java :sun))
                :verify (resource/phase
                         (exec-script/exec-checked-script
                          "check java installed"
                          (java -version)))}}}
     (core/lift (:java node-types) :phase :verify :compute compute))))

;; To run this test you will need to download the Oracle Java rpm downloads in
;; the artifacts directory.
(deftest centos-live-test
  (doseq [image [{:os-family :centos :os-version-matches "5.5"}]]
    (live-test/test-nodes
     [compute node-map node-types]
     {:java
      {:image image
       :count 1
       :phases
       {:bootstrap (resource/phase
                    (automated-admin-user/automated-admin-user))
        :configure (resource/phase
                    (remote-file/remote-file
                     "jdk.bin"
                     :local-file "artifacts/jdk-6u23-linux-x64-rpm.bin"
                     :mode "755")
                    (java :sun :rpm-bin "./jdk.bin"))
        :verify (resource/phase
                 (exec-script/exec-checked-script
                  "check java installed"
                  (java -version)))}}}
     (core/lift (:java node-types) :phase :verify :compute compute))))
