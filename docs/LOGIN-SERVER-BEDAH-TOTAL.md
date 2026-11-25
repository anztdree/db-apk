# BEDAH TOTAL LOGIN-SERVER DI main.min.js — ATLAS VERBATIM (Bagian 64)

> Owner: "usulan di tolak!! saya sudah bilang **bedah total dulu** login-server
> yg ada di main.min.js!!" → dokumen ini = bedah TOTAL: SETIAP fungsi, SETIAP
> event, SETIAP field yang dibaca client dari login server — semua VERBATIM
> dari `tools/out/main.min_777039fc.js` (4.902.347 byte) + offset posisi supaya
> bisa dicek siapa pun. **TIDAK ADA usulan struktur di dokumen ini** — struktur
> baru diturunkan SETELAH owner cermat atlas ini.
>
> Metode: `python3` slice pada offset — reproduksi:
> `python3 -c "s=open('tools/out/main.min_777039fc.js').read(); print(s[OFFSET:OFFSET+N])"`
>
> Sumber pendukung (repo): live ACK asli `verify-artifacts/getserverlist-ack-LIVE-20261126.txt`,
> transkrip live `live-login-contract-20261126.txt`, handshake verify
> `game-verify-LIVE-20261126.txt`, rencana `docs/LOGIN-SERVER-PLAN.md` (§2 = hasil
> live-verify; dokumen ini = hasil bedah client).

---

## 1. KELAS TSSocketClient — SATU KELAS UNTUK 4 SOCKET (offset 30514–33406)

Instansiasi (offset 762352) — `login-server` = SATU-SATUNYA dengan verifyEnable FALSE:

```js
t.loginClient  = new TSSocketClient("login-server",  !1),   // verify MATI
t.mainClient   = new TSSocketClient("main-server",   !0),
t.chatClient   = new TSSocketClient("chat-server",   !0),
t.dungeonClient= new TSSocketClient("dungeon-server",!0),
t.loginUserInfo={userId:"",serverId:"",serverName:"",sign:"",sdk:""},
t.fromReconnect=!1,
```

Kelas lengkap (offset 30514, dipangkas komentar):

```js
function e(e,t){this.socket=null,this.socketErrorLastTime=0,this.serverName="",
this.verifyEnable=!0,this.maxReconnectWaitTime=6e5,          // 600000 ms = 10 menit
this.serverName=e,this.verifyEnable=t}

e.prototype.connectToServer=function(e,t,n){                  // (url, onOk, onErr)
  var o=this; this.socket&&this.destroy(),                    // socket lama dihancurkan dulu
  this.socket=this.connectWithSocket(e,function(){t&&t(),t=null},
    function(e){o.stopListenNotify(),n&&(n(e),n=null)})},

e.prototype.listenNotify=function(e){this.socket&&this.socket.on("Notify",e)},
e.prototype.stopListenNotify=function(){this.socket&&this.socket.off("Notify")},
e.prototype.destroy=function(){this.socket&&(this.socket.destroy(),this.socket=null)},

e.prototype.sendToServer=function(e,t){                       // (payload, ackCallback)
  this.socket&&this.socket.emit("handler.process",e,t)},      // ← EVENT socket.io "handler.process"
                                                              //   ackCallback = ACK socket.io (server balas 43<n>)
Object.defineProperty(e.prototype,"isConnect",{get:function(){return this.socket&&this.socket.connected}}),

e.prototype.connectWithSocket=function(e,t,n){
  var o,a=this;
  return o=io.connect(e,{reconnectionAttempts:10}),           // socket.io v2 (EIO=3), MAKS 10 percobaan reconnect
  o.on("connect",function(){
    a.socketErrorLastTime=0,
    a.verifyEnable?(a.socketOnVerify(t),Logger.showInfoLog("网络已连接，等待验证",a.serverName)):t()   // ← verifyEnable FALSE → onOk LANGSUNG
  }),
  o.on("reconnect",function(){}),
  o.on("error",           function(e){Logger.showInfoLog("socket error",e.message,a.serverName),a.socketOnError(TSSocketErrorType.Error,n)}),
  o.on("reconnect_error", function(e){...socketOnError(TSSocketErrorType.ReconnectError,n)}),
  o.on("reconnect_failed",function(e){...socketOnError(TSSocketErrorType.ReconnectFail,n)}),
  o.on("connect_timeout", function(e){...socketOnError(TSSocketErrorType.Timeout,n)}),
  o.on("disconnect",      function(e){Logger.showInfoLog("server disconnect reason",e,a.serverName),a.socketOnError(TSSocketErrorType.Disconnect,n)}),
  o.on("reconnect_attempt",function(e){ ... jika offline ≥ maxReconnectWaitTime → socketOnError(Timeout,n) })},

e.prototype.socketOnError=function(e,t){
  if(e==TSSocketErrorType.ReconnectFail||e==TSSocketErrorType.Timeout) return void t(!0);
  if(e!=TSSocketErrorType.ReconnectError){
    n.socketErrorLastTime<=0&&(n.socketErrorLastTime=(new Date).getTime());
    var o=n.checkOfflineTimeout();                            // ≥ 10 menit offline?
    o?(n.socketErrorLastTime=0,Logger.showInfoLog("离线时间过长，需要重新登录。"),t(!0)):t(!1)}},
  // onErr(true)  = kembali ke scene Login (clientStartGame onErr true branch)
  // onErr(false) = diam / retry lembut

e.prototype.socketOnVerify=function(e){                       // HANYA main/chat/dungeon — BUKAN login
  t.socket.on("verify",function(n){
    var o=(new TEA).encrypt(n,"verification");                // XXTEA key "verification" (Bagian 58)
    t.socket.emit("verify",o,function(n){0==n.ret?e():(ErrorHandler.ShowErrorTips(n.ret,...),t.destroy())})})},

e.prototype.checkOfflineTimeout=function(){ ... (now - socketErrorLastTime) >= 6e5 }
```

