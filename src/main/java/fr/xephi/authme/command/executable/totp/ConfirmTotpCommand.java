package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.broadcast.PlayerTOTPConfirmEvent;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.security.totp.GenerateTotpService;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.service.bungeecord.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command to enable TOTP by supplying the proper code as confirmation.
 */
public class ConfirmTotpCommand extends PlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(ConfirmTotpCommand.class);

    @Inject
    private GenerateTotpService generateTotpService;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private DataSource dataSource;

    @Inject
    private Messages messages;

    @Inject
    private BukkitService bukkitService;

    @Inject
    private BungeeSender bungeeSender;

    @Override
    protected void runCommand(Player player, List<String> arguments) {
        PlayerAuth auth = playerCache.getAuth(player.getName());
        if (auth == null) {
            messages.send(player, MessageKey.NOT_LOGGED_IN);
        } else if (auth.getTotpKey() != null) {
            messages.send(player, MessageKey.TWO_FACTOR_ALREADY_ENABLED);
        } else {
            verifyTotpCodeConfirmation(player, auth, arguments.get(0));
        }
    }

    private void verifyTotpCodeConfirmation(Player player, PlayerAuth auth, String inputTotpCode) {
        final TotpGenerationResult totpDetails = generateTotpService.getGeneratedTotpKey(player);
        if (totpDetails == null) {
            messages.send(player, MessageKey.TWO_FACTOR_ENABLE_ERROR_NO_CODE);
        } else {
            boolean isCodeValid = generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(player, inputTotpCode);
            if (isCodeValid) {
                generateTotpService.removeGenerateTotpKey(player);
                insertTotpKeyIntoDatabase(player, auth, totpDetails);
            } else {
                messages.send(player, MessageKey.TWO_FACTOR_ENABLE_ERROR_WRONG_CODE);
            }
        }
    }

    private void insertTotpKeyIntoDatabase(Player player, PlayerAuth auth, TotpGenerationResult totpDetails) {
        if (dataSource.setTotpKey(player.getName(), totpDetails.getTotpKey())) {
            messages.send(player, MessageKey.TWO_FACTOR_ENABLE_SUCCESS);
            auth.setTotpKey(totpDetails.getTotpKey());
            playerCache.updatePlayer(auth);
            logger.info("Player '" + player.getName() + "' has successfully added a TOTP key to their account");

            if (bungeeSender.isEnabled()) {
                // As described at https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
                // "Keep in mind that you can't send plugin messages directly after a player joins."
                bukkitService.scheduleSyncDelayedTask(() -> {
                    logger.info("DEBUG - [BC/totpAdd]  " + player.getName());
                    bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.TOTP_ENABLE);
                }, 5L);

            }

            PlayerTOTPConfirmEvent event = new PlayerTOTPConfirmEvent(player);
            Bukkit.getServer().getPluginManager().callEvent(event);

        } else {
            messages.send(player, MessageKey.ERROR);
        }
    }
}
