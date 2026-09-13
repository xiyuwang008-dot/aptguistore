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
}
