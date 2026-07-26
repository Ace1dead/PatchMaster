package com.patchmaster

import android.app.Application
import android.content.Context
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

        val prefs = getSharedPreferences("patchmaster", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "")
        if (!savedKey.isNullOrEmpty()) {
            aresAgent.setApiKey(savedKey)
        }
        val savedModel = prefs.getString("model", null)
        if (savedModel != null) {
            aresAgent.setModel(savedModel)
        }
        aresAgent.jailbreakEnabled = prefs.getBoolean("jailbreak", true)
    }

    companion object {
        lateinit var instance: PatchMasterApp
    }
}
