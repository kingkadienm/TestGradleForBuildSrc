package com.wangzs.buildsrc.util

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BasePlugin
import com.wangzs.buildsrc.ext.LibraryExt
import com.wangzs.buildsrc.ext.ModulesExt
import groovy.util.slurpersupport.GPathResult
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.gradle.api.Project

abstract class ManifestStrategy {

    protected String path
    protected String outputGroupPath
    protected String outputPath
    protected GPathResult manifest
    private Project project

    ManifestStrategy(Project project) {
        this.project = project
        path = "${project.getBuildFile().getParent()}/src/main/AndroidManifest.xml"
        outputGroupPath = "${project.getBuildFile().getParent()}/component"
        outputPath = "${project.getBuildFile().getParent()}/component/AndroidManifest.xml"
        File manifestFile = new File(path)
        if (!manifestFile.getParentFile().exists() && !manifestFile.getParentFile().mkdirs()) {
            println "Unable to find AndroidManifest and create fail, please manually create"
        }
        manifest = new XmlSlurper(false, false).parse(manifestFile)
    }

    abstract void setApplication(def application, ModulesExt modulesExt)

    abstract void setMainIntentFilter(def activity, boolean isFindMain)

    void resetManifest(ModulesExt moduleExt, BasePlugin appPlugin, boolean isDebug) {
        setApplication(manifest.application, moduleExt)
        if (manifest.@package != moduleExt.applicationId && moduleExt.applicationId != null && !moduleExt.applicationId.isEmpty()) {
            manifest.@package = moduleExt.applicationId
        }

        boolean isFindMain = false
        if (moduleExt.mainActivity != null && !moduleExt.mainActivity.isEmpty()) {
            manifest.application.activity.each { activity ->
                if (activity.@'android:name' == moduleExt.mainActivity) {
                    def filter = activity.'intent-filter'.find {
                        it.action.@'android:name' == "android.intent.action.MAIN"
                    }
                    isFindMain = true
                    setMainIntentFilter(activity, filter != null && filter.size() > 0)
                }
            }
        }

        manifest.application.activity.each { activity ->
            def filter = activity.'intent-filter'.find {
                it.action.@'android:name' == "android.intent.action.MAIN"
            }
            if (filter != null
                    && moduleExt.mainActivity != null
                    && !moduleExt.mainActivity.isEmpty()
                    && activity.@'android:name' != moduleExt.mainActivity) {
                filter.replaceNode {}
            }
        }

        if (!isFindMain) {
            addMainActivity(manifest.application, moduleExt)
        }

        buildModulesManifest(manifest, moduleExt, appPlugin, isDebug)
    }

    void addMainActivity(def application, ModulesExt modulesExt) {
        if (modulesExt.mainActivity != null && !modulesExt.mainActivity.isEmpty()) {
            application.appendNode {
                activity('android:name': modulesExt.mainActivity) {
                    'intent-filter' {
                        action('android:name': "android.intent.action.MAIN")
                        category('android:name': "android.intent.category.LAUNCHER")
                    }
                }
            }
        }

    }

    void buildModulesManifest(def manifestFile, ModulesExt moduleExt, BasePlugin appPlugin, boolean isDebug) {
        println ":${moduleExt.name}cleanBuildModulesManifest"
        def outputGroupFile = new File(outputGroupPath)
        if (outputGroupFile.exists()) {
            outputGroupFile.deleteDir()
        }

        if ((moduleExt instanceof LibraryExt) && !((moduleExt as LibraryExt).isRunAlone && isDebug)) {
            return
        }

        println ":${moduleExt.name}buildModulesManifest"

        outputGroupFile = new File(outputGroupPath)

        if (!outputGroupFile.exists()) {
            outputGroupFile.mkdirs()
        }
        def outputFile = new File(outputPath)

        StreamingMarkupBuilder outputBuilder = new StreamingMarkupBuilder()
        String root = outputBuilder.bind {
            mkp.xmlDeclaration()
            mkp.yield manifestFile
        }
        String result = XmlUtil.serialize(root)
        outputFile.text = result
        if (appPlugin instanceof AppPlugin) {
            appPlugin.extension.sourceSets {
                main {
                    manifest.srcFile(outputPath)
                }
            }
        }

    }
}

class AppManifestStrategy extends ManifestStrategy {

    AppManifestStrategy(Project project) {
        super(project)
    }

    @Override
    void setApplication(def application, ModulesExt modulesExt) {
        if (modulesExt.applicationName == null || modulesExt.applicationName.isEmpty()) {
            application.each {
                it.attributes().remove("android:name")
            }
            return
        }
        if (application.@'android:name' == null ||
                application.@'android:name' != modulesExt.applicationName) {
            application.@'android:name' = modulesExt.applicationName
        }
    }

    @Override
    void setMainIntentFilter(def activity, boolean isFindMain) {
        if (!isFindMain) {
            activity.appendNode {
                'intent-filter' {
                    action('android:name': "android.intent.action.MAIN")
                    category('android:name': "android.intent.category.LAUNCHER")
                }
            }
        }
    }
}

class LibraryManifestStrategy extends ManifestStrategy {

    LibraryManifestStrategy(Project project) {
        super(project)
    }

    @Override
    void setApplication(def application, ModulesExt modulesExt) {
        if (!modulesExt.isRunAlone || modulesExt.applicationName == null || modulesExt.applicationName.isEmpty()) {
            application.each {
                it.attributes().remove("android:name")
            }
        }
    }

    @Override
    void setMainIntentFilter(def activity, boolean isFindMain) {
        if (isFindMain) {
            println "build lib"
            activity.'intent-filter'.each {
                if (it.action.@'android:name' == "android.intent.action.MAIN") {
                    it.replaceNode {}
                }
            }
        }
    }
}
