package com.db.local.server;

import java.util.UUID;

/**
 * DATA PEMAIN GAME LOKAL — milik server ini, disimpan dragonball.db (tabel
 * kv, kunci "game_player:<akun>") — tahan reboot, satu mekanisme yang sama
 * di semua engine (Android/JDBC/memori). Bagian handcraft mesin game lokal.
 *
 * SUMBER KONTRAK (keputusan berulang owner: online cuma jejak arah, bukan
 * sumber nilai): struktur model dibaca dari KODE KLIEN SENDIRI
 * (main.min_777039fc.js — UserDataParser.saveUserData + penerima aksi
 * terkait). NILAI dibuat server lokal sendiri:
 *   - _id sesi game  = UUID server lokal (stabil per pemain, disimpan DB)
 *   - nickName       = akun SDK lokal (guest atau email) dari dragonball.db
 *   - currency       = "IDR" — negara perangkat pemain (SDK init lokal
 *                      menjawab countryCode ID); kode tampilan mata uang
 *   - level/ekonomi  = nol / minimal — keadaan dunia lokal mula-mula
 *   - waktu          = server lokal sendiri (serverTime) + offset zona
 *                      waktu perangkat (server0Time)
 *
 * Field yang WAJIB ada (dibaca klien tanpa guard) — semua dipenuhi:
 *   currency · user{_id,_pwd,_nickName,_headImage,_lastLoginTime,
 *   _createTime,_bulletinVersions,_oriServerId} · hangup{7 field} ·
 *   summon{6 field} · totalProps._items (id 104 = PLAYERLEVELID klien) ·
 *   backpackLevel · superSkill[] · heros._heros{} · curMainTask{} ·
 *   scheduleInfo{…} · dragonEquiped{} · broadcastRecord[]
 * Field ber-guard (imprint/dungeon/checkin/userGuild/…) dikosongkan —
 * klien memeriksanya sebelum membaca (bukti: kode klien).
 */
public final class GamePlayer {

    /** ID item "level pemain" menurut klien: PLAYERLEVELID=104 (konstanta klien). */
    static final int PLAYER_LEVEL_ID = 104;

    private GamePlayer() {
    }

    // ---------------- penyimpanan (dragonball.db · kv) ----------------

    private static String key(String sdkUserId) {
        return "game_player:" + (sdkUserId == null ? "" : sdkUserId);
    }

    /** Pastikan pemain ada — bila baru, buat model mula-mula. Return model. */
    public static synchronized String ensure(String sdkUserId) {
        String k = key(sdkUserId);
        String model = Db.i().kv(k);
        if (model != null && model.length() > 0) {
            return model;
        }
        model = buildModel(sdkUserId, System.currentTimeMillis());
        Db.i().kvPut(k, model);
        return model;
    }

    /** Simpan model (setelah aksi yang mengubah keadaan pemain). */
    public static synchronized void save(String sdkUserId, String modelJson) {
        Db.i().kvPut(key(sdkUserId), modelJson == null ? "" : modelJson);
    }

    // ---------------- model ----------------