FAKTA KELAS (semua baru tercatat utuh di sini):
1. ACK = mekanisme ACK socket.io BAWAAN — client `emit("handler.process", payload, ackFn)`;
   server WAJIB memanggil ack → frame `43<ackId>[...]`. Tidak ada ackId buatan sendiri.
2. Maks reconnect **10 percobaan**; offline ≥ **10 menit** = "harus login ulang" → onErr(true).
3. `connectToServer` selalu `destroy()` socket lama dulu — tidak ada socket ganda.
4. Login socket: `connect` → onOk LANGSUNG (tanpa verify) — cocok live capture (tidak ada challenge di 610).
5. `isConnect` = `socket && socket.connected` — dipakai processHandlerWithLogin (§2).

---

## 2. PENGIRIM + DISPATCHER ACK — processHandlerWithLogin (offset 774449)

```js
t.prototype.processHandlerWithLogin=function(e,t,n,o){        // (payload, autoConnect, onOk, onErr)
  var a=this,
  r=function(e){                                              // ← ACK handler
    if(0===e.ret){
      var t=e.data;
      e.compress&&(t=LZString.decompressFromUTF16(t)),        // compress OPSIONAL
      var a=JSON.parse(t);                                    // data = JSON STRING (double-encoded)
      a.language&&(ts.language=a.language),                   // ← top-level "language" DIBACA DI SINI (semua event!)
      n&&n(a)
    } else Logger.showInfoLog("登录出错",e.ret),o&&o()         // ret != 0 → log + onErr
  };
  t&&!this.loginClient.isConnect
    ? this.connectToLogin(function(){a.loginClient.sendToServer(e,r)})
    : this.loginClient.isConnect&&this.loginClient.sendToServer(e,r)
}
```

FAKTA DISPATCHER (penting, beberapa BARU):
1. `0===e.ret` STRICT (number) → `data` wajib STRING JSON → `JSON.parse`.
2. `compress` opsional — truthy → LZString dekompresi; absen → polos.
3. **`a.language&&(ts.language=a.language)` di dispatcher** — top-level `language`
   pada ACK EVENT APAPUN mengubah bahasa client, bukan cuma GetServerList.
4. `ret != 0` → TIDAK ADA dialog, hanya log "登录出错" + onErr.
5. **autoConnect=false + socket belum connect = EVENT DIBUANG SENYAP** (tidak
   dikirim, tanpa error, tanpa log) — ini jalur loginGame asli (§4.2).
6. **TIDAK ADA TIMEOUT** di jalur login. Bandingkan `processHandler` jalur GAME
   (offset 772977): loading panel 600 ms + timeout 10 dtk → pesan aset
   "TSUIController id10". Login path: kalau server DIAM, client diam selamanya
   (callback tak pernah jalan) — sama dgn temuan live LoginAnnounce 6 dtk.

---

## 3. SUMBER URL LOGIN — connectToLogin (offset 797029)

