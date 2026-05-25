import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import java.awt.*;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

public class TerminalApp {
    private static java.net.Socket keepAliveSocket;
    private static String originalUserPrompt = "";

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";

    private static final String BLUE = "\u001B[38;2;56;182;255m";
    private static final String WHITE = "\u001B[37m";

    private static final String BOLD = "\u001B[1m";

    private static final String AI_COLOR = "\u001B[38;2;255;173;97m";
    private static final String APP_VERSION = "1.1";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/kayaberkkan/cmd-AI/main/version.txt";

    static class Message {
        String role;
        String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static java.util.List<Message> history = new ArrayList<>();

    private static void addToHistory(String role, String content) {
        history.add(new Message(role, content));
        if (history.size() > 10) {
            history.remove(0);
        }
    }

    static class ExecutionResult {
        boolean success;
        String output;

        public ExecutionResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private static Terminal terminal;
    private static LineReader lineReader;

    private static String getJarDir() {
        try {
            String path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
            File f = new File(path);
            if (path.endsWith(".jar") || f.getName().contains("cmd_asistan")) {
                if (f.isFile()) {
                    return f.getParent() + File.separator;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeTerminalTabAndExit() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "taskkill /F /FI \"WINDOWTITLE eq cmdAI*\"").start();
                Thread.sleep(100);
            } catch (Exception ignored) {
            }
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        String launcherPortStr = System.getProperty("app.launcher.port");
        if (launcherPortStr != null) {
            try {
                int port = Integer.parseInt(launcherPortStr);
                keepAliveSocket = new java.net.Socket("127.0.0.1", port);
                Thread socketThread = new Thread(() -> {
                    try {
                        keepAliveSocket.getInputStream().read();
                    } catch (Exception e) {
                    } finally {
                        closeTerminalTabAndExit();
                    }
                });
                socketThread.setDaemon(true);
                socketThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        enableWindowsAnsiSupport();

        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .build();

            try {
                terminal.puts(InfoCmp.Capability.clear_screen);
                terminal.flush();
            } catch (Exception ignored) {
            }

            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, System.getProperty("user.home") + "/.cmd_asistan_history")
                    .build();
        } catch (Throwable e) {
            System.err.println("JLine baslatilamadi: " + e.getMessage());
        }

        System.out.print("\u001B]0;cmdAI\u0007");
        System.out.print("\u001B[8;35;100t");
        System.out.print("\033[H\033[2J\033[3J");
        System.out.print("\033[H\033[2J\033[3J");
        System.out.flush();

        checkForUpdates();

        printWelcomeMessage();
        if (isRunningAsAdmin()) {
            String GREEN = "\u001B[32m";
            String RESET = "\u001B[0m";
            System.out.println(GREEN + "[✓] Yönetici yetkisi ile çalışılıyor!" + RESET);
        }

        String userHome = System.getProperty("user.home");
        File configFile = new File(userHome, ".terminal_asistan_config.properties");
        Properties config = new Properties();

        try {
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
            } else {
                config.setProperty("provider", "Google Gemini");
                config.setProperty("api_key", "");
                config.setProperty("gemini_model", "gemini-3-flash-preview");
                config.setProperty("ollama_model", "llama3.2");
            }
        } catch (Exception ignored) {
        }

        if (terminal == null) {
        }

        Scanner scannerFallback = null;
        if (lineReader == null) {
            scannerFallback = new Scanner(System.in);
        }

        while (true) {
            try {
                if (lineReader != null) {
                    originalUserPrompt = lineReader.readLine(GREEN + "> " + RESET).trim();
                } else {
                    System.out.print(GREEN + "> " + RESET);
                    if (!scannerFallback.hasNextLine()) {
                        break;
                    }
                    originalUserPrompt = scannerFallback.nextLine().trim();
                }
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                break;
            } catch (Exception e) {
                break;
            }

            if (originalUserPrompt.isEmpty())
                continue;

            if (originalUserPrompt.equalsIgnoreCase("exit") || originalUserPrompt.equalsIgnoreCase("cikis")
                    || originalUserPrompt.equalsIgnoreCase("çıkış")) {
                closeTerminalTabAndExit();
            }

            if (originalUserPrompt.equalsIgnoreCase("ayarlar")) {
                openSettingsWindow(config, configFile);
                continue;
            }

            if (originalUserPrompt.equalsIgnoreCase("yardim") || originalUserPrompt.equalsIgnoreCase("rehber")
                    || originalUserPrompt.equalsIgnoreCase("talimatlar")
                    || originalUserPrompt.equalsIgnoreCase("bilgi")) {
                printGuide();
                continue;
            }
            if (originalUserPrompt.equalsIgnoreCase("yonetici") || originalUserPrompt.equalsIgnoreCase("admin")) {
                restartAsAdmin();
                continue;
            }
            if (originalUserPrompt.equalsIgnoreCase("temizle") || originalUserPrompt.equalsIgnoreCase("clear")
                    || originalUserPrompt.equalsIgnoreCase("cls")) {
                clearScreen();
                continue;
            }

            if (originalUserPrompt.equalsIgnoreCase("unut") || originalUserPrompt.equalsIgnoreCase("forget")
                    || originalUserPrompt.equalsIgnoreCase("reset")) {
                history.clear();
                System.out.println("\u001B[33m" + "✓ Hafiza temizlendi." + "\u001B[0m");
                System.out.println();
                continue;
            }

            String os = System.getProperty("os.name").toLowerCase();
            processWorkflow(originalUserPrompt, config, os);
            System.out.println();
        }
    }

