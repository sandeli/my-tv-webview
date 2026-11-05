package com.lizongying.mytv0

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.lizongying.mytv0.databinding.WebBinding
import com.lizongying.mytv0.models.TVModel

// 网页频道播放层：源地址带 web: 前缀的频道用 WebView 打开网页播放，
// 页面加载后注入脚本，把网页里的 video 拉满全屏并自动播放。
class WebFragment : Fragment() {
    private var _binding: WebBinding? = null
    private val binding get() = _binding!!

    private var tvModel: TVModel? = null
    private var active = false
    private var generation = 0
    private var currentUrl: String? = null

    private var suspended = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val handler = Handler(Looper.getMainLooper())
    private val failedUrls = mutableListOf<String>()
    private val consoleLogs = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = WebBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.visibility = View.GONE

        val webView = binding.webView
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = DESKTOP_UA
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!active || url != currentUrl) {
                    return
                }
                view?.evaluateJavascript(INJECT_JS, null)
                // 页面已打开就算播放就绪，清掉错误信息让主界面收起加载层
                handler.postDelayed({
                    if (active) {
                        tvModel?.setErrInfo("")
                    }
                }, 800)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // 只记日志不报播放错误：切台时旧页面的中断回调、子资源失败都很常见，
                // 报上去会把错误页盖到正在播的网页上；页面真打不开时 WebView 本身会显示出来。
                if (active && request?.isForMainFrame == true) {
                    Log.w(TAG, "web main frame error ${error?.errorCode} ${request.url}")
                }
                if (active && request?.isForMainFrame == false) {
                    val u = request.url?.toString() ?: ""
                    if (u.contains("thmz") || u.contains("m3u8") || u.contains(".ts")) {
                        synchronized(failedUrls) {
                            failedUrls.add(u.take(110))
                            if (failedUrls.size > 5) failedUrls.removeAt(0)
                        }
                    }
                }
                super.onReceivedError(view, request, error)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                if (cm != null) {
                    synchronized(consoleLogs) {
                        consoleLogs.add(cm.messageLevel().name + ": " + cm.message().take(90))
                        if (consoleLogs.size > 5) consoleLogs.removeAt(0)
                    }
                }
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) {
                    return
                }
                if (customView != null) {
                    onHideCustomView()
                }
                customView = view
                customViewCallback = callback
                view.setOnKeyListener { _, keyCode, event -> forwardKey(keyCode, event) }
                binding.fullscreenContainer.addView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                if (customView == null) {
                    return
                }
                binding.fullscreenContainer.removeView(customView)
                binding.fullscreenContainer.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
        // 遥控器按键转发：网页频道下 WebView 拿到焦点会吞掉确认/方向键，
        // 菜单调不出来，这里把按键先交回 MainActivity 统一处理（见 forwardKey）。
        webView.setOnKeyListener { _, keyCode, event -> forwardKey(keyCode, event) }
    }

    // 把遥控器按键交回 MainActivity.onKey 统一处理：它负责的按键就地消费，
    // 不负责的（如音量）返回 false 还给 WebView/系统。
    private fun forwardKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BOOKMARK, KeyEvent.KEYCODE_HELP,
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                (activity as? MainActivity)?.onKey(keyCode)
                true
            }

            else -> false
        }
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val url = tvModel.getWebUrl()
        if (url.isNullOrEmpty()) {
            tvModel.setErrInfo(R.string.play_error.getString())
            return
        }
        active = true
        suspended = false
        generation++
        currentUrl = url
        binding.root.visibility = View.VISIBLE
        Log.i(TAG, "play web channel ${tvModel.tv.title} $url")
        tvModel.setErrInfo("")
        synchronized(failedUrls) { failedUrls.clear() }
        synchronized(consoleLogs) { consoleLogs.clear() }
        binding.webView.loadUrl(url)
    }

    fun stopPlayback() {
        if (!active && _binding == null) {
            return
        }
        active = false
        suspended = false
        currentUrl = null
        if (_binding != null) {
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            binding.root.visibility = View.GONE
        }
    }

    // 菜单/设置等界面打开时先把网页层藏起来：电视上网页视频层会盖住后开的界面，
    // 菜单关掉后主界面再调 softShow 恢复（页面不重载，直播直接续上）。
    fun isWebActive(): Boolean = active

    // 菜单/设置打开时：这台电视的硬件视频层压得住一切视图级隐藏
    // （视图 GONE、脚本暂停+display:none 都试过，画面仍盖住菜单），
    // 唯一可靠的让开办法是把页面导航走——文档销毁，视频层必然释放；
    // 菜单关掉时重新加载当前频道（直播流，几秒回到画面）。
    fun softHide() {
        if (active && _binding != null && !suspended) {
            suspended = true
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            binding.root.visibility = View.GONE
        }
    }

    fun softShow() {
        if (active && _binding != null) {
            binding.root.visibility = View.VISIBLE
            if (suspended) {
                suspended = false
                val url = currentUrl
                if (!url.isNullOrEmpty()) {
                    binding.webView.loadUrl(url)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (_binding != null) {
            binding.webView.destroy()
        }
        _binding = null
    }

    companion object {
        private const val TAG = "WebFragment"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        // 找 video、拉满全屏、自动播放；带声自动播放被拒时退到静音播放保画面。
        // 播放器晚加载的情况用 MutationObserver + 定时重扫兜底。
        private const val INJECT_JS = """
(function(){
  function stretch(v){
    try{
      v.style.setProperty('position','fixed','important');
      v.style.setProperty('left','0','important');
      v.style.setProperty('top','0','important');
      v.style.setProperty('width','100%','important');
      v.style.setProperty('height','100%','important');
      v.style.setProperty('z-index','2147483647','important');
      v.style.setProperty('object-fit','contain','important');
      v.style.setProperty('background','#000','important');
    }catch(e){}
  }
  function fit(v){
    stretch(v);
    try{
      if(!document.getElementById('__webtv_style')){
        var st=document.createElement('style');
        st.id='__webtv_style';
        st.innerHTML='html,body{width:100%!important;height:100%!important;background:#000!important;overflow:hidden!important}video{position:fixed!important;left:0!important;top:0!important;width:100%!important;height:100%!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important}video::-webkit-media-controls,video::-webkit-media-controls-panel,video::-webkit-media-controls-play-button,video::-webkit-media-controls-start-playback-button,video::-webkit-media-controls-timeline,video::-webkit-media-controls-current-time-display,video::-webkit-media-controls-time-remaining-display,video::-webkit-media-controls-seek-back-button,video::-webkit-media-controls-seek-forward-button,video::-webkit-media-controls-fullscreen-button{display:none!important;-webkit-appearance:none!important}.jw-controlbar{display:none!important}.jw-controls{display:none!important}';
        document.head.appendChild(st);
      }
      v.setAttribute('playsinline','');
      try{ v.controls=false; v.removeAttribute('controls'); }catch(e){}
      try{ v.click(); }catch(e){}
      var p=v.play();
      if(p&&p.catch){
        p.catch(function(){
          try{ v.muted=true; v.play(); }catch(e){}
        });
      }
    }catch(e){}
  }
  function sweep(doc){
    try{
      var W=doc.defaultView;
      if(!W){ return; }
      var els=doc.querySelectorAll('div,ul,footer,section');
      for(var i=0;i<els.length;i++){
        var el=els[i];
        try{
          if(el.querySelector('video')){ continue; }
          var r=el.getBoundingClientRect();
          if(r.height>=18&&r.height<=120&&r.width>W.innerWidth*0.35&&r.bottom>W.innerHeight*0.85&&r.top>W.innerHeight*0.5){
            var cs=W.getComputedStyle(el);
            if(cs&&(cs.position==='fixed'||cs.position==='absolute')){
              el.style.setProperty('display','none','important');
            }
          }
        }catch(e){}
      }
    }catch(e){}
  }
  function scan(){
    sweep(document);
    var vs=document.querySelectorAll('video');
    for(var i=0;i<vs.length;i++){ fit(vs[i]); }
    if(vs.length===0){
      var bfs=document.querySelectorAll('iframe');
      var best=null,barea=0;
      for(var b=0;b<bfs.length;b++){
        try{
          var br=bfs[b].getBoundingClientRect();
          var ba=br.width*br.height;
          if(br.height>180&&ba>barea){ barea=ba; best=bfs[b]; }
        }catch(e){}
      }
      if(best){ stretch(best); }
    }
    var frs=document.querySelectorAll('iframe');
    for(var j=0;j<frs.length;j++){
      try{
        var d=frs[j].contentDocument;
        if(d){
          sweep(d);
          var ivs=d.querySelectorAll('video');
          if(ivs.length>0){ stretch(frs[j]); }
          for(var k=0;k<ivs.length;k++){ fit(ivs[k]); }
        }
      }catch(e){}
    }
    return vs.length>0;
  }
  if(!scan()){
    var mo=new MutationObserver(function(){ scan(); });
    mo.observe(document.documentElement,{childList:true,subtree:true});
    setTimeout(function(){ mo.disconnect(); },90000);
  }
  var n=0;
  var timer=setInterval(function(){
    n++;
    sweep(document);
    var vs=document.querySelectorAll('video');
    for(var i=0;i<vs.length;i++){ if(vs[i].paused){ fit(vs[i]); }else{ stretch(vs[i]); } }
    var frs2=document.querySelectorAll('iframe');
    for(var j=0;j<frs2.length;j++){
      try{
        var d2=frs2[j].contentDocument;
        if(d2){
          var ivs2=d2.querySelectorAll('video');
          if(ivs2.length>0){ stretch(frs2[j]); }
          for(var k=0;k<ivs2.length;k++){ if(ivs2[k].paused){ fit(ivs2[k]); }else{ stretch(ivs2[k]); } }
        }
      }catch(e){}
    }
    if(n>=120){ clearInterval(timer); }
  },1000);
})();
"""

    }
}
