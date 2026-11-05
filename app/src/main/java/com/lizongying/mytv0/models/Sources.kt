package com.lizongying.mytv0.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.lizongying.mytv0.SP
import com.lizongying.mytv0.data.Global.gson
import com.lizongying.mytv0.data.Global.typeSourceList
import com.lizongying.mytv0.data.Source

class Sources {
    var version = 0

    private val _removed = MutableLiveData<Pair<Int, Int>>()
    val removed: LiveData<Pair<Int, Int>>
        get() = _removed

    private val _added = MutableLiveData<Pair<Int, Int>>()
    val added: LiveData<Pair<Int, Int>>
        get() = _added

    private val _changed = MutableLiveData<Int>()
    val changed: LiveData<Int>
        get() = _changed

    private val _sources = MutableLiveData<List<Source>>()
    val sources: LiveData<List<Source>>
        get() = _sources
    private val sourcesValue: List<Source>
        get() = _sources.value ?: emptyList()

    private val _checked = MutableLiveData<Int>()
    val checked: LiveData<Int>
        get() = _checked
    val checkedValue: Int
        get() = _checked.value ?: DEFAULT_CHECKED

    fun setChecked(position: Int) {
        _checked.value = position
    }

    fun setSourceChecked(position: Int, checked: Boolean): Boolean {
        val checkedBefore = getSource(position)?.checked
        if (checkedBefore == checked) {
            return false
        } else {
            getSource(position)?.checked = checked
//            if (checked) {
//                Log.i(TAG, "setChecked $position")
//                setChecked(position)
//            }
//
//            SP.sources = gson.toJson(sources, type) ?: ""
            return true
        }
    }

    private fun setSources(sources: List<Source>) {
        _sources.value = sources
        SP.sources = gson.toJson(sources, typeSourceList) ?: ""
    }

    fun addSource(source: Source) {
        val index = sourcesValue.indexOfFirst { it.uri == source.uri }
        if (index == -1) {
            setSourceChecked(checkedValue, false)

            _sources.value = sourcesValue.toMutableList().apply {
                add(0, source)
            }

            _checked.value = 0
            setSourceChecked(checkedValue, true)
            SP.sources = gson.toJson(sourcesValue, typeSourceList) ?: ""

            _changed.value = version
            version++
        }
    }

    fun removeSource(id: String): Boolean {
        if (sourcesValue.isEmpty()) {
            Log.i(TAG, "sources is empty")
            return false
        }

        val index = sourcesValue.indexOfFirst { it.id == id }
        if (index != -1) {
            _sources.value = sourcesValue.toMutableList().apply {
                removeAt(index)
            }
            SP.sources = gson.toJson(sourcesValue, typeSourceList) ?: ""

            _removed.value = Pair(index, version)
            version++
            return true
        }

        Log.i(TAG, "sourceId is not exists")
        return false
    }

    fun getSource(idx: Int): Source? {
        if (idx < 0 || idx >= size()) {
            return null
        }

        return sourcesValue[idx]
    }

    fun init() {
        if (!SP.sources.isNullOrEmpty()) {
            try {
                val sources: List<Source> = gson.fromJson(SP.sources!!, typeSourceList)
                var loaded = sources.map { it.apply { checked = false } }
                // v36 一次性清理：内置预置源只保留无锡一条（用户要求）。
                // 仅执行一次（标记存 SP），之后用户手动添加的源不受影响。
                if (!SP.sourcesPurgedV36) {
                    SP.sourcesPurgedV36 = true
                    val wuxiUri =
                        "https://raw.githubusercontent.com/sandeli/my-tv-webview/master/examples/wuxi-web.txt"
                    if (loaded.any { it.uri == wuxiUri }) {
                        loaded = loaded.filter { it.uri == wuxiUri }
                    }
                }
                setSources(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
                SP.sources = SP.DEFAULT_SOURCES
            }
        }

        if (size() > 0) {
            _checked.value = sourcesValue.indexOfFirst { it.uri == SP.configUrl }

            if (checkedValue > -1) {
                setSourceChecked(checkedValue, true)
            }
        }

        _changed.value = version
        version++
    }

    init {
        init()
    }

    fun size(): Int {
        return sourcesValue.size
    }

    companion object {
        const val TAG = "Sources"
        const val DEFAULT_CHECKED = -1
    }
}