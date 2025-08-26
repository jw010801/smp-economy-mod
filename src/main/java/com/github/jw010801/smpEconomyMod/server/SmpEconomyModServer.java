package com.github.jw010801.smpeconomymod.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.server.commands.EconomyCommands;
import com.github.jw010801.smpeconomymod.server.commands.TerritoryCommands;
import com.github.jw010801.smpeconomymod.server.network.ServerNetworkHandler;

public class SmpEconomyModServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        SmpEconomyMod.LOGGER.info("SMP Economy & Territory 서버를 초기화합니다...");
        
        // 서버 네트워크 핸들러 초기화
        ServerNetworkHandler.init();
        
        // 명령어 등록
        EconomyCommands.register();
        TerritoryCommands.register();
        
        // 서버 이벤트 리스너 등록
        registerServerEvents();
        
        SmpEconomyMod.LOGGER.info("SMP Economy & Territory 서버 초기화 완료!");
    }
    
    private void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SmpEconomyMod.LOGGER.info("서버가 시작되었습니다. 데이터베이스 연결을 초기화합니다...");
            SmpEconomyMod.databaseManager.initialize();
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SmpEconomyMod.LOGGER.info("서버가 종료됩니다. 데이터베이스 연결을 정리합니다...");
            SmpEconomyMod.databaseManager.shutdown();
        });
    }
}
