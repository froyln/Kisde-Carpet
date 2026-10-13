package carpet.prometheus;

import carpet.prometheus.metrics.AbstractMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;

public class MetricUpdater extends TimerTask {
	
	private final PrometheusExtension extension;
    private final List<AbstractMetric> metrics = new ArrayList<>();
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile boolean stopped;
    private long nextFailureLog;

    public MetricUpdater(PrometheusExtension prometheusExtension) {
        this.extension = prometheusExtension;
    }

    @Override
    public void run() {
        MinecraftServer server = this.extension.getServer();
        if (this.stopped || server == null || !server.isServerRunning() || !this.pending.compareAndSet(false, true)) return;
        try {
            server.addScheduledTask(() -> {
                try {
                    if (this.stopped || this.extension.getServer() != server || !server.isServerRunning()) return;
                    for (AbstractMetric metric : this.metrics) {
                        try {
                            metric.getGauge().clear();
                            metric.update(this.extension);
                        } catch (Exception exception) {
                            long now = System.currentTimeMillis();
                            if (now >= this.nextFailureLog) {
                                this.nextFailureLog = now + 60000;
                                LogManager.getLogger().warn("Unable to collect Prometheus metric", exception);
                            }
                        }
                    }
                } finally {
                    this.pending.set(false);
                }
            });
        } catch (RuntimeException e) {
            this.pending.set(false);
            throw e;
        }
    }

    @Override
    public boolean cancel() {
        this.stopped = true;
        return super.cancel();
    }

    public void addMetric(AbstractMetric metric) {
        this.metrics.add(metric);
    }

    public List<AbstractMetric> getMetrics() {
        return metrics;
    }

}
