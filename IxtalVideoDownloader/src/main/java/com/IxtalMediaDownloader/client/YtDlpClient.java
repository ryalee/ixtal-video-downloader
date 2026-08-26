package com.IxtalMediaDownloader.client;

import com.IxtalMediaDownloader.model.MediaInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download]\\s+([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+|[0-9]+)%");

    // metodo para pegar o caminho do executável dentro da pasta /bin
    private String getExecutablePath(String executableName) {
        // procura na pasta bin
        File localBinFolder = new File("bin", executableName);
        if (localBinFolder.exists()) {
            return localBinFolder.getAbsolutePath();
        }

        File appBinFolder = new File("app/bin", executableName);
        if (appBinFolder.exists()) {
            return appBinFolder.getAbsolutePath();
        }

        // fallback para desenvolvimento local na IDE
        File devBin = new File("src/main/resources/bin/" + executableName);
        if (devBin.exists()) {
            return devBin.getAbsolutePath();
        }

        // se estiver dentro do jar, extrai para uma pasta temporária do sistema
        try {
            java.io.InputStream inputStream = getClass().getResourceAsStream("/bin/" + executableName);
            if (inputStream != null) {
                File tempFile = new File(System.getProperty("java.io.tmpdir"), executableName);
                if (!tempFile.exists()) {
                    java.nio.file.Files.copy(inputStream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile.setExecutable(true);
                }
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executableName; // comando global caso falhe
    }

    public MediaInfo getVideoInfo(String videoUrl) throws Exception {
        String ytDlpPath = getExecutablePath("yt-dlp.exe");

        ProcessBuilder processBuilder = new ProcessBuilder(
                ytDlpPath,
                "--dump-json",
                "--no-playlist",
                "--no-warnings",
                "--extractor-args", "youtube:player_client=android,web",
                videoUrl
        );

        Process process = processBuilder.start();

        try {
            StringBuilder jsonOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("{")) {
                        jsonOutput.append(line);
                    }
                }
            }

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("Erro ao buscar informações do vídeo:\n" + errorOutput);
            }

            if (jsonOutput.isEmpty()) {
                throw new RuntimeException("Não foi possível extrair os dados do vídeo. Verifique se o link é válido.");
            }

            return objectMapper.readValue(jsonOutput.toString(), MediaInfo.class);
        } finally {
            process.destroy();
        }
    }

    public void downloadMedia(String videoUrl, Path outputDir, String formatChoice, String resolutionChoice, String audioQualityChoice, BiConsumer<Double, String> progressListener) throws Exception {
        // cria um diretório temporario exclusivo pra esse download no temp do sistema
        Path tempFolder = java.nio.file.Files.createTempDirectory("ixtal_download_");

        // template aponta pra pasta temp
        String tempOutputTemplate = tempFolder.resolve("%(title)s.%(ext)s").toString();

        // mapeia os caminhos dos executáveis
        String ytDlpPath = getExecutablePath("yt-dlp.exe");
        String ffmpegPath = getExecutablePath("ffmpeg.exe");

        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);

        // configs globais
        command.addAll(Arrays.asList(
                "--newline",
                "--concurrent-fragments", "4",
                "--buffer-size", "16k",
                "--no-playlist",
                "--ffmpeg-location", ffmpegPath,
                "--js-runtimes", "node"
        ));

        if ("Apenas Áudio (MP3)".equals(formatChoice)) {
            String bitrate = "320k";
            if (audioQualityChoice != null && audioQualityChoice.contains("192")) {
                bitrate = "192k";
            } else if (audioQualityChoice != null && audioQualityChoice.contains("128")) {
                bitrate = "128k";
            }

            command.addAll(Arrays.asList(
                    "-f", "bestaudio/best",
                    "-x",
                    "--audio-format", "mp3",
                    "--audio-quality", bitrate,
                    "-o", tempOutputTemplate,
                    videoUrl
            ));
        } else {
            // regra pra mesclar vídeo e áudio em MP4
            String formatRule = switch (resolutionChoice) {
                case "1080p" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best";
                case "720p"  -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best";
                case "480p"  -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best";
                default      -> "bestvideo+bestaudio/best";
            };

            command.addAll(Arrays.asList(
                    "-f", formatRule,
                    "--merge-output-format", "mp4",
                    "-o", tempOutputTemplate,
                    videoUrl
            ));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseProgressLine(line, progressListener);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Falha no download. Código de erro: " + exitCode);
            }

            // transfere o arquivo final da pasta temp para a pasta selecionada
            try (var stream = java.nio.file.Files.list(tempFolder)) {
                for (Path file : stream.toList()) {
                    Path targetPath = outputDir.resolve(file.getFileName());
                    java.nio.file.Files.move(file, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

        } finally {
            process.destroy();
            // limpaa pasta temporária do sistema
            try {
                java.nio.file.Files.walk(tempFolder)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    private void parseProgressLine(String line, BiConsumer<Double, String> progressListener) {
        if (progressListener == null || line == null) return;

        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                double percentage = Double.parseDouble(matcher.group(1));
                double normalizedProgress = percentage / 100.0;

                String statusMsg = String.format("Baixando: %.1f%%", percentage);
                progressListener.accept(normalizedProgress, statusMsg);
            } catch (NumberFormatException ignored) {
            }
        } else if (line.contains("[Merger]") || line.contains("Merging")) {
            progressListener.accept(-1.0, "Só mais um pouquinho...");
        } else if (line.contains("[ExtractAudio]") || line.contains("Converting")) {
            progressListener.accept(-1.0, "Convertendo para MP3...");
        }
    }
}