```js
t.prototype.connectToLogin=function(e){
  var t=this,n=TSBrowser.executeFunction("getLoginServer");
  if(console.log("serverurl:"+n),n) this.loginClient.connectToServer(n,e);
  else{
    var o=""; isWeixin&&(o=TSBrowser.getVariantValue("clientserver")+"/"),
    RES.getResByUrl(o+"resource/properties/serversetting.json?v="+Math.random(),
      function(n){console.log("setting.loginserver:"+n.loginserver),
      t.loginClient.connectToServer(n.loginserver,e)},this,RES.ResourceItem.TYPE_JSON)
  }
}
```

1. Prioritas: **bridge native `getLoginServer()`** → kalau ada, DIPAKAI.
2. Fallback: `resource/properties/serversetting.json` field **`loginserver`**
   (cache-buster `?v=random` — selalu fetch segar).
3. Device lokal kita: bridge `getLoginServer` = `http://127.0.0.1:9528`
   (dari `setting_BS_Android.bin` XOR) — jalur bridge menang; serversetting.json
   juga sudah kita isi `http://127.0.0.1:9528` (cadangan).

---

## 4. INVENTARIS TOTAL EVENT LOGIN — TEPAT 6 (7 hit processHandlerWithLogin = 1 def + 6 panggilan)

Bukti lengkap: semua `loginClient.sendToServer` hanya dari dispatcher; semua
panggilan dispatcher tercantum di bawah. **Tidak ada event login ke-7.**

### 4.1 GetServerList (offset 793433 — `clientRequestServerList`)

```js
t.prototype.clientRequestServerList=function(e,t,n){          // (userId, channel, cb)
  var o=TSBrowser.executeFunction("getAppId")||"",
  a={type:"User",action:"GetServerList",userId:e,subChannel:o,channel:t};
  ts.processHandlerWithLogin(a,!0,n)                          // autoConnect TRUE
}
```
- Pemanggil: (a) jalur SDK `sdkLoginSuccess` (1457749); (b) jalur origin
  `doOriginLoginRequest` (1454700) — userId/channel AMBIL dari ACK loginGame;
  (c) `refreshServerList` (1456690, tombol logout jalur SDK).
- Callback langsung = pemilih server (§6); **`errorCode` TIDAK dibaca client**.

### 4.2 loginGame (offset 792556 — `clientLoginUser`)

```js
t.prototype.clientLoginUser=function(e,t,n,o){                // (userId, password, fromChannel, cb)
  var a=TSBrowser.executeFunction("getAppId")||"",
  r=function(){
    var r={type:"User",action:"loginGame",userId:e,password:t,fromChannel:n,
           channelName:"",headImageUrl:"",nickName:"",subChannel:a,version:"1.0"};
    ts.processHandlerWithLogin(r,!1,o)                        // autoConnect FALSE
  };
  this.connectToLogin(r)                                      // TAPI SELALU connectToLogin dulu
}
```
- Pemanggil: `doOriginLoginRequest` (1454837) — 3 pintu: URL `?userid=` /
  localStorage `last_game_server` / tombol manual.
- **Field ACK yang dibaca**: `o.userId` + `o.channelCode` → dipakai GetServerList
  berikutnya. `loginToken/nickName/isCreated/errorCode/errorMessage` → TIDAK
  dibaca di pangkalan ini (errorCode memang diabaikan — quirk asli).
- Detail:`password` kosong → `"game_origin"`; `fromChannel` = password yang sama
  (verbatim: `ts.clientLoginUser(e.username,e.password,e.password,cb)`).

### 4.3 SaveHistory (offset 1453497 — `startBtnTap`)

