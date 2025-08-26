package com.github.jw010801.smpeconomymod.territory;

import java.util.Objects;
import java.util.UUID;

public class Claim {
    
    private final long id;
    private final UUID ownerUuid;
    private final String worldName;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final long createdAt;
    
    public Claim(long id, UUID ownerUuid, String worldName, int minX, int minZ, int maxX, int maxZ, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.createdAt = createdAt;
    }
    
    /**
     * 주어진 청크 좌표가 이 클레임 영역 안에 있는지 확인
     */
    public boolean contains(String world, int chunkX, int chunkZ) {
        return worldName.equals(world) && 
               chunkX >= minX && chunkX <= maxX && 
               chunkZ >= minZ && chunkZ <= maxZ;
    }
    
    /**
     * 이 클레임의 청크 개수를 반환
     */
    public int getChunkCount() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }
    
    /**
     * 다른 클레임과 겹치는지 확인
     */
    public boolean overlaps(Claim other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }
        
        return !(this.maxX < other.minX || this.minX > other.maxX || 
                 this.maxZ < other.minZ || this.minZ > other.maxZ);
    }
    
    /**
     * 클레임 영역의 중심 좌표 (블록 좌표)
     */
    public int[] getCenterBlockCoords() {
        int centerChunkX = (minX + maxX) / 2;
        int centerChunkZ = (minZ + maxZ) / 2;
        
        // 청크 좌표를 블록 좌표로 변환 (청크 중심)
        return new int[]{centerChunkX * 16 + 8, centerChunkZ * 16 + 8};
    }
    
    // Getters
    public long getId() {
        return id;
    }
    
    public UUID getOwnerUuid() {
        return ownerUuid;
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    public int getMinX() {
        return minX;
    }
    
    public int getMinZ() {
        return minZ;
    }
    
    public int getMaxX() {
        return maxX;
    }
    
    public int getMaxZ() {
        return maxZ;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Claim claim = (Claim) o;
        return id == claim.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Claim{id=%d, owner=%s, world='%s', area=(%d,%d) to (%d,%d), chunks=%d}", 
                id, ownerUuid, worldName, minX, minZ, maxX, maxZ, getChunkCount());
    }
}
