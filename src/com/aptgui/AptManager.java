package com.aptgui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 封装 APT 相关命令调用：搜索、查询详情、安装。
 * 所有命令均通过 ProcessBuilder 执行，输出通过回调实时推送。
 */
public class AptManager {

    private static final int CMD_TIMEOUT_SECONDS = 120;

    /**
     * 执行 apt-cache search 搜索软件包。
     * @param keyword 搜索关键词
     * @return 匹配的软件包列表（名称 + 简短描述）
     */
    public List<PackageInfo> search(String keyword) throws IOException, InterruptedException {
        List<PackageInfo> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }

        ProcessBuilder pb = new ProcessBuilder("apt-cache", "search", "--names-only", keyword.trim());
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // apt-cache search 输出格式: "包名 - 描述"
                int dash = line.indexOf(" - ");
                if (dash > 0) {
                    String name = line.substring(0, dash).trim();
                    String desc = line.substring(dash + 3).trim();
                    results.add(new PackageInfo(name, desc));
                }
            }
        }
        proc.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return results;
    }

    /**
     * 查询软件包详细信息（apt-cache show）。
     */
    public PackageInfo show(String packageName) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("apt-cache", "show", packageName);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        Map<String, String> fields = new HashMap<>();
        StringBuilder longDesc = new StringBuilder();
        boolean inDescription = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(" ")) {
                    // 续行（描述的多行内容）
                    if (inDescription) {
                        longDesc.append(line.trim()).append("\n");
                    }
                    continue;
                }
                inDescription = false;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    fields.put(key, value);
                    if (key.equals("Description")) {
                        inDescription = true;
                        longDesc.append(value).append("\n");
                    }
                }
            }
        }
        proc.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (fields.isEmpty()) {
            return null;
        }

        boolean installed = isInstalled(packageName);

        return new PackageInfo(
                packageName,
                fields.getOrDefault("Version", "—"),
                fields.getOrDefault("Section", "—"),
                fields.getOrDefault("Architecture", "—"),
                fields.getOrDefault("Installed-Size", "—"),
                fields.getOrDefault("Depends", "—"),
                fields.getOrDefault("Description", ""),
                longDesc.toString(),
                installed
        );
    }

    /**
     * 检查软件包是否已安装（dpkg -s）。
     */
    public boolean isInstalled(String packageName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dpkg", "-s", packageName);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Status:") && line.contains("install ok installed")) {
                        return true;
                    }
                }
            }
            proc.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            return false;
        }
        return false;
    }

    /**
     * 安装软件包，使用 pkexec 提权（图形化密码提示）。
     * 实时输出通过 outputConsumer 回调推送。
     * @return 进程退出码（0 表示成功）
     */
    public int install(String packageName, Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        // 使用 pkexec 弹出图形化认证窗口；apt-get 非交互模式
        ProcessBuilder pb = new ProcessBuilder(
                "pkexec", "apt-get", "install", "-y", packageName);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // 读取输出流
        Thread readerThread = new Thread(() -> {
            try (InputStream is = proc.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputConsumer != null) {
                        outputConsumer.accept(line);
                    }
                }
            } catch (IOException e) {
                if (outputConsumer != null) {
                    outputConsumer.accept("[读取输出错误] " + e.getMessage());
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished = proc.waitFor(300, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            if (outputConsumer != null) {
                outputConsumer.accept("[错误] 安装超时，进程已强制终止。");
            }
            return -1;
        }
        readerThread.join(5000);
        return proc.exitValue();
    }

    /**
     * 执行 apt-get update（可选，用于刷新软件源缓存）。
     */
    public int update(Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("pkexec", "apt-get", "update");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        Thread readerThread = new Thread(() -> {
            try (InputStream is = proc.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputConsumer != null) {
                        outputConsumer.accept(line);
                    }
                }
            } catch (IOException e) {
                if (outputConsumer != null) {
                    outputConsumer.accept("[读取输出错误] " + e.getMessage());
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished = proc.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return -1;
        }
        readerThread.join(5000);
        return proc.exitValue();
    }

    // ========== 系统工具（「更多」菜单） ==========

    /** 通用提权命令执行：pkexec <cmd...>，实时推送输出 */
    private int runPrivileged(List<String> command, Consumer<String> outputConsumer,
                              int timeoutSeconds) throws IOException, InterruptedException {
        List<String> full = new ArrayList<>();
        full.add("pkexec");
        full.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(full);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        Thread readerThread = new Thread(() -> {
            try (InputStream is = proc.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputConsumer != null) {
                        outputConsumer.accept(line);
                    }
                }
            } catch (IOException e) {
                if (outputConsumer != null) {
                    outputConsumer.accept("[读取输出错误] " + e.getMessage());
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            if (outputConsumer != null) {
                outputConsumer.accept("[错误] 操作超时，进程已强制终止。");
            }
            return -1;
        }
        readerThread.join(5000);
        return proc.exitValue();
    }

    /**
     * APT 修复：apt-get install -f，修复损坏的依赖。
     */
    public int fixBroken(Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        return runPrivileged(List.of("apt-get", "install", "-f", "-y"),
                outputConsumer, 300);
    }

    /**
     * 添加软件源：add-apt-repository -y <source>
     */
    public int addRepository(String source, Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        return runPrivileged(List.of("add-apt-repository", "-y", source),
                outputConsumer, 180);
    }

    /**
     * 卸载软件包：apt-get remove -y <pkg>
     */
    public int uninstall(String packageName, Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        return runPrivileged(List.of("apt-get", "remove", "-y", packageName),
                outputConsumer, 300);
    }

    /**
     * 清理无用依赖：apt-get autoremove -y
     */
    public int autoremove(Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        return runPrivileged(List.of("apt-get", "autoremove", "-y"),
                outputConsumer, 300);
    }

    /**
     * 升级全部软件包：apt-get upgrade -y
     */
    public int upgrade(Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        return runPrivileged(List.of("apt-get", "upgrade", "-y"),
                outputConsumer, 600);
    }

    /**
     * 列出所有已安装的软件包（dpkg-query，状态为 installed）。
     */
    public List<PackageInfo> listInstalled() throws IOException, InterruptedException {
        List<PackageInfo> result = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("dpkg-query", "-W",
                "-f=${binary:Package}\t${Version}\t${db:Status-Status}\n");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3 && "installed".equals(parts[2])) {
                    result.add(new PackageInfo(parts[0], parts[1], true));
                }
            }
        }
        proc.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return result;
    }
}
