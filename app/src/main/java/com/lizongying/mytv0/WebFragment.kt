package com.lizongying.mytv0

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
                        consoleLogs.add(cm.messageLevel().name() + ": " + cm.message().take(90))
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
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val url = tvModel.getWebUrl()
        if (url.isNullOrEmpty()) {
            tvModel.setErrInfo(R.string.play_error.getString())
            return
        }
        active = true
        generation++
        currentUrl = url
        binding.root.visibility = View.VISIBLE
        Log.i(TAG, "play web channel ${tvModel.tv.title} $url")
        tvModel.setErrInfo("")
        synchronized(failedUrls) { failedUrls.clear() }
        synchronized(consoleLogs) { consoleLogs.clear() }
        binding.webView.loadUrl(url)
        val gen = generation
        binding.diagView.visibility = View.VISIBLE
        binding.diagView.text = "诊断: 页面加载中…"
        for (delay in listOf(4000L, 9000L, 16000L)) {
            handler.postDelayed({ if (active && generation == gen) probe(gen) }, delay)
        }
    }

    fun stopPlayback() {
        if (!active && _binding == null) {
            return
        }
        active = false
        currentUrl = null
        if (_binding != null) {
            binding.diagView.visibility = View.GONE
        }
        if (_binding != null) {
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            binding.root.visibility = View.GONE
        }
    }

    private fun probe(gen: Int) {
        if (_binding == null) return
        binding.webView.evaluateJavascript(PROBE_JS) { raw ->
            if (!active || generation != gen || _binding == null) return@evaluateJavascript
            var text = raw ?: "null"
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            text = text.replace("\\n", "\n").replace("\\u003C", "<").replace("\\"", "\"")
            val extra = StringBuilder()
            synchronized(failedUrls) {
                if (failedUrls.isNotEmpty()) {
                    extra.append("\n失败请求:")
                    failedUrls.forEach { extra.append("\n  ").append(it) }
                }
            }
            synchronized(consoleLogs) {
                if (consoleLogs.isNotEmpty()) {
                    extra.append("\n控制台:")
                    consoleLogs.forEach { extra.append("\n  ").append(it) }
                }
            }
            binding.diagView.text = text + extra.toString()
        }
    }

    override fun onPause() {
        super.onPause()
        if (_binding != null) {
            binding.webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            binding.webView.onResume()
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
        private const val PROBE_JS = """
(function(){
  var out=[];
  out.push('标题: '+document.title);
  out.push('地址: '+location.href);
  var vs=document.querySelectorAll('video');
  out.push('video数: '+vs.length);
  for(var i=0;i<vs.length;i++){
    var v=vs[i];
    var src=(v.currentSrc||v.src||'(无)');
    if(src.length>90){ src=src.substring(0,90)+'...'; }
    out.push(' v'+i+' ready='+v.readyState+' paused='+v.paused+' 尺寸='+v.videoWidth+'x'+v.videoHeight+' err='+(v.error?v.error.code:'-'));
    out.push('   src='+src);
  }
  var frs=document.querySelectorAll('iframe');
  out.push('iframe数: '+frs.length);
  for(var j=0;j<frs.length;j++){
    var fs=frs[j].src||'(无)';
    if(fs.length>90){ fs=fs.substring(0,90)+'...'; }
    out.push(' f'+j+' '+fs);
  }
  var txt='';
  try{ txt=(document.body?document.body.innerText:'').replace(/\s+/g,' ').substring(0,140); }catch(e){}
  out.push('正文: '+txt);
  return out.join('\n');
})();
"""

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        // 找 video、拉满全屏、自动播放；带声自动播放被拒时退到静音播放保画面。
        // 播放器晚加载的情况用 MutationObserver + 定时重扫兜底。
        private const val INJECT_JS = """
(function(){
  function fit(v){
    try{
      if(!document.getElementById('__webtv_style')){
        var st=document.createElement('style');
        st.id='__webtv_style';
        st.innerHTML='html,body{width:100%!important;height:100%!important;background:#000!important;overflow:hidden!important}video{position:fixed!important;left:0!important;top:0!important;width:100%!important;height:100%!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important}';
        document.head.appendChild(st);
      }
      v.setAttribute('playsinline','');
      try{ v.click(); }catch(e){}
      var p=v.play();
      if(p&&p.catch){
        p.catch(function(){
          try{ v.muted=true; v.play(); }catch(e){}
        });
      }
    }catch(e){}
  }
  function scan(){
    var vs=document.querySelectorAll('video');
    for(var i=0;i<vs.length;i++){ fit(vs[i]); }
    var frs=document.querySelectorAll('iframe');
    for(var j=0;j<frs.length;j++){
      try{
        var d=frs[j].contentDocument;
        if(d){
          var ivs=d.querySelectorAll('video');
          for(var k=0;k<ivs.length;k++){ fit(ivs[k]); }
        }
      }catch(e){}
    }
    return vs.length>0;
  }
  if(!scan()){
    var mo=new MutationObserver(function(){ scan(); });
    mo.observe(document.documentElement,{childList:true,subtree:true});
    setTimeout(function(){ mo.disconnect(); },20000);
  }
  var n=0;
  var timer=setInterval(function(){
    n++;
    var vs=document.querySelectorAll('video');
    for(var i=0;i<vs.length;i++){ if(vs[i].paused){ fit(vs[i]); } }
    if(n>=10){ clearInterval(timer); }
  },1000);
})();
"""
    }
}
