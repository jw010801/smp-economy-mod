package com.github.jw010801.smpeconomymod.economy;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EconomyManager {
    
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService executor;
    
    // 캐시: 플레이어 UUID -> 잔액
    private final ConcurrentHashMap<UUID, BigDecimal> balanceCache = new ConcurrentHashMap<>();
    
    // 기본 설정값
    public static final BigDecimal DEFAULT_STARTING_BALANCE = new BigDecimal("100.00");
    public static final BigDecimal MINIMUM_BALANCE = new BigDecimal("0.00");
    
    public EconomyManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.executor = Executors.newScheduledThreadPool(2);
        
        // 5분마다 캐시 데이터를 데이터베이스에 동기화
        this.executor.scheduleAtFixedRate(this::syncCacheToDatabase, 5, 5, TimeUnit.MINUTES);
        
        SmpEconomyMod.LOGGER.info("EconomyManager 초기화됨");
    }
    
    /**
     * 플레이어의 잔액을 조회합니다. (캐시 우선)
     */
    public CompletableFuture<BigDecimal> getBalance(UUID playerUuid) {
        // 캐시에 있으면 바로 반환
        if (balanceCache.containsKey(playerUuid)) {
            return CompletableFuture.completedFuture(balanceCache.get(playerUuid));
        }
        
        // 캐시에 없으면 데이터베이스에서 조회
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal balance = getBalanceFromDatabase(playerUuid);
                balanceCache.put(playerUuid, balance);
                return balance;
            } catch (SQLException e) {
                SmpEconomyMod.LOGGER.error("플레이어 {} 잔액 조회 중 오류: {}", playerUuid, e.getMessage());
                return BigDecimal.ZERO;
            }
        }, executor);
    }
    
    private BigDecimal getBalanceFromDatabase(UUID playerUuid) throws SQLException {
        String query = "SELECT balance FROM balances WHERE player_uuid = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, playerUuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                } else {
                    // 새 플레이어인 경우 기본 잔액으로 계정 생성
                    createNewAccount(playerUuid);
                    return DEFAULT_STARTING_BALANCE;
                }
            }
        }
    }
    
    /**
     * 플레이어 잔액을 설정합니다.
     */
    public CompletableFuture<Boolean> setBalance(UUID playerUuid, BigDecimal amount, String reason) {
        if (amount.compareTo(MINIMUM_BALANCE) < 0) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal oldBalance = balanceCache.getOrDefault(playerUuid, getBalanceFromDatabase(playerUuid));
                
                // 캐시 업데이트
                balanceCache.put(playerUuid, amount);
                
                // 트랜잭션 로그 기록
                logTransaction(null, playerUuid, amount.subtract(oldBalance), TransactionType.ADMIN_SET, reason);
                
                return true;
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("플레이어 {} 잔액 설정 중 오류: {}", playerUuid, e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * 플레이어 잔액을 증가시킵니다.
     */
    public CompletableFuture<Boolean> addBalance(UUID playerUuid, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal currentBalance = balanceCache.computeIfAbsent(playerUuid, 
                    uuid -> {
                        try {
                            return getBalanceFromDatabase(uuid);
                        } catch (SQLException e) {
                            return DEFAULT_STARTING_BALANCE;
                        }
                    });
                
                BigDecimal newBalance = currentBalance.add(amount);
                balanceCache.put(playerUuid, newBalance);
                
                // 트랜잭션 로그 기록
                logTransaction(null, playerUuid, amount, TransactionType.EARN, reason);
                
                return true;
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("플레이어 {} 잔액 증가 중 오류: {}", playerUuid, e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * 플레이어 잔액을 감소시킵니다.
     */
    public CompletableFuture<Boolean> subtractBalance(UUID playerUuid, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal currentBalance = balanceCache.computeIfAbsent(playerUuid, 
                    uuid -> {
                        try {
                            return getBalanceFromDatabase(uuid);
                        } catch (SQLException e) {
                            return DEFAULT_STARTING_BALANCE;
                        }
                    });
                
                BigDecimal newBalance = currentBalance.subtract(amount);
                
                // 잔액 부족 확인
                if (newBalance.compareTo(MINIMUM_BALANCE) < 0) {
                    return false;
                }
                
                balanceCache.put(playerUuid, newBalance);
                
                // 트랜잭션 로그 기록
                logTransaction(playerUuid, null, amount, TransactionType.SPEND, reason);
                
                return true;
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("플레이어 {} 잔액 감소 중 오류: {}", playerUuid, e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * 플레이어 간 송금을 처리합니다.
     */
    public CompletableFuture<Boolean> transferMoney(UUID fromPlayer, UUID toPlayer, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || fromPlayer.equals(toPlayer)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 송금자 잔액 확인
                BigDecimal fromBalance = balanceCache.computeIfAbsent(fromPlayer, 
                    uuid -> {
                        try {
                            return getBalanceFromDatabase(uuid);
                        } catch (SQLException e) {
                            return BigDecimal.ZERO;
                        }
                    });
                
                if (fromBalance.compareTo(amount) < 0) {
                    return false; // 잔액 부족
                }
                
                // 수신자 잔액 조회
                BigDecimal toBalance = balanceCache.computeIfAbsent(toPlayer, 
                    uuid -> {
                        try {
                            return getBalanceFromDatabase(uuid);
                        } catch (SQLException e) {
                            return DEFAULT_STARTING_BALANCE;
                        }
                    });
                
                // 송금 실행
                balanceCache.put(fromPlayer, fromBalance.subtract(amount));
                balanceCache.put(toPlayer, toBalance.add(amount));
                
                // 트랜잭션 로그 기록
                logTransaction(fromPlayer, toPlayer, amount, TransactionType.TRANSFER, reason);
                
                return true;
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("송금 처리 중 오류 ({} -> {}): {}", fromPlayer, toPlayer, e.getMessage());
                return false;
            }
        }, executor);
    }
    
    private void createNewAccount(UUID playerUuid) throws SQLException {
        String insertQuery = "INSERT INTO balances (player_uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = balance";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            
            stmt.setString(1, playerUuid.toString());
            stmt.setBigDecimal(2, DEFAULT_STARTING_BALANCE);
            stmt.executeUpdate();
            
            SmpEconomyMod.LOGGER.info("새 계정 생성: {} (초기 잔액: {})", playerUuid, DEFAULT_STARTING_BALANCE);
        }
    }
    
    private void logTransaction(UUID fromUuid, UUID toUuid, BigDecimal amount, TransactionType type, String description) {
        executor.execute(() -> {
            String insertQuery = "INSERT INTO tx_ledger (from_uuid, to_uuid, amount, transaction_type, description) VALUES (?, ?, ?, ?, ?)";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                
                stmt.setString(1, fromUuid != null ? fromUuid.toString() : null);
                stmt.setString(2, toUuid != null ? toUuid.toString() : null);
                stmt.setBigDecimal(3, amount);
                stmt.setString(4, type.name().toLowerCase());
                stmt.setString(5, description);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                SmpEconomyMod.LOGGER.error("트랜잭션 로그 기록 실패: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 캐시된 잔액들을 데이터베이스에 동기화
     */
    private void syncCacheToDatabase() {
        if (balanceCache.isEmpty()) return;
        
        SmpEconomyMod.LOGGER.debug("잔액 캐시를 데이터베이스에 동기화 중... ({} 개의 계정)", balanceCache.size());
        
        String upsertQuery = "INSERT INTO balances (player_uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = VALUES(balance), updated_at = CURRENT_TIMESTAMP";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertQuery)) {
            
            conn.setAutoCommit(false);
            
            for (var entry : balanceCache.entrySet()) {
                stmt.setString(1, entry.getKey().toString());
                stmt.setBigDecimal(2, entry.getValue());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            conn.commit();
            
            SmpEconomyMod.LOGGER.debug("잔액 캐시 동기화 완료");
            
        } catch (SQLException e) {
            SmpEconomyMod.LOGGER.error("잔액 캐시 동기화 실패: {}", e.getMessage());
        }
    }
    
    public void shutdown() {
        // 종료 전 마지막 동기화
        syncCacheToDatabase();
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        SmpEconomyMod.LOGGER.info("EconomyManager 종료됨");
    }
    
    public enum TransactionType {
        TRANSFER, EARN, SPEND, TAX, QUEST_REWARD, ADMIN_SET
    }
}
