package com.github.jw010801.smpeconomymod.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import com.github.jw010801.smpeconomymod.client.data.ClientPlayerData;

import java.math.BigDecimal;

public class EconomyHud implements HudRenderCallback {
    
    // HUD 위치 및 크기 설정
    private static final int HUD_X = 10;
    private static final int HUD_Y = 10;
    private static final int HUD_WIDTH = 200;
    private static final int HUD_HEIGHT = 80;
    private static final int PADDING = 8;
    
    // 색상 설정
    private static final int BACKGROUND_COLOR = 0x88000000;  // 반투명 검정
    private static final int BORDER_COLOR = 0xFF444444;      // 회색 테두리
    private static final int TEXT_COLOR = 0xFFFFFFFF;        // 흰색 텍스트
    private static final int GOLD_COLOR = 0xFFFFD700;        // 금색 (골드 표시용)
    
    // Fantasy UI 텍스처 (나중에 추가 예정)
    // private static final Identifier HUD_TEXTURE = new Identifier(SmpEconomyMod.MOD_ID, "textures/gui/hud_background.png");
    
    private final MinecraftClient client;
    
    public EconomyHud() {
        this.client = MinecraftClient.getInstance();
    }
    
    @Override
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        // 플레이어가 없거나 디버그 화면이 열려있으면 HUD 숨기기  
        if (client.player == null || client.options.debugEnabled) {
            return;
        }
        
        // 채팅창이 열려있을 때는 HUD를 약간 투명하게
        boolean chatOpen = client.currentScreen != null;
        float alpha = chatOpen ? 0.5f : 1.0f;
        
        renderHudBackground(drawContext, alpha);
        renderPlayerInfo(drawContext, alpha);
        renderEconomyInfo(drawContext, alpha);
        renderSkillInfo(drawContext, alpha);
    }
    
    private void renderHudBackground(DrawContext drawContext, float alpha) {
        // 배경 렌더링 (나중에 Fantasy UI 텍스처로 교체)
        int bgColor = (int)(alpha * 0x88) << 24 | (BACKGROUND_COLOR & 0xFFFFFF);
        int borderColor = (int)(alpha * 0xFF) << 24 | (BORDER_COLOR & 0xFFFFFF);
        
        // 메인 배경
        drawContext.fill(HUD_X, HUD_Y, HUD_X + HUD_WIDTH, HUD_Y + HUD_HEIGHT, bgColor);
        
        // 테두리
        drawContext.fill(HUD_X, HUD_Y, HUD_X + HUD_WIDTH, HUD_Y + 1, borderColor); // 상단
        drawContext.fill(HUD_X, HUD_Y + HUD_HEIGHT - 1, HUD_X + HUD_WIDTH, HUD_Y + HUD_HEIGHT, borderColor); // 하단
        drawContext.fill(HUD_X, HUD_Y, HUD_X + 1, HUD_Y + HUD_HEIGHT, borderColor); // 좌측
        drawContext.fill(HUD_X + HUD_WIDTH - 1, HUD_Y, HUD_X + HUD_WIDTH, HUD_Y + HUD_HEIGHT, borderColor); // 우측
    }
    
    private void renderPlayerInfo(DrawContext drawContext, float alpha) {
        TextRenderer textRenderer = client.textRenderer;
        int textColor = (int)(alpha * 0xFF) << 24 | (TEXT_COLOR & 0xFFFFFF);
        
        // 플레이어 이름 표시 (아이콘으로 최소화 예정) - NPE 방지
        if (client.player == null) return;
        
        String playerName = client.player.getName().getString();
        if (playerName.length() > 12) {
            playerName = playerName.substring(0, 12) + "...";
        }
        
        // 👤 아이콘 + 플레이어명
        Text playerText = Text.of("👤 " + playerName);
        drawContext.drawText(textRenderer, playerText, HUD_X + PADDING, HUD_Y + PADDING, textColor, true);
    }
    
    private void renderEconomyInfo(DrawContext drawContext, float alpha) {
        TextRenderer textRenderer = client.textRenderer;
        int goldColor = (int)(alpha * 0xFF) << 24 | (GOLD_COLOR & 0xFFFFFF);
        
        // 현재 잔액 표시 (아이콘으로 최소화)
        ClientPlayerData playerData = ClientPlayerData.getInstance();
        BigDecimal balance = playerData.getBalance();
        
        // 💰 아이콘 + 잔액 (천 단위 구분 쉼표)
        String formattedBalance = formatBalance(balance);
        Text balanceText = Text.of("💰 " + formattedBalance);
        
        int yPos = HUD_Y + PADDING + textRenderer.fontHeight + 2;
        drawContext.drawText(textRenderer, balanceText, HUD_X + PADDING, yPos, goldColor, true);
    }
    
    private void renderSkillInfo(DrawContext drawContext, float alpha) {
        TextRenderer textRenderer = client.textRenderer;
        int textColor = (int)(alpha * 0xFF) << 24 | (TEXT_COLOR & 0xFFFFFF);
        
        ClientPlayerData playerData = ClientPlayerData.getInstance();
        
        // 스킬 레벨들 (아이콘으로 최소화)
        int yOffset = HUD_Y + PADDING + (textRenderer.fontHeight + 2) * 2;
        
        // ⚒️ 채굴 레벨
        Text miningText = Text.of("⚒️ " + playerData.getMiningLevel());
        drawContext.drawText(textRenderer, miningText, HUD_X + PADDING, yOffset, textColor, true);
        
        // 🌾 농업 레벨 (옆에 표시)
        Text farmingText = Text.of("🌾 " + playerData.getFarmingLevel());
        drawContext.drawText(textRenderer, farmingText, HUD_X + PADDING + 80, yOffset, textColor, true);
    }
    
    private String formatBalance(BigDecimal balance) {
        if (balance == null) {
            return "0";
        }
        
        // 천 단위 구분 쉼표 추가
        long longValue = balance.longValue();
        return String.format("%,d", longValue);
    }
    
    /**
     * HUD 표시 토글 (F3+H 같은 기능으로 나중에 확장 가능)
     */
    private static boolean hudVisible = true;
    
    public static void toggleHudVisibility() {
        hudVisible = !hudVisible;
    }
    
    public static boolean isHudVisible() {
        return hudVisible;
    }
}
