package carpet.prometheus.metrics;

import carpet.prometheus.PrometheusExtension;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.HashMap;

public class EntitiesMetrics extends AbstractMetric {
	
	public EntitiesMetrics(String name, String help) {
        super(name, help, "world", "group", "type");
    }

    @Override
    public void update(PrometheusExtension extension) {
    	for (WorldServer world : extension.getServer().worlds) {
    		
    		HashMap<String, Integer> players = new HashMap<>();
            HashMap<String, Integer> items = new HashMap<>();
            HashMap<String, Integer> livingEntities = new HashMap<>();
            
            world.loadedEntityList.forEach(entity -> {
            	if (entity instanceof EntityPlayerMP) {
            		players.put("player", players.getOrDefault("player", 0) + 1);
                } else if (entity instanceof EntityItem) {
            		items.put(((EntityItem)entity).getItem().getDisplayName(), items.getOrDefault(((EntityItem)entity).getItem().getDisplayName(), 0) + 1);
            	} else {
                    ResourceLocation key = EntityList.getKey(entity);
                    if (key != null) livingEntities.put(key.getPath(), livingEntities.getOrDefault(key.getPath(), 0) + 1);
            	}
            });
            
            if(!players.isEmpty()) this.getGauge().labels(world.provider.getDimensionType().getName(), "player", "Player").set(players.get("player"));
            
            for (String itemName : items.keySet()) {
            	this.getGauge().labels(world.provider.getDimensionType().getName(), "item", itemName).set(items.get(itemName));
            }
            
            for (String entityName : livingEntities.keySet()) {
            	Class<? extends Entity> entityClass = EntityList.REGISTRY.getObject(new ResourceLocation(entityName));
            	if(IAnimals.class.isAssignableFrom(entityClass)) {
            		for (EnumCreatureType enumcreaturetype : EnumCreatureType.values()) {
                		if (enumcreaturetype.getCreatureClass().isAssignableFrom(EntityList.REGISTRY.getObject(new ResourceLocation(entityName)))) {
                			this.getGauge().labels(world.provider.getDimensionType().getName(), enumcreaturetype.name().toLowerCase(), EntityList.getTranslationName(new ResourceLocation(entityName))).set(livingEntities.get(entityName));
                			break;
            			}
                	}
            	} else {
            		this.getGauge().labels(world.provider.getDimensionType().getName(), "item", EntityList.getTranslationName(new ResourceLocation(entityName))).set(livingEntities.get(entityName));
            	}
            }
            
        }
    }

}