    private static void processWorkflow(String prompt, Properties config, String os) {
        addToHistory("user", prompt);

        String prov = config.getProperty("provider", "Google Gemini");
        System.out.print(BLUE + "[*] Yapay zeka düşünüyor..." + RESET);
        System.out.flush();

        String cmd = getCommandFromAI(prompt, config, os);

        System.out.print("\r" + " ".repeat(40) + "\r");

        if (cmd == null) {
            String YELLOW = "\u001B[33m";
            if (prov.equals("Local Ollama")) {
                System.out.println(YELLOW
                        + "[!] Hata: Ollama'dan yanıt alınamadı. Ollama'nın çalıştığından ve model adının doğru olduğundan emin olun."
                        + RESET);
            } else {
                System.out.println(
                        YELLOW + "[!] Hata: Komut üretilemedi. API anahtarını ve bağlantınızı kontrol edin." + RESET);
            }
            return;
        }

        runWithAutoFix(cmd, prompt, config, os);
    }

    private static void runWithAutoFix(String cmd, String prompt, Properties config, String os) {
        cmd = cleanCommand(cmd);
        System.out.println(AI_COLOR + "Komut: " + cmd + RESET);

        addToHistory("assistant", cmd);
        if (confirm(AI_COLOR + "Çalıştırılsın mı? (E/H): " + RESET)) {
            ExecutionResult result = execute(cmd, os);

            String output = result.output;
            if (output != null && !output.isEmpty()) {
                if (output.length() > 1000)
                    output = output.substring(0, 1000) + "\n... [kısaltıldı]";
                addToHistory("user", "Sistem Çıktısı:\n" + output);
            }

            if (!result.success) {
                System.out.println("\n[!] Komut hata verdi: " + result.output.split("\n")[0] + ". Cozum araniyor...");

                String fixRequest = "AUTO_FIX_REQUEST:\n" +
                        "[INTENT]: " + prompt + "\n" +
                        "[FAILED_COMMAND]: " + cmd + "\n" +
                        "[ERROR_OUTPUT]: " + result.output + "\n" +
                        "[OS_CONTEXT]: " + os;

                String fixedCmd = getCommandFromAI(fixRequest, config, os);
                if (fixedCmd != null) {
                    fixedCmd = cleanCommand(fixedCmd);
                    System.out.println(AI_COLOR + "[FIX] Düzeltilmiş komut: " + fixedCmd + RESET);
                    addToHistory("assistant", fixedCmd);

                    if (confirm(AI_COLOR + "Bunu çalıştıralım mı? (E/H): " + RESET)) {
                        ExecutionResult fixResult = execute(fixedCmd, os);
                        String fixOutput = fixResult.output;
                        if (fixOutput != null && !fixOutput.isEmpty()) {
                            if (fixOutput.length() > 500)
                                fixOutput = fixOutput.substring(0, 500) + "...";
                            addToHistory("user", "Sistem Çıktısı:\n" + fixOutput);
                        }
                    }
                } else {
                    System.out.println("Hata: Düzeltme önerisi alınamadı.");
                }
            }
        }
    }

    private static String getCommandFromAI(String prompt, Properties config, String os) {
        String prov = config.getProperty("provider", "Google Gemini");
        if (prov.equals("Local Ollama")) {
            return callOllamaApi(prompt, config.getProperty("ollama_model", "llama3.2"), os);
        } else if (prov.equals("Groq (Llama 3.1)")) {
            return callGroqApi(prompt, config.getProperty("groq_api_key", ""), os);
        } else {
            return callGeminiApi(prompt, config.getProperty("api_key", ""), os,
                    config.getProperty("gemini_model", "gemini-3-flash-preview"));
        }
    }

    private static String cleanCommand(String cmd) {
        return cmd.replaceAll("```[a-zA-Z]*", "").replace("```", "").trim();
    }

