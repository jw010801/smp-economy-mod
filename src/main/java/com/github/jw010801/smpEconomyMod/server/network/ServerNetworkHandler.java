package com.github.jw010801.smpeconomymod.server.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.network.NetworkConstants;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerNetworkHandler {
    
    // 플레이어별 데이터 동기화 상태 추적
    private static final Map<UUID, Long> lastSyncTime = new ConcurrentHashMap<>();
    private static final long SYNC_INTERVAL = 5000; // 5초 간격
    
    public static void init() {
        SmpEconomyMod.LOGGER.info("서버 네트워크 핸들러를 초기화합니다...");
        
        // 클라이언트 연결 이벤트 처리
        registerConnectionEvents();
        
        // 클라이언트에서 오는 패킷들 처리
        registerIncomingPackets();
        
        SmpEconomyMod.LOGGER.info("서버 네트워크 핸들러 초기화 완료");
    }
    
    private static void registerConnectionEvents() {
        // 플레이어 접속시 데이터 동기화
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // 접속 후 1초 뒤에 데이터 동기화 (클라이언트 초기화 대기)
            server.execute(() -> {
                try {
                    Thread.sleep(1000);
                    syncPlayerDataToClient(player);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            SmpEconomyMod.LOGGER.info("플레이어 {} 접속 - 데이터 동기화 예약", player.getName().getString());
        });
        
        // 플레이어 퇴장시 정리
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerUuid = handler.getPlayer().getUuid();
            lastSyncTime.remove(playerUuid);
            
            SmpEconomyMod.LOGGER.debug("플레이어 {} 퇴장 - 동기화 데이터 정리", playerUuid);
        });
    }
    
    private static void registerIncomingPackets() {
        // 플레이어 데이터 요청
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.REQUEST_PLAYER_DATA, 
            (server, player, handler, buf, responseSender) -> 
                server.execute(() -> syncPlayerDataToClient(player)));
        
        // 경제 명령어 처리
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.ECONOMY_COMMAND,
            (server, player, handler, buf, responseSender) -> {
                String command = buf.readString();
                int argsLength = buf.readInt();
                String[] args = new String[argsLength];
                for (int i = 0; i < argsLength; i++) {
                    args[i] = buf.readString();
                }
                
                server.execute(() -> handleEconomyCommand(player, command, args));
        });
    }
    
    /**
     * 플레이어 데이터를 클라이언트에 동기화
     */
    public static void syncPlayerDataToClient(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // 동기화 간격 체크 (너무 빈번한 동기화 방지)
        long currentTime = System.currentTimeMillis();
        Long lastSync = lastSyncTime.get(playerUuid);
        if (lastSync != null && (currentTime - lastSync) < SYNC_INTERVAL) {
            return;
        }
        
        try {
            // 경제 데이터 조회
            BigDecimal balance = SmpEconomyMod.economyManager.getBalance(playerUuid).get();
            BigDecimal dailyEarnings = BigDecimal.ZERO; // TODO: 일일 수익 계산
            
            // 스킬 데이터 조회 (TODO: 실제 스킬 시스템 구현시 수정)
            PlayerSkillData skillData = getPlayerSkillData(playerUuid);
            
            PacketByteBuf buf = PacketByteBufs.create();
            
            // 경제 정보
            buf.writeString(balance.toString());
            buf.writeString(dailyEarnings.toString());
            
            // 스킬 정보
            buf.writeInt(skillData.miningLevel);
            buf.writeLong(skillData.miningExp);
            buf.writeInt(skillData.farmingLevel);
            buf.writeLong(skillData.farmingExp);
            buf.writeInt(skillData.fishingLevel);
            buf.writeLong(skillData.fishingExp);
            buf.writeInt(skillData.lumberjackLevel);
            buf.writeLong(skillData.lumberjackExp);
            
            ServerPlayNetworking.send(player, NetworkConstants.PLAYER_DATA_SYNC, buf);
            
            lastSyncTime.put(playerUuid, currentTime);
            
            SmpEconomyMod.LOGGER.debug("플레이어 {} 데이터 동기화 완료", player.getName().getString());
            
        } catch (Exception e) {
            SmpEconomyMod.LOGGER.error("플레이어 {} 데이터 동기화 실패: {}", player.getName().getString(), e.getMessage());
        }
    }
    
    /**
     * 경제 정보만 업데이트
     */
    public static void syncEconomyDataToClient(ServerPlayerEntity player, BigDecimal newBalance, BigDecimal dailyEarnings) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(newBalance.toString());
        buf.writeString(dailyEarnings.toString());
        
        ServerPlayNetworking.send(player, NetworkConstants.ECONOMY_UPDATE, buf);
        
        SmpEconomyMod.LOGGER.debug("플레이어 {} 경제 데이터 업데이트: {}", player.getName().getString(), newBalance);
    }
    
    /**
     * 스킬 정보 업데이트
     */
    public static void syncSkillDataToClient(ServerPlayerEntity player, String skillType, int level, long experience) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(skillType);
        buf.writeInt(level);
        buf.writeLong(experience);
        
        ServerPlayNetworking.send(player, NetworkConstants.SKILL_UPDATE, buf);
        
        SmpEconomyMod.LOGGER.debug("플레이어 {} 스킬 업데이트: {} Lv.{}", player.getName().getString(), skillType, level);
    }
    
    /**
     * 영토 정보 업데이트
     */
    public static void syncTerritoryDataToClient(ServerPlayerEntity player, String ownerName, String claimName) {
        PacketByteBuf buf = PacketByteBufs.create();
        
        boolean hasClaim = ownerName != null && claimName != null;
        buf.writeBoolean(hasClaim);
        
        if (hasClaim) {
            buf.writeString(ownerName);
            buf.writeString(claimName);
        }
        
        ServerPlayNetworking.send(player, NetworkConstants.TERRITORY_UPDATE, buf);
    }
    
    /**
     * 알림 메시지 전송
     */
    public static void sendNotificationToClient(ServerPlayerEntity player, String message, long durationMs) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(message);
        buf.writeLong(durationMs);
        
        ServerPlayNetworking.send(player, NetworkConstants.NOTIFICATION, buf);
    }
    
    private static void handleEconomyCommand(ServerPlayerEntity player, String command, String[] args) {
        UUID playerUuid = player.getUuid();
        
        switch (command) {
            case "balance" -> {
                if (args.length == 0) {
                    // 자신의 잔액 조회
                    SmpEconomyMod.economyManager.getBalance(playerUuid).thenAccept(balance -> 
                        player.sendMessage(Text.of("§6💰 현재 잔액: §e" + formatMoney(balance) + "골드")));
                } else {
                    // 다른 플레이어 잔액 조회 (관리자만)
                    if (player.hasPermissionLevel(2)) {
                        String targetName = args[0];
                        // TODO: 플레이어 이름으로 UUID 찾기 구현
                        player.sendMessage(Text.of("§c해당 기능은 아직 구현되지 않았습니다."));
                    } else {
                        player.sendMessage(Text.of("§c권한이 없습니다."));
                    }
                }
            }
            
            case "pay" -> {
                if (args.length >= 2) {
                    String targetName = args[0];
                    try {
                        BigDecimal amount = new BigDecimal(args[1]);
                        
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            player.sendMessage(Text.of("§c송금 금액은 0보다 커야 합니다."));
                            return;
                        }
                        
                        // TODO: 플레이어 이름으로 UUID 찾기 및 송금 처리
                        player.sendMessage(Text.of("§c송금 기능은 아직 구현되지 않았습니다."));
                        
                    } catch (NumberFormatException e) {
                        player.sendMessage(Text.of("§c잘못된 금액입니다: " + args[1]));
                    }
                } else {
                    player.sendMessage(Text.of("§c사용법: /pay <플레이어> <금액>"));
                }
            }
            
            default -> player.sendMessage(Text.of("§c알 수 없는 명령어: " + command));
        }
    }
    
    private static PlayerSkillData getPlayerSkillData(UUID playerUuid) {
        // TODO: 실제 스킬 시스템 구현시 데이터베이스에서 조회
        // 현재는 더미 데이터 반환
        return new PlayerSkillData();
    }
    
    private static String formatMoney(BigDecimal amount) {
        return String.format("%,d", amount.longValue());
    }
    
    /**
     * 플레이어 스킬 데이터 (임시 클래스)
     */
    private static class PlayerSkillData {
        int miningLevel = 1;
        long miningExp = 0;
        int farmingLevel = 1;
        long farmingExp = 0;
        int fishingLevel = 1;
        long fishingExp = 0;
        int lumberjackLevel = 1;
        long lumberjackExp = 0;
    }
}
