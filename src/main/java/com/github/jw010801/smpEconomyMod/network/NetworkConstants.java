package com.github.jw010801.smpeconomymod.network;

import net.minecraft.util.Identifier;
import com.github.jw010801.smpeconomymod.SmpEconomyMod;

/**
 * 서버-클라이언트 간 공통 네트워크 패킷 ID 정의
 */
public class NetworkConstants {
    
    // 서버 → 클라이언트 패킷들
    public static final Identifier PLAYER_DATA_SYNC = new Identifier(SmpEconomyMod.MOD_ID, "player_data_sync");
    public static final Identifier ECONOMY_UPDATE = new Identifier(SmpEconomyMod.MOD_ID, "economy_update");
    public static final Identifier SKILL_UPDATE = new Identifier(SmpEconomyMod.MOD_ID, "skill_update");
    public static final Identifier TERRITORY_UPDATE = new Identifier(SmpEconomyMod.MOD_ID, "territory_update");
    public static final Identifier NOTIFICATION = new Identifier(SmpEconomyMod.MOD_ID, "notification");
    
    // 클라이언트 → 서버 패킷들
    public static final Identifier REQUEST_PLAYER_DATA = new Identifier(SmpEconomyMod.MOD_ID, "request_player_data");
    public static final Identifier ECONOMY_COMMAND = new Identifier(SmpEconomyMod.MOD_ID, "economy_command");
}
