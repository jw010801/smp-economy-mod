package com.github.jw010801.smpeconomymod.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.territory.Claim;
import com.github.jw010801.smpeconomymod.territory.ClaimMember;
import com.github.jw010801.smpeconomymod.territory.TerritoryManager;

import java.math.BigDecimal;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TerritoryCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register(TerritoryCommands::registerCommands);
        SmpEconomyMod.LOGGER.info("영토 명령어 등록됨");
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        
        // /claim - 메인 영토 명령어 그룹
        dispatcher.register(literal("claim")
            .executes(TerritoryCommands::executeClaimInfo)
            
            .then(literal("create")
                .then(argument("size", IntegerArgumentType.integer(1, 10))
                    .executes(TerritoryCommands::executeClaimCreate)))
            
            .then(literal("expand")
                .then(argument("direction", StringArgumentType.word())
                    .then(argument("amount", IntegerArgumentType.integer(1, 10))
                        .executes(TerritoryCommands::executeClaimExpand))))
            
            .then(literal("info")
                .executes(TerritoryCommands::executeClaimInfo))
            
            .then(literal("list")
                .executes(TerritoryCommands::executeClaimList))
            
            .then(literal("delete")
                .executes(TerritoryCommands::executeClaimDelete)
                .then(argument("claim_id", IntegerArgumentType.integer(1))
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(TerritoryCommands::executeClaimDeleteById)))
            
            .then(literal("trust")
                .then(argument("player", EntityArgumentType.player())
                    .executes(TerritoryCommands::executeClaimTrustMember)
                    .then(argument("level", StringArgumentType.word())
                        .executes(TerritoryCommands::executeClaimTrustWithLevel))))
            
            .then(literal("untrust")
                .then(argument("player", EntityArgumentType.player())
                    .executes(TerritoryCommands::executeClaimUntrust)))
            
            .then(literal("members")
                .executes(TerritoryCommands::executeClaimMembers))
        );
        
        // 간단한 별칭들
        dispatcher.register(literal("claiminfo").executes(TerritoryCommands::executeClaimInfo));
        dispatcher.register(literal("claimlist").executes(TerritoryCommands::executeClaimList));
        dispatcher.register(literal("trust")
            .then(argument("player", EntityArgumentType.player())
                .executes(TerritoryCommands::executeClaimTrustMember)));
        dispatcher.register(literal("untrust")
            .then(argument("player", EntityArgumentType.player())
                .executes(TerritoryCommands::executeClaimUntrust)));
        
        // 한국어 별칭 명령어들
        dispatcher.register(literal("영토")
            .executes(TerritoryCommands::executeClaimInfo)
            
            .then(literal("생성")
                .then(argument("size", IntegerArgumentType.integer(1, 10))
                    .executes(TerritoryCommands::executeClaimCreate)))
            
            .then(literal("확장")
                .then(argument("direction", StringArgumentType.word())
                    .then(argument("amount", IntegerArgumentType.integer(1, 10))
                        .executes(TerritoryCommands::executeClaimExpand))))
            
            .then(literal("정보")
                .executes(TerritoryCommands::executeClaimInfo))
            
            .then(literal("목록")
                .executes(TerritoryCommands::executeClaimList))
            
            .then(literal("삭제")
                .executes(TerritoryCommands::executeClaimDelete)
                .then(argument("claim_id", IntegerArgumentType.integer(1))
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(TerritoryCommands::executeClaimDeleteById)))
            
            .then(literal("신뢰")
                .then(argument("player", EntityArgumentType.player())
                    .executes(TerritoryCommands::executeClaimTrustMember)
                    .then(argument("level", StringArgumentType.word())
                        .executes(TerritoryCommands::executeClaimTrustWithLevel))))
            
            .then(literal("불신뢰")
                .then(argument("player", EntityArgumentType.player())
                    .executes(TerritoryCommands::executeClaimUntrust)))
            
            .then(literal("멤버")
                .executes(TerritoryCommands::executeClaimMembers)));
        
        // 추가 한국어 별칭들
        dispatcher.register(literal("클레임")
            .executes(TerritoryCommands::executeClaimInfo)
            .then(literal("생성")
                .then(argument("size", IntegerArgumentType.integer(1, 10))
                    .executes(TerritoryCommands::executeClaimCreate)))
            .then(literal("정보")
                .executes(TerritoryCommands::executeClaimInfo)));
        
        dispatcher.register(literal("영토정보").executes(TerritoryCommands::executeClaimInfo));
        dispatcher.register(literal("영토목록").executes(TerritoryCommands::executeClaimList));
        dispatcher.register(literal("신뢰")
            .then(argument("player", EntityArgumentType.player())
                .executes(TerritoryCommands::executeClaimTrustMember)));
        dispatcher.register(literal("불신뢰")
            .then(argument("player", EntityArgumentType.player())
                .executes(TerritoryCommands::executeClaimUntrust)));
    }
    
    /**
     * /claim create <size> - 새로운 클레임 생성
     */
    private static int executeClaimCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int size = IntegerArgumentType.getInteger(context, "size");
        
        // 플레이어의 현재 위치
        BlockPos playerPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        
        // 청크 좌표 계산
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        
        // size x size 영역으로 클레임 생성
        int radius = (size - 1) / 2;
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        
        // 비용 계산 미리 표시
        int chunkCount = size * size;
        BigDecimal totalCost = TerritoryManager.CLAIM_BASE_COST.add(
            TerritoryManager.CLAIM_COST_PER_CHUNK.multiply(new BigDecimal(chunkCount))
        );
        
        player.sendMessage(Text.of(String.format("§6🏘️ %dx%d 영역 클레임을 생성합니다... (비용: %s골드)", 
                size, size, formatMoney(totalCost))));
        
        // 클레임 생성
        SmpEconomyMod.territoryManager.createClaim(
            player.getUuid(), 
            world.getRegistryKey().getValue().toString(),
            minChunkX, minChunkZ, maxChunkX, maxChunkZ
        ).thenAccept(result -> {
            if (result.isSuccess()) {
                player.sendMessage(Text.of("§a✅ " + result.getMessage()));
                
                Claim claim = result.getClaim();
                player.sendMessage(Text.of(String.format("§6📍 클레임 ID: %d | 청크: (%d,%d) ~ (%d,%d) | 총 %d청크",
                        claim.getId(), claim.getMinX(), claim.getMinZ(), 
                        claim.getMaxX(), claim.getMaxZ(), claim.getChunkCount())));
                
            } else {
                player.sendMessage(Text.of("§c❌ " + result.getMessage()));
            }
        });
        
        return 1;
    }
    
    /**
     * /claim info - 현재 위치의 클레임 정보 조회
     */
    private static int executeClaimInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BlockPos playerPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        
        SmpEconomyMod.territoryManager.getClaimAt(
            world.getRegistryKey().getValue().toString(), chunkX, chunkZ
        ).thenAccept(claimOpt -> {
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                
                // 소유자 이름 조회 (TODO: UUID -> 이름 변환)
                String ownerUuid = claim.getOwnerUuid().toString();
                String ownerName = ownerUuid.substring(0, 8) + "...";
                
                player.sendMessage(Text.of("§6🏘️ === 클레임 정보 ==="));
                player.sendMessage(Text.of(String.format("§f📍 ID: §e%d", claim.getId())));
                player.sendMessage(Text.of(String.format("§f👤 소유자: §a%s", ownerName)));
                player.sendMessage(Text.of(String.format("§f🗺️ 영역: §b(%d,%d) ~ (%d,%d)", 
                        claim.getMinX(), claim.getMinZ(), claim.getMaxX(), claim.getMaxZ())));
                player.sendMessage(Text.of(String.format("§f📦 크기: §d%d청크", claim.getChunkCount())));
                player.sendMessage(Text.of(String.format("§f🕒 생성일: §7%s", 
                        new java.util.Date(claim.getCreatedAt()))));
                
                // 권한 확인
                SmpEconomyMod.territoryManager.getPlayerPermission(
                    player.getUuid(), world.getRegistryKey().getValue().toString(), chunkX, chunkZ
                ).thenAccept(permission -> {
                    String permissionText = switch (permission) {
                        case OWNER -> "§c소유자";
                        case ADMIN -> "§6관리자";
                        case MEMBER -> "§a멤버";
                        case GUEST -> "§7게스트";
                        case NONE -> "§8권한 없음";
                    };
                    player.sendMessage(Text.of("§f🔒 권한: " + permissionText));
                });
                
            } else {
                player.sendMessage(Text.of("§7이 위치는 클레임되지 않은 영역입니다."));
            }
        });
        
        return 1;
    }
    
    /**
     * /claim list - 자신의 클레임 목록 조회
     */
    private static int executeClaimList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.of("§c클레임 목록 조회 기능은 아직 구현되지 않았습니다."));
        // TODO: 플레이어의 클레임 목록 조회 구현
        
        return 1;
    }
    
    /**
     * /claim delete - 현재 위치의 클레임 삭제
     */
    private static int executeClaimDelete(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.of("§c클레임 삭제 기능은 아직 구현되지 않았습니다."));
        // TODO: 클레임 삭제 구현
        
        return 1;
    }
    
    /**
     * /claim delete <claim_id> - 특정 클레임 삭제 (관리자)
     */
    private static int executeClaimDeleteById(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        int claimId = IntegerArgumentType.getInteger(context, "claim_id");
        
        context.getSource().sendFeedback(() -> Text.of("§c관리자 클레임 삭제 기능은 아직 구현되지 않았습니다."), false);
        // TODO: 관리자 클레임 삭제 구현
        
        return 1;
    }
    
    /**
     * /claim trust <player> - 플레이어를 클레임에 추가 (MEMBER 권한)
     */
    private static int executeClaimTrustMember(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return executeClaimTrustWithLevel(context, ClaimMember.PermissionLevel.MEMBER);
    }
    
    /**
     * /claim trust <player> <level> - 플레이어를 특정 권한으로 클레임에 추가
     */
    private static int executeClaimTrustWithLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String levelStr = StringArgumentType.getString(context, "level").toUpperCase();
        ClaimMember.PermissionLevel level;
        
        try {
            level = ClaimMember.PermissionLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            context.getSource().sendError(Text.of("§c잘못된 권한 레벨입니다. (GUEST, MEMBER, ADMIN)"));
            return 0;
        }
        
        return executeClaimTrustWithLevel(context, level);
    }
    
    private static int executeClaimTrustWithLevel(CommandContext<ServerCommandSource> context, ClaimMember.PermissionLevel level) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        
        // 자기 자신은 추가할 수 없음
        if (player.getUuid().equals(targetPlayer.getUuid())) {
            player.sendMessage(Text.of("§c자기 자신은 멤버로 추가할 수 없습니다."));
            return 0;
        }
        
        BlockPos playerPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;
        
        // 현재 위치의 클레임 확인
        SmpEconomyMod.territoryManager.getClaimAt(
            world.getRegistryKey().getValue().toString(), chunkX, chunkZ
        ).thenAccept(claimOpt -> {
            if (claimOpt.isEmpty()) {
                player.sendMessage(Text.of("§c이 위치는 클레임된 영역이 아닙니다."));
                return;
            }
            
            Claim claim = claimOpt.get();
            
            // 소유자 권한 확인
            if (!claim.getOwnerUuid().equals(player.getUuid()) && !player.hasPermissionLevel(2)) {
                player.sendMessage(Text.of("§c이 클레임의 소유자만 멤버를 추가할 수 있습니다."));
                return;
            }
            
            // 멤버 추가
            SmpEconomyMod.territoryManager.addClaimMember(claim.getId(), targetPlayer.getUuid(), level).thenAccept(success -> {
                if (success) {
                    String levelName = switch (level) {
                        case GUEST -> "게스트";
                        case MEMBER -> "멤버";
                        case ADMIN -> "관리자";
                    };
                    
                    player.sendMessage(Text.of(String.format("§a✅ %s를 클레임에 %s로 추가했습니다.", 
                            targetPlayer.getName().getString(), levelName)));
                    targetPlayer.sendMessage(Text.of(String.format("§a🏘️ %s의 클레임에 %s로 추가되었습니다.", 
                            player.getName().getString(), levelName)));
                } else {
                    player.sendMessage(Text.of("§c❌ 멤버 추가에 실패했습니다."));
                }
            });
        });
        
        return 1;
    }
    
    /**
     * /claim untrust <player> - 플레이어를 클레임에서 제거
     */
    private static int executeClaimUntrust(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        
        player.sendMessage(Text.of("§c멤버 제거 기능은 아직 구현되지 않았습니다."));
        // TODO: 클레임에서 멤버 제거 구현
        
        return 1;
    }
    
    /**
     * /claim members - 현재 클레임의 멤버 목록 조회
     */
    private static int executeClaimMembers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.of("§c멤버 목록 조회 기능은 아직 구현되지 않았습니다."));
        // TODO: 클레임 멤버 목록 조회 구현
        
        return 1;
    }
    
    /**
     * /claim expand <direction> <amount> - 클레임 확장
     */
    private static int executeClaimExpand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String direction = StringArgumentType.getString(context, "direction").toLowerCase();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        
        player.sendMessage(Text.of("§c클레임 확장 기능은 아직 구현되지 않았습니다."));
        // TODO: 클레임 확장 구현
        
        return 1;
    }
    
    private static String formatMoney(BigDecimal amount) {
        return String.format("%,d", amount.longValue());
    }
}
