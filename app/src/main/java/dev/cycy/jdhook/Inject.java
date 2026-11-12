package dev.cycy.jdhook;

final class Inject {

    /**
     * 在页面里装 XHR / fetch 钩子，把请求和响应记到 window.__jdhook_report。
     * 目的：拿到 signBeanAct 的完整请求（含 h5st / whwswswws 等风控参数）和响应。
     */
    static final String ARM_JS = """
            (function(){
              try{
                if(window.__jdhook_armed) return 'already';
                window.__jdhook_armed = true;
                var R = window.__jdhook_report = window.__jdhook_report || {net:[]};
                R.url = location.href;
                function push(o){ try{ if(R.net.length<60) R.net.push(o); }catch(e){} }
                var XO=XMLHttpRequest.prototype.open, XS=XMLHttpRequest.prototype.send, XH=XMLHttpRequest.prototype.setRequestHeader;
                XMLHttpRequest.prototype.open=function(m,u){ try{this.__m=m;this.__u=String(u);this.__h={};}catch(e){} return XO.apply(this,arguments); };
                XMLHttpRequest.prototype.setRequestHeader=function(k,v){ try{this.__h[k]=String(v);}catch(e){} return XH.apply(this,arguments); };
                XMLHttpRequest.prototype.send=function(b){
                  try{
                    var u=this.__u||'';
                    push({k:'req',m:this.__m||'GET',u:u,b:String(b||'').slice(0,3000),h:this.__h||{}});
                    var self=this;
                    if(u.indexOf('sign')>=0||u.indexOf('jingdou')>=0||u.indexOf('bean')>=0||u.indexOf('client.action')>=0){
                      self.addEventListener('load',function(){
                        var t=''; try{ t=String(self.responseText||'').slice(0,4000); }catch(e){}
                        push({k:'resp',u:u,s:self.status,r:t});
                      });
                    }
                  }catch(e){}
                  return XS.apply(this,arguments);
                };
                if(window.fetch){
                  var F=window.fetch;
                  window.fetch=function(i,o){
                    try{
                      var u=String((i&&i.url)||i);
                      var h={};
                      try{ if(o&&o.headers){ if(o.headers.forEach){o.headers.forEach(function(v,k){h[k]=v;});} else {h=o.headers;} } }catch(e){}
                      push({k:'freq',u:u,b:o&&o.body?String(o.body).slice(0,3000):'',h:h});
                    }catch(e){}
                    return F.apply(this,arguments);
                  };
                }
                return 'armed';
              }catch(e){ return 'err:'+e; }
            })()
            """;

    /** 采集 DOM + 网络报告，返回 JSON 字符串（evaluateJavascript 会再 JSON 编码一层） */
    static final String DUMP_JS = """
            (function(){
              try{
                var R = window.__jdhook_report = window.__jdhook_report || {net:[]};
                R.url = location.href;
                R.title = document.title;
                var d = {};
                try{ d.text = (document.body?document.body.innerText:'').slice(0,4000); }catch(e){}
                try{
                  var els=document.querySelectorAll('a,button,div,span,p,img,i');
                  var out=[];
                  for(var i=0;i<els.length&&out.length<150;i++){
                    var e=els[i], t=(e.innerText||'').replace(/\\s+/g,' ').trim();
                    if(!t||t.length>24) continue;
                    out.push({tag:e.tagName,id:e.id||'',cls:String(e.className||'').slice(0,60),t:t});
                  }
                  d.click=out;
                }catch(e){}
                try{ d.ls=Object.keys(localStorage).slice(0,40); }catch(e){}
                try{ d.ss=Object.keys(sessionStorage).slice(0,40); }catch(e){}
                R.dom=d;
                return JSON.stringify(R).slice(0,60000);
              }catch(e){ return '{"err":"'+e+'"}'; }
            })()
            """;

    /** 找“签到”按钮并点击（只点，不做别的） */
    static final String CLICK_JS = """
            (function(){
              try{
                var kw=['立即签到','点击签到','签到领京豆','签到'];
                var els=document.querySelectorAll('a,button,div,span,p');
                var hit=[];
                for(var i=0;i<els.length;i++){
                  var e=els[i], t=(e.innerText||'').replace(/\\s+/g,'').trim();
                  if(!t||t.length>10) continue;
                  for(var j=0;j<kw.length;j++){
                    if(t.indexOf(kw[j])>=0){
                      hit.push(t);
                      try{ e.click(); }catch(x){}
                      break;
                    }
                  }
                  if(hit.length>=2) break;
                }
                return JSON.stringify({clicked:hit});
              }catch(e){ return '{"err":"'+e+'"}'; }
            })()
            """;

    private Inject() {
    }
}
