package carpet.prometheus;

import carpet.prometheus.metrics.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class MetricReg {
	
	private final PrometheusExtension extension;
    private Timer timer;
    private MetricUpdater updater;
    private final List<AbstractMetric> metrics = new ArrayList<>();
    
    public MetricReg(PrometheusExtension prometheusExtension) {
        this.extension = prometheusExtension;
    }

	public void registerMetrics() {
		MSPTMetric msptMetric = new MSPTMetric("mspt", "Current MSPT on server.");
        msptMetric.getGauge().register();
        
        TPSMetric tpsMetric = new TPSMetric("tps", "Current TPS on server.");
        tpsMetric.getGauge().register();

        LoadedChunksMetrics loadedChunksMetrics = new LoadedChunksMetrics("loaded_chunks", "Amount of loaded chunks.");
        loadedChunksMetrics.getGauge().register();
        
        EntitiesMetrics entitiesMetrics = new EntitiesMetrics("entities", "Amount of entities.");
        entitiesMetrics.getGauge().register();
        
        TileEntitiesMetrics blockEntitiesMetrics = new TileEntitiesMetrics("tile_entities", "Amount of tile entities");
        blockEntitiesMetrics.getGauge().register();
        
        RamMetric ramMetric = new RamMetric("ram", "Usage of RAM.");
        ramMetric.getGauge().register();
        
        OnlinePlayersMetrics onlinePlayersMetrics = new OnlinePlayersMetrics("online_players", "Connected players");
        onlinePlayersMetrics.getGauge().register();
        
        metrics.add(msptMetric);
        metrics.add(tpsMetric);
        metrics.add(loadedChunksMetrics);
        metrics.add(entitiesMetrics);
        metrics.add(blockEntitiesMetrics);
        metrics.add(ramMetric);
        metrics.add(onlinePlayersMetrics);
	}
    public void emptyMetrics(){
        stopUpdater();
        for(AbstractMetric metric : metrics){
            metric.getGauge().unregister();
            metric.getGauge().clear();
        }
        metrics.clear();
    }

	public void runUpdater() {
        stopUpdater();
        this.timer = new Timer();
        MetricUpdater metricUpdater = new MetricUpdater(this.extension);
        this.updater = metricUpdater;
        for (AbstractMetric m : metrics) {
            metricUpdater.addMetric(m);
        }
        this.timer.schedule(metricUpdater, 1000, 1000);
	}

    public void stopUpdater() {
        if (this.updater != null) this.updater.cancel();
        if (this.timer != null) this.timer.cancel();
        this.updater = null;
        this.timer = null;
    }

	public Timer getTimer() {
		return this.timer;
	}

}
