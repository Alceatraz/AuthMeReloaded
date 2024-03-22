package fr.xephi.authme.context;


import com.earth2me.essentials.libs.checkerframework.checker.nullness.qual.NonNull;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.broadcast.PlayerTOTPConfirmEvent;
import fr.xephi.authme.broadcast.PlayerTOTPRemoveEvent;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Authme2FAStatusCalculator implements Listener, ContextCalculator<Player> {

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private static final String CONTEXT_NAME = "totp";
    private static final String CONTEXT_Y = "enabled";
    private static final String CONTEXT_N = "disable";

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder()
            .add(CONTEXT_NAME, CONTEXT_Y)
            .add(CONTEXT_NAME, CONTEXT_N)
            .build();
    }

    @Override
    public void calculate(@NonNull Player player, @NonNull ContextConsumer consumer) {
        String name = player.getName();
        boolean status = CACHE.computeIfAbsent(name, it -> AuthMeApi.getInstance().isTOTPEnabled(it));
        consumer.accept(CONTEXT_NAME, status ? CONTEXT_Y : CONTEXT_N);
    }

    @EventHandler
    public void confirm(PlayerTOTPConfirmEvent event) {
        CACHE.put(event.getPlayer().getName(), true);
    }

    @EventHandler
    public void remove(PlayerTOTPRemoveEvent event) {
        CACHE.put(event.getPlayer().getName(), false);
    }
}
