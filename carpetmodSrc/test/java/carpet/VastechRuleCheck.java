package carpet;

import carpet.helpers.ClusterHelper;
import carpet.helpers.SignSignal;
import net.minecraft.block.BlockObserver;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import java.util.Arrays;

public final class VastechRuleCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkClusterPermission() throws Exception {
        net.minecraft.command.ICommandSender denied = (net.minecraft.command.ICommandSender) java.lang.reflect.Proxy.newProxyInstance(
                VastechRuleCheck.class.getClassLoader(), new Class<?>[] {net.minecraft.command.ICommandSender.class},
                (proxy, method, arguments) -> method.getName().equals("canUseCommand") ? false : null);
        carpet.commands.CommandCluster command = new carpet.commands.CommandCluster();
        check(!command.checkPermission(null, denied) && command.getRequiredPermissionLevel() == 2, "Cluster permission missing");
        try {
            command.execute(null, denied, new String[] {"loadGrid"});
            throw new AssertionError("Unauthorized cluster command executed");
        } catch (net.minecraft.command.CommandException expected) {
            check(expected.getMessage().equals("commands.generic.permission"), "Permission denial reached world state");
        }
    }

    private static void checkSuppressionLogging() {
        java.util.List<Object[]> calls = new java.util.ArrayList<>();
        org.apache.logging.log4j.Logger logger = (org.apache.logging.log4j.Logger) java.lang.reflect.Proxy.newProxyInstance(
                VastechRuleCheck.class.getClassLoader(), new Class<?>[] {org.apache.logging.log4j.Logger.class},
                (proxy, method, arguments) -> { if (method.getName().equals("fatal")) calls.add(arguments); return null; });
        boolean previousFix = CarpetSettings.updateSuppressionCrashFix;
        CarpetSettings.updateSuppressionCrashFix = false;
        CarpetSettings.disableUpdateSuppressionLogging = true;
        net.minecraft.util.Util.runTask(new java.util.concurrent.FutureTask<Object>(() -> {
            throw new carpet.helpers.ThrowableSuppression("fixture");
        }), logger);
        check(calls.size() == 1 && calls.get(0).length == 1, "Suppression log retained stack trace");
        calls.clear();
        net.minecraft.util.Util.runTask(new java.util.concurrent.FutureTask<Object>(() -> {
            throw new IllegalStateException("fixture");
        }), logger);
        check(calls.size() == 1 && calls.get(0).length == 2 && calls.get(0)[1] instanceof Throwable, "Unrelated exception lost stack trace");
        calls.clear();
        CarpetSettings.disableUpdateSuppressionLogging = false;
        net.minecraft.util.Util.runTask(new java.util.concurrent.FutureTask<Object>(() -> {
            throw new carpet.helpers.ThrowableSuppression("fixture");
        }), logger);
        check(calls.size() == 1 && calls.get(0).length == 2, "Default suppression logging changed");
        CarpetSettings.updateSuppressionCrashFix = previousFix;
    }

    public static void main(String[] args) throws Exception {
        checkClusterPermission();
        for (String rule : new String[] {"updateTabEveryGametick", "alwaysSetPlayerIntoSurvival", "commandEnderchest", "commandInventory", "scoreboardStats", "blockStateSyncing", "extremeBehaviours", "prometheusIntegration"}) {
            check(Arrays.asList(CarpetSettings.getCategories(rule)).contains(CarpetSettings.RuleCategory.BULLET), "Bullet attribution: " + rule);
            check(!Arrays.asList(CarpetSettings.getCategories(rule)).contains(CarpetSettings.RuleCategory.KISDE), "Misattributed Bullet rule: " + rule);
        }
        check(Arrays.asList(CarpetSettings.getCategories("schematicPaste")).contains(CarpetSettings.RuleCategory.KISDE), "Kisde category missing");
        for (String rule : new String[] {"commandCluster", "tickVillages", "freezeBlockEventsOnTickFreeze", "disableLightUpdates", "TNTdoesNotPrime", "setBlockCanPlaceLitObservers", "disableUpdateSuppressionLogging", "totallyNoBlockUpdates", "disableBlockSelfChecks", "castSuppressorCrashFix", "signComparatorOutput", "signComparatorDisplay"}) {
            check(Arrays.asList(CarpetSettings.findAll("vastech")).contains(rule), "Vastech filtering: " + rule);
            check(CarpetSettings.getDefault(rule).equals(rule.equals("tickVillages") || rule.equals("freezeBlockEventsOnTickFreeze") ? "true" : "false"), "Changed default: " + rule);
        }
        ClusterHelper helper = new ClusterHelper();
        NBTTagCompound parameters = new NBTTagCompound();
        parameters.setInteger("endX", 1);
        parameters.setInteger("endZ", 1);
        parameters.setInteger("hashSize", 32);
        parameters.setInteger("clusterWidth", 8);
        parameters.setInteger("maxClusterHeight", 8);
        helper.readFromNBT(parameters);
        check(helper.compute() >= 1 && helper.optimalTargetChunk != null && helper.checkClusterLoader() == 0, "Zero clustering/connectivity failed");
        check(helper.optimalLoadingGrid.containsAll(helper.optimalClusterChunks), "Grid misses cluster chunks");
        int[] hashes = new int[21];
        Arrays.fill(hashes, 1);
        hashes[20] = 30;
        check(ClusterHelper.getHashLength(20, hashes, 30, 6, 32) == 3, "Hash wrap failed");
        parameters.setInteger("desiredClustering", 20);
        helper.readFromNBT(parameters);
        check(helper.compute() > 0, "Positive clustering failed");
        parameters.setInteger("desiredClustering", Integer.MAX_VALUE);
        helper.readFromNBT(parameters);
        check(helper.compute() == -1 && helper.optimalTargetChunk == null && helper.optimalLoadingGrid.isEmpty(), "Stale failed result");
        parameters.setInteger("hashSize", Integer.MAX_VALUE);
        helper.readFromNBT(parameters);
        try { helper.validate(); throw new AssertionError("Unbounded hash accepted"); }
        catch (IllegalArgumentException expected) { }
        String[] inputs = {"0", "0b1010", "0xF", "017", "-9", "999", "invalid", "0b", "2147483648"};
        int[] signals = {0, 10, 15, 15, 0, 15, 0, 0, 0};
        for (int i = 0; i < inputs.length; i++) check(SignSignal.parse(inputs[i]) == signals[i], "Sign parse: " + inputs[i]);
        Bootstrap.register();
        CarpetSettings.setBlockCanPlaceLitObservers = false;
        check(!Blocks.OBSERVER.getStateFromMeta(8).getValue(BlockObserver.POWERED), "Observer baseline changed");
        CarpetSettings.setBlockCanPlaceLitObservers = true;
        for (int meta = 0; meta < 16; meta++) {
            check(Blocks.OBSERVER.getStateFromMeta(meta).getValue(BlockObserver.POWERED) == ((meta & 8) != 0), "Observer powered bit lost");
        }
        CarpetSettings.setBlockCanPlaceLitObservers = false;
        checkSuppressionLogging();
        VastechWorldCheck.run();
        System.out.println("Vastech category, clustering, bounds, sign and observer checks passed");
    }
}
