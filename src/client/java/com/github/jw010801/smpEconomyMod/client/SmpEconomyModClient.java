package com.github.jw010801.smpeconomymod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.client.hud.EconomyHud;
import com.github.jw010801.smpeconomymod.client.network.ClientNetworkHandler;

public class SmpEconomyModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SmpEconomyMod.LOGGER.info("SMP Economy & Territory 클라이언트를 초기화합니다...");
        
        // HUD 렌더링 등록
        HudRenderCallback.EVENT.register(new EconomyHud());
        
        // 클라이언트 네트워크 핸들러 초기화
        ClientNetworkHandler.init();
        
        SmpEconomyMod.LOGGER.info("SMP Economy & Territory 클라이언트 초기화 완료!");
    }
}
