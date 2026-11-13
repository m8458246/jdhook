package dev.cycy.jdhook;

final class Inject {

    /**
     * 在页面（含同源 iframe）里装 XHR / fetch 钩子，把请求和响应记到 window.__jdhook_report。
     * 京东的 H5 大量用 iframe 容器（mTabId），只打顶层窗口会什么都抓不到。
     */
    static final String ARM_JS = """
            (function(){
              function rep(w){
                try{ if(w.__jdhook_report) return w.__jdhook_report; }catch(e){}
                try{ if(w.parent && w.parent!==w && w.parent.__jdhook_report) return w.parent.__jdhook_report; }catch(e){}
                try{ w.__jdhook_report = {net:[]}; return w.__jdhook_report; }catch(e){}
                return null;
              }
              function patch(w){
                try{
                  if(!w || w.__jdhook_armed) return;
                  w.__jdhook_armed = true;
                  var R = rep(w);
                  if(!R) return;
                  var X = w.XMLHttpRequest;
                  if(!X) return;
                  var P = X.prototype;
                  var XO=P.open, XS=P.send, XH=P.setRequestHeader;
                  P.open=function(m,u){ try{this.__m=m;this.__u=String(u);this.__h={};}catch(e){} return XO.apply(this,arguments); };
                  P.setRequestHeader=function(k,v){ try{this.__h[k]=String(v);}catch(e){} return XH.apply(this,arguments); };
                  P.send=function(b){
                    try{
                      var u=this.__u||'';
                      if(R.net.length<80) R.net.push({k:'req',m:this.__m||'GET',u:u,b:String(b||'').slice(0,3000),h:this.__h||{}});
                      var self=this;
                      if(u.indexOf('sign')>=0||u.indexOf('jingdou')>=0||u.indexOf('bean')>=0||u.indexOf('client.action')>=0){
                        self.addEventListener('load',function(){
                          var t=''; try{ t=String(self.responseText||'').slice(0,4000); }catch(e){}
                          if(R.net.length<80) R.net.push({k:'resp',u:u,s:self.status,r:t});
                        });
                      }
                    }catch(e){}
                    return XS.apply(this,arguments);
                  };
                  if(w.fetch){
                    var F=w.fetch;
                    w.fetch=function(i,o){
                      try{
                        var u=String((i&&i.url)||i);
                        var h={};
                        try{ if(o&&o.headers){ if(o.headers.forEach){o.headers.forEach(function(v,k){h[k]=v;});} else {h=o.headers;} } }catch(e){}
                        if(R.net.length<80) R.net.push({k:'freq',u:u,b:o&&o.body?String(o.body).slice(0,3000):'',h:h});
                      }catch(e){}
                      return F.apply(this,arguments);
                    };
                  }
                }catch(e){}
              }
              patch(window);
              try{
                var fs=document.querySelectorAll('iframe,frame');
                for(var i=0;i<fs.length&&i<8;i++){ try{ patch(fs[i].contentWindow); }catch(e){} }
              }catch(e){}
              return 'armed';
            })()
            """;

    /** 采集顶层窗口 + 同源 iframe 的 DOM，返回 JSON 字符串 */
    static final String DUMP_JS = """
            (function(){
              try{
                var R = window.__jdhook_report = window.__jdhook_report || {net:[]};
                R.url = location.href;
                R.title = document.title;
                function grab(w,tag){
                  var d={tag:tag};
                  try{ d.url=w.location.href; }catch(e){ d.url='?'; }
                  try{ d.text=(w.document.body?w.document.body.innerText:'').replace(/\\s+/g,' ').slice(0,3000); }catch(e){ d.text=''; }
                  try{
                    var els=w.document.querySelectorAll('a,button,div,span,p,img,i');
                    var out=[];
                    for(var i=0;i<els.length&&out.length<120;i++){
                      var e=els[i], t=(e.innerText||'').replace(/\\s+/g,' ').trim();
                      if(!t||t.length>24) continue;
                      out.push({tag:e.tagName,id:e.id||'',cls:String(e.className||'').slice(0,50),t:t});
                    }
                    d.click=out;
                  }catch(e){}
                  return d;
                }
                var frames=[grab(window,'top')];
                try{
                  var fs=document.querySelectorAll('iframe,frame');
                  for(var i=0;i<fs.length&&i<8;i++){
                    var f=fs[i];
                    try{ frames.push(grab(f.contentWindow,'if'+i+':'+String(f.src||'').slice(0,120))); }
                    catch(e){ frames.push({tag:'if'+i,src:String(f.src||'').slice(0,120),err:'cross-origin'}); }
                  }
                }catch(e){}
                R.frames=frames;
                return JSON.stringify(R).slice(0,60000);
              }catch(e){ return '{"err":"'+e+'"}'; }
            })()
            """;

    /** 找“签到/刮/领”按钮并点击（只点，不做别的） */
    static final String CLICK_JS = """
            (function(){
              try{
                var kw=['立即签到','点击签到','签到领京豆','天天领京豆','立即领取','签到','刮一刮','刮京豆','立即刮'];
                var out=[];
                function scan(w,tag){
                  try{
                    var els=w.document.querySelectorAll('a,button,div,span,p');
                    for(var i=0;i<els.length;i++){
                      var e=els[i], t=(e.innerText||'').replace(/\\s+/g,'').trim();
                      if(!t||t.length>12) continue;
                      for(var j=0;j<kw.length;j++){
                        if(t.indexOf(kw[j])>=0){
                          out.push(tag+'|'+t);
                          try{ e.click(); }catch(x){}
                          break;
                        }
                      }
                      if(out.length>=3) return;
                    }
                  }catch(e){}
                }
                scan(window,'top');
                try{
                  var fs=document.querySelectorAll('iframe,frame');
                  for(var i=0;i<fs.length&&out.length<3;i++){ try{ scan(fs[i].contentWindow,'if'+i); }catch(e){} }
                }catch(e){}
                return JSON.stringify({clicked:out});
              }catch(e){ return '{"err":"'+e+'"}'; }
            })()
            """;

    private Inject() {
    }
}
