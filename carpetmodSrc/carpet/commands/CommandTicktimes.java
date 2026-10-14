package carpet.commands;

import carpet.CarpetSettings;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

public class CommandTicktimes extends CommandCarpetBase {
    private static volatile WriteThread writeThread;

    public String getUsage(ICommandSender sender) { return "/ticktimes <start|stop> [filename]"; }
    public String getName() { return "ticktimes"; }
    public int getRequiredPermissionLevel() { return 2; }
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(getRequiredPermissionLevel(), getName());
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!checkPermission(server, sender)) throw new CommandException("commands.generic.permission");
        if (!command_enabled("commandTicktimes", sender)) return;
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        switch (args[0].toLowerCase(Locale.ENGLISH)) {
            case "start":
                if (args.length > 2) throw new WrongUsageException(getUsage(sender));
                if (writeThread != null) throw new CommandException("Already logging or stopping %s", writeThread.file.getName());
                File file = new File(args.length == 2 ? args[1] : "ticktimes.txt");
                try {
                    startLogging(server, file);
                } catch (IOException e) { throw new CommandException("Error logging to file"); }
                notifyCommandListener(sender, this, "Logging ticktimes to %s", file.getPath());
                return;
            case "stop":
                if (args.length != 1) throw new WrongUsageException(getUsage(sender));
                if (writeThread == null) notifyCommandListener(sender, this, "Not currently logging");
                else if (stopLogging()) notifyCommandListener(sender, this, "Stopped logging ticktimes");
                else notifyCommandListener(sender, this, "Ticktime logging is still stopping");
                return;
            default: throw new WrongUsageException(getUsage(sender));
        }
    }

    static void startLogging(MinecraftServer server, File file) throws IOException {
        if (writeThread != null) throw new IOException("A ticktime writer is already active");
        WriteThread next = new WriteThread(server, file);
        writeThread = next;
        next.start();
    }

    public static void record(long duration) {
        WriteThread current = writeThread;
        if (current != null) current.record(duration);
    }

    public static boolean stopLogging() {
        WriteThread current = writeThread;
        if (current == null) return true;
        current.finish();
        try { current.join(5000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return !current.isAlive();
    }

    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (!CarpetSettings.commandTicktimes || !checkPermission(server, sender)) return Collections.emptyList();
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "start", "stop") : Collections.emptyList();
    }

    private static final class WriteThread extends Thread {
        final File file;
        final MinecraftServer server;
        final BufferedWriter writer;
        final LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<>();
        boolean closing;

        WriteThread(MinecraftServer server, File file) throws IOException {
            super("Carpet ticktime writer");
            this.server = server;
            this.file = file;
            this.writer = new BufferedWriter(new FileWriter(file, true));
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }

        synchronized void record(long duration) { if (!closing) queue.offer(duration); }
        synchronized void finish() { if (!closing) { closing = true; queue.offer(-1L); } }

        public void run() {
            try (BufferedWriter output = writer) {
                while (true) {
                    long duration = queue.take();
                    if (duration == -1L) break;
                    output.write(Long.toString(duration));
                    output.newLine();
                    output.flush();
                }
            } catch (InterruptedException | IOException e) {
                CarpetSettings.LOG.error("Ticktime logging stopped after a write failure", e);
                if (server != null && server.isServerRunning()) server.addScheduledTask(() -> carpet.utils.Messenger.print_server_message(server, "Error logging ticktimes; logging has stopped"));
            } finally {
                if (writeThread == this) writeThread = null;
            }
        }
    }
}
