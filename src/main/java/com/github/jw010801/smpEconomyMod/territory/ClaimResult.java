package com.github.jw010801.smpeconomymod.territory;

public class ClaimResult {
    
    private final boolean success;
    private final String message;
    private final Claim claim;
    
    private ClaimResult(boolean success, String message, Claim claim) {
        this.success = success;
        this.message = message;
        this.claim = claim;
    }
    
    public static ClaimResult success(Claim claim, String message) {
        return new ClaimResult(true, message, claim);
    }
    
    public static ClaimResult failure(String message) {
        return new ClaimResult(false, message, null);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Claim getClaim() {
        return claim;
    }
    
    @Override
    public String toString() {
        return String.format("ClaimResult{success=%s, message='%s', claim=%s}", 
                success, message, claim);
    }
}
