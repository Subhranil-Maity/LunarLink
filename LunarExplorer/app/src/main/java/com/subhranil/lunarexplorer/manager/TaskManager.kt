package com.subhranil.lunarexplorer.manager

interface TaskManager {
    fun getNamer(): String

    fun getCurrentTaskName(): String
    fun start()
    fun cancel()
    fun pause()
    fun resume()
    fun getProgress(): Int
}