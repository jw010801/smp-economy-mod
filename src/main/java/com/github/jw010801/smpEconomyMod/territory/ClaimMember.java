package com.github.jw010801.smpeconomymod.territory;

import java.util.Objects;
import java.util.UUID;

public class ClaimMember {
    
    private final long claimId;
    private final UUID memberUuid;
    private final PermissionLevel permissionLevel;
    private final long addedAt;
    
    public ClaimMember(long claimId, UUID memberUuid, PermissionLevel permissionLevel, long addedAt) {
        this.claimId = claimId;
        this.memberUuid = memberUuid;
        this.permissionLevel = permissionLevel;
        this.addedAt = addedAt;
    }
    
    // Getters
    public long getClaimId() {
        return claimId;
    }
    
    public UUID getMemberUuid() {
        return memberUuid;
    }
    
    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }
    
    public long getAddedAt() {
        return addedAt;
    }
    
    /**
     * 특정 작업을 수행할 권한이 있는지 확인
     */
    public boolean canPerformAction(ClaimAction action) {
        return switch (permissionLevel) {
            case ADMIN -> true; // 관리자는 모든 권한
            case MEMBER -> switch (action) {
                case BUILD, DESTROY, USE_ITEMS, INTERACT -> true;
                case MANAGE_MEMBERS, DELETE_CLAIM -> false;
            };
            case GUEST -> switch (action) {
                case USE_ITEMS, INTERACT -> true;
                case BUILD, DESTROY, MANAGE_MEMBERS, DELETE_CLAIM -> false;
            };
        };
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClaimMember that = (ClaimMember) o;
        return claimId == that.claimId && Objects.equals(memberUuid, that.memberUuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(claimId, memberUuid);
    }
    
    @Override
    public String toString() {
        return String.format("ClaimMember{claimId=%d, member=%s, level=%s}", 
                claimId, memberUuid, permissionLevel);
    }
    
    public enum PermissionLevel {
        GUEST,    // 기본 상호작용만 가능
        MEMBER,   // 건설/파괴 가능
        ADMIN     // 멤버 관리 가능
    }
    
    public enum ClaimAction {
        BUILD,           // 블록 설치
        DESTROY,         // 블록 파괴
        USE_ITEMS,       // 아이템 사용
        INTERACT,        // 블록/엔티티 상호작용
        MANAGE_MEMBERS,  // 멤버 관리
        DELETE_CLAIM     // 클레임 삭제
    }
}
