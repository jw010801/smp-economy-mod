package com.github.jw010801.smpeconomymod.client.data;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 클라이언트에서 플레이어의 경제/스킬 데이터를 관리하는 싱글톤 클래스
 * 서버에서 패킷을 통해 전송받은 데이터를 저장하고 HUD에서 표시
 */
public class ClientPlayerData {
    
    private static ClientPlayerData instance;
    
    // 경제 정보
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal dailyEarnings = BigDecimal.ZERO;
    
    // 스킬 레벨 정보
    private int miningLevel = 1;
    private int farmingLevel = 1;
    private int fishingLevel = 1;
    private int lumberjackLevel = 1;
    
    // 스킬 경험치
    private long miningExp = 0;
    private long farmingExp = 0;
    private long fishingExp = 0;
    private long lumberjackExp = 0;
    
    // 영토 정보
    private String currentClaimOwner = null;
    private String currentClaimName = null;
    
    // 알림 메시지 큐
    private final Map<String, Long> notifications = new ConcurrentHashMap<>();
    
    private ClientPlayerData() {
        // 싱글톤 생성자
    }
    
    public static ClientPlayerData getInstance() {
        if (instance == null) {
            instance = new ClientPlayerData();
        }
        return instance;
    }
    
    /**
     * 서버에서 받은 경제 데이터로 업데이트
     */
    public void updateEconomyData(BigDecimal newBalance, BigDecimal dailyEarnings) {
        BigDecimal oldBalance = this.balance;
        this.balance = newBalance;
        this.dailyEarnings = dailyEarnings;
        
        // 잔액 변화가 있으면 알림 표시
        if (oldBalance.compareTo(newBalance) != 0) {
            BigDecimal change = newBalance.subtract(oldBalance);
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                addNotification("💰 +" + formatMoney(change), 3000);
            } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                addNotification("💸 " + formatMoney(change.abs()), 3000);
            }
        }
    }
    
    /**
     * 서버에서 받은 스킬 데이터로 업데이트
     */
    public void updateSkillData(String skillType, int level, long experience) {
        int oldLevel = switch (skillType) {
            case "mining" -> {
                miningExp = experience;
                yield miningLevel;
            }
            case "farming" -> {
                farmingExp = experience;
                yield farmingLevel;
            }
            case "fishing" -> {
                fishingExp = experience;
                yield fishingLevel;
            }
            case "lumberjack" -> {
                lumberjackExp = experience;
                yield lumberjackLevel;
            }
            default -> 0;
        };
        
        // 레벨 업데이트
        switch (skillType) {
            case "mining" -> miningLevel = level;
            case "farming" -> farmingLevel = level;
            case "fishing" -> fishingLevel = level;
            case "lumberjack" -> lumberjackLevel = level;
        }
        
        // 레벨업 알림
        if (level > oldLevel) {
            String emoji = switch (skillType) {
                case "mining" -> "⚒️";
                case "farming" -> "🌾";
                case "fishing" -> "🎣";
                case "lumberjack" -> "🪓";
                default -> "📈";
            };
            addNotification(emoji + " 레벨 " + level + "!", 5000);
        }
    }
    
    /**
     * 현재 위치의 영토 정보 업데이트
     */
    public void updateTerritoryInfo(String ownerName, String claimName) {
        this.currentClaimOwner = ownerName;
        this.currentClaimName = claimName;
    }
    
    /**
     * 알림 메시지 추가
     */
    public void addNotification(String message, long durationMs) {
        notifications.put(message, System.currentTimeMillis() + durationMs);
    }
    
    /**
     * 만료된 알림 메시지 정리
     */
    public void cleanupExpiredNotifications() {
        long currentTime = System.currentTimeMillis();
        notifications.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
    
    /**
     * 현재 활성 알림 메시지들 반환
     */
    public Map<String, Long> getActiveNotifications() {
        cleanupExpiredNotifications();
        return new ConcurrentHashMap<>(notifications);
    }
    
    /**
     * 클라이언트 데이터 초기화 (로그아웃시)
     */
    public void reset() {
        balance = BigDecimal.ZERO;
        dailyEarnings = BigDecimal.ZERO;
        miningLevel = 1;
        farmingLevel = 1;
        fishingLevel = 1;
        lumberjackLevel = 1;
        miningExp = 0;
        farmingExp = 0;
        fishingExp = 0;
        lumberjackExp = 0;
        currentClaimOwner = null;
        currentClaimName = null;
        notifications.clear();
    }
    
    // Getters
    public BigDecimal getBalance() {
        return balance;
    }
    
    public BigDecimal getDailyEarnings() {
        return dailyEarnings;
    }
    
    public int getMiningLevel() {
        return miningLevel;
    }
    
    public int getFarmingLevel() {
        return farmingLevel;
    }
    
    public int getFishingLevel() {
        return fishingLevel;
    }
    
    public int getLumberjackLevel() {
        return lumberjackLevel;
    }
    
    public long getMiningExp() {
        return miningExp;
    }
    
    public long getFarmingExp() {
        return farmingExp;
    }
    
    public long getFishingExp() {
        return fishingExp;
    }
    
    public long getLumberjackExp() {
        return lumberjackExp;
    }
    
    public String getCurrentClaimOwner() {
        return currentClaimOwner;
    }
    
    public String getCurrentClaimName() {
        return currentClaimName;
    }
    
    /**
     * 스킬의 다음 레벨까지 필요한 경험치 계산
     */
    public long getExpToNextLevel(String skillType, int currentLevel) {
        // 간단한 지수 증가 공식: level^2 * 100
        return (long) Math.pow(currentLevel + 1, 2) * 100;
    }
    
    /**
     * 현재 레벨에서의 진행률 계산 (0.0 ~ 1.0)
     */
    public double getSkillProgress(String skillType) {
        long currentExp = switch (skillType) {
            case "mining" -> miningExp;
            case "farming" -> farmingExp;
            case "fishing" -> fishingExp;
            case "lumberjack" -> lumberjackExp;
            default -> 0;
        };
        
        int level = switch (skillType) {
            case "mining" -> miningLevel;
            case "farming" -> farmingLevel;
            case "fishing" -> fishingLevel;
            case "lumberjack" -> lumberjackLevel;
            default -> 1;
        };
        
        long currentLevelExp = (long) Math.pow(level, 2) * 100;
        long nextLevelExp = getExpToNextLevel(skillType, level);
        
        if (currentExp <= currentLevelExp) return 0.0;
        
        long expInCurrentLevel = currentExp - currentLevelExp;
        long expNeededForNextLevel = nextLevelExp - currentLevelExp;
        
        return Math.min(1.0, (double) expInCurrentLevel / expNeededForNextLevel);
    }
    
    private String formatMoney(BigDecimal amount) {
        return String.format("%,d", amount.longValue());
    }
}
