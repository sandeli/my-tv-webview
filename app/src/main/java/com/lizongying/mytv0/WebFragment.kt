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

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val handler = Handler(Looper.getMainLooper())

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
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!active) {
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
                if (active && request?.isForMainFrame == true) {
                    tvModel?.setErrInfo(R.string.play_error.getString())
                }
                super.onReceivedError(view, request, error)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
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
        binding.root.visibility = View.VISIBLE
        Log.i(TAG, "play web channel ${tvModel.tv.title} $url")
        binding.webView.loadUrl(url)
    }

    fun stopPlayback() {
        if (!active && _binding == null) {
            return
        }
        active = false
        if (_binding != null) {
            binding.webView.stopLoading()
            binding.webView.loadUrl("about:blank")
            binding.root.visibility = View.GONE
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
