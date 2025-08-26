# SMP Economy & Territory Mod

> 🏰 6~10인 생활·경영 SMP를 위한 DB 권위 경제와 영토 시스템 통합 모드

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.2-blue.svg)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## ✨ 주요 기능

### 💰 DB 권위 경제 시스템
- **MySQL 8.x + HikariCP** 기반 안정적인 데이터베이스 연동
- **비동기 캐시 + 배치 처리**로 최적화된 성능
- **ACID 보장** 트랜잭션으로 안전한 금융 거래
- **완전한 거래 로그** 추적 및 감사

### 🏘️ 영토 시스템
- **청크 기반 클레임** 시스템 (16x16 블록 단위)
- **4단계 권한 관리**: 소유자 → 관리자 → 멤버 → 게스트
- **자동 세금 징수**: 매일 자정 영토 유지비 차감
- **겹침 방지**: 정확한 좌표 계산으로 클레임 충돌 방지

### 📱 실시간 HUD
- **좌상단 정보 패널**: 👤 플레이어 💰 잔액 ⚒️ 채굴 🌾 농업
- **아이콘 기반 최소화** 디자인으로 화면 공간 절약
- **실시간 동기화**: 서버-클라이언트 5초 간격 업데이트
- **알림 시스템**: 거래/레벨업 시 팝업 메시지

### 🌐 네트워크 시스템
- **패킷 기반 통신**으로 빠른 데이터 동기화
- **이벤트 기반 업데이트**: 로그인/거래 시 즉시 반영
- **오프라인 동기화**: 재접속 시 전체 데이터 갱신

## 🚀 설치 및 실행

### 📋 요구사항

| 구성 요소 | 버전 | 필수 여부 |
|-----------|------|-----------|
| **Minecraft** | 1.20.1 | ✅ 필수 |
| **Fabric Loader** | 0.17.2+ | ✅ 필수 |
| **Fabric API** | 0.92.6+ | ✅ 필수 |
| **Java** | 17+ | ✅ 필수 |
| **MySQL** | 8.0+ | ✅ 필수 |

### 📦 서버 설치

1. **모드 파일 다운로드**
   ```bash
   # 빌드된 JAR 파일을 서버 mods 폴더에 복사
   cp build/libs/smp-economy-mod-*.jar /path/to/server/mods/
   ```