    private static void printWelcomeMessage() {
        try {
            System.out.println("");
            System.out.println(
                    BLUE + "===========================================================================" + RESET);
            System.out.println(BLUE + "                   _____ __  __ ____          _     ___ " + RESET);
            System.out.println(BLUE + "                  / ____|  \\/  |  _ \\        / \\   |_ _|" + RESET);
            System.out.println(BLUE + "                 | |    | \\  / | | | |      / _ \\   | | " + RESET);
            System.out.println(BLUE + "                 | |____| |\\/| | |_| |     / ___ \\  | | " + RESET);
            System.out.println(BLUE + "                  \\_____|_|  |_|____/     /_/   \\_\\|___|" + RESET);
            System.out.println("");
            System.out.println(BOLD + WHITE + "                   :: INTELLIGENT COMMAND INTERFACE ::" + RESET);
            System.out.println(
                    GREEN + "             Dev: Berkkan Kaya | github.com/kayaberkkan | v" + APP_VERSION + RESET);
            System.out.println(
                    BLUE + "===========================================================================" + RESET);
            System.out.println(BLUE + " KULLANIM: " + RESET + "Dogal dilde ne yapmak istediginizi yazin.");
            System.out
                    .println(BLUE + " ORNEK:    " + RESET + "\"Masaustundeki tum PDF'leri Belgelerim klasorune tasi\"");
            System.out.println("");
            System.out.println(BLUE + " [KOMUTLAR]" + RESET);
            System.out.println("  * " + BOLD + "yardim" + RESET + "    : Kurulum ve API rehberi");
            System.out.println("  * " + BOLD + "ayarlar" + RESET + "   : API ve Model ayarlari");
            System.out.println("  * " + BOLD + "yonetici" + RESET + "  : Admin (Sudo) yetkisi al");
            System.out.println("  * " + BOLD + "temizle" + RESET + "   : Ekrani temizle");
            System.out.println("  * " + BOLD + "unut" + RESET + "      : Hafızayı temizle (Yeni Sohbet)");
            System.out.println("  * " + BOLD + "cikis" + RESET + "     : Kapat");
            System.out.println("");
            System.out.println(BLUE + " [DURUM]" + RESET + " Auto-Fix (Self-Healing): " + GREEN + "AKTIF" + RESET);
            System.out.println(
                    BLUE + "===========================================================================" + RESET);
        } catch (Exception e) {
            System.out.println("--- TERMINAL ASISTANI v" + APP_VERSION + " ---");
        }
    }

    private static boolean confirm(String msg) {
        String ans = "";
        try {
            ans = lineReader.readLine(msg);
        } catch (Exception e) {
            return false;
        }
        return ans.trim().equalsIgnoreCase("e") || ans.trim().equalsIgnoreCase("evet")
                || ans.trim().equalsIgnoreCase("y");
    }

    private static ExecutionResult execute(String cmd, String os) {
        StringBuilder fullOutput = new StringBuilder();
        try {
            String[] c = os.toLowerCase().contains("win") ? new String[] { "cmd.exe", "/c", cmd }
                    : new String[] { "/bin/sh", "-c", cmd };
            Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            String l;
            boolean hasOutput = false;
            while ((l = r.readLine()) != null) {
                System.out.println(WHITE + l + RESET);
                fullOutput.append(l).append("\n");
                hasOutput = true;
            }
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                if (!hasOutput) {
                    if (cmd.contains("find") || cmd.contains("grep") || cmd.contains("mdfind") ||
                            cmd.contains("findstr")) {
                        String YELLOW = "\u001B[33m";
                        String RESET = "\u001B[0m";
                        System.out.println(YELLOW + "✓ Arama tamamlandı, eşleşen sonuç bulunamadı." + RESET);
                    } else {
                        System.out.println("✓ İşlem başarıyla tamamlandı.");
                    }
                }
                return new ExecutionResult(true, fullOutput.toString());
            } else {
                return new ExecutionResult(false, fullOutput.length() > 0 ? fullOutput.toString()
                        : "Bilinmeyen hata (Exit Code: " + exitCode + ")");
            }
        } catch (Exception e) {
            return new ExecutionResult(false, e.getMessage());
        }
    }

