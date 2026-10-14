package carpet.prometheus;

import carpet.prometheus.helpers.client.exporter.HTTPServer;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrometheusExtension {

    private static final Logger LOGGER = LogManager.getLogger();
    private HTTPServer httpServer;
    private int port = 1234;
    public static volatile MinecraftServer server;
    private MetricReg metricReg = new MetricReg(this);
    final Path path;

    public PrometheusExtension() {
        this(Paths.get("Prometheus config.json"));
    }

    PrometheusExtension(Path path) {
        this.path = path;
        if (!Files.exists(path)) {
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                writer.write("port:1234");
            } catch (IOException e) {
                LOGGER.warn("Unable to create Prometheus configuration; using port 1234", e);
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String text = reader.readLine();
            if (text == null || !text.startsWith("port:")) throw new IllegalArgumentException("Expected port:<number>");
            int configured = Integer.parseInt(text.substring(5).trim());
            if (configured < 2 || configured > 65535) throw new IllegalArgumentException("Port must be between 2 and 65535");
            port = configured;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Invalid Prometheus configuration; using port 1234", e);
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
