package com.github.jw010801.smpeconomymod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jw010801.smpeconomymod.database.DatabaseManager;
import com.github.jw010801.smpeconomymod.economy.EconomyManager;
import com.github.jw010801.smpeconomymod.territory.TerritoryManager;

public class SmpEconomyMod implements ModInitializer {
    
    public static final String MOD_ID = "smp-economy-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static DatabaseManager databaseManager;
    public static EconomyManager economyManager;
    public static TerritoryManager territoryManager;

    @Override
    public void onInitialize() {
        LOGGER.info("SMP Economy & Territory 모드를 초기화합니다...");
        
        // 데이터베이스 매니저 초기화
        databaseManager = new DatabaseManager();
        
        // 경제 시스템 초기화
        economyManager = new EconomyManager(databaseManager);
        
        // 영토 시스템 초기화
        territoryManager = new TerritoryManager(databaseManager);
        
        LOGGER.info("SMP Economy & Territory 모드 초기화 완료!");
    }
}