    private static String callGeminiApi(String p, String k, String os, String model) {
        try {
            if (k.isEmpty())
                return null;

            String sys = SystemPrompts.getGeminiPrompt();

            String targetModel = (model == null || model.isEmpty()) ? "gemini-2.0-flash" : model;
            String url, body;

            if (targetModel.contains("2.0") || targetModel.contains("2.5") || targetModel.contains("3")
                    || targetModel.contains("exp")) {

                StringBuilder contents = new StringBuilder();
                if (!history.isEmpty()) {
                    for (Message msg : history) {
                        if (msg.content.equals(p) && msg.role.equals("user"))
                            continue;
                        String geminiRole = msg.role.equals("user") ? "user" : "model";
                        contents.append("{\"role\":\"").append(geminiRole).append("\",\"parts\":[{\"text\":\"")
                                .append(escapeJson(msg.content)).append("\"}]},");
                    }
                }
                contents.append("{\"role\":\"user\",\"parts\":[{\"text\":\"").append(escapeJson(p)).append("\"}]}");

                url = "https://generativelanguage.googleapis.com/v1beta/models/" + targetModel + ":generateContent?key="
                        + k;
                body = "{\"system_instruction\":{\"parts\":[{\"text\":\"" + escapeJson(sys + "\nOS: " + os)
                        + "\"}]},\"contents\":[" + contents.toString() + "]}";
            } else {
                url = "https://generativelanguage.googleapis.com/v1/models/" + targetModel + ":generateContent?key="
                        + k;

                StringBuilder combined = new StringBuilder();
                combined.append("SYSTEM INSTRUCTION:\n").append(sys).append("\n\nENVIRONMENT:\nOS: ").append(os)
                        .append("\n\n");
                if (!history.isEmpty()) {
                    combined.append("Previous Conversation:\n");
                    for (Message msg : history) {
                        if (msg.content.equals(p) && msg.role.equals("user"))
                            continue;
                        combined.append(msg.role).append(": ").append(msg.content).append("\n");
                    }
                    combined.append("\n");
                }
                combined.append("USER REQUEST: ").append(p);

                body = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(combined.toString()) + "\"}]}]}";
            }

            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                if (res.statusCode() == 429)
                    System.err.println("\n[!] Kota siniri asildi. Lutfen bekleyin.");
                else
                    System.err.println("\n[!] API Hatasi: " + res.statusCode());
                return null;
            }

            String fullText = unescapeJsonString(extractJsonValue(res.body(), "text"));
            if (fullText == null)
                return null;

            String cmd = extractJsonValue(fullText, "command");
            return (cmd != null) ? cmd : fullText.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String callGroqApi(String p, String k, String os) {
        try {
            if (k.isEmpty())
                return null;
            String sys = SystemPrompts.getGroqPrompt();

            String url = "https://api.groq.com/openai/v1/chat/completions";
            StringBuilder messages = new StringBuilder();
            messages.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(sys + "\nOS: " + os))
                    .append("\"},");

            if (!history.isEmpty()) {
                for (Message msg : history) {
                    if (msg.content.equals(p) && msg.role.equals("user"))
                        continue;
                    messages.append("{\"role\":\"").append(msg.role).append("\",\"content\":\"")
                            .append(escapeJson(msg.content)).append("\"},");
                }
            }
            messages.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(p)).append("\"}");

            String body = "{\"model\":\"llama-3.3-70b-versatile\","
                    + "\"messages\":[" + messages.toString() + "],"
                    + "\"temperature\":0.3,\"max_tokens\":500}";

            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + k)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                System.err.println("\n[DEBUG] Groq API Hatası: " + res.statusCode());
                System.err.println("[DEBUG] Response: " + res.body().substring(0, Math.min(500, res.body().length())));
                return null;
            }