```js
t.prototype.startBtnTap=function(e){
  if(!ts.loginInfo.serverItem) return void ErrorHandler.ShowNormalErrror(ToolCommon.getLanguageWithEditor("login","id2"));
  if(!ts.loginInfo.serverItem.online) return void ErrorHandler.ShowNormalErrror(ts.loginInfo.serverItem.offlineReason);   // ← TOAST teks offlineReason
  ...
  var n={type:"User",action:"SaveHistory",
         accountToken:ts.loginInfo.userInfo.userId,
         channelCode:ts.loginInfo.userInfo.channelCode,
         serverId:ts.loginInfo.serverItem.serverId,
         securityCode:ts.loginInfo.userInfo.securityCode,
         subChannel:t,version:"1.0"},
  o=function(e){
    e&&e.loginToken&&(ts.loginInfo.userInfo.loginToken=e.loginToken),   // ← INJEKSI loginToken!
    ts.reportLogToPP("disConnectLoginSocket",null),
    ts.clientStartGame(!1),
    var t=e.todayLoginCount; 4===t&&ReportToSdkCommon(blackStoneLoginCount4),
                              6===t&&ReportToSdkCommon(blackStoneLoginCount6)
  };
  ts.openWindow("LoadingPanel",{parent:"Tips"}),
  ts.processHandlerWithLogin(n,!0,o,o)                        // onOk = onErr = SAMA
}
```
- **Fakta BARU (belum pernah dicatat repo)**: ACK SaveHistory yang membawa
  `loginToken` akan MENIMPA `userInfo.loginToken` — satu-satunya pintu server
  login MENYISIPKAN token sesi game ke client. Server asli TIDAK PERNAH mengirim
  (selalu kosong → token SDK dipertahankan) — tapi jalurnya ADA dan hidup.
- `todayLoginCount` hanya memicu analytics nilai 4 dan 6.
- **onErr = onOk** → ACK error pun `clientStartGame` TETAP jalan (SaveHistory
  tidak pernah memblokir mulai game).

### 4.4 LoginAnnounce (offset 1458983 — `getNotice`)

```js
t.prototype.getNotice=function(){
  var t={type:"User",action:"LoginAnnounce"};
  ts.processHandlerWithLogin(t,!0,function(t){
    e.notice={};var n=ToolCommon.getLanguage(),o=!1,a=!1,r=t.data;
    for(var i in r){
      var s=r[i].text[n]||"",l=r[i].title[n]||"",u=r[i].version,c=r[i].orderNo;
      r[i].alwaysPopup&&(o=!0),
      e.notice[i]={bulletin:s,bulletinVersion:u,bulletinTitle:l,order:c},a=!0
    }
    e.noticeBtn.visible=e.noticeBtn.includeInLayout=a,
    o&&e.noticeBtnTap()                                       // popup OTOMATIS
  })
}
```
- Dipanggil PARALEL dgn GetServerList (SDK: dalam callback setelah
  `selectNewServer`; origin: langsung setelah request GetServerList dikirim).
- `text[lang]||""` TANPA fallback; `version/orderNo` wajib ada di entri;
  `alwaysPopup` opsional; ≥1 entri → tombol notice muncul; `{}` → tidak muncul;
  server DIAM → callback tak jalan → juga tidak muncul (3 keadaan, 1 visual).

### 4.5 SaveLanguage (offset 790564 — `saveLanguage`)

```js
t.prototype.saveLanguage=function(e){
  var t=TSBrowser.executeFunction("getAppId")||"",n=...,o=ts.loginUserInfo.userId,a=ts.loginUserInfo.sdk;
  !o&&n&&(o=n.userId,a=n.sdk);
  var r={type:"User",action:"SaveLanguage",userid:ts.loginUserInfo.userId,
         sdk:ts.loginUserInfo.sdk,appid:t,language:e},
  i=function(t){
    0===t.errorCode?(ts.closeWindow("LanguageList"),TSBrowser.executeFunction("changeLanguage",e))
                   :(console.log("failed save language",t),ts.closeWindow("LanguageList"),window.changeLanguage(e))
  },
  s=function(){console.log("failed save language"),ts.closeWindow("LanguageList")};
  ts.processHandlerWithLogin(r,!0,i,s)
}
```
- **Pesan server (errorMessage) TIDAK PERNAH tampil** — console.log saja; kedua
  cabang SAMA-SAMA ganti bahasa.
- Sumber daftar bahasa = `window.supportLang` (aset .bin game: en/fr/kr) —
  client UI hanya PERNAH mengirim 3 nilai itu.
- CATATAN offset: `userid` diambil `loginUserInfo.userId` (fallback SDK info),
  BUKAN `loginInfo.userInfo.userId` — dua wadah identitas berbeda (§9).

### 4.6 SaveUserEnterInfo (offset 794977 — `reportToLoginEnterInfo`)

