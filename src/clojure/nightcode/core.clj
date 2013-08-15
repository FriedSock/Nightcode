(ns nightcode.core
  (:require [seesaw.core :as s]
            [nightcode.shortcuts :as shortcuts]
            [nightcode.lein :as lein]
            [nightcode.projects :as p]
            [nightcode.utils :as utils])
  (:import [java.awt.event WindowAdapter]
           [javax.swing.event TreeExpansionListener TreeSelectionListener]
           [org.pushingpixels.substance.api SubstanceLookAndFeel]
           [org.pushingpixels.substance.api.skin GraphiteSkin])
  (:gen-class))

(defn start-repl
  [process console]
  (when console
    (s/request-focus! (.getView (.getViewport console)))
    (lein/run-repl process
                   (utils/get-console-input console)
                   (utils/get-console-output console))))

(defn get-project-pane
  "Returns the pane with the project tree."
  [process]
  (let [project-tree (s/tree :id :project-tree
                             :focusable? true)
        create-new-project (fn [e]
                             (->> (s/select @utils/ui-root [:#repl-console])
                                  (p/new-project process)
                                  (start-repl process)))
        btn-group (s/horizontal-panel
                    :items [(s/button :id :new-project-button
                                      :text (utils/get-string :new_project)
                                      :listen [:action create-new-project]
                                      :focusable? false)
                            (s/button :id :new-file-button
                                      :text (utils/get-string :new_file)
                                      :listen [:action p/new-file]
                                      :focusable? false)
                            (s/button :id :rename-file-button
                                      :text (utils/get-string :rename_file)
                                      :listen [:action p/rename-file]
                                      :focusable? false
                                      :visible? false)
                            (s/button :id :import-button
                                      :text (utils/get-string :import)
                                      :listen [:action p/import-project]
                                      :focusable? false)
                            (s/button :id :remove-button
                                      :text (utils/get-string :remove)
                                      :listen [:action p/remove-item]
                                      :focusable? false)
                            :fill-h])
        project-pane (s/vertical-panel
                       :id :project-pane
                       :items [btn-group
                               (s/scrollable project-tree)])]
    (doto project-tree
          (.setRootVisible false)
          (.setShowsRootHandles true)
          (.addTreeExpansionListener
            (reify TreeExpansionListener
              (treeCollapsed [this e] (p/remove-expansion e))
              (treeExpanded [this e] (p/add-expansion e))))
          (.addTreeSelectionListener
            (reify TreeSelectionListener
              (valueChanged [this e] (p/set-selection e)))))
    (shortcuts/create-mappings project-pane
                               {:new-project-button create-new-project
                                :new-file-button p/new-file
                                :rename-file-button p/rename-file
                                :import-button p/import-project
                                :remove-button p/remove-item})
    project-pane))

(defn get-repl-pane
  "Returns the pane with the REPL."
  [process]
  (let [console (s/config! (utils/create-console) :id :repl-console)]
    (start-repl process console)
    (shortcuts/create-mappings console
                               {:repl-console (fn [e]
                                                (start-repl process console))})
    console))

(defn get-editor-pane
  "Returns the pane with the editors."
  []
  (s/card-panel :id :editor-pane
                :items [["" :default-card]]))

(defn get-builder-pane
  "Returns the pane with the builders."
  []
  (s/card-panel :id :builder-pane
                :items [["" :default-card]]))

(defn get-window-content []
  "Returns the entire window with all panes."
  (let [repl-process (atom nil)]
    (s/left-right-split
      (s/top-bottom-split (get-project-pane repl-process)
                          (get-repl-pane repl-process)
                          :divider-location 0.8
                          :resize-weight 0.5)
      (s/top-bottom-split (get-editor-pane)
                          (get-builder-pane)
                          :divider-location 0.8
                          :resize-weight 0.5)
      :divider-location 0.4)))

(defn -main
  "Launches the main window."
  [& args]
  (s/native!)
  (SubstanceLookAndFeel/setSkin (GraphiteSkin.))
  (s/invoke-later
    ; create and show the frame
    (reset! utils/ui-root
      (doto (s/frame :title (str (utils/get-string :app_name)
                                 " "
                                 (utils/get-version))
                     :content (get-window-content)
                     :width 1024
                     :height 768
                     :on-close :exit)
        ; create the shortcut hints for the main buttons
        shortcuts/create-hints
        ; listen for keys while modifier is down
        (shortcuts/listen-for-shortcuts
          (fn [key-code]
            (case key-code
              ; enter
              10 (p/toggle-project-tree-selection)
              ; left
              37 (p/move-tab-selection -1)
              ; up
              38 (p/move-project-tree-selection -1)
              ; right
              39 (p/move-tab-selection 1)
              ; down
              40 (p/move-project-tree-selection 1)
              ; Q
              81 (System/exit 0)
              false)))
        ; update the project tree when window comes into focus
        (.addWindowListener (proxy [WindowAdapter] []
                              (windowActivated [e]
                                (p/update-project-tree))))
        ; show the frame
        s/show!))
    ; initialize the project pane
    (p/update-project-tree)))
