package com.github.jw010801.smpeconomymod.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.github.jw010801.smpeconomymod.SmpEconomyMod;
import com.github.jw010801.smpeconomymod.server.network.ServerNetworkHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EconomyCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register(EconomyCommands::registerCommands);
        SmpEconomyMod.LOGGER.info("경제 명령어 등록됨");
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        
        // /money - 메인 경제 명령어 그룹
        dispatcher.register(literal("money")
            .then(literal("balance")
                .executes(EconomyCommands::executeBalance)
                .then(argument("player", EntityArgumentType.player())
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(EconomyCommands::executeBalanceOther)))
            
            .then(literal("pay")
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executePay))))
            
            .then(literal("give")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executeGive)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeGiveWithReason)))))
            
            .then(literal("take")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executeTake)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeTakeWithReason)))))
            
            .then(literal("set")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(EconomyCommands::executeSet)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeSetWithReason)))))
            
            .then(literal("top")
                .executes(EconomyCommands::executeTop))
        );
        
        // 간단한 별칭 명령어들
        dispatcher.register(literal("balance").executes(EconomyCommands::executeBalance));
        dispatcher.register(literal("bal").executes(EconomyCommands::executeBalance));
        dispatcher.register(literal("pay")
            .then(argument("player", EntityArgumentType.player())
                .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(EconomyCommands::executePay))));
        
        // 한국어 별칭 명령어들
        dispatcher.register(literal("돈")
            .then(literal("잔액")
                .executes(EconomyCommands::executeBalance)
                .then(argument("player", EntityArgumentType.player())
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(EconomyCommands::executeBalanceOther)))
            
            .then(literal("송금")
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executePay))))
            
            .then(literal("지급")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executeGive)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeGiveWithReason)))))
            
            .then(literal("차감")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::executeTake)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeTakeWithReason)))))
            
            .then(literal("설정")
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(EconomyCommands::executeSet)
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes(EconomyCommands::executeSetWithReason)))))
            
            .then(literal("순위")
                .executes(EconomyCommands::executeTop)));
        
        // 추가 한국어 별칭들
        dispatcher.register(literal("잔액").executes(EconomyCommands::executeBalance));
        dispatcher.register(literal("송금")
            .then(argument("player", EntityArgumentType.player())
                .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(EconomyCommands::executePay))));
    }
    
    /**
     * /money balance - 자신의 잔액 조회
     */
    private static int executeBalance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID playerUuid = player.getUuid();
        
        SmpEconomyMod.economyManager.getBalance(playerUuid).thenAccept(balance -> {
            player.sendMessage(Text.of(String.format("§6💰 현재 잔액: §e%s골드", formatMoney(balance))));
            
            // 클라이언트 HUD 즉시 업데이트
            ServerNetworkHandler.syncEconomyDataToClient(player, balance, BigDecimal.ZERO);
        });
        
        return 1;
    }
    
    /**
     * /money balance <player> - 다른 플레이어 잔액 조회 (관리자만)
     */
    private static int executeBalanceOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        
        SmpEconomyMod.economyManager.getBalance(targetPlayer.getUuid()).thenAccept(balance -> {
            source.sendFeedback(() -> Text.of(String.format("§6💰 %s의 잔액: §e%s골드", 
                    targetPlayer.getName().getString(), formatMoney(balance))), false);
        });
        
        return 1;
    }
    
    /**
     * /money pay <player> <amount> - 송금
     */
    private static int executePay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        BigDecimal transferAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        
        // 자기 자신에게 송금 방지
        if (player.getUuid().equals(targetPlayer.getUuid())) {
            player.sendMessage(Text.of("§c자기 자신에게는 송금할 수 없습니다."));
            return 0;
        }
        
        // 송금 실행
        SmpEconomyMod.economyManager.transferMoney(
            player.getUuid(), 
            targetPlayer.getUuid(), 
            transferAmount,
            String.format("플레이어 송금: %s -> %s", player.getName().getString(), targetPlayer.getName().getString())
        ).thenAccept(success -> {
            if (success) {
                // 송금 성공
                player.sendMessage(Text.of(String.format("§a✅ %s에게 %s골드를 송금했습니다.", 
                        targetPlayer.getName().getString(), formatMoney(transferAmount))));
                targetPlayer.sendMessage(Text.of(String.format("§a💰 %s로부터 %s골드를 받았습니다.", 
                        player.getName().getString(), formatMoney(transferAmount))));
                
                // 클라이언트 HUD 업데이트
                SmpEconomyMod.economyManager.getBalance(player.getUuid()).thenAccept(balance -> {
                    ServerNetworkHandler.syncEconomyDataToClient(player, balance, BigDecimal.ZERO);
                });
                SmpEconomyMod.economyManager.getBalance(targetPlayer.getUuid()).thenAccept(balance -> {
                    ServerNetworkHandler.syncEconomyDataToClient(targetPlayer, balance, BigDecimal.ZERO);
                });
                
            } else {
                // 송금 실패 (주로 잔액 부족)
                player.sendMessage(Text.of("§c❌ 송금에 실패했습니다. 잔액이 부족하거나 오류가 발생했습니다."));
            }
        });
        
        return 1;
    }
    
    /**
     * /money give <player> <amount> [reason] - 관리자가 돈 지급
     */
    private static int executeGive(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return executeGiveWithReason(context, "관리자 지급");
    }
    
    private static int executeGiveWithReason(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String reason = StringArgumentType.getString(context, "reason");
        return executeGiveWithReason(context, reason);
    }
    
    private static int executeGiveWithReason(CommandContext<ServerCommandSource> context, String reason) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        BigDecimal giveAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        
        SmpEconomyMod.economyManager.addBalance(targetPlayer.getUuid(), giveAmount, reason).thenAccept(success -> {
            if (success) {
                source.sendFeedback(() -> Text.of(String.format("§a✅ %s에게 %s골드를 지급했습니다. (사유: %s)", 
                        targetPlayer.getName().getString(), formatMoney(giveAmount), reason)), true);
                targetPlayer.sendMessage(Text.of(String.format("§a💰 %s골드를 받았습니다. (사유: %s)", 
                        formatMoney(giveAmount), reason)));
                
                // 클라이언트 HUD 업데이트
                SmpEconomyMod.economyManager.getBalance(targetPlayer.getUuid()).thenAccept(balance -> {
                    ServerNetworkHandler.syncEconomyDataToClient(targetPlayer, balance, BigDecimal.ZERO);
                    ServerNetworkHandler.sendNotificationToClient(targetPlayer, 
                        "💰 +" + formatMoney(giveAmount), 3000);
                });
            } else {
                source.sendError(Text.of("§c❌ 돈 지급에 실패했습니다."));
            }
        });
        
        return 1;
    }
    
    /**
     * /money take <player> <amount> [reason] - 관리자가 돈 차감
     */
    private static int executeTake(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return executeTakeWithReason(context, "관리자 차감");
    }
    
    private static int executeTakeWithReason(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String reason = StringArgumentType.getString(context, "reason");
        return executeTakeWithReason(context, reason);
    }
    
    private static int executeTakeWithReason(CommandContext<ServerCommandSource> context, String reason) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        BigDecimal takeAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        
        SmpEconomyMod.economyManager.subtractBalance(targetPlayer.getUuid(), takeAmount, reason).thenAccept(success -> {
            if (success) {
                source.sendFeedback(() -> Text.of(String.format("§a✅ %s에게서 %s골드를 차감했습니다. (사유: %s)", 
                        targetPlayer.getName().getString(), formatMoney(takeAmount), reason)), true);
                targetPlayer.sendMessage(Text.of(String.format("§c💸 %s골드가 차감되었습니다. (사유: %s)", 
                        formatMoney(takeAmount), reason)));
                
                // 클라이언트 HUD 업데이트
                SmpEconomyMod.economyManager.getBalance(targetPlayer.getUuid()).thenAccept(balance -> {
                    ServerNetworkHandler.syncEconomyDataToClient(targetPlayer, balance, BigDecimal.ZERO);
                    ServerNetworkHandler.sendNotificationToClient(targetPlayer, 
                        "💸 -" + formatMoney(takeAmount), 3000);
                });
            } else {
                source.sendError(Text.of("§c❌ 돈 차감에 실패했습니다. (잔액 부족 가능성)"));
            }
        });
        
        return 1;
    }
    
    /**
     * /money set <player> <amount> [reason] - 관리자가 잔액 설정
     */
    private static int executeSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return executeSetWithReason(context, "관리자 설정");
    }
    
    private static int executeSetWithReason(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String reason = StringArgumentType.getString(context, "reason");
        return executeSetWithReason(context, reason);
    }
    
    private static int executeSetWithReason(CommandContext<ServerCommandSource> context, String reason) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        
        BigDecimal setAmount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        
        SmpEconomyMod.economyManager.setBalance(targetPlayer.getUuid(), setAmount, reason).thenAccept(success -> {
            if (success) {
                source.sendFeedback(() -> Text.of(String.format("§a✅ %s의 잔액을 %s골드로 설정했습니다. (사유: %s)", 
                        targetPlayer.getName().getString(), formatMoney(setAmount), reason)), true);
                targetPlayer.sendMessage(Text.of(String.format("§6💰 잔액이 %s골드로 설정되었습니다. (사유: %s)", 
                        formatMoney(setAmount), reason)));
                
                // 클라이언트 HUD 업데이트
                ServerNetworkHandler.syncEconomyDataToClient(targetPlayer, setAmount, BigDecimal.ZERO);
            } else {
                source.sendError(Text.of("§c❌ 잔액 설정에 실패했습니다."));
            }
        });
        
        return 1;
    }
    
    /**
     * /money top - 부자 순위 (TODO: 구현 예정)
     */
    private static int executeTop(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.of("§c부자 순위 기능은 아직 구현되지 않았습니다."), false);
        return 1;
    }
    
    private static String formatMoney(BigDecimal amount) {
        return String.format("%,d", amount.longValue());
    }
}