```js
t.prototype.reportToLoginEnterInfo=function(){
  var e=UserInfoSingleton.getInstance(),t=TSBrowser.executeFunction("getAppId")||"",
  n={type:"User",action:"SaveUserEnterInfo",
     accountToken:ts.loginInfo.userInfo.userId,
     channelCode:ts.loginInfo.userInfo.channelCode,
     subChannel:t,createTime:e.createTime,userLevel:e.getUserLevel(),version:"1.0"},
  o=function(){ts.loginClient.destroy()};                     // ← SATU-SATUNYA destroy login socket
  ts.processHandlerWithLogin(n,!0,o,o)
}
```
- Dikirim SETELAH ACK `enterGame` (server GAME) sukses (offset 794567) →
  inilah akhir siklus login socket: ACK (atau gagal) → `destroy()`.
- createTime/userLevel dari `UserInfoSingleton` (profil game), bukan login.

---

## 5. ALUR SCENE LOGIN — STATE MACHINE END-TO-END

### 5.1 Pintu masuk scene (offset 1450644 → 1451400)

```
scene Login load selesai:
  showClientVer() → showNetworkType() → stopMusic()
  → gameReady2Report()
  → var o = this.getSdkLoginInfo()          // (1457534): checkLoginFromSdk() && window.getSdkLoginInfo
  → setLoginLink()
  → o ADA  : UserInfoSingleton.isNoSdk = ("game_origin"==o.sdk)
             ts.loginUserInfo.{userId,sign,sdk} = o.{userId,sign,sdk}
             isFromSdk = true
             showLoginSuccessStatus()          // tombol start muncul (TANPA auto-enter)
             sdkLoginSuccess(o)                // (1457566)
             catch(e) → console.log + showLoginSuccessStatus()
  → o KOSONG: console.log("无渠道登录"); isNoSdk=true; checkLastLogin()   // (1451565)
```

### 5.2 Jalur SDK (1457566 — `sdkLoginSuccess`)

```js
ts.loginInfo.userInfo={loginToken:e.loginToken,userId:e.userId,nickName:e.nickName,
                       channelCode:e.sdk,securityCode:e.security},
ts.clientRequestServerList(e.userId,e.sdk,function(e){
  n.selectNewServer(ts.loginInfo.userInfo,e),t.getNotice()
})
```
- identitas = hasil `/loginchecknative` (thirdParams SDK) — server login lokal
  TIDAK perlu validasi apa pun di GetServerList.

### 5.3 Jalur origin / manual (1454471 — `checkLastLogin` + `doOriginLoginRequest`)

```js
checkLastLogin: URL ?userid= → doOriginLoginRequest({username:userid,password:"",lastServerID:""})
                else getLocalCacheServer() → doOriginLoginRequest(cache)
doOriginLoginRequest(e):
  e.password||(e.password="game_origin"),
  ts.clientLoginUser(e.username,e.password,e.password,function(o){     // loginGame; fromChannel=password
    ts.clientRequestServerList(o.userId,o.channelCode,function(t){     // ← userId/channelCode DARI ACK loginGame
      egret.localStorage.setItem(storageKeyServer,JSON.stringify(e)),  // simpan kredensial origin
      n.doLoginLogic(o,t,e) }),                                        // → selectServer (WINDOW MANUAL)
    t.getNotice() })                                                   // LoginAnnounce PARALEL
```
- loginBtnTap (manual, 1452212): username kosong → toast aset "login id1";
  else doOriginLoginRequest({username,password,lastServerID:""}).
- **Quirk penting**: jalur origin selalu berakhir di `selectServer` (window
  pilih server MANUAL) — auto-enter hanya jalur SDK dgn history kosong (§6).

---

## 6. RANTAI PEMILIH SERVER (offset 1455169–1456700)

