package com.github.jw010801.smpeconomymod.territory;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.database.DatabaseManager;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TerritoryManager {
    
    private final DatabaseManager databaseManager;
    private final ScheduledExecutorService executor;
    
    // 캐시: (월드명, 청크좌표) -> 클레임 정보
    private final ConcurrentHashMap<String, Claim> claimCache = new ConcurrentHashMap<>();
    
    // 경제 설정
    public static final BigDecimal CLAIM_BASE_COST = new BigDecimal("100.00");
    public static final BigDecimal CLAIM_COST_PER_CHUNK = new BigDecimal("10.00");
    public static final BigDecimal DAILY_TAX_PER_CHUNK = new BigDecimal("1.00");
    
    public TerritoryManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.executor = Executors.newScheduledThreadPool(2);
        
        // 매일 자정에 세금 징수
        scheduleDaily(() -> collectDailyTaxes(), 0, 0);
        
        // 30분마다 캐시 동기화
        this.executor.scheduleAtFixedRate(this::syncCacheToDatabase, 30, 30, TimeUnit.MINUTES);
        
        SmpEconomyMod.LOGGER.info("TerritoryManager 초기화됨");
    }
    
    /**
     * 새로운 영토를 클레임합니다.
     */
    public CompletableFuture<ClaimResult> createClaim(UUID ownerUuid, String worldName, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 영역 유효성 검사
                if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                    return ClaimResult.failure("잘못된 영역 좌표입니다.");
                }
                
                // 겹치는 클레임 확인
                if (hasOverlappingClaim(worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                    return ClaimResult.failure("이미 클레임된 영역과 겹칩니다.");
                }
                
                // 비용 계산
                int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
                BigDecimal totalCost = CLAIM_BASE_COST.add(CLAIM_COST_PER_CHUNK.multiply(new BigDecimal(chunkCount)));
                
                // 잔액 확인 및 차감
                if (!SmpEconomyMod.economyManager.subtractBalance(ownerUuid, totalCost, "영토 클레임 비용").get()) {
                    return ClaimResult.failure("잔액이 부족합니다. 필요 금액: " + totalCost);
                }
                
                // 데이터베이스에 클레임 생성
                long claimId = createClaimInDatabase(ownerUuid, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
                
                // 세금 정보 생성
                createClaimTax(claimId, DAILY_TAX_PER_CHUNK.multiply(new BigDecimal(chunkCount)));
                
                // 캐시 업데이트
                Claim newClaim = new Claim(claimId, ownerUuid, worldName, minChunkX, minChunkZ, maxChunkX, maxChunkZ, System.currentTimeMillis());
                addClaimToCache(newClaim);
                
                SmpEconomyMod.LOGGER.info("새 클레임 생성: {} (소유자: {}, 청크: {}개)", claimId, ownerUuid, chunkCount);
                
                return ClaimResult.success(newClaim, "영토가 성공적으로 클레임되었습니다! 비용: " + totalCost);
                
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("클레임 생성 중 오류: {}", e.getMessage(), e);
                return ClaimResult.failure("클레임 생성 중 오류가 발생했습니다.");
            }
        }, executor);
    }
    
    /**
     * 특정 위치의 클레임 정보를 조회합니다.
     */
    public CompletableFuture<Optional<Claim>> getClaimAt(String worldName, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 캐시에서 먼저 찾기
                String cacheKey = getCacheKey(worldName, chunkX, chunkZ);
                for (Claim claim : claimCache.values()) {
                    if (claim.contains(worldName, chunkX, chunkZ)) {
                        return Optional.of(claim);
                    }
                }
                
                // 데이터베이스에서 조회
                return getClaimFromDatabase(worldName, chunkX, chunkZ);
                
            } catch (Exception e) {
                SmpEconomyMod.LOGGER.error("클레임 조회 중 오류: {}", e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }
    
    /**
     * 플레이어의 클레임 권한을 확인합니다.
     */
    public CompletableFuture<ClaimPermission> getPlayerPermission(UUID playerUuid, String worldName, int chunkX, int chunkZ) {
        return getClaimAt(worldName, chunkX, chunkZ).thenApply(claimOpt -> {
            if (claimOpt.isEmpty()) {
                return ClaimPermission.NONE; // 클레임되지 않은 영역
            }
            
            Claim claim = claimOpt.get();
            
            // 소유자인지 확인
            if (claim.getOwnerUuid().equals(playerUuid)) {
                return ClaimPermission.OWNER;
            }
            
            // 멤버인지 확인
            ClaimMember member = getClaimMember(claim.getId(), playerUuid);
            if (member != null) {
                return switch (member.getPermissionLevel()) {
                    case ADMIN -> ClaimPermission.ADMIN;
                    case MEMBER -> ClaimPermission.MEMBER;
                    case GUEST -> ClaimPermission.GUEST;
                };
            }
            
            return ClaimPermission.NONE;
        });
    }
    
    /**
     * 클레임에 멤버를 추가합니다.
     */
    public CompletableFuture<Boolean> addClaimMember(long claimId, UUID memberUuid, ClaimMember.PermissionLevel level) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String insertQuery = "INSERT INTO claim_members (claim_id, member_uuid, permission_level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE permission_level = VALUES(permission_level)";
                
                try (Connection conn = databaseManager.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                    
                    stmt.setLong(1, claimId);
                    stmt.setString(2, memberUuid.toString());
                    stmt.setString(3, level.name().toLowerCase());
                    
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                }
                
            } catch (SQLException e) {
                SmpEconomyMod.LOGGER.error("클레임 멤버 추가 실패: {}", e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * 겹치는 클레임이 있는지 확인
     */
    private boolean hasOverlappingClaim(String worldName, int minX, int minZ, int maxX, int maxZ) {
        String query = """
            SELECT COUNT(*) FROM claims 
            WHERE world_name = ? 
            AND NOT (max_x < ? OR min_x > ? OR max_z < ? OR min_z > ?)
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, worldName);
            stmt.setInt(2, minX);
            stmt.setInt(3, maxX);
            stmt.setInt(4, minZ);
            stmt.setInt(5, maxZ);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            SmpEconomyMod.LOGGER.error("겹치는 클레임 확인 중 오류: {}", e.getMessage());
        }
        
        return false;
    }
    
    private long createClaimInDatabase(UUID ownerUuid, String worldName, int minX, int minZ, int maxX, int maxZ) throws SQLException {
        String insertQuery = "INSERT INTO claims (owner_uuid, world_name, min_x, max_x, min_z, max_z) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, worldName);
            stmt.setInt(3, minX);
            stmt.setInt(4, maxX);
            stmt.setInt(5, minZ);
            stmt.setInt(6, maxZ);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    throw new SQLException("클레임 생성 실패: ID를 얻을 수 없음");
                }
            }
        }
    }
    
    private void createClaimTax(long claimId, BigDecimal dailyTax) throws SQLException {
        String insertQuery = "INSERT INTO claim_tax (claim_id, daily_tax) VALUES (?, ?)";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            
            stmt.setLong(1, claimId);
            stmt.setBigDecimal(2, dailyTax);
            stmt.executeUpdate();
        }
    }
    
    private Optional<Claim> getClaimFromDatabase(String worldName, int chunkX, int chunkZ) throws SQLException {
        String query = "SELECT * FROM claims WHERE world_name = ? AND min_x <= ? AND max_x >= ? AND min_z <= ? AND max_z >= ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, worldName);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkX);
            stmt.setInt(4, chunkZ);
            stmt.setInt(5, chunkZ);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Claim claim = new Claim(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("world_name"),
                        rs.getInt("min_x"),
                        rs.getInt("min_z"),
                        rs.getInt("max_x"),
                        rs.getInt("max_z"),
                        rs.getTimestamp("created_at").getTime()
                    );
                    
                    // 캐시에 추가
                    addClaimToCache(claim);
                    
                    return Optional.of(claim);
                }
            }
        }
        
        return Optional.empty();
    }
    
    private ClaimMember getClaimMember(long claimId, UUID memberUuid) {
        // TODO: 캐싱된 멤버 정보 조회 구현
        // 현재는 간단히 데이터베이스에서 직접 조회
        String query = "SELECT * FROM claim_members WHERE claim_id = ? AND member_uuid = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setLong(1, claimId);
            stmt.setString(2, memberUuid.toString());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ClaimMember(
                        claimId,
                        memberUuid,
                        ClaimMember.PermissionLevel.valueOf(rs.getString("permission_level").toUpperCase()),
                        rs.getTimestamp("added_at").getTime()
                    );
                }
            }
        } catch (SQLException e) {
            SmpEconomyMod.LOGGER.error("클레임 멤버 조회 실패: {}", e.getMessage());
        }
        
        return null;
    }
    
    private void addClaimToCache(Claim claim) {
        String key = String.format("%s:%d-%d-%d-%d", claim.getWorldName(), claim.getMinX(), claim.getMinZ(), claim.getMaxX(), claim.getMaxZ());
        claimCache.put(key, claim);
    }
    
    private String getCacheKey(String worldName, int chunkX, int chunkZ) {
        return String.format("%s:%d:%d", worldName, chunkX, chunkZ);
    }
    
    /**
     * 매일 세금 징수
     */
    private void collectDailyTaxes() {
        SmpEconomyMod.LOGGER.info("일일 영토 세금 징수를 시작합니다...");
        
        String query = """
            SELECT ct.claim_id, ct.daily_tax, c.owner_uuid, ct.tax_arrears
            FROM claim_tax ct
            JOIN claims c ON ct.claim_id = c.id
            WHERE DATEDIFF(NOW(), ct.last_tax_paid) >= 1
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long claimId = rs.getLong("claim_id");
                    BigDecimal dailyTax = rs.getBigDecimal("daily_tax");
                    UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                    BigDecimal currentArrears = rs.getBigDecimal("tax_arrears");
                    
                    // 세금 징수 시도
                    boolean paidSuccessfully = SmpEconomyMod.economyManager.subtractBalance(
                        ownerUuid, dailyTax, "영토 일일 세금 (클레임 ID: " + claimId + ")"
                    ).get();
                    
                    if (paidSuccessfully) {
                        // 성공적으로 징수된 경우
                        updateTaxPaid(claimId);
                        SmpEconomyMod.LOGGER.debug("영토 {} 세금 징수 완료: {}", claimId, dailyTax);
                    } else {
                        // 징수 실패 - 연체료 누적
                        BigDecimal newArrears = currentArrears.add(dailyTax);
                        updateTaxArrears(claimId, newArrears);
                        SmpEconomyMod.LOGGER.warn("영토 {} 세금 징수 실패. 연체료 누적: {}", claimId, newArrears);
                        
                        // 연체료가 일정 금액 이상이면 클레임 보호 해제 (TODO)
                        if (newArrears.compareTo(new BigDecimal("100.00")) > 0) {
                            SmpEconomyMod.LOGGER.warn("영토 {} 연체료 과다로 보호 해제 예정", claimId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SmpEconomyMod.LOGGER.error("일일 세금 징수 중 오류: {}", e.getMessage(), e);
        }
        
        SmpEconomyMod.LOGGER.info("일일 영토 세금 징수 완료");
    }
    
    private void updateTaxPaid(long claimId) throws SQLException {
        String updateQuery = "UPDATE claim_tax SET last_tax_paid = NOW(), tax_arrears = 0 WHERE claim_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            stmt.setLong(1, claimId);
            stmt.executeUpdate();
        }
    }
    
    private void updateTaxArrears(long claimId, BigDecimal arrears) throws SQLException {
        String updateQuery = "UPDATE claim_tax SET tax_arrears = ? WHERE claim_id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            stmt.setBigDecimal(1, arrears);
            stmt.setLong(2, claimId);
            stmt.executeUpdate();
        }
    }
    
    private void scheduleDaily(Runnable task, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, hour);
        nextRun.set(Calendar.MINUTE, minute);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);
        
        if (nextRun.before(now)) {
            nextRun.add(Calendar.DATE, 1);
        }
        
        long initialDelay = nextRun.getTimeInMillis() - now.getTimeInMillis();
        long period = 24 * 60 * 60 * 1000; // 24시간
        
        executor.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS);
    }
    
    private void syncCacheToDatabase() {
        // TODO: 필요시 캐시 동기화 구현
        SmpEconomyMod.LOGGER.debug("영토 캐시 동기화 (현재 캐시 크기: {})", claimCache.size());
    }
    
    public void shutdown() {
        syncCacheToDatabase();
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        SmpEconomyMod.LOGGER.info("TerritoryManager 종료됨");
    }
    
    public enum ClaimPermission {
        NONE, GUEST, MEMBER, ADMIN, OWNER
    }
}