            String content = extractJsonValue(res.body(), "content");
            if (content == null) {
                System.err.println("\n[DEBUG] 'content' alanı bulunamadı, response: "
                        + res.body().substring(0, Math.min(200, res.body().length())));
                return null;
            }
            content = unescapeJsonString(content);
            return (content != null) ? content.trim() : null;
        } catch (Exception e) {
            System.err.println("\n[DEBUG] Groq Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String callOllamaApi(String p, String m, String os) {
        try {
            String sys = SystemPrompts.getOllamaPrompt();

            String enhancedPrompt = p;

            StringBuilder context = new StringBuilder();
            if (!history.isEmpty()) {
                context.append("GEÇMİŞ KONUŞMA:\n");
                for (Message msg : history) {
                    if (msg.content.equals(p) && msg.role.equals("user"))
                        continue;

                    context.append(msg.role.equals("user") ? "Kullanıcı: " : "Sistem/Asistan: ").append(msg.content)
                            .append("\n");
                }
                context.append("\n");
            }

            String body = "{\"model\":\"" + m + "\","
                    + "\"system\":\"" + escapeJson(sys) + "\","
                    + "\"prompt\":\""
                    + escapeJson("İşletim Sistemi: " + os + "\n\n" + context.toString() + "Kullanıcı İsteği: "
                            + enhancedPrompt
                            + "\n\nSADECE KOMUT VER:")
                    + "\","
                    + "\"stream\":false}";

            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                System.out.println("\n[DEBUG] Ollama API yanıtı: " + res.statusCode() + " - "
                        + res.body().substring(0, Math.min(200, res.body().length())));
                return null;
            }

            String fullResp = unescapeJsonString(extractJsonValue(res.body(), "response"));
            if (fullResp == null) {
                System.out.println("\n[DEBUG] Ollama yanıtından 'response' alanı çıkarılamadı");
                return null;
            }

            fullResp = fullResp.trim();
            if (fullResp.contains("\n")) {
                String firstLine = fullResp.split("\n")[0].trim();
                if (firstLine.matches("^[a-zA-Z/$.].*")) {
                    fullResp = firstLine;
                }
            }

            String cmd = extractJsonValue(fullResp, "command");
            return (cmd != null) ? cmd : fullResp;
        } catch (java.net.http.HttpTimeoutException e) {
            System.out.println("\n[DEBUG] Ollama zaman aşımı - model yanıt vermedi");
            return null;
        } catch (java.net.ConnectException e) {
            System.out.println("\n[DEBUG] Ollama'ya bağlanılamadı - Ollama çalışıyor mu?");
            return null;
        } catch (Exception e) {
            System.out.println("\n[DEBUG] Ollama hatası: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private static void clearScreen() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } catch (Exception ignored) {
            }
        } else {
            System.out.print("\033[H\033[2J\033[3J");
            System.out.flush();
        }
        printWelcomeMessage();
    }

    private static String escapeJson(String s) {
        return s == null ? ""
                : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
                        "\\t");
    }

    private static String extractJsonValue(String j, String k) {
        if (j == null)
            return null;
        String searchKey = "\"" + k + "\"";
        int keyIndex = j.indexOf(searchKey);
        if (keyIndex == -1)
            return null;
        int colonIndex = j.indexOf(":", keyIndex + searchKey.length());
        if (colonIndex == -1)
            return null;
        int startQuote = j.indexOf("\"", colonIndex + 1);
        if (startQuote == -1)
            return null;

        StringBuilder result = new StringBuilder();
        int i = startQuote + 1;
        while (i < j.length()) {
            char c = j.charAt(i);
            if (c == '\\' && i + 1 < j.length()) {
                result.append(c);
                result.append(j.charAt(i + 1));
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    private static String unescapeJsonString(String s) {
        if (s == null)
            return null;
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(s);
        while (m.find())
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        m.appendTail(sb);
        return sb.toString().replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static void restartAsAdmin() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsolutePath();
            String jarDir = new File(jarPath).getParent();

            if (os.contains("win")) {
                File batFile = new File(jarDir, "_admin_start.bat");
                try (PrintWriter pw = new PrintWriter(new FileWriter(batFile))) {
                    pw.println("@echo off");
                    pw.println("chcp 65001 >nul 2>&1");
                    pw.println("title cmdAI (Yönetici)");
                    pw.println("mode con: cols=100 lines=35");
                    pw.println("cd /d \"" + jarDir + "\"");
                    pw.println("java -Dfile.encoding=UTF-8 -jar \"" + jarPath + "\"");
                    pw.println("pause");
                    pw.println("del \"%~f0\"");
                }
                String psCommand = "Start-Process -FilePath '" + batFile.getAbsolutePath() + "' -Verb RunAs";
                new ProcessBuilder("powershell", "-Command", psCommand).start();
                System.exit(0);
            } else if (os.contains("mac")) {
                System.out.println("\n[*] Yönetici yetkisi alınıyor...\n");
                String[] cmd = { "/bin/sh", "-c",
                        "sudo java -Dfile.encoding=UTF-8 -jar '" + jarPath.replace("'", "'\\''") + "'" };
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.inheritIO();
                Process p = pb.start();
                int exitCode = p.waitFor();

                if (exitCode == 0) {
                    System.exit(0);
                } else {
                    String YELLOW = "\u001B[33m";
                    String RESET = "\u001B[0m";
                    System.out.println(
                            "\n" + YELLOW + "[!] Yönetici yetkisi alınamadı. Normal modda devam ediliyor..." + RESET);
                    return;
                }
            } else {
                System.out.println("Bu işletim sisteminde yönetici moduna otomatik geçiş desteklenmiyor.");
                return;
            }
        } catch (Exception e) {
            System.out.println("[!] Yönetici moduna geçilemedi: " + e.getMessage());
        }
    }

    private static boolean isRunningAsAdmin() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Process p = new ProcessBuilder("cmd", "/c", "net session >nul 2>&1 && echo ADMIN").start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = r.readLine();
                p.waitFor();
                return line != null && line.contains("ADMIN");
            } else {
                Process p = new ProcessBuilder("id", "-u").start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String uid = r.readLine();
                p.waitFor();
                return uid != null && uid.trim().equals("0");
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void enableWindowsAnsiSupport() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win"))
            return;

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "echo.");
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void openSettingsWindow(Properties config, File configFile) {
        System.setProperty("java.awt.headless", "false");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        openSettingsGUI(config, configFile);
    }

    private static void openSettingsGUI(Properties config, File configFile) {
        Runnable r = () -> {
            boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
            int w = isMac ? 340 : 500;
            int h = isMac ? 360 : 600;

            try {
                String iconPath = getJarDir() + "icon.png";
                File iconFile = new File(iconPath);
                if (iconFile.exists()) {
                    Image icon = new ImageIcon(iconPath).getImage();
                    if (Taskbar.isTaskbarSupported()) {
                        Taskbar taskbar = Taskbar.getTaskbar();
                        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                            taskbar.setIconImage(icon);
                        }
                    }
                } else {
                    System.out.println("İkon bulunamadı: " + iconPath);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            JDialog d = new JDialog((Frame) null, "Ayarlar", true);
            d.setResizable(false);
            d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            d.setSize(w, h);
            d.setLocationRelativeTo(null);
            d.setAlwaysOnTop(true);

            Color bg = new Color(33, 37, 43);
            Color accent = new Color(97, 175, 239);
            Color success = new Color(152, 195, 121);
            Color danger = new Color(224, 108, 117);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBackground(bg);

            JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, isMac ? 10 : 25));
            titlePanel.setBackground(bg);
            JLabel titleLabel = new JLabel("TERMINAL YAPILANDIRMASI");
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, isMac ? 16 : 20));
            titleLabel.setForeground(accent);
            titlePanel.add(titleLabel);
            mainPanel.add(titlePanel, BorderLayout.NORTH);

            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(bg);

            p.setBorder(BorderFactory.createEmptyBorder(5, isMac ? 15 : 40, 20, isMac ? 15 : 40));
            GridBagConstraints g = new GridBagConstraints();
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(isMac ? 3 : 10, 0, isMac ? 3 : 10, 0);

            Font lF = new Font("Segoe UI", Font.BOLD, isMac ? 12 : 13);
            Font iF = new Font("Segoe UI", Font.PLAIN, isMac ? 13 : 14);

            Map<String, String[]> libGemini = new LinkedHashMap<>();
            libGemini.put("Google Gemini",
                    new String[] { "gemini-3-flash-preview", "gemini-2.5-flash", "gemini-2.5-flash-lite" });

            JLabel pL = new JLabel("SERVIS SAĞLAYICI");
            pL.setFont(lF);
            pL.setForeground(accent.brighter());
            JComboBox<String> pB = new JComboBox<>(
                    new String[] { "Google Gemini", "Groq (Llama 3.1)", "Local Ollama" });
            pB.setFont(iF);
            pB.setBackground(Color.WHITE);
            pB.setForeground(Color.BLACK);

            JLabel kL = new JLabel("GEMINI API ANAHTARI");
            kL.setFont(lF);
            kL.setForeground(accent.brighter());
            JPasswordField kF = new JPasswordField(config.getProperty("api_key", ""), 15);
            kF.setFont(iF);
            kF.setBackground(Color.WHITE);
            kF.setForeground(Color.BLACK);
            kF.setCaretColor(Color.BLACK);

            JLabel gL = new JLabel("GROQ API ANAHTARI");
            gL.setFont(lF);
            gL.setForeground(accent.brighter());
            JPasswordField gF = new JPasswordField(config.getProperty("groq_api_key", ""), 15);
            gF.setFont(iF);
            gF.setBackground(Color.WHITE);
            gF.setForeground(Color.BLACK);
            gF.setCaretColor(Color.BLACK);

            JLabel mL = new JLabel("AKTIF MODEL");
            mL.setFont(lF);
            mL.setForeground(accent.brighter());
            JComboBox<String> mB = new JComboBox<>();
            mB.setFont(iF);
            mB.setBackground(Color.WHITE);
            mB.setForeground(Color.BLACK);
            mB.setEditable(false);

            for (Component comp : new Component[] { pB, mB }) {
                JComboBox<?> cb = (JComboBox<?>) comp;
                cb.setBorder(BorderFactory.createLineBorder(accent.darker(), 2));

                Object popup = cb.getUI().getAccessibleChild(cb, 0);
                if (popup instanceof JPopupMenu) {
                    JPopupMenu popupMenu = (JPopupMenu) popup;
                    popupMenu.setLightWeightPopupEnabled(false);
                    popupMenu.setBorder(BorderFactory.createLineBorder(accent, 4));
                    popupMenu.setBackground(bg);
                }

                cb.setRenderer(new DefaultListCellRenderer() {
                    @Override
                    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                            boolean isSelected, boolean cellHasFocus) {
                        JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                                cellHasFocus);
                        c.setOpaque(true);
                        c.setForeground(Color.BLACK);
                        if (isSelected) {
                            c.setBackground(accent);
                            c.setForeground(Color.WHITE);
                        } else {
                            c.setBackground(Color.WHITE);
                        }
                        c.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                        return c;
                    }
                });
            }
            kF.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent.darker()),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            gF.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent.darker()),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));

            Runnable up = () -> {
                String selected = (String) pB.getSelectedItem();
                boolean isGemini = "Google Gemini".equals(selected);
                boolean isGroq = "Groq (Llama 3.1)".equals(selected);
                boolean isOllama = "Local Ollama".equals(selected);

                kL.setVisible(isGemini);
                kF.setVisible(isGemini);
                gL.setVisible(isGroq);
                gF.setVisible(isGroq);
                mL.setVisible(!isGroq);
                mB.setVisible(!isGroq);

                mB.removeAllItems();
                if (isGemini) {
                    for (String s : libGemini.get("Google Gemini"))
                        mB.addItem(s);
                    mB.setSelectedItem(config.getProperty("gemini_model", "gemini-3-flash-preview"));
                } else if (isOllama) {
                    java.util.List<String> ollamaModels = getInstalledOllamaModels();
                    if (ollamaModels.isEmpty()) {
                        mB.addItem("(Ollama çalışmıyor veya model yok)");
                    } else {
                        for (String model : ollamaModels) {
                            mB.addItem(model);
                        }
                        String savedModel = config.getProperty("ollama_model", "");
                        if (!savedModel.isEmpty() && ollamaModels.contains(savedModel)) {
                            mB.setSelectedItem(savedModel);
                        }
                    }
                }
                d.revalidate();
                d.repaint();
            };

            pB.addActionListener(e -> up.run());
            pB.setSelectedItem(config.getProperty("provider", "Google Gemini"));
            up.run();

            JButton sV = new JButton("AYARLARI KAYDET");
            sV.setFont(new Font("Segoe UI", Font.BOLD, isMac ? 10 : 13));
            sV.setBackground(success);
            sV.setForeground(Color.BLACK);
            sV.setFocusPainted(false);
            sV.setOpaque(true);
            sV.setCursor(new Cursor(Cursor.HAND_CURSOR));
            int bpV = isMac ? 5 : 15;
            int bpH = isMac ? 10 : 25;
            sV.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(success.darker(), 2),
                    BorderFactory.createEmptyBorder(bpV, bpH, bpV, bpH)));

            JButton cV = new JButton("VAZGEÇ");
            cV.setFont(new Font("Segoe UI", Font.BOLD, isMac ? 10 : 13));
            cV.setBackground(danger);
            cV.setForeground(Color.BLACK);
            cV.setFocusPainted(false);
            cV.setOpaque(true);
            cV.setCursor(new Cursor(Cursor.HAND_CURSOR));
            cV.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(danger.darker(), 2),
                    BorderFactory.createEmptyBorder(bpV, bpH, bpV, bpH)));

            JPanel btnP = new JPanel(new GridLayout(1, 2, 15, 0));
            btnP.setBackground(bg);
            btnP.add(sV);
            btnP.add(cV);

            g.gridx = 0;
            g.gridy = 0;
            p.add(pL, g);
            g.gridy = 1;
            p.add(pB, g);
            g.gridy = 2;
            p.add(kL, g);
            g.gridy = 3;
            p.add(kF, g);
            g.gridy = 4;
            p.add(gL, g);
            g.gridy = 5;
            p.add(gF, g);
            g.gridy = 6;
            p.add(mL, g);
            g.gridy = 7;
            p.add(mB, g);
            g.gridy = 8;
            g.insets = new Insets(30, 0, 0, 0);
            p.add(btnP, g);

            sV.addActionListener(e -> {
                config.setProperty("provider", (String) pB.getSelectedItem());
                String selected = (String) pB.getSelectedItem();
                if ("Google Gemini".equals(selected)) {
                    config.setProperty("api_key", new String(kF.getPassword()).trim());
                    config.setProperty("gemini_model", (String) mB.getSelectedItem());
                } else if ("Groq (Llama 3.1)".equals(selected)) {
                    config.setProperty("groq_api_key", new String(gF.getPassword()).trim());
                } else {
                    config.setProperty("ollama_model", (String) mB.getSelectedItem());
                }
                try (FileOutputStream fos = new FileOutputStream(configFile)) {
                    config.store(fos, "Terminal Asistani Config");
                    d.dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(d, ex.getMessage());
                }
            });
            cV.addActionListener(e -> d.dispose());

            mainPanel.add(p, BorderLayout.CENTER);
            d.add(mainPanel);
            d.setVisible(true);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (Exception e) {
            }
        }
    }

    private static java.util.List<String> getInstalledOllamaModels() {
        java.util.List<String> models = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                while (m.find())
                    models.add(m.group(1));
            }
        } catch (Exception ignored) {
        }
        return models;
    }

    private static void printGuide() {
        System.out.println("\n" + BOLD + "=== CMD AI REHBERI ===" + RESET);
        System.out.println("");
        System.out.println(BLUE + "[API ANAHTARLARI NASIL ALINIR?]" + RESET);
        System.out.println("1. " + BOLD + "Google Gemini" + RESET + " (Hızlı / Free Tier Mevcut):");
        System.out.println("   Adres: " + YELLOW + "https://aistudio.google.com/app/apikey" + RESET);
        System.out.println("   - 'Create API Key' butonuna basıp anahtarı kopyalayın.");
        System.out.println("");
        System.out.println("2. " + BOLD + "Groq Cloud" + RESET + " (Ultra Hızlı / Free Beta):");
        System.out.println("   Adres: " + YELLOW + "https://console.groq.com/keys" + RESET);
        System.out.println("   - 'Create API Key' diyerek anahtarı alın.");
        System.out.println("");
        System.out.println(BLUE + "[YEREL AI (OLLAMA) NASIL KURULUR?]" + RESET);
        System.out.println("1. İndir: " + YELLOW + "https://ollama.com/download" + RESET);
        System.out.println("2. İndirdiğin dosyayı kur.");
        System.out.println("3. Terminal'i açıp şu komutu yaz:");
        System.out.println("   " + BOLD + "ollama pull llama3" + RESET);
        System.out.println("4. CMD AI ayarlarından 'Model' olarak 'ollama' seç.");
        System.out.println("");
        System.out.println(BLUE + "[NASIL KAYDEDİLİR?]" + RESET);
        System.out.println("Bu pencerede " + BOLD + "'ayarlar'" + RESET + " yazarak anahtarlarını girebilirsin.");
        System.out.println("");
        System.out.println(BLUE + "[HAFIZA (MEMORY) NASIL ÇALIŞIR?]" + RESET);
        System.out.println("Yapay zeka, son " + BOLD + "10 mesajı" + RESET + " (soru-cevap) hafızasında tutar.");
        System.out.println("Bu sayede sohbet eder gibi ilerleyebilirsiniz.");
        System.out.println("");
        System.out.println(BOLD + "Örnek Senaryo:" + RESET);
        System.out.println("1. Komut: " + YELLOW + "\"Masaüstündeki dosyaları listele\"" + RESET);
        System.out.println(
                "2. Komut: " + YELLOW + "\"Bu dosyalardan sadece son 5 günde değiştirilenleri göster\"" + RESET);
        System.out.println("3. Komut: " + YELLOW + "\"Bunların hepsini 'Yedek' klasörüne taşı\"" + RESET);
        System.out.println("");
        System.out.println("- Hafızayı sıfırlamak için " + BOLD + "unut" + RESET + " yazman yeterlidir.");
        System.out.println("");
    }

    private static void checkForUpdates() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERSION_URL))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String latestVersion = response.body().trim();

                if (!latestVersion.isEmpty() && !latestVersion.equals(APP_VERSION)) {
                    System.out.print("\033[H\033[2J\033[3J");
                    System.out.flush();

                    String RED = "\u001B[31m";
                    String YELLOW = "\u001B[33m";

                    System.out.println(RED + "\n╔══════════════════════════════════════════════════╗" + RESET);
                    System.out.println(RED + "║              KRITIK GUNCELLEME GEREKLI           ║" + RESET);
                    System.out.println(RED + "╠══════════════════════════════════════════════════╣" + RESET);
                    System.out.println("║ Mevcut Sürüm: " + APP_VERSION);
                    System.out.println("║ Yeni Sürüm:   " + latestVersion);
                    System.out.println(RED + "╚══════════════════════════════════════════════════╝" + RESET);

                    System.out.println("\n" + YELLOW + "Bu sürüm artık desteklenmiyor. Lütfen güncelleyin." + RESET);

                    try {
                        System.out.println("İndirme sayfası açılıyor...");
                        String url = "https://github.com/kayaberkkan/cmd-AI";
                        String os = System.getProperty("os.name").toLowerCase();
                        boolean isMac = os.contains("mac");
                        boolean isWindows = os.contains("win");

                        if (isMac) {
                            new ProcessBuilder("open", url).start();
                        } else if (isWindows) {
                            new ProcessBuilder("cmd", "/c", "start", url).start();
                        } else {
                            new ProcessBuilder("xdg-open", url).start();
                        }
                    } catch (Exception e) {
                    }

                    System.out.println("\nProgram kapatılıyor...");
                    System.exit(0);
                }
            }
        } catch (Throwable e) {
        }
    }
}