```js
selectNewServer=function(e,t){                                // (userInfo, data ACK GetServerList)
  this.filterByWhiteList(t.serverList);                       // ← bridge window.serverList
  var n=this;n.changeServerInfo(t);                           // ← top-level offlineReason + demote new
  var o=!t.history||t.history.length<=0,                      // isNew = history kosong
  a=t.history.length>0?t.history[0]:t.serverList[0].serverId; // kandidat = history[0] ?? entri pertama
  if(!a) return n.selectServer(e,t);
  var r=n.matchServerUrl(a,t.serverList);
  return r?n.onLoginSuccess(e,r,o):n.selectServer(e,t)
}

selectServer=function(e,t){                                   // jalur MANUAL (window pilih server)
  this.filterByWhiteList(t.serverList);var n=this;n.changeServerInfo(t);
  var o=this.getLocalCacheServer(),
  a=function(t){ o?(o.lastServerID=t.serverId,localStorage set)
                 :localStorage set({lastServerID:t.serverId}),
                 n.onLoginSuccess(e,t)};
  ts.openWindow("ServerListNode",{fn:a,serverData:t})
}

changeServerInfo=function(e){                                  // (1456979) — VERBATIM UTUH
  for(var t=e.serverList,n=0;n<t.length;n++)
    t[n].offlineReason=e.offlineReason,                        // top-level MENIMPA semua entri
    n>0&&t[n]["new"]&&(t[n]["new"]=!1,t[n].hot=!0)             // entri index>0: new → hot
}

filterByWhiteList=function(e){                                 // (1457145) — VERBATIM UTUH
  var t=window.serverList;                                     // BRIDGE NATIVE
  if(t&&!(Object.keys(t).length<=0))
    for(var n=0;n<e.length;n++){var o=e[n].serverId;
      t&&t.hasOwnProperty(o)?e[n].name=t[o]:(e.splice(n,1),n--)}
}
// window.serverList ADA + tidak kosong → serverId yang tak ada di bridge DIHAPUS
// dari daftar; yang ada → name DIGANTI nilai bridge. Bridge kosong/absen = no-op.

matchServerUrl=function(e,t){                                  // (1456646)
  for(var n=0;n<t.length;n++){var o=t[n];
    if(e==o.serverId){                                         // LOOSE == (string/number sama saja)
      var a=window.serverList;
      return a&&a.hasOwnProperty(e)&&(o.name=a[e]),o}}         // bridge override name SEKALI LAGI
}

onLoginSuccess=function(e,t,n){                                // (1455870) n=isNew
  ts.loginInfo={userInfo:e,serverItem:t};
  var a=window.replaceServerName||[],                          // BRIDGE range-name
  r=function(e){for(var t=0;t<a.length;t++)
    if(e>=a[t].from&&e<a[t].to)return a[t].name.replace("{x}",e-a[t].reset);return""},
  i=r(t.serverId)||t.name;                                    // range-name MENANG atas entry.name
  ts.loginUserInfo.serverId=t.serverId,ts.loginUserInfo.serverName=i,o.loginUser.text=i,
  t.online? t.hot?o.serverState.source=ServerHotIcon
                 :t["new"]?o.serverState.source=ServerNewIcon
                 :o.serverState.visible=!1
          :o.serverState.source=ServerMaintainIcon,
  ts.reportLogToPP("enterServerList",null);
  ...sendCustomEvent(EventNameDefine.loginSuccess,...),
  o.showLoginSuccessStatus(n)
}

showLoginSuccessStatus=function(e){                            // (1454277)
  this.inputGroup.visible=!1,this.loginBtn.visible=!1,
  this.logoutGroup.visible=!0,this.startBtn.visible=!0,
  e&&this.startBtnTap()                                        // ← isNew = AUTO-ENTER
}

refreshServerList=function(){                                  // logout jalur SDK → request ulang
  ts.clientRequestServerList(t.userId,t.channelCode,function(n){e.selectServer(t,n)})
}
logoutBtnTap: isFromSdk ? refreshServerList() : showDefaultLoginStatus()
```

FAKTA rantai:
1. `serverList[0]` = entri PERTAMA array — urutan array = urutan prioritas auto.
2. `matchServerUrl` gagal (history[0] tidak ada di list) → window manual.
3. Ikon: `online:false` → ServerMaintainIcon; `hot` → Hot; `new` → New; else none.
4. Nama tampil: bridge `replaceServerName` (range) > bridge `serverList[id]` >
   `entry.name` — dua bridge native bisa MENIMPA isi serverlist.json kita.
5. toast offline = `serverItem.offlineReason` (hasil overwrite changeServerInfo).

---

## 7. PASCA-SaveHistory — PINTU KE SERVER GAME

