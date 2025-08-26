package com.github.jw010801.smpeconomymod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.client.data.ClientPlayerData;
import com.github.jw010801.smpeconomymod.network.NetworkConstants;

import java.math.BigDecimal;

public class ClientNetworkHandler {
    
    public static void init() {
        SmpEconomyMod.LOGGER.info("클라이언트 네트워크 핸들러를 초기화합니다...");
        
        // 서버에서 오는 패킷들 처리
        registerIncomingPackets();
        
        SmpEconomyMod.LOGGER.info("클라이언트 네트워크 핸들러 초기화 완료");
    }
    
    private static void registerIncomingPackets() {
        // 플레이어 데이터 전체 동기화 (로그인시)
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.PLAYER_DATA_SYNC, (client, handler, buf, responseSender) -> {
            // 패킷 데이터 읽기
            BigDecimal balance = new BigDecimal(buf.readString());
            BigDecimal dailyEarnings = new BigDecimal(buf.readString());
            
            int miningLevel = buf.readInt();
            long miningExp = buf.readLong();
            int farmingLevel = buf.readInt();
            long farmingExp = buf.readLong();
            int fishingLevel = buf.readInt();
            long fishingExp = buf.readLong();
            int lumberjackLevel = buf.readInt();
            long lumberjackExp = buf.readLong();
            
            // 메인 스레드에서 실행
            client.execute(() -> {
                ClientPlayerData playerData = ClientPlayerData.getInstance();
                playerData.updateEconomyData(balance, dailyEarnings);
                playerData.updateSkillData("mining", miningLevel, miningExp);
                playerData.updateSkillData("farming", farmingLevel, farmingExp);
                playerData.updateSkillData("fishing", fishingLevel, fishingExp);
                playerData.updateSkillData("lumberjack", lumberjackLevel, lumberjackExp);
                
                SmpEconomyMod.LOGGER.debug("플레이어 데이터 동기화 완료 - 잔액: {}", balance);
            });
        });
        
        // 경제 정보 업데이트
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.ECONOMY_UPDATE, (client, handler, buf, responseSender) -> {
            BigDecimal newBalance = new BigDecimal(buf.readString());
            BigDecimal dailyEarnings = new BigDecimal(buf.readString());
            
            client.execute(() -> {
                ClientPlayerData.getInstance().updateEconomyData(newBalance, dailyEarnings);
            });
        });
        
        // 스킬 업데이트
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.SKILL_UPDATE, (client, handler, buf, responseSender) -> {
            String skillType = buf.readString();
            int level = buf.readInt();
            long experience = buf.readLong();
            
            client.execute(() -> {
                ClientPlayerData.getInstance().updateSkillData(skillType, level, experience);
            });
        });
        
        // 영토 정보 업데이트
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.TERRITORY_UPDATE, (client, handler, buf, responseSender) -> {
            boolean hasClaim = buf.readBoolean();
            String ownerName = null;
            String claimName = null;
            
            if (hasClaim) {
                ownerName = buf.readString();
                claimName = buf.readString();
            }
            
            String finalOwnerName = ownerName;
            String finalClaimName = claimName;
            
            client.execute(() -> {
                ClientPlayerData.getInstance().updateTerritoryInfo(finalOwnerName, finalClaimName);
            });
        });
        
        // 알림 메시지
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.NOTIFICATION, (client, handler, buf, responseSender) -> {
            String message = buf.readString();
            long duration = buf.readLong();
            
            client.execute(() -> {
                ClientPlayerData.getInstance().addNotification(message, duration);
            });
        });
    }
    
    /**
     * 서버에게 플레이어 데이터 요청
     */
    public static void requestPlayerData() {
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(NetworkConstants.REQUEST_PLAYER_DATA, buf);
        
        SmpEconomyMod.LOGGER.debug("서버에 플레이어 데이터 요청");
    }
    
    /**
     * 경제 명령어 실행 요청 (송금, 잔액 조회 등)
     */
    public static void sendEconomyCommand(String command, String... args) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(command);
        buf.writeInt(args.length);
        for (String arg : args) {
            buf.writeString(arg);
        }
        
        ClientPlayNetworking.send(NetworkConstants.ECONOMY_COMMAND, buf);
        
        SmpEconomyMod.LOGGER.debug("경제 명령어 전송: {} {}", command, String.join(" ", args));
    }
    
    /**
     * 서버에 채팅 메시지로 송금 요청
     */
    public static void requestMoneyTransfer(String targetPlayer, BigDecimal amount) {
        sendEconomyCommand("pay", targetPlayer, amount.toString());
    }
    
    /**
     * 서버에 잔액 조회 요청
     */
    public static void requestBalanceCheck() {
        sendEconomyCommand("balance");
    }
    
    /**
     * 서버에 다른 플레이어 잔액 조회 요청 (관리자 권한 필요)
     */
    public static void requestBalanceCheck(String targetPlayer) {
        sendEconomyCommand("balance", targetPlayer);
    }
}