2. **데이터베이스 설정**
   ```sql
   -- MySQL에서 데이터베이스 생성
   CREATE DATABASE smp_economy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'smp_user'@'localhost' IDENTIFIED BY 'smp_password';
   GRANT ALL PRIVILEGES ON smp_economy.* TO 'smp_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. **모드 설정 파일** (config/smp-economy-mod.json)
   ```json
   {
     "database": {
       "host": "localhost",
       "port": 3306,
       "database": "smp_economy",
       "username": "smp_user",
       "password": "smp_password"
     },
     "economy": {
       "startingBalance": 100.00,
       "dailySyncInterval": 5
     },
     "territory": {
       "claimBaseCost": 100.00,
       "claimCostPerChunk": 10.00,
       "dailyTaxPerChunk": 1.00
     }
   }
   ```

### 🛠️ 개발 환경 설정

1. **프로젝트 클론**
   ```bash
   git clone https://github.com/jw010801/smp-economy-mod.git
   cd smp-economy-mod
   ```

2. **개발 도구**
   - **IDE**: IntelliJ IDEA (권장) 또는 Visual Studio Code
   - **JDK**: Eclipse Temurin 17+ 또는 OpenJDK 17+
   - **Gradle**: 8.0+ (Wrapper 포함됨)

3. **빌드 실행**
   ```bash
   # Windows
   .\gradlew.bat build
   
   # Linux/macOS
   ./gradlew build
   ```

4. **개발 서버 실행**
   ```bash
   # 개발용 서버 실행 (핫 리로드 지원)
   .\gradlew.bat runServer
   ```

## 📖 사용법

### 💰 경제 명령어

| 명령어 | 한국어 | 설명 | 예시 |
|--------|--------|------|------|
| `/money balance` | `/돈 잔액` | 자신의 잔액 조회 | `/잔액` |
| `/money pay <플레이어> <금액>` | `/돈 송금` | 다른 플레이어에게 송금 | `/송금 Steve 100` |
| `/money give <플레이어> <금액> [사유]` | `/돈 지급` | 관리자가 돈 지급 | `/돈 지급 Steve 1000 이벤트상금` |
| `/money take <플레이어> <금액> [사유]` | `/돈 차감` | 관리자가 돈 차감 | `/돈 차감 Steve 50 세금` |
| `/money set <플레이어> <금액> [사유]` | `/돈 설정` | 관리자가 잔액 설정 | `/돈 설정 Steve 500` |

### 🏘️ 영토 명령어

| 명령어 | 한국어 | 설명 | 예시 |
|--------|--------|------|------|
| `/claim create <크기>` | `/영토 생성` | 새 영토 생성 | `/영토 생성 3` |
| `/claim info` | `/영토 정보` | 현재 위치 영토 정보 | `/영토정보` |
| `/claim trust <플레이어> [권한]` | `/영토 신뢰` | 플레이어 권한 부여 | `/신뢰 Steve member` |
| `/claim untrust <플레이어>` | `/영토 불신뢰` | 플레이어 권한 제거 | `/불신뢰 Steve` |
| `/claim list` | `/영토 목록` | 자신의 영토 목록 | `/영토목록` |
| `/claim members` | `/영토 멤버` | 영토 멤버 목록 | `/영토 멤버` |

### 🎯 권한 레벨

| 권한 | 한국어 | 건설 | 파괴 | 상호작용 | 멤버관리 |
|------|--------|------|------|----------|----------|
| **OWNER** | 소유자 | ✅ | ✅ | ✅ | ✅ |
| **ADMIN** | 관리자 | ✅ | ✅ | ✅ | ✅ |
| **MEMBER** | 멤버 | ✅ | ✅ | ✅ | ❌ |
| **GUEST** | 게스트 | ❌ | ❌ | ✅ | ❌ |

## 🏗️ 아키텍처

### 📊 데이터베이스 스키마

```sql
-- 경제 시스템
CREATE TABLE balances (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance DECIMAL(15,2) DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE tx_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_uuid VARCHAR(36),
    to_uuid VARCHAR(36), 
    amount DECIMAL(15,2) NOT NULL,
    transaction_type ENUM('transfer', 'earn', 'spend', 'tax', 'quest_reward'),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 영토 시스템
CREATE TABLE claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    world_name VARCHAR(50) NOT NULL,
    min_x INT NOT NULL, max_x INT NOT NULL,
    min_z INT NOT NULL, max_z INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE claim_members (
    claim_id BIGINT,
    member_uuid VARCHAR(36),
    permission_level ENUM('guest', 'member', 'admin'),
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (claim_id, member_uuid)
);

CREATE TABLE claim_tax (
    claim_id BIGINT PRIMARY KEY,
    daily_tax DECIMAL(10,2) DEFAULT 1.00,
    last_tax_paid TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tax_arrears DECIMAL(15,2) DEFAULT 0.00
);
```

### 🌐 네트워크 프로토콜

**서버 → 클라이언트**
- `player_data_sync`: 전체 플레이어 데이터 동기화
- `economy_update`: 경제 정보 업데이트  
- `skill_update`: 스킬 레벨 업데이트
- `territory_update`: 영토 정보 업데이트
- `notification`: 알림 메시지

**클라이언트 → 서버**
- `request_player_data`: 플레이어 데이터 요청
- `economy_command`: 경제 명령어 실행

## 🐛 이슈 재현 방법

### 일반적인 문제 해결

1. **모드가 로드되지 않을 때**
   ```bash
   # 로그 파일 확인
   tail -f logs/latest.log | grep "smp-economy-mod"
   ```

2. **데이터베이스 연결 오류**
   ```bash
   # MySQL 연결 테스트
   mysql -u smp_user -p -h localhost smp_economy
   ```

3. **HUD가 표시되지 않을 때**
   - F3+H를 눌러 HUD 토글 확인
   - 클라이언트 로그에서 오류 메시지 확인

### 🐛 버그 리포트 작성

**이슈 제출시 포함해야 할 정보:**

1. **환경 정보**
   - Minecraft 버전
   - Fabric Loader 버전  
   - 모드 버전
   - Java 버전
   - 운영체제

2. **재현 단계**
   ```
   1. 서버에 접속
   2. /돈 잔액 명령어 입력
   3. 오류 메시지 발생
   ```

3. **로그 파일** (문제 발생 시점 포함)
   ```bash
   # 서버 로그
   logs/latest.log
   
   # 클라이언트 로그 (Windows)
   %APPDATA%/.minecraft/logs/latest.log
   ```

4. **예상 동작 vs 실제 동작**
   - 예상: 잔액이 HUD에 표시되어야 함
   - 실제: HUD에 0원으로 표시됨

## 📈 성능 최적화

### 💾 메모리 사용량
- **서버**: ~50MB 추가 메모리 사용
- **클라이언트**: ~20MB 추가 메모리 사용

### ⚡ TPS 영향
- **평상시**: 거의 영향 없음 (< 0.1ms)
- **대량 거래시**: ~1-2ms 추가 처리 시간

### 📊 데이터베이스 최적화
- **인덱싱**: 모든 검색 필드에 적절한 인덱스 적용
- **캐싱**: 5분 주기 메모리 캐시 동기화
- **배치 처리**: 트랜잭션 로그 비동기 기록

## 🤝 기여하기

1. **Fork** 후 새 브랜치 생성
2. **기능 개발** 또는 **버그 수정**
3. **테스트 코드** 작성 (선택사항)
4. **Pull Request** 제출

### 📋 코딩 스타일
- **Java 17+ 문법** 사용
- **4칸 들여쓰기**, **camelCase** 네이밍
- **한국어 주석** 및 **JavaDoc** 권장

## 📄 라이선스

이 프로젝트는 [MIT License](LICENSE) 하에 배포됩니다.

## 🙏 감사의 말

- [Fabric](https://fabricmc.net) 개발팀
- [HikariCP](https://github.com/brettwooldridge/HikariCP) 개발팀  
- [Minecraft Forge Community](https://forums.minecraftforge.net)

---

**⚠️ 주의사항**: 이 모드는 서버 전용입니다. 싱글플레이어에서는 작동하지 않습니다.

**📞 지원**: [Issues](https://github.com/jw010801/smp-economy-mod/issues)에서 문의해주세요.

**🌟 별표**: 유용하다면 GitHub에서 ⭐를 눌러주세요!