    /**
     * Model pemain mula-mula — disimpan sekali; _id/_createTime/nickName
     * stabil antar masuk. Nilai = keputusan server lokal (lihat header).
     */
    private static String buildModel(String sdkUserId, long now) {
        String gameUuid = UUID.randomUUID().toString();
        String nick = (sdkUserId == null || sdkUserId.length() == 0)
                ? "pemain" : sdkUserId;
        StringBuilder b = new StringBuilder(1024);
        b.append("{");
        b.append("\"currency\":\"IDR\",");
        b.append("\"user\":{");
        b.append("\"_id\":\"").append(Json.esc(gameUuid)).append("\",");
        b.append("\"_pwd\":\"\",");
        b.append("\"_nickName\":\"").append(Json.esc(nick)).append("\",");
        b.append("\"_headImage\":\"\",");
        b.append("\"_lastLoginTime\":").append(now).append(",");
        b.append("\"_createTime\":").append(now).append(",");
        b.append("\"_bulletinVersions\":[],");
        b.append("\"_oriServerId\":1");
        b.append("},");
        b.append("\"hangup\":{");
        b.append("\"_curLess\":0,");
        b.append("\"_maxPassLesson\":0,");
        b.append("\"_haveGotChapterReward\":{},");
        b.append("\"_maxPassChapter\":0,");
        b.append("\"_clickGlobalWarBuffTag\":0,");
        b.append("\"_buyFund\":0,");
        b.append("\"_haveGotFundReward\":{}");
        b.append("},");
        b.append("\"summon\":{");
        b.append("\"_energy\":0,");
        b.append("\"_wishList\":[],");
        b.append("\"_wishVersion\":0,");
        b.append("\"_canCommonFreeTime\":0,");
        b.append("\"_canSuperFreeTime\":0,");
        b.append("\"_summonTimes\":{}");
        b.append("},");
        b.append("\"totalProps\":{");
        b.append("\"_items\":{\"").append(PLAYER_LEVEL_ID)
                .append("\":{\"_id\":").append(PLAYER_LEVEL_ID)
                .append(",\"_num\":1}}");
        b.append("},");
        b.append("\"backpackLevel\":0,");
        b.append("\"imprint\":{\"_items\":{}},");
        b.append("\"superSkill\":[],");
        b.append("\"heros\":{\"_heros\":{}},");
        b.append("\"curMainTask\":{},");
        b.append("\"scheduleInfo\":{");
        b.append("\"_marketDiamondRefreshCount\":0,");
        b.append("\"_vipMarketDiamondRefreshCount\":0,");
        b.append("\"_arenaAttackTimes\":0,");
        b.append("\"_arenaBuyTimesCount\":0,");
        b.append("\"_snakeResetTimes\":0,");
        b.append("\"_snakeSweepCount\":0,");
        b.append("\"_cellGameHaveGotReward\":{},");
        b.append("\"_cellGameHaveTimes\":0,");
        b.append("\"_cellgameHaveSetHero\":0,");
        b.append("\"_strongEnemyTimes\":0,");
        b.append("\"_strongEnemyBuyCount\":0,");
        b.append("\"_mergeBossBuyCount\":0,");
        b.append("\"_dungeonTimes\":{},");
        b.append("\"_dungeonBuyTimesCount\":{},");
        b.append("\"_karinBattleTimes\":0,");
        b.append("\"_karinBuyBattleTimesCount\":0,");
        b.append("\"_karinBuyFeetCount\":0,");
        b.append("\"_entrustResetTimes\":0,");
        b.append("\"_dragonExchangeSSPoolId\":0,");
        b.append("\"_dragonExchangeSSSPoolId\":0,");
        b.append("\"_teamDugeonUsedRobots\":0,");
        b.append("\"_timeTrialBuyTimesCount\":0,");
        b.append("\"_monthCardHaveGotReward\":{},");
        b.append("\"_goldBuyCount\":0,");
        b.append("\"_likeRank\":0,");
        b.append("\"_mahaAttackTimes\":0,");
        b.append("\"_mahaBuyTimesCount\":0,");
        b.append("\"_mineResetTimes\":0,");
        b.append("\"_mineBuyResetTimesCount\":0,");
        b.append("\"_mineBuyStepCount\":0,");
        b.append("\"_guildBossTimes\":0,");
        b.append("\"_guildBossTimesBuyCount\":0,");
        b.append("\"_treasureTimes\":0,");
        b.append("\"_guildCheckInType\":0,");
        b.append("\"_templeBuyCount\":0,");
        b.append("\"_trainingBuyCount\":0,");
        b.append("\"_bossCptTimes\":0,");
        b.append("\"_bossCptBuyCount\":0,");
        b.append("\"_ballWarBuyCount\":0,");
        b.append("\"_expeditionEvents\":{},");
        b.append("\"_clickExpedition\":0,");
        b.append("\"_expeditionSpeedUpCost\":0");
        b.append("},");
        b.append("\"dragonEquiped\":{},");
        b.append("\"summonLog\":{},");
        b.append("\"broadcastRecord\":[]");
        b.append("}");
        return b.toString();
    }

    // ---------------- perubahan kecil model ----------------

    /** Perbarui _lastLoginTime di model (dipanggil saat enterGame). */
    public static String withFreshLoginTime(String modelJson, long now) {
        String marker = "\"_lastLoginTime\":";
        int i = modelJson.indexOf(marker);
        if (i < 0) {
            return modelJson;
        }
        int vStart = i + marker.length();
        int vEnd = vStart;
        while (vEnd < modelJson.length()
                && (Character.isDigit(modelJson.charAt(vEnd)))) {
            vEnd++;
        }
        return modelJson.substring(0, vStart) + now
                + modelJson.substring(vEnd);
    }

    /** Ambil _id sesi game dari model (identitas pemain di dunia lokal). */
    public static String gameId(String modelJson) {
        int i = modelJson.indexOf("\"_id\":\"");
        if (i < 0) {
            return "";
        }
        int s = i + 7;
        int e = modelJson.indexOf('"', s);
        return e < 0 ? "" : modelJson.substring(s, e);
    }
}