```js
clientStartGame=function(e){                                   // (792866) e=fromReconnect
  onOk : startListenNotify() + clientEnterGame()
  onErr(true) : clearInterval(chatInterval); mainClient/chatClient/dungeonClient.destroy();
                runScene("Login"); normalErrorMsg(aset "TSUIController id9")
  onErr(false): chatClient.destroy(); showReconnectionLoading(); clientStartGame(!0)   // retry
  a=ts.loginInfo.serverItem.url;                               // ← URL DARI serverList ENTRY
  mainClient.connectToServer(a,...)
}

clientEnterGame (792983): loginToken/userId dari loginInfo.userInfo;
  serverId = parseInt(serverItem.serverId); language = ToolCommon.getLanguage();
  gameVersion = await getClientVer();
  processHandler({type:"user",action:"enterGame",loginToken,userId,serverId:NUMBER,
                  version:"1.0",language,gameVersion})          // ← ke server GAME (mainClient)
  ACK sukses → (reconnect: saveUserData+refresh) else loginSuccessCallBack(t)
             + reportToLoginEnterInfo()                          // → SaveUserEnterInfo → destroy login socket
             + chatInterval=setInterval(registChat,3000)

registChat (794977+): ACK membaca _chatServerUrl/_worldRoomId/_guildRoomId/...
  → ditulis ke ts.loginInfo.serverItem.* (chat = URUSAN server GAME, bukan login)

startListenNotify (788287): envelope Notify server GAME {ret:"SUCCESS",data,compress}
  "Kickout" → mainClient+chatClient.destroy() → runScene Login + aset id8
```

Kontras envelope: **login** = `{"ret":0,"data":"<string>"}`; **game Notify** =
`{"ret":"SUCCESS","data":"<string>","compress":...}`. `serverTime`/`server0Time`
dibaca di jalur game (Bagian 58) — bukan login.

---

## 8. BRIDGE NATIVE (window.*) YANG DISENTUH JALUR LOGIN

| Bridge | Dipakai di | Efek pada jalur login |
|---|---|---|
| `getLoginServer()` | connectToLogin | URL login socket — **punya kita** (9528) |
| `getAppId()` | semua event (subChannel) | string kosong bila tidak ada |
| `checkSDK()` / `getSdkLoginInfo()` | pintu scene | menentukan jalur SDK vs origin |
| `changeLanguage(lang)` | saveLanguage kedua cabang | bahasa tetap berganti |
| `supportLang` | languageBtnTap | daftar bahasa = ASET game (en/fr/kr) |
| `serverList` (object) | filterByWhiteList + matchServerUrl | **WHITELIST + ganti nama** — bisa MEMBUANG entri serverlist.json kita |
| `replaceServerName` | onLoginSuccess | nama per-RANGE serverId, menimpa entry.name |
| `getQueryStringByName("userid")` | checkLastLogin | pintu origin via URL |
| `contactSdk/userCenterSdk/switchUser/paySdk` | tombol lain | di luar alur login socket |

Implikasi jujur: dua bridge (`serverList`, `replaceServerName`) adalah LAYER
NATIVE yang bisa mengubah hasil serverList.json SEBELUM pemain melihatnya —
bedah ini yang membuat keduanya terlihat (belum pernah dipetakan di Bagian mana pun).

---

## 9. STATE CLIENT + LOCALSTORAGE

- `ts.loginUserInfo = {userId:"", serverId:"", serverName:"", sign:"", sdk:""}`
  — diisi jalur SDK (userId/sign/sdk) dan onLoginSuccess (serverId/serverName).
- `ts.loginInfo = {userInfo, serverItem}` — dibentuk onLoginSuccess; userInfo
  dari SDK path ATAU ACK loginGame (origin: hanya userId+channelCode terisi).
- DUA wadah identitas: `loginUserInfo` (scene) vs `loginInfo.userInfo` (sesi
  login) — SaveLanguage memakai `loginUserInfo.userid`; SaveHistory/SaveUserEnterInfo
  memakai `loginInfo.userInfo.accountToken`. Bedah ini yang memperjelas.
- localStorage key **`last_game_server`** (`storageKeyServer`, offset 926625):
  jalur origin menyimpan `{username,password,lastServerID}`; jalur manual
  `selectServer` menyimpan `{lastServerID}`. `getLocalCacheServer` JSON.parse
  (gagal → null). Ini CACHE CLIENT — bukan server-side.
- `ts.language` — hanya berubah via dispatcher (`a.language`) atau changeLanguage.

---

## 10. URUTAN WIRE LENGKAP (hasil gabungan bedah + live capture)

