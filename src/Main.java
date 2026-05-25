import java.io.File;
import java.lang.reflect.Method;

public class Main {
    private static java.net.ServerSocket serverSocket;
    private static java.net.Socket clientSocket;

    public static void main(String[] args) {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH);

        try {
            boolean isRestarted = Boolean.getBoolean("app.restarted");

            String os = System.getProperty("os.name").toLowerCase();
            boolean isMac = os.contains("mac");
            boolean hasConsole = System.console() != null;

            if (isMac && !hasConsole && !isRestarted) {
                try {
                    java.awt.Toolkit.getDefaultToolkit();
                } catch (Throwable t) {
                }
                launchMacTerminal();
                return;
            }

            if (os.contains("win") && !hasConsole && !isRestarted) {
                launchWindowsTerminal();
                return;
            }

            try {
                Class.forName("org.jline.terminal.Terminal");

                Class<?> appClass = Class.forName("TerminalApp");
                Method mainMethod = appClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) args);

            } catch (ClassNotFoundException e) {
                launchWithLib(args);
            } catch (NoClassDefFoundError e) {
                launchWithLib(args);
            } catch (Exception e) {
                e.printStackTrace();
                launchWithLib(args);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fatal Error: " + e.getMessage());
            System.err.println("Please run via script on MacOS/Linux/Windows.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
            }
        }
    }

    private static void launchWindowsTerminal() throws Exception {
        String jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getAbsolutePath();
        String jarDir = new File(jarPath).getParent();

        File libDir = new File(new File(jarDir).getParent(), "lib");
        String libPath = libDir.getAbsolutePath() + "/*";

        String classpath = libPath + ";" + jarPath;

        String javaCmd = "java -Dapp.restarted=true -Dfile.encoding=UTF-8 -cp \"" + classpath + "\" TerminalApp";

        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "cmdAI", "cmd", "/c", javaCmd);
        pb.start();
        System.exit(0);
    }

    private static void launchMacTerminal() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                String closeScript = "tell application \"Terminal\"\n" +
                        "  repeat with w in windows\n" +
                        "    repeat with t in tabs of w\n" +
                        "      try\n" +
                        "        if (custom title of t contains \"cmdAI\") or (title of t contains \"cmdAI\") then\n" +
                        "          close w\n" +
                        "          return\n" +
                        "        end if\n" +
                        "      end try\n" +
                        "    end repeat\n" +
                        "  end repeat\n" +
                        "end tell";
                new ProcessBuilder("osascript", "-e", closeScript).start().waitFor();
            } catch (Exception ignored) {}
        }));

        serverSocket = new java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
        int port = serverSocket.getLocalPort();

        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                desktop.addAppEventListener(new java.awt.desktop.AppReopenedListener() {
                    @Override
                    public void appReopened(java.awt.desktop.AppReopenedEvent e) {
                        try {
                            String focusScript = "tell application \"Terminal\"\n" +
                                    "  repeat with w in windows\n" +
                                    "    repeat with t in tabs of w\n" +
                                    "      try\n" +
                                    "        if (custom title of t contains \"cmdAI\") or (title of t contains \"cmdAI\") then\n" +
                                    "          set selected of t to true\n" +
                                    "          set index of w to 1\n" +
                                    "          activate\n" +
                                    "          return\n" +
                                    "        end if\n" +
                                    "      end try\n" +
                                    "    end repeat\n" +
                                    "  end repeat\n" +
                                    "  activate\n" +
                                    "end tell";
                            new ProcessBuilder("osascript", "-e", focusScript).start();
                        } catch (Exception ex) {
                        }
                    }
                });
            }
        } catch (Throwable t) {
        }

        Thread waiterThread = new Thread(() -> {
            try {
                serverSocket.setSoTimeout(30000);
                clientSocket = serverSocket.accept();
                clientSocket.getInputStream().read();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {}
                System.exit(0);
            }
        });
        waiterThread.setDaemon(false);
        waiterThread.start();

        String jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getAbsolutePath();
        String jarDir = new File(jarPath).getParent();

        String libPath = new File(jarDir).getParent() + "/lib/*";
        String classpath = libPath + ":" + jarPath;

        jarDir = jarDir.replace("'", "'\\''");
        classpath = classpath.replace("'", "'\\''");

        String command = "cd '" + jarDir + "' && clear && java -Dapp.restarted=true -Dapp.launcher.port=" + port + " -Dfile.encoding=UTF-8 -cp '"
                + classpath + "' TerminalApp; exit";

        boolean isTerminalRunning = false;
        try {
            isTerminalRunning = new ProcessBuilder("pgrep", "-x", "Terminal").start().waitFor() == 0;
        } catch (Exception e) {
        }

        String appleScript;
        if (isTerminalRunning) {
            appleScript = "tell application \"Terminal\"\n" +
                    "  activate\n" +
                    "  do script \"" + command + "\"\n" +
                    "  activate\n" +
                    "end tell";
        } else {
            appleScript = "tell application \"Terminal\"\n" +
                    "  activate\n" +
                    "  delay 0.5\n" +
                    "  if (exists window 1) and (busy of window 1 is false) then\n" +
                    "      do script \"" + command + "\" in window 1\n" +
                    "  else\n" +
                    "      do script \"" + command + "\"\n" +
                    "  end if\n" +
                    "  activate\n" +
                    "end tell";
        }

        new ProcessBuilder("osascript", "-e", appleScript).start().waitFor();
    }

    private static void launchWithLib(String[] args) throws Exception {
        if (Boolean.getBoolean("app.restarted")) {
            System.err.println("Error: Failed to launch application even with libraries.");
            System.exit(1);
        }

        String jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getAbsolutePath();
        String jarDir = new File(jarPath).getParent();

        String libPath = new File(jarDir).getParent() + "/lib/*";

        String cpSeparator = System.getProperty("path.separator");
        String classpath = libPath + cpSeparator + jarPath;

        ProcessBuilder pb = new ProcessBuilder("java", "-Dapp.restarted=true", "-Dfile.encoding=UTF-8", "-cp",
                classpath, "TerminalApp");
        pb.inheritIO();
        pb.start().waitFor();
    }
}
