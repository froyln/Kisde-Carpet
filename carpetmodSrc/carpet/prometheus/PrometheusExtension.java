package carpet.prometheus;

import carpet.prometheus.helpers.client.exporter.HTTPServer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrometheusExtension {

    private static final Logger LOGGER = LogManager.getLogger();
    private HTTPServer httpServer;
    private int port;
    public static volatile MinecraftServer server;
    private MetricReg metricReg = new MetricReg(this);
    Path path = Paths.get("Prometheus config.json");

    public PrometheusExtension() {
        if (!Files.exists(path)) {
            File file = new File("Prometheus config.json");
            try {
                BufferedWriter writer = Files.newBufferedWriter(this.path);
                writer.write("port:1234");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(this.path)) {
            String text = reader.readLine();
            port = Integer.parseInt(text.split(":")[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onServerRun(MinecraftServer minecraftServer) {
        this.server = minecraftServer;
        this.metricReg.registerMetrics();
        this.startPrometheusEndpoint();
    }

    public void onServerStop() {
        this.stopPrometheusEndpoint();
        this.server = null;
        this.metricReg.emptyMetrics();
    }

    private void startPrometheusEndpoint() {
        try {
            this.httpServer = new HTTPServer(this.port);
            LOGGER.info(String.format("Prometheus listener on %d", this.port));
            this.metricReg.runUpdater();
        } catch (IOException e) {
            e.printStackTrace();
            this.stopPrometheusEndpoint();
        }
    }

    private void stopPrometheusEndpoint() {
        if (this.httpServer != null) {
            this.httpServer.close();
        }
        this.metricReg.stopUpdater();
        LOGGER.info("Prometheus stopped");
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public void changePorts(int port) {
        if (port != this.port) {
            try {
                BufferedWriter writer = Files.newBufferedWriter(this.path);
                writer.write("port:"+port);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.port = port;
            this.stopPrometheusEndpoint();
            this.startPrometheusEndpoint();
        }
    }
}
