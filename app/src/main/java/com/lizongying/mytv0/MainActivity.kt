package com.lizongying.mytv0

import MainViewModel
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.widget.ListView
import android.widget.BaseAdapter
import android.view.ViewGroup
import android.widget.TextView
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.lizongying.mytv0.databinding.SettingsWebBinding
import java.util.Locale
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private var ok = 0
    private var playerFragment = PlayerFragment()
    private var webFragment = WebFragment()
    private val errorFragment = ErrorFragment()
    private val loadingFragment = LoadingFragment()
    private var infoFragment = InfoFragment()
    private var channelFragment = ChannelFragment()
    private var timeFragment = TimeFragment()
    private var menuFragment = MenuFragment()
    private var settingFragment = SettingFragment()
    private var programFragment = ProgramFragment()

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideMenu = 10 * 1000L
    private val delayHideSetting = 3 * 60 * 1000L

    private var doubleBackToExitPressedOnce = false

    private lateinit var gestureDetector: GestureDetector

    private var server: SimpleServer? = null

    private lateinit var viewModel: MainViewModel

    private var isSafeToPerformFragmentTransactions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        requestWindowFeature(FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            windowInsetsController.let { controller ->
                controller.isAppearanceLightNavigationBars = true
                controller.isAppearanceLightStatusBars = true
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.setAttributes(lp)
        }

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            suppressAutoPlay = true
            pendingSettings = true
        }

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, playerFragment)
                .add(R.id.main_browse_fragment, webFragment)
                .add(R.id.main_browse_fragment, infoFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .commitNowAllowingStateLoss()
        }
    }

    fun updateMenuSize() {
        menuFragment.updateSize()
    }

    fun ready(tag: String) {
        Log.i(TAG, "ready $tag")
        ok++
        if (ok == 2) {
            Log.i(TAG, "all ready")

            gestureDetector = GestureDetector(this, GestureListener(this))

            viewModel.groupModel.change.observe(this) { _ ->
                Log.i(TAG, "group changed")
                if (viewModel.groupModel.tvGroup.value != null) {
                    watch()
                    menuFragment.update()
                }
            }

//            if (SP.defaultLike) {
//                TVList.groupModel.setPosition(0)
//                val tvModel = TVList.listModel.find { it.like.value as Boolean }
//                TVList.setPosition(tvModel?.tv?.id ?: 0)
//                "播放收藏频道".showToast()
//            }

            viewModel.channelsOk.observe(this) { it ->
                if (it) {
                    val prevGroup = viewModel.groupModel.positionValue
                    val tvModel = if (SP.channel > 0) {
                        val position = if (SP.channel < viewModel.listModel.size) {
                            // R.string.play_default_channel.showToast()
                            SP.channel - 1
                        } else {
                            // R.string.default_channel_out_of_range.showToast()
                            SP.channel = 0
                            0
                        }
                        Log.i(TAG, "播放默認頻道")
                        viewModel.groupModel.getPosition(position)
                    } else {
//                if (SP.position < 0 || SP.position >= TVList.groupModel.getAllList()!!
//                        .size()
//                ) {
//                    // R.string.last_channel_out_of_range.showToast()
//                    0
//                } else {
//                    // R.string.play_last_channel.showToast()
//                    SP.position
//                }
                        Log.i(TAG, "播放上次頻道")
                        viewModel.groupModel.getCurrent()
                    }
                    viewModel.groupModel.setPositionPlaying()
                    viewModel.groupModel.getCurrentList()
                        ?.let {
                            Log.i(TAG, "當前組 ${it.getName()}")
                            it.setPositionPlaying()
                        }
                    if (!suppressAutoPlay) {
                        tvModel?.setReady()
                    }

                    val currentGroup = viewModel.groupModel.positionValue
                    if (currentGroup != prevGroup) {
                        Log.i(TAG, "group change")
                        menuFragment.updateList(currentGroup)
                    }

                    viewModel.groupModel.isInLikeMode =
                        SP.defaultLike && viewModel.groupModel.positionValue == 0
                    if (viewModel.groupModel.isInLikeMode) {
//                R.string.favorite_mode.showToast()
                    } else {
//                R.string.standard_mode.showToast()
                    }

                    // TODO group position
                    viewModel.updateEPG()
                }
            }

            Utils.isp.observe(this) {
                val id = when (it) {
//                    ISP.CHINA_MOBILE -> R.raw.mobile
//                    ISP.IPV6->R.raw.ipv6
                    else -> 0
                }

                if (id == 0) {
                    return@observe
                }

                resources.openRawResource(id).bufferedReader()
                    .use { i ->
                        val channels = i.readText()
                        if (channels.isNotEmpty()) {
                            viewModel.tryStr2Channels(channels, null, "")
                        } else {
                            Log.w(TAG, "$it is empty")
                        }
                    }
            }

            server = SimpleServer(this, viewModel)

            viewModel.updateConfig()
        }
    }

    private fun watch() {
        viewModel.listModel.forEach { tvModel ->
            tvModel.errInfo.observe(this) { _ ->

                if (tvModel.errInfo.value != null
//                    && tvModel.tv.id == TVList.positionValue
                ) {
                    hideFragment(loadingFragment)
                    if (tvModel.errInfo.value == "") {
                        Log.i(TAG, "${tvModel.tv.title} playing")
                        hideFragment(errorFragment)
                        if (tvModel.isWebChannel()) {
                            hideFragment(playerFragment)
                        } else {
                            showFragment(playerFragment)
                        }
                    } else {
                        Log.i(TAG, "${tvModel.tv.title} ${tvModel.errInfo.value.toString()}")
                        hideFragment(playerFragment)
                        if (tvModel.isWebChannel()) {
                            // 网页频道的错误不盖错误页、也不关 WebView：
                            // 让网页本身保持可见（拦截页/播放器界面都能直接看到），
                            // 且迟到的旧频道错误不会误杀正在播的网页。
                            hideFragment(errorFragment)
                        } else {
                            webFragment.stopPlayback()
                            errorFragment.setMsg(tvModel.errInfo.value.toString())
                            showFragment(errorFragment)
                        }
                    }
                }
            }

            tvModel.ready.observe(this) { _ ->

                // not first time && channel is not changed
                if (tvModel.ready.value != null
//                    && tvModel.tv.id == TVList.positionValue
                ) {
                    Log.i(TAG, "${tvModel.tv.title} 嘗試播放")
                    hideFragment(errorFragment)
                    showFragment(loadingFragment)
                    if (tvModel.isWebChannel()) {
                        playerFragment.stopPlayback()
                        hideFragment(playerFragment)
                        webFragment.play(tvModel)
                    } else {
                        webFragment.stopPlayback()
                        playerFragment.play(tvModel)
                    }
                    infoFragment.show(tvModel)
                    if (SP.channelNum) {
                        channelFragment.show(tvModel)
                    }
                }
            }

            tvModel.like.observe(this) { _ ->
                if (tvModel.like.value != null && tvModel.tv.id != -1) {
                    val liked = tvModel.like.value as Boolean
                    if (liked) {
                        viewModel.groupModel.getFavoritesList()?.replaceTVModel(tvModel)
                    } else {
                        viewModel.groupModel.getFavoritesList()
                            ?.removeTVModel(tvModel.tv.id)
                    }
                    SP.setLike(tvModel.tv.id, liked)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private inner class GestureListener(context: Context) :
        GestureDetector.SimpleOnGestureListener() {

        private var screenWidth = windowManager.defaultDisplay.width
        private var screenHeight = windowManager.defaultDisplay.height
        private val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager

        private var maxVolume = 0

        init {
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }

        override fun onDown(e: MotionEvent): Boolean {
            playerFragment.hideVolumeNow()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            showFragment(menuFragment)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showSetting()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            showProgram()
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val oldX = e1?.rawX ?: 0f
            val oldY = e1?.rawY ?: 0f
            val newX = e2.rawX
            val newY = e2.rawY
            if (oldX > screenWidth / 3 && oldX < screenWidth * 2 / 3 && abs(newX - oldX) < abs(newY - oldY)) {
                if (velocityY > 0) {
                    if ((!menuFragment.isAdded || menuFragment.isHidden) && (!settingFragment.isAdded || settingFragment.isHidden)) {
                        prev()
                    }
                }
                if (velocityY < 0) {
                    if ((!menuFragment.isAdded || menuFragment.isHidden) && (!settingFragment.isAdded || settingFragment.isHidden)) {
                        next()
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        private var lastScrollTime: Long = 0
        private var decayFactor: Float = 1.0f

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val oldX = e1?.rawX ?: 0f
            val oldY = e1?.rawY ?: 0f
            val newX = e2.rawX
            val newY = e2.rawY

            if (oldX < screenWidth / 3) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastScrollTime
                lastScrollTime = currentTime

                decayFactor =
                    0.01f.coerceAtLeast(decayFactor - 0.03f * deltaTime)
                val delta =
                    ((oldY - newY) * decayFactor * 0.2 / screenHeight).toFloat()
                adjustBrightness(delta)
                decayFactor = 1.0f
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            if (oldX > screenWidth * 2 / 3 && abs(distanceY) > abs(distanceX)) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastScrollTime
                lastScrollTime = currentTime

                decayFactor =
                    0.01f.coerceAtLeast(decayFactor - 0.03f * deltaTime)
                val delta =
                    ((oldY - newY) * maxVolume * decayFactor * 0.2 / screenHeight).toInt()
                adjustVolume(delta)
                decayFactor = 1.0f
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            return super.onScroll(e1, e2, distanceX, distanceY)
        }

        private fun adjustVolume(deltaVolume: Int) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            var newVolume = currentVolume + deltaVolume

            if (newVolume < 0) {
                newVolume = 0
            } else if (newVolume > maxVolume) {
                newVolume = maxVolume
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

            playerFragment.setVolumeMax(maxVolume * 100)
            playerFragment.setVolume(newVolume.toInt() * 100, true)
            playerFragment.showVolume(View.VISIBLE)
        }

        private fun adjustBrightness(deltaBrightness: Float) {
            var brightness = window.attributes.screenBrightness

            brightness += deltaBrightness
            brightness = 0.1f.coerceAtLeast(0.9f.coerceAtMost(brightness))

            val attributes = window.attributes.apply {
                screenBrightness = brightness
            }
            window.attributes = attributes

            playerFragment.setVolumeMax(100)
            playerFragment.setVolume((brightness * 100).toInt())
            playerFragment.showVolume(View.VISIBLE)
        }
    }

    fun onPlayEnd() {
        val tvModel = viewModel.groupModel.getCurrent()!!
        if (SP.repeatInfo) {
            infoFragment.show(tvModel)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }
    }

    fun play(position: Int): Boolean {
        return if (position > -1 && position < viewModel.groupModel.getAllList()!!.size()) {
            val prevGroup = viewModel.groupModel.positionValue
            val tvModel = viewModel.groupModel.getPosition(position)

            tvModel?.setReady()
            viewModel.groupModel.setPositionPlaying()
            viewModel.groupModel.getCurrentList()?.setPositionPlaying()

            val currentGroup = viewModel.groupModel.positionValue
            if (currentGroup != prevGroup) {
                menuFragment.updateList(currentGroup)
            }
            true
        } else {
            R.string.channel_not_exist.showToast()
            false
        }
    }

    fun prev() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel =
            if (SP.defaultLike && viewModel.groupModel.isInLikeMode && viewModel.groupModel.getFavoritesList() != null
            ) {
                viewModel.groupModel.getPrev(true)
            } else {
                viewModel.groupModel.getPrev()
            }

        tvModel?.setReady()
        viewModel.groupModel.setPositionPlaying()
        viewModel.groupModel.getCurrentList()?.setPositionPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    fun next() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel =
            if (SP.defaultLike && viewModel.groupModel.isInLikeMode && viewModel.groupModel.getFavoritesList() != null
            ) {
                viewModel.groupModel.getNext(true)
            } else {
                viewModel.groupModel.getNext()
            }

        tvModel?.setReady()
        viewModel.groupModel.setPositionPlaying()
        viewModel.groupModel.getCurrentList()?.setPositionPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!isSafeToPerformFragmentTransactions) {
            return
        }

        if (fragment === menuFragment || fragment === settingFragment || fragment === programFragment) {
            webFragment.softHide()
        }

        if (!fragment.isAdded) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, fragment)
                .commitAllowingStateLoss()
            return
        }

        if (!fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitAllowingStateLoss()
    }

    private fun hideFragment(fragment: Fragment) {
        if (!isSafeToPerformFragmentTransactions) {
            return
        }

        if (!fragment.isAdded || fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitAllowingStateLoss()

        if (fragment === menuFragment || fragment === settingFragment || fragment === programFragment) {
            webFragment.softShow()
        }

        if (fragment === settingFragment && suppressAutoPlay) {
            suppressAutoPlay = false
            // 「重启进设置」的会话：关设置后自动续播刚才的频道，
            // 不用再打开菜单手动选台。
            viewModel.groupModel.getCurrent()?.setReady()
        }
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!menuFragment.isHidden) {
                supportFragmentManager.beginTransaction()
                    .hide(menuFragment)
                    .commitAllowingStateLoss()
                webFragment.softShow()
            }
        }
    }

    fun switchSoftDecode() {
        if (!playerFragment.isAdded || playerFragment.isHidden) {
            return
        }

        playerFragment.updatePlayer()
    }

    fun settingActive() {
        handler.removeCallbacks(hideSetting)
        handler.postDelayed(hideSetting, delayHideSetting)
    }

    private val hideSetting = Runnable {
        hideFragment(settingFragment)
        showTimeFragment()
    }

    fun showTimeFragment() {
        if (SP.time) {
            showFragment(timeFragment)
        } else {
            hideFragment(timeFragment)
        }
    }

    private fun showChannel(channel: Int) {
        if (!menuFragment.isHidden) {
            return
        }

        if (settingFragment.isVisible) {
            return
        }

//        if (SP.channelNum) {
//            channelFragment.show(channel)
//        }
        channelFragment.show(channel)
    }


    private fun channelUp() {
        if (programFragment.isAdded && !programFragment.isHidden) {
            return
        }

        if ((!menuFragment.isAdded || menuFragment.isHidden) && (!settingFragment.isAdded || settingFragment.isHidden)) {
            if (SP.channelReversal) {
                next()
                return
            }
            prev()
        }
    }

    private fun channelDown() {
        if (programFragment.isAdded && !programFragment.isHidden) {
            return
        }

        if ((!menuFragment.isAdded || menuFragment.isHidden) && (!settingFragment.isAdded || settingFragment.isHidden)) {
            if (SP.channelReversal) {
                prev()
                return
            }
            next()
        }
    }

    private fun back() {
        if (menuFragment.isAdded && !menuFragment.isHidden) {
            hideFragment(menuFragment)
            return
        }

        if (programFragment.isAdded && !programFragment.isHidden) {
            hideFragment(programFragment)
            return
        }

        if (settingFragment.isAdded && !settingFragment.isHidden) {
            hideFragment(settingFragment)
            showTimeFragment()
            return
        }

        if (channelFragment.isAdded && channelFragment.isVisible) {
            channelFragment.hideSelf()
            return
        }

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }

        doubleBackToExitPressedOnce = true
        R.string.press_again_to_exit.showToast()

        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }

    private fun showSetting() {
        if (programFragment.isAdded && !programFragment.isHidden) {
            return
        }

        if (menuFragment.isAdded && !menuFragment.isHidden) {
            return
        }

        showFragment(settingFragment)

        settingActive()
    }

    private fun showProgram() {
        if (menuFragment.isAdded && !menuFragment.isHidden) {
            return
        }

        if (settingFragment.isAdded && !settingFragment.isHidden) {
            return
        }

        viewModel.groupModel.getCurrent()?.let {
            if (it.epgValue.isEmpty()) {
                R.string.epg_is_empty.showToast()
                return
            }
        }

        showFragment(programFragment)
    }

    private fun hideProgram(): Boolean {
        if (!programFragment.isAdded || programFragment.isHidden) {
            return false
        }

        hideFragment(programFragment)
        return true
    }

    fun showWebViewPopup(url: String) {
        val binding = SettingsWebBinding.inflate(layoutInflater)

        val webView = binding.web
        webView.settings.javaScriptEnabled = true
        webView.isFocusableInTouchMode = true
        webView.isFocusable = true
        webView.loadUrl(url)

        val popupWindow = PopupWindow(
            binding.root,
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        popupWindow.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        popupWindow.isFocusable = true
        popupWindow.isTouchable = true

        popupWindow.isClippingEnabled = false

        popupWindow.showAtLocation(window.decorView, Gravity.CENTER, 0, 0)

        webView.requestFocus()

        binding.close.setOnClickListener {
            popupWindow.dismiss()
        }
    }

    fun onKey(keyCode: Int): Boolean {
        Log.d(TAG, "keyCode $keyCode")
        if (isWebMenuOpen()) {
            return onWebMenuKey(keyCode)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9,
                -> {
                showChannel(keyCode - 7)
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BOOKMARK -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_UNKNOWN -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_HELP -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_SETTINGS -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                if (webFragment.isWebActive()) {
                    showWebMenu()
                    return true
                }
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_ENTER -> {
                if (channelFragment.isAdded && channelFragment.isVisible) {
                    channelFragment.playNow()
                    return true
                }

                if (webFragment.isWebActive()) {
                    showWebMenu()
                    return true
                }

                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (channelFragment.isAdded && channelFragment.isVisible) {
                    channelFragment.playNow()
                    return true
                }

                if (webFragment.isWebActive()) {
                    showWebMenu()
                    return true
                }

                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                channelUp()
            }

            KeyEvent.KEYCODE_CHANNEL_UP -> {
                channelUp()
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                channelDown()
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                channelDown()
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (webFragment.isWebActive()) {
                    showWebMenu()
                    return true
                }
                showProgram()
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (webFragment.isWebActive()) {
                    showWebMenu()
                    return true
                }
                showSetting()
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onKey(keyCode)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    // 网页频道全屏播放时，视频视图会先一步吃掉确认/左右等按键（拿去开关它
    // 自己的控制条），按键根本到不了 onKeyDown。这里在分发最前端拦截：
    // 网页播放中且没有菜单/设置/节目单打开时，先把遥控按键交给 onKey。
    // ===== 网页频道专用悬浮菜单 =====
    // 微鲸电视的硬件视频层盖住本窗口的一切界面（菜单/设置碎片都看不见），
    // 但独立窗口（Dialog）能画在视频层之上（v26 探针实证）。
    // 故网页频道播放时，OK/菜单键改为弹出这个非焦点对话框当频道菜单：
    // 窗口不抢焦点，按键仍走 Activity 的 onKey，由这里维护选中项。
    private data class WebMenuRow(
        val kind: Int,
        val groupIdx: Int,
        val posInGroup: Int,
        val title: String
    )

    private var webMenuDialog: Dialog? = null
    private var webMenuList: ListView? = null
    private var webMenuRows: List<WebMenuRow> = emptyList()
    private var webMenuSel: Int = -1
    private val webMenuAutoClose = Runnable { closeWebMenu() }

    // 从网页菜单点「打开设置」时重启进来：压住开机自动播放，并自动打开设置。
    private var suppressAutoPlay = false
    private var pendingSettings = false

    private fun isWebMenuOpen(): Boolean = webMenuDialog?.isShowing == true

    private fun buildWebMenuRows(): List<WebMenuRow> {
        val rows = mutableListOf<WebMenuRow>()
        val gm = viewModel.groupModel
        val playingGroup = gm.positionPlayingValue
        for ((gIdx, listModel) in gm.tvGroupValue.withIndex()) {
            if (gIdx == 1) continue // 「全部」组与其它组重复，不列
            val channels = listModel.tvList.value ?: emptyList()
            if (channels.isEmpty()) continue
            rows.add(WebMenuRow(0, gIdx, -1, listModel.getName()))
            val playingPos = if (gIdx == playingGroup) listModel.positionPlayingValue else -1
            for ((pos, tvModel) in channels.withIndex()) {
                val prefix = if (pos == playingPos) "▶ " else ""
                rows.add(WebMenuRow(1, gIdx, pos, prefix + tvModel.tv.title))
            }
        }
        rows.add(WebMenuRow(2, -1, -1, "打开设置（将停止播放）"))
        return rows
    }

    private fun showWebMenu() {
        if (isWebMenuOpen()) {
            closeWebMenu()
            return
        }
        val rows = buildWebMenuRows()
        if (rows.none { it.kind == 1 }) {
            R.string.channel_not_exist.showToast()
            return
        }
        webMenuRows = rows
        val gm = viewModel.groupModel
        var sel = rows.indexOfFirst { it.kind == 1 && it.groupIdx == gm.positionPlayingValue }
        if (sel == -1) sel = rows.indexOfFirst { it.kind == 1 }
        val playingPos = gm.getCurrentList()?.positionPlayingValue ?: -1
        val exact = rows.indexOfFirst {
            it.kind == 1 && it.groupIdx == gm.positionPlayingValue && it.posInGroup == playingPos
        }
        if (exact != -1) sel = exact
        webMenuSel = sel

        val listView = ListView(this)
        listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = webMenuRows.size
            override fun getItem(position: Int): Any = webMenuRows[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val tv = (convertView as? TextView) ?: TextView(this@MainActivity)
                val row = webMenuRows[position]
                tv.text = row.title
                tv.setPadding(36, if (row.kind == 0) 14 else 20, 24, if (row.kind == 0) 8 else 20)
                when (row.kind) {
                    0 -> {
                        tv.setTextColor(Color.rgb(170, 170, 170))
                        tv.textSize = 15f
                        tv.setBackgroundColor(Color.TRANSPARENT)
                    }
                    2 -> {
                        tv.setTextColor(Color.rgb(255, 213, 120))
                        tv.textSize = 18f
                        tv.setBackgroundColor(
                            if (position == webMenuSel) Color.rgb(90, 70, 0) else Color.TRANSPARENT
                        )
                    }
                    else -> {
                        tv.setTextColor(Color.WHITE)
                        tv.textSize = 19f
                        tv.setBackgroundColor(
                            if (position == webMenuSel) Color.rgb(21, 101, 192) else Color.TRANSPARENT
                        )
                    }
                }
                return tv
            }
        }
        listView.divider = null
        listView.setBackgroundColor(Color.argb(165, 48, 48, 54))

        val d = Dialog(this)
        d.setContentView(listView)
        d.setCancelable(false)
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        webMenuDialog = d
        webMenuList = listView
        d.show()
        val dm = resources.displayMetrics
        d.window?.setLayout((dm.widthPixels * 0.30).toInt(), (dm.heightPixels * 0.86).toInt())
        d.window?.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        listView.setSelection(webMenuSel)
        armWebMenuAutoClose()
    }

    private fun armWebMenuAutoClose() {
        handler.removeCallbacks(webMenuAutoClose)
        handler.postDelayed(webMenuAutoClose, 15 * 1000L)
    }

    private fun refreshWebMenu() {
        (webMenuList?.adapter as? BaseAdapter)?.notifyDataSetChanged()
        webMenuList?.setSelection(webMenuSel)
        armWebMenuAutoClose()
    }

    private fun moveWebMenu(delta: Int) {
        val selectable = webMenuRows.indices.filter { webMenuRows[it].kind != 0 }
        if (selectable.isEmpty()) return
        var cur = selectable.indexOf(webMenuSel)
        cur = if (cur == -1) 0 else (cur + delta + selectable.size) % selectable.size
        webMenuSel = selectable[cur]
        refreshWebMenu()
    }

    private fun activateWebMenuRow() {
        val row = webMenuRows.getOrNull(webMenuSel) ?: return
        when (row.kind) {
            1 -> {
                val gm = viewModel.groupModel
                gm.setPosition(row.groupIdx)
                gm.setPositionPlaying()
                gm.getCurrentList()?.let {
                    it.setPosition(row.posInGroup)
                    it.setPositionPlaying()
                    it.getCurrent()?.setReady()
                }
                closeWebMenu()
            }
            2 -> {
                // 冻帧赖在硬件视频层上不走，原地打开设置会被它盖住；
                // 重启 Activity 换新窗口（视频层随旧窗口销毁），
                // 并压住开机自动播放、自动打开设置。
                closeWebMenu()
                val restart = Intent(this, MainActivity::class.java)
                restart.putExtra(EXTRA_OPEN_SETTINGS, true)
                restart.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                startActivity(restart)
                finish()
            }
        }
    }

    private fun closeWebMenu() {
        handler.removeCallbacks(webMenuAutoClose)
        webMenuDialog?.dismiss()
        webMenuDialog = null
        webMenuList = null
    }

    private fun onWebMenuKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveWebMenu(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveWebMenu(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activateWebMenuRow()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_MENU -> closeWebMenu()
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            webFragment.isWebActive() &&
            (!menuFragment.isAdded || menuFragment.isHidden) &&
            (!settingFragment.isAdded || settingFragment.isHidden) &&
            (!programFragment.isAdded || programFragment.isHidden)
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_HELP,
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    onKey(event.keyCode)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()

        isSafeToPerformFragmentTransactions = true

        showTimeFragment()

        if (pendingSettings) {
            pendingSettings = false
            showSetting()
        }
    }

    override fun onPause() {
        super.onPause()

        isSafeToPerformFragmentTransactions = false
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    override fun attachBaseContext(base: Context) {
        try {
            val locale = Locale.TRADITIONAL_CHINESE
            val config = Configuration()
            config.setLocale(locale)
            super.attachBaseContext(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    base.createConfigurationContext(config)
                } else {
                    val resources = base.resources
                    resources.updateConfiguration(config, resources.displayMetrics)
                    base
                }
            )
        } catch (_: Exception) {
            super.attachBaseContext(base)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}