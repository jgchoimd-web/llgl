package com.llgl.tube.yt

/**
 * The page the WebView loads: YouTube's official IFrame player, playing a channel's uploads playlist
 * shuffled, scaled to cover or fit the screen, with a small bridge back to Kotlin.
 *
 * The page is loaded with [BASE_URL] as its address, so the embed sees a plain https origin of this
 * app's own as the referrer. It must not pretend to be youtube.com: since 2025 the embed answers that
 * with error 152 ("This video is unavailable"), which is exactly what a youtube.com base URL produced
 * on a phone and in a headless Chromium probe, while an app origin loaded the playlist fine.
 */
object PlayerPage {
    const val ORIGIN = "https://tube.llgl.app"
    const val BASE_URL = "$ORIGIN/player.html"
    const val FIT_COVER = "cover"
    const val FIT_CONTAIN = "contain"

    fun html(listId: String, fit: String, muted: Boolean): String = TEMPLATE
        .replace("__LIST__", listId)
        .replace("__FIT__", if (fit == FIT_CONTAIN) FIT_CONTAIN else FIT_COVER)
        .replace("__MUTED__", if (muted) "true" else "false")

    private val TEMPLATE = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<style>
html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
#wrap{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:100%;height:56.25vw}
#player{width:100%;height:100%}
#notice{position:absolute;left:0;right:0;bottom:10%;text-align:center;color:#8fa3b8;font:15px/1.4 sans-serif;padding:0 8%;pointer-events:none}
</style></head><body>
<div id="wrap"><div id="player"></div></div>
<div id="notice"></div>
<script>
var player=null, ready=false, list='__LIST__', fit='__FIT__', muted=__MUTED__, wanted=false;
function layout(){
  var W=window.innerWidth,H=window.innerHeight,r=16/9,w,h;
  var wide=(W/H)>r;
  if(fit==='cover'){ if(wide){w=W;h=W/r;}else{h=H;w=H*r;} } else { if(wide){h=H;w=H*r;}else{w=W;h=W/r;} }
  var el=document.getElementById('wrap'); el.style.width=w+'px'; el.style.height=h+'px';
  if(player&&player.setSize){ try{player.setSize(w,h);}catch(e){} }
}
function tell(n,a){ try{ if(window.Bridge&&Bridge[n]) Bridge[n](a===undefined?'':String(a)); }catch(e){} }
function onYouTubeIframeAPIReady(){
  player=new YT.Player('player',{width:'100%',height:'100%',
    playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,modestbranding:1,iv_load_policy:3,fs:0,disablekb:1,listType:'playlist',list:list,loop:1,origin:location.origin},
    events:{
      onReady:function(){ ready=true; layout(); if(muted)player.mute(); else player.unMute(); try{player.setShuffle(true);player.setLoop(true);}catch(e){} if(wanted)player.playVideo(); else player.pauseVideo(); tell('onReady'); },
      onStateChange:function(e){ tell('onState',e.data); if(e.data===1){ try{ tell('onTitle', player.getVideoData().title||''); }catch(x){} } },
      onError:function(e){ tell('onError',e.data); setTimeout(function(){ try{player.nextVideo();}catch(x){} },1500); }
    }});
}
window.addEventListener('resize',layout);
function play(){ wanted=true; if(ready){ try{player.playVideo();}catch(e){} } }
function pause(){ wanted=false; if(ready){ try{player.pauseVideo();}catch(e){} } }
function next(){ if(ready){ try{player.nextVideo();}catch(e){} } }
function setMuted(m){ muted=!!m; if(ready){ try{ if(muted)player.mute(); else player.unMute(); }catch(e){} } }
function setFit(f){ fit=f; layout(); }
function notice(t){ document.getElementById('notice').textContent=t||''; }
layout();
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body></html>
"""
}
