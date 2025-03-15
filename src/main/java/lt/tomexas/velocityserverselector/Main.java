package lt.tomexas.velocityserverselector;

import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import us.ajg0702.queue.api.AjQueueAPI;
import us.ajg0702.queue.api.events.PositionChangeEvent;
import us.ajg0702.queue.api.events.PreQueueEvent;
import us.ajg0702.queue.api.players.QueuePlayer;
import us.ajg0702.queue.api.queues.QueueServer;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@Plugin(id = "velocityserverselector", name = "VelocityServerSelector", version = BuildConstants.VERSION)
public class Main {

    private final ProxyServer server;
    Logger logger = Logger.getLogger("velocityserverselector");
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("lobby:server_selector");

    @Inject
    public Main(ProxyServer server) {
        this.server = server;
        this.server.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        AjQueueAPI.getInstance().listen(PreQueueEvent.class, e -> {
            try {
                Player player = server.getPlayer(e.getPlayer().getUniqueId()).orElse(null);
                if (player == null) return;

                Optional<ServerConnection> serverConnection = player.getCurrentServer();
                if (serverConnection.isEmpty()) return;

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(stream);

                out.writeUTF("PlayerInQueue");
                out.writeUTF(player.getUniqueId().toString());
                out.writeBoolean(true);

                serverConnection.get().sendPluginMessage(CHANNEL, stream.toByteArray());
                logger.info("[DEBUG] Sent 'PlayerInQueue' message to " + player.getUsername());
            } catch (IOException err) {
                logger.warning("[WARN] Failed to send PlayerInQueue message: " + err.getMessage());
            }
        });

        AjQueueAPI.getInstance().listen(PositionChangeEvent.class, e -> {
            try {
                Player player = server.getPlayer(e.getPlayer().getUniqueId()).orElse(null);
                if (player == null) return;

                Optional<ServerConnection> serverConnection = player.getCurrentServer();
                if (serverConnection.isEmpty()) return;

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(stream);

                out.writeUTF("PlayerPositionChanged");
                out.writeUTF(player.getUniqueId().toString());
                out.writeInt(e.getPosition());
                out.writeInt(e.getQueue().getQueueHolder().getAllPlayers().size());

                serverConnection.get().sendPluginMessage(CHANNEL, stream.toByteArray());
                logger.info("[DEBUG] Sent 'PlayerPositionChanged' message to " + player.getUsername());
            } catch (IOException err) {
                logger.warning("[WARN] Failed to send PlayerPositionChanged message: " + err.getMessage());
            }
        });
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        logger.info("[DEBUG] Received plugin message on channel " + CHANNEL.getId());
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(event.getData());
            DataInputStream in = new DataInputStream(stream);

            String action = in.readUTF();
            String playerUUID  = in.readUTF();
            String serverName = in.readUTF();

            /*logger.info("Action: " + action);
            logger.info("Player UUID: " + playerUUID);
            logger.info("Server: " + serverName);*/

            if (action.equals("RemoveFromQueue")) {
                QueueServer queueServer = AjQueueAPI.getInstance().getQueueManager().findServer(serverName);
                QueuePlayer player = queueServer.getQueueHolder().getAllPlayers().stream()
                        .filter(queuePlayer -> queuePlayer.getUniqueId().equals(UUID.fromString(playerUUID)))
                        .findFirst().orElse(null);
                queueServer.removePlayer(player);
                logger.info("[DEBUG] Removed player " + playerUUID + " from queue " + serverName);
            }
        } catch (Exception e) {
            logger.warning("[WARN] Failed to read plugin message data: " + e.getMessage());
        }
    }
}
