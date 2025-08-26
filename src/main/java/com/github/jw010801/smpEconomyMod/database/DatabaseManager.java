package com.github.jw010801.smpeconomymod.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.github.jw010801.smpeconomymod.SmpEconomyMod;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    
    private HikariDataSource dataSource;
    private boolean isInitialized = false;
    
    // 데이터베이스 설정 (실제 운영시에는 config 파일에서 읽어올 예정)
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "smp_economy";
    private static final String DB_USERNAME = "smp_user";
    private static final String DB_PASSWORD = "smp_password";
    
    public DatabaseManager() {
        SmpEconomyMod.LOGGER.info("DatabaseManager 생성됨");
    }
    
    public void initialize() {
        if (isInitialized) {
            SmpEconomyMod.LOGGER.warn("DatabaseManager는 이미 초기화되었습니다.");
            return;
        }
        
        try {
            setupDataSource();
            createTables();
            isInitialized = true;
            SmpEconomyMod.LOGGER.info("데이터베이스 연결이 성공적으로 초기화되었습니다.");
        } catch (Exception e) {
            SmpEconomyMod.LOGGER.error("데이터베이스 초기화 중 오류가 발생했습니다: {}", e.getMessage(), e);
        }
    }
    
    private void setupDataSource() {
        HikariConfig config = createHikariConfig();
        this.dataSource = new HikariDataSource(config);
        
        SmpEconomyMod.LOGGER.info("HikariCP 데이터소스가 설정되었습니다.");
    }
    
    private HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // 데이터베이스 연결 설정
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
                DB_HOST, DB_PORT, DB_NAME));
        config.setUsername(DB_USERNAME);
        config.setPassword(DB_PASSWORD);
        
        // HikariCP 최적화 설정
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // MySQL 최적화 설정
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        return config;
    }
    
    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            createEconomyTables(conn);
            createTerritoryTables(conn);
        }
    }
    
    private void createEconomyTables(Connection conn) throws SQLException {
        SmpEconomyMod.LOGGER.info("경제 시스템 테이블을 생성합니다...");
        
        // 잔액 테이블
        String balancesTable = """
            CREATE TABLE IF NOT EXISTS balances (
                player_uuid VARCHAR(36) PRIMARY KEY,
                balance DECIMAL(15,2) DEFAULT 0.00,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_balance (balance),
                INDEX idx_updated (updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        
        // 거래 원장 테이블
        String txLedgerTable = """
            CREATE TABLE IF NOT EXISTS tx_ledger (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                from_uuid VARCHAR(36),
                to_uuid VARCHAR(36),
                amount DECIMAL(15,2) NOT NULL,
                transaction_type ENUM('transfer', 'earn', 'spend', 'tax', 'quest_reward') NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_from_uuid (from_uuid),
                INDEX idx_to_uuid (to_uuid),
                INDEX idx_tx_type (transaction_type),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(balancesTable);
            stmt.execute(txLedgerTable);
        }
        
        SmpEconomyMod.LOGGER.info("경제 시스템 테이블 생성 완료");
    }
    
    private void createTerritoryTables(Connection conn) throws SQLException {
        SmpEconomyMod.LOGGER.info("영토 시스템 테이블을 생성합니다...");
        
        // 영토 기본 정보 테이블
        String claimsTable = """
            CREATE TABLE IF NOT EXISTS claims (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                owner_uuid VARCHAR(36) NOT NULL,
                world_name VARCHAR(50) NOT NULL,
                min_x INT NOT NULL,
                max_x INT NOT NULL,
                min_z INT NOT NULL,
                max_z INT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY chunk_constraint (world_name, min_x, max_x, min_z, max_z),
                INDEX idx_owner (owner_uuid),
                INDEX idx_world (world_name),
                INDEX idx_coords (min_x, max_x, min_z, max_z)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        
        // 영토 멤버 관리 테이블
        String claimMembersTable = """
            CREATE TABLE IF NOT EXISTS claim_members (
                claim_id BIGINT,
                member_uuid VARCHAR(36),
                permission_level ENUM('guest', 'member', 'admin') DEFAULT 'member',
                added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (claim_id, member_uuid),
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                INDEX idx_member_uuid (member_uuid),
                INDEX idx_permission (permission_level)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        
        // 영토 세금 시스템 테이블
        String claimTaxTable = """
            CREATE TABLE IF NOT EXISTS claim_tax (
                claim_id BIGINT PRIMARY KEY,
                daily_tax DECIMAL(10,2) DEFAULT 1.00,
                last_tax_paid TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                tax_arrears DECIMAL(15,2) DEFAULT 0.00,
                FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE,
                INDEX idx_last_paid (last_tax_paid),
                INDEX idx_arrears (tax_arrears)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(claimsTable);
            stmt.execute(claimMembersTable);
            stmt.execute(claimTaxTable);
        }
        
        SmpEconomyMod.LOGGER.info("영토 시스템 테이블 생성 완료");
    }
    
    public Connection getConnection() throws SQLException {
        if (!isInitialized) {
            throw new SQLException("DatabaseManager가 초기화되지 않았습니다.");
        }
        return dataSource.getConnection();
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            SmpEconomyMod.LOGGER.info("데이터베이스 연결이 종료되었습니다.");
        }
        isInitialized = false;
    }
    
    public boolean isInitialized() {
        return isInitialized;
    }
}
