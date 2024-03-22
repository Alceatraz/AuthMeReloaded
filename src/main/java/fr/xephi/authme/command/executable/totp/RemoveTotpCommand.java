package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.broadcast.PlayerTOTPConfirmEvent;
import fr.xephi.authme.broadcast.PlayerTOTPRemoveEvent;
import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.security.totp.TotpAuthenticator;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.service.bungeecord.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Command for a player to remove 2FA authentication.
 */
public class RemoveTotpCommand extends PlayerCommand {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RemoveTotpCommand.class);

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private TotpAuthenticator totpAuthenticator;

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
        } else if (auth.getTotpKey() == null) {
            messages.send(player, MessageKey.TWO_FACTOR_NOT_ENABLED_ERROR);
        } else {
            if (totpAuthenticator.checkCode(auth, arguments.get(0))) {
                removeTotpKeyFromDatabase(player, auth);
            } else {
                messages.send(player, MessageKey.TWO_FACTOR_INVALID_CODE);
            }
        }
    }

    private void removeTotpKeyFromDatabase(Player player, PlayerAuth auth) {
        if (dataSource.removeTotpKey(auth.getNickname())) {
            auth.setTotpKey(null);
            playerCache.updatePlayer(auth);
            messages.send(player, MessageKey.TWO_FACTOR_REMOVED_SUCCESS);
            logger.info("Player '" + player.getName() + "' removed their TOTP key");

            if (bungeeSender.isEnabled()) {
                // As described at https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/
                // "Keep in mind that you can't send plugin messages directly after a player joins."
                bukkitService.scheduleSyncDelayedTask(() -> {
                    logger.info("DEBUG - [BC/totpAdd]  " + player.getName());
                    bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.TOTP_DISABLE);
                }, 5L);
            }

            PlayerTOTPRemoveEvent event = new PlayerTOTPRemoveEvent(player);
            Bukkit.getServer().getPluginManager().callEvent(event);

        } else {
            messages.send(player, MessageKey.ERROR);
        }
    }
}
