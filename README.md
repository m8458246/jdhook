# jdhook

京东（`com.jingdong.app.mall` 16.1.50 / targetSdk 36）自动签到的 LSPosed 模块 + crond 调度脚本。

## 为什么是这种架构

先做了实验，再定的方案。

**1. 签到不在 Java 层。**
在 85994 个类里搜 `signBean` / `signBeanAct` / `signIndex`，0 命中。
「签到领京豆」入口在 `com.jingdong.common.utils.personal.PlatformLocalStaticConfig` 的静态配置里：

```
jumpUrl = https://pro.m.jd.com/mall/active/Md9FMi1pJXg2q7qc8CmE9FNYDS4/index.html?babelChannel=ttt91
jumpType = 1, needLogin = 1
```

是登录态 H5 活动页，签到请求由页面 JS 发出。

**2. 纯 cookie 的 curl 走不通。**
从京东 WebView Cookie 库取出 `pt_key`（有效期正常）后直连接口：

| 请求 | 返回 |
|---|---|
| `signBeanAct` | `{"code":"402","message":"活动现在挤不进去呀，待会再来试试吧"}` |
| `signBeanIndex` | `{"code":"3","errorMessage":"用户未登录"}` |

402 是风控门，不是登录门。

**3. 风控参数只能运行时生成。**
`whwswswws` 在整个 data 目录里只出现在一个 hybrid4 缓存 JS 中，不落盘。

**结论**：脚本负责调度和通知，真正的签到必须由模块在京东进程内驱动页面完成。

## 结构

```
app/                       LSPosed 模块（libxposed API 102）
  ModuleMain.java          入口
  Hooks.java               hook WebView.loadUrl / WebViewClient.onPageFinished
  Inject.java              注入 JS：XHR/fetch 记录 + DOM 采集 + 点签到
  Io.java                  单向输出到 /data/user/0/<pkg>/files/jdhook/
script/jd_signin.sh        crond 调度 + 通知
.github/workflows/build.yml
```

模块只写不读：脚本写文件给模块会踩 SELinux 文件上下文，所以触发靠 `am start` 的 deeplink，
URL 里的 `jdhook=click` 就是「点签到」的指令。

## 装

1. 装 `app-debug.apk`
2. LSPosed 里启用 JDHook，作用域勾选「京东」，重启京东
3. 脚本放到 `/data/adb/crond/jd_signin.sh`，`chmod 755`
4. crontab 加一行：

```
40 8 * * * /system/bin/sh /data/adb/crond/jd_signin.sh >/dev/null 2>&1
```

## 产物

| 文件 | 内容 |
|---|---|
| `files/jdhook/hook.log` | 模块安装与页面加载记录 |
| `files/jdhook/report.json` | 页面 DOM + 全部 XHR（含 signBeanAct 请求与响应） |
| `files/jdhook/report2.json` | 点击后的第二轮采集 |
| `files/jdhook/click.json` | 实际点到的按钮文本 |

## v0.1 状态

采集 + 首次点击。签到成功与否以页面/接口响应为准，判定逻辑在脚本里，
等第一轮真机数据回来后再收紧。浏览任务默认不做。
