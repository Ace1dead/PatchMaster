package com.patchmaster

import android.app.Application
import com.patchmaster.engine.ToolManager
import com.patchmaster.agent.AresAgent

class PatchMasterApp : Application() {
    lateinit var toolManager: ToolManager
    lateinit var aresAgent: AresAgent

    override fun onCreate() {
        super.onCreate()
        instance = this
        toolManager = ToolManager(this)
        aresAgent = AresAgent(this, toolManager)
        toolManager.discoverTools()
    }

    companion object {
        lateinit var instance: PatchMasterApp
    }
}