```
JALUR SDK (device lokal kita):
 1. bridge getLoginServer() → http://127.0.0.1:9528
 2. io.connect → OPEN polling (96:0{...}2:40) → upgrade WS → 2probe/3probe → packet "5"
 3. 420["handler.process",{type:"User",action:"GetServerList",userId,subChannel,channel}]
 4. (hampir bersamaan) 420[...{action:"LoginAnnounce"}]
 5. 43<id>[{"ret":0,"data":"{serverList:[...],history:[...]}"}]  → selectNewServer
    → onLoginSuccess → tombol start (history kosong → AUTO startBtnTap)
 6. 43<id>[{announce...}] / DIAM (tak apa-apa — tidak ada timeout)
 7. pemain start (atau auto) → 420[...{action:"SaveHistory",accountToken,serverId,...}]
 8. 43<id>[{"ret":0,"data":"{errorCode:0,todayLoginCount:N}"}]
    → [loginToken injeksi BILA server kirim] → clientStartGame(serverItem.url)
 9. socket GAME: verify XXTEA (Bagian 58) → enterGame → ACK
10. 420[...{action:"SaveUserEnterInfo",...}] → 43<id>[...] → loginClient.destroy()
    (socket login selesai; game lanjut di socket game)

JALUR ORIGIN: loginGame (step 3–4 diganti: loginGame dulu → GetServerList
memakai userId/channelCode dari ACK-nya → selectServer WINDOW MANUAL, bukan auto).
```

---

## 11. PETA KONSEKUENSI UTK SERVER LOKAL (dari bedah — bukan usulan struktur)

| # | Fakta bedah | Konsekuensi WAJIB (parser) | Konsekuensi BEBAS (pilihan owner) |
|---|---|---|---|
| 1 | `0===ret`, data=string JSON, compress opsional | bentuk envelope tak bisa dilanggar | isi `data` sepenuhnya milik kita |
| 2 | dispatcher baca `language` di SEMUA ACK | kunci `language` = top-level opsional | kirim/tidak — kendali bahasa di tangan owner |
| 3 | `serverList[0]` = prioritas auto | urutan array bermakna | jumlah entri + urutan bebas |
| 4 | history[0] dicocokkan loose `==` | serverId STRING konsisten = aman | history `[]`/isi = UX masuk instan vs resume |
| 5 | `text[lang]||""` tanpa fallback | kunci bahasa wajib ada per entri | `in` TAMPIL walau SaveLanguage 111 |
| 6 | SaveLanguage: kedua cabang ganti bahasa; pesan tak tampil | errorCode 0/111 dua-duanya sah | teks errorMessage bebas (tak terlihat) |
| 7 | ACK SaveHistory = pintu INJEKSI loginToken | field opsional — absen = aman | kirim/tidak (asli tidak pernah kirim) |
| 8 | todayLoginCount hanya analytics 4/6 | angka apa pun aman | nilai bebas owner |
| 9 | login path TANPA timeout | DIAM = client diam, TANPA crash | jawab atau tidak — keduanya aman |
| 10 | SaveHistory onErr=onOk | ACK gagal pun game tetap mulai | tidak perlu logika retry |
| 11 | enterGame→SaveUserEnterInfo→destroy | harus melayani SaveUserEnterInfo | isi ACK tak dibaca → `{errorCode:0}` cukup |
| 12 | bridge `serverList`/`replaceServerName` bisa membuang/menamai ulang entri | serverList.json tidak selalu tampil mentah | kendali nama/whitelist bisa juga dari sisi native (di luar scope folder login) |
| 13 | 6 event TOTAL, tidak ada ke-7 | event lain tak perlu dijawab | log verbatim + NO ACK (disiplin v19) |

---

## 12. YANG TIDAK DITEMUKAN DI main.min.js (jujur — dicari, nol hasil)

1. Tidak ada event login ke-7 (semua `sendToServer` login lewat 6 fungsi §4).
2. Tidak ada ping/pong manual — milik engine.io (25 dtk, Bagian 58).
3. Tidak ada timeout/retry khusus jalur login (beda dgn jalur game).
4. Tidak ada dekompresi REQUEST (compress hanya untuk data MASUK).
5. Tidak ada validasi `errorCode` GetServerList/loginGame di client.
6. Tidak ada pemakaian field `openDate/merge/mergeTo/urlInner/chatInner`
   (mirror saja — konsisten dgn Bagian 58/61).
7. Tidak ada penyimpanan history di sisi client (history = murni server-side
   + localStorage `lastServerID` untuk jalur origin/manual).

---

## 13. STATUS

Bedah TOTAL selesai: 1 kelas + 1 dispatcher + 1 sumber URL + 6 event + rantai
pemilih server + pintu game + 9 bridge + state + wire + 7 nol-hasil.
**Struktur folder TIDAK diusulkan di dokumen ini** — usulan berikutnya WAJIB
merujuk baris atlas ini (offset verbatim), tanpa satu pun perilaku yang tidak
ada di atlas. Plan only — nol baris kode di APK sampai owner oke.
