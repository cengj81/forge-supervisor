package com.example.supervisor;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@Mod("worldsupervisor")
public class SupervisorMod {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final List<Process> PROCESSES = new ArrayList<>();

    public SupervisorMod() {
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process p : PROCESSES) {
                if (p.isAlive()) p.destroyForcibly();
            }
        }));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        File modsDir = new File("mods");
        File pluginDir = new File(modsDir, "ws");
        File bootstrapScript = new File(modsDir, "world-supervisor_bootstrap.sh");

        if (!bootstrapScript.exists()) {
            LOGGER.warn("[supervisor] bootstrap script not found");
            return;
        }

        pluginDir.mkdirs();
        bootstrapScript.setExecutable(true);

        File configPath = new File(pluginDir, "supervisor.yml");
        File logFile = new File(pluginDir, "bootstrap.log");

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", bootstrapScript.getAbsolutePath());
            pb.environment().put("WS_PROCESS_CWD", new File("").getAbsolutePath());
            pb.environment().put("WS_PLUGIN_DIR", pluginDir.getAbsolutePath());
            pb.environment().put("WS_CONFIG_PATH", configPath.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);
            Process bootstrap = pb.start();
            LOGGER.info("[supervisor] bootstrap started");
            bootstrap.waitFor(120, TimeUnit.SECONDS);
            LOGGER.info("[supervisor] bootstrap finished");
        } catch (Exception e) {
            LOGGER.error("[supervisor] bootstrap failed", e);
            return;
        }

        // start processes defined in supervisor.yml
        startProcesses(pluginDir);
    }

    private void startProcesses(File pluginDir) {
        File[] dirs = { 
            new File(pluginDir, "xy"),
            new File(pluginDir, "sb"),
            new File(pluginDir, "cf"),
            new File(pluginDir, "gost"),
            new File(pluginDir, "td")
        };

        for (File dir : dirs) {
            File startup = new File(dir, "startup.sh");
            if (!startup.exists()) continue;
            startup.setExecutable(true);
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", startup.getAbsolutePath());
                pb.directory(dir);
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File(dir, "process.log"));
                Process p = pb.start();
                PROCESSES.add(p);
                LOGGER.info("[supervisor] started: {}", dir.getName());
            } catch (Exception e) {
                LOGGER.error("[supervisor] failed to start: {}", dir.getName(), e);
            }
        }
    }
}
