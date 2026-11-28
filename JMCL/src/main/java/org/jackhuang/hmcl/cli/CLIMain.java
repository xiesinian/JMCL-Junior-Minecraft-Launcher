/*
 * JMCL - Junior Minecraft Launcher
 * Copyright (C) 2026 xiesinian and liangchen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.cli;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.game.HMCLGameLauncher;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.ProgressBarMode;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/// Command-line interface of JMCL.
///
/// The interface is entered when the program is started with `--cli` as the first argument,
/// so double-clicking the executable still opens the graphical launcher.
/// Supported commands: `help`, `list`, `java`, `install <version>`, `launch <version> [options]`.
@NotNullByDefault
public final class CLIMain {

    private CLIMain() {
    }

    /// Checks whether the given program arguments request command-line mode.
    ///
    /// @param args the raw program arguments
    /// @return `true` if the first argument is `--cli` or `-c`
    public static boolean isCLI(String @Nullable [] args) {
        return args != null && args.length > 0
                && ("--cli".equalsIgnoreCase(args[0]) || "-c".equalsIgnoreCase(args[0]));
    }

    /// Runs the command-line interface and returns the process exit code.
    ///
    /// @param args the raw program arguments, whose first element is the CLI switch
    /// @return `0` on success, non-zero on failure
    public static int run(String[] args) {
        List<String> rawCommand = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(args, 1, args.length)));
        for (Iterator<String> iterator = rawCommand.iterator(); iterator.hasNext(); ) {
            String argument = iterator.next();
            if ("--progress".equalsIgnoreCase(argument) || "--progress-bar".equalsIgnoreCase(argument)) {
                ProgressBarMode.setOverride(true);
                iterator.remove();
            } else if ("--no-progress".equalsIgnoreCase(argument)) {
                ProgressBarMode.setOverride(false);
                iterator.remove();
            }
        }
        String[] command = rawCommand.toArray(new String[0]);
        if (command.length == 0) {
            return interactive();
        }
        if ("help".equalsIgnoreCase(command[0]) || "-h".equalsIgnoreCase(command[0])) {
            printHelp();
            return 0;
        }

        try {
            initCore();
            return execute(command);
        } catch (Throwable e) {
            err.println("命令执行失败: " + e);
            e.printStackTrace(err);
            return 1;
        }
    }

    /// Initializes the launcher services shared by all commands.
    private static void initCore() throws IOException {
        SettingsManager.init();
        DownloadProviders.init();
        GameDirectoryManager.init();
    }

    /// Executes a single command and returns its exit code.
    ///
    /// @param command the command and its arguments
    private static int execute(String[] command) throws Exception {
        return switch (command[0].toLowerCase(Locale.ROOT)) {
            case "list" -> listVersions();
            case "java" -> listJava();
            case "install" -> installVersion(requireArgument(command, 1, "install <版本号>"));
            case "launch" -> launchVersion(command);
            default -> {
                err.println("未知命令: " + command[0]);
                printHelp();
                yield 2;
            }
        };
    }

    /// Runs the interactive shell, reading commands from the terminal until `exit` or end of input.
    ///
    /// This mode is entered by running `JMCL --cli` without a sub-command.
    private static int interactive() {
        try {
            initCore();
        } catch (Throwable e) {
            err.println("初始化失败: " + e);
            e.printStackTrace(err);
            return 1;
        }

        out.println(Metadata.FULL_TITLE + " 交互式命令行");
        out.println("输入 help 查看命令，输入 exit 退出。");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                out.print("JMCL> ");
                out.flush();

                String line = reader.readLine();
                if (line == null) {
                    out.println();
                    return 0;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] command = line.split("\\s+");
                String name = command[0].toLowerCase(Locale.ROOT);
                if ("exit".equals(name) || "quit".equals(name) || "q".equals(name)) {
                    return 0;
                }
                if ("help".equals(name) || "-h".equals(name)) {
                    printHelp();
                    continue;
                }
                if ("clear".equals(name) || "cls".equals(name)) {
                    out.print("\u001B[H\u001B[2J");
                    out.flush();
                    continue;
                }

                try {
                    execute(command);
                } catch (Throwable e) {
                    err.println("命令执行失败: " + e);
                }
            }
        } catch (IOException e) {
            err.println("读取输入失败: " + e);
            return 1;
        }
    }

    /// Stream receiving normal command output.
    private static PrintStream out = System.out;

    /// Stream receiving error output.
    private static PrintStream err = System.err;

    /// Guards [#runInteractiveCommand(String)] because it temporarily redirects [#out] and [#err].
    private static final Object COMMAND_LOCK = new Object();

    /// Whether the launcher services were already initialized for the built-in console.
    private static boolean coreInitialized = false;

    /// Executes one command typed in the launcher's built-in console and returns the captured output.
    ///
    /// The command runs synchronously and its [#out] / [#err] output is captured into the returned
    /// string. Commands are serialized because the output streams are temporarily redirected.
    ///
    /// @param line the command line, for example `install 1.20.1`
    /// @return the text the command printed
    public static String runInteractiveCommand(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] command = trimmed.split("\\s+");
        if ("exit".equalsIgnoreCase(command[0]) || "quit".equalsIgnoreCase(command[0])) {
            return "";
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        synchronized (COMMAND_LOCK) {
            PrintStream previousOut = out;
            PrintStream previousErr = err;
            try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
                out = capture;
                err = capture;
                try {
                    if (!coreInitialized) {
                        try {
                            initCore();
                        } catch (IllegalStateException alreadyInitialized) {
                            // The graphical launcher has already loaded these services.
                        }
                        coreInitialized = true;
                    }
                    if ("help".equalsIgnoreCase(command[0]) || "-h".equalsIgnoreCase(command[0])) {
                        printHelp();
                    } else if ("clear".equalsIgnoreCase(command[0]) || "cls".equalsIgnoreCase(command[0])) {
                        // Nothing to clear in the captured output.
                    } else {
                        execute(command);
                    }
                } catch (Throwable e) {
                    e.printStackTrace(capture);
                }
            } finally {
                out = previousOut;
                err = previousErr;
            }
        }

        return buffer.toString(StandardCharsets.UTF_8);
    }

    /// Lists all installed game versions in the currently selected game directory.
    private static int listVersions() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        out.println("游戏目录: " + repository.getBaseDirectory());
        for (Version version : repository.getVersions()) {
            out.println(" - " + version.getId());
        }
        return 0;
    }

    /// Prints the Java runtimes detected on this machine.
    private static int listJava() throws InterruptedException {
        JavaManager.initialize();
        out.println("Java 版本: " + JavaRuntime.CURRENT_VERSION);
        for (JavaRuntime runtime : JavaManager.getAllJava()) {
            out.println(" - " + runtime.getVersion() + " [" + runtime.getArchitecture() + "] " + runtime.getBinary());
        }
        return 0;
    }

    /// Downloads and installs a vanilla game version into the selected game directory.
    ///
    /// @param versionId the game version to install, for example `1.20.1`
    @SuppressWarnings("unchecked")
    private static int installVersion(String versionId) throws Exception {
        DownloadProvider provider = DownloadProviders.getDownloadProvider();
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();

        VersionList<GameRemoteVersion> versionList =
                (VersionList<GameRemoteVersion>) provider.getVersionListById("game");
        if (!versionList.isLoaded()) {
            out.println("正在获取版本列表...");
            versionList.refreshAsync().run();
        }

        GameRemoteVersion remote = versionList.getVersion(versionId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("未找到游戏版本: " + versionId));

        out.println("开始下载 " + versionId + " ...");
        DefaultDependencyManager dependencyManager =
                new DefaultDependencyManager(repository, provider, HMCLCacheRepository.REPOSITORY);
        var installTask = remote.getInstallTask(dependencyManager, new Version(versionId));
        withProgressBar("下载并安装 " + versionId, () -> {
            installTask.run();
            return null;
        });
        repository.refreshVersionsAsync().run();
        out.println("安装完成: " + versionId);
        return 0;
    }

    /// Launches an installed game version.
    ///
    /// Supported options: `--username <名称>`, `--memory <MB>`.
    ///
    /// @param command the CLI arguments starting with the `launch` sub-command
    private static int launchVersion(String[] command) throws Exception {
        String versionId = requireArgument(command, 1, "launch <版本号> [--username 名称] [--memory MB]");
        String username = parseOption(command, "--username");
        String memoryText = parseOption(command, "--memory");

        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        Version version = repository.getVersion(versionId);

        JavaManager.initialize();
        JavaRuntime javaRuntime = JavaManager.findSuitableJava(
                GameVersionNumber.asGameVersion(repository.getGameVersion(version)), version);
        if (javaRuntime == null) {
            err.println("未找到适合 " + versionId + " 的 Java，请先在图形界面中安装 Java。");
            return 1;
        }

        String name = username != null ? username : "JMCL";
        AuthInfo authInfo = new AuthInfo(name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)),
                "-", AuthInfo.USER_TYPE_LEGACY, "{}");

        LaunchOptions.Builder builder = repository.getLaunchOptions(
                versionId, javaRuntime, repository.getBaseDirectory(),
                new ArrayList<>(), new ArrayList<>(), false);
        if (memoryText != null) {
            int memory = Integer.parseInt(memoryText);
            builder.setMaxMemory(memory);
            builder.setMinMemory(Math.min(512, memory));
        }

        out.println("使用 Java: " + javaRuntime.getBinary());
        var process = withProgressBar("启动 " + versionId,
                () -> new HMCLGameLauncher(repository, version, authInfo, builder.create(), null, true).launch());
        out.println("游戏已启动，进程 PID: " + process.getProcess().pid());
        return 0;
    }

    /// Runs the given action while drawing a text progress bar with random Minecraft bug trivia.
    ///
    /// When the progress bar mode is disabled the action runs directly without any extra output.
    ///
    /// @param label the description shown next to the progress bar
    /// @param action the action to run
    /// @param <T> the action result type
    /// @return the action result
    private static <T> T withProgressBar(String label, Callable<T> action) throws Exception {
        if (!ProgressBarMode.isEnabled()) {
            return action.call();
        }

        AtomicBoolean running = new AtomicBoolean(true);
        Thread printer = new Thread(() -> {
            int percent = 0;
            long lastBug = System.currentTimeMillis();
            try {
                while (running.get()) {
                    StringBuilder bar = new StringBuilder("\r[");
                    int filled = percent / 5;
                    for (int i = 0; i < 20; i++) {
                        bar.append(i < filled ? '#' : '-');
                    }
                    bar.append("] ").append(String.format("%3d%%", percent)).append(' ').append(label);
                    out.print(bar);
                    out.flush();

                    if (System.currentTimeMillis() - lastBug >= 5000) {
                        out.println();
                        out.println("  * " + ProgressBarMode.randomBug());
                        lastBug = System.currentTimeMillis();
                    }

                    if (percent < 95) {
                        percent++;
                    }
                    Thread.sleep(400);
                }
            } catch (InterruptedException ignored) {
            }
        }, "jmcl-progress");
        printer.setDaemon(true);
        printer.start();

        try {
            return action.call();
        } finally {
            running.set(false);
            printer.interrupt();
            printer.join(1000);
            out.println();
            out.println("完成: " + label);
        }
    }

    /// Reads the value following the given option name.
    ///
    /// @param command the CLI arguments
    /// @param option the option name, for example `--username`
    /// @return the option value, or `null` if the option is absent
    private static @Nullable String parseOption(String[] command, String option) {
        for (int i = 0; i + 1 < command.length; i++) {
            if (option.equalsIgnoreCase(command[i])) {
                return command[i + 1];
            }
        }
        return null;
    }

    /// Returns the argument at the given index or throws with a usage hint.
    ///
    /// @param command the CLI arguments
    /// @param index the argument index
    /// @param usage the usage example shown on failure
    /// @return the argument value
    private static String requireArgument(String[] command, int index, String usage) {
        if (command.length <= index) {
            throw new IllegalArgumentException("用法: " + usage);
        }
        return command[index];
    }

    /// Prints the usage information of the command-line interface.
    private static void printHelp() {
        List<String> lines = List.of(
                Metadata.FULL_TITLE + " 命令行",
                "",
                "用法: JMCL --cli <命令> [参数]",
                "      JMCL --cli                 进入交互式命令行，可连续输入命令",
                "",
                "命令:",
                "  help                              显示本帮助",
                "  list                              列出已安装的游戏版本",
                "  java                              列出检测到的 Java",
                "  install <版本号>                  下载并安装原版游戏，例如 1.20.1",
                "  launch <版本号> [--username 名称] [--memory MB]",
                "                                    启动已安装的游戏",
                "  exit, quit                        退出命令行（在启动器控制台中会关闭窗口）",
                "",
                "全局选项:",
                "  --progress, --progress-bar        用进度条显示下载与启动，并随机显示 MC 冷知识",
                "  --no-progress                     关闭进度条，使用普通输出",
                "",
                "示例:",
                "  JMCL --cli install 1.20.1",
                "  JMCL --cli --progress install 1.20.1",
                "  JMCL --cli launch 1.20.1 --username Steve --memory 4096");
        lines.forEach(out::println);
    }
}
