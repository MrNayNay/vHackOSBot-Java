package net.olympiccode.vhackos.bot.core.server;

import io.sentry.Sentry;
import net.olympiccode.vhackos.api.entities.AppType;
import net.olympiccode.vhackos.api.server.Server;
import net.olympiccode.vhackos.bot.core.BotService;
import net.olympiccode.vhackos.bot.core.vHackOSBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ServerService implements BotService {

    public static ScheduledExecutorService serverService;
    Logger LOG = LoggerFactory.getLogger("ServerService");

    public ServerService() {
        LOG.info("Creating ServerService...");
        serverService = Executors.newScheduledThreadPool(1, new ServerServiceFactory());
    }


    @Override
    public ScheduledExecutorService getService() {
        return serverService;
    }

    @Override
    public void setup() {
        LOG.info("Setting up ServerSerice...");
        if (serverService.isTerminated() || serverService.isShutdown()) {
            serverService = Executors.newScheduledThreadPool(1, new ServerServiceFactory());
        }
        if (vHackOSBot.api.getAppManager().getApp(AppType.Server).isInstalled()) {
            serverService.scheduleAtFixedRate(() -> runService(), 0, 330000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void runService() {
        try {
            Server server = vHackOSBot.api.getServer();
            server.update();
            if (server.getPackages() > 0) {
                Server.OpenResult result = server.openAllPacks();
                LOG.info("Opened " + server.getPackages() + " server packages, got " + result.getServer() + " server, " + result.getAv() + " av, " + result.getFw() + " fw and " + result.getBoost() + " boosters.");
            }
            server.update();
            if (ServerConfigValues.upgradeNodes) {
                if (server.getServerPieces() > 9) {
                    int times = 0;
                    int cur = server.getServerStrength();
                    LOG.info("Upgrading server's server...");
                    while (server.getServerPieces() > 9 && (server.getServerStrength() < server.getServerStrengthMax())) {
                        if (server.upgrade(Server.NODE_TYPE.SERVER, 1)) times++;
                    }
                    server.update();
                    LOG.info("Upgraded server's server " + times + " times. (" + cur + "->" + server.getServerStrength() + ")");
                }

                server.update();
                int fwNodes = (int) Arrays.stream(server.getFirewallStrength()).filter(value -> value != 0).count();
                for (int i = 0; i < fwNodes; i++) {
                    if (server.getFirewallPieces() > 9) {
                        LOG.info("Upgrading server's firewall node " + (i + 1) + "...");
                        int times = 0;
                        int cur = server.getFirewallStrength()[i];
                        while (server.getFirewallPieces() > 9 && (server.getFirewallStrength()[i] < server.getFirewallStrengthMax()[i])) {
                            if (server.upgrade(Server.NODE_TYPE.FW, i + 1)) times++;
                        }
                        server.update();
                        LOG.info("Upgraded server's firewall node " + (i + 1) + " " + times + " times. (" + cur + "->" + server.getFirewallStrength()[i] + ")");
                    }
                }


                server.update();
                int avNodes = (int) Arrays.stream(server.getAntivirusStrength()).filter(value -> value != 0).count();
                for (int i = 0; i < avNodes; i++) {
                    if (server.getAntivirusPieces() > 9) {
                        LOG.info("Upgrading server's antivirus node " + (i + 1) + "...");
                        int times = 0;
                        int cur = server.getAntivirusStrength()[0];
                        while (server.getAntivirusPieces() > 9 && (server.getAntivirusStrength()[i] < server.getAntivirusStrengthMax()[i])) {
                            if (server.upgrade(Server.NODE_TYPE.AV, i + 1)) times++;
                        }
                        server.update();
                        LOG.info("Upgraded server's antivirus node " + (i + 1) + " " + times + " times. (" + cur + "->" + server.getAntivirusStrength()[i] + ")");
                    }
                }
            }

        } catch (Exception e) {
            Sentry.capture(e);
            LOG.warn("The server service has been shutdown due to an error.");
            e.printStackTrace();
            serverService.shutdownNow();
        }
    }

    public class ServerServiceFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            return new Thread(r, "vHackOSBot-ServerService");
        }
    }
}
