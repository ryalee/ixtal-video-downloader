package com.IxtalMediaDownloader.client;

import com.IxtalMediaDownloader.model.MediaInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 1. Correção: Removida a barra redundante '\]' do conjunto na expressão regular
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download]\\s+([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+|[0-9]+)%");

    public MediaInfo getVideoInfo(String videoUrl) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp",
                "--dump-json",
                "--no-playlist",
                "--no-warnings",
                "--extractor-args", "youtube:player_client=android,web",
                videoUrl
        );

        // 2. Correção: Gerenciamento do Process para garantir o fechamento do recurso
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
                // 3. Correção: Removida a chamada redundante .toString() sobre variável do tipo String
                throw new RuntimeException("Erro ao buscar informações do vídeo:\n" + errorOutput);
            }

            // 4. Correção: Substituído .length() == 0 pelo método .isEmpty()
            if (jsonOutput.isEmpty()) {
                throw new RuntimeException("Não foi possível extrair os dados do vídeo. Verifique se o link é válido.");
            }

            return objectMapper.readValue(jsonOutput.toString(), MediaInfo.class);
        } finally {
            process.destroy();
        }
    }

    public void downloadMedia(String videoUrl, Path outputDir, String formatChoice, String resolutionChoice, String audioQualityChoice, BiConsumer<Double, String> progressListener) throws Exception {
        String outputTemplate = outputDir.resolve("%(title)s.%(ext)s").toString();

        List<String> command = new ArrayList<>();
        command.add("yt-dlp");

        command.addAll(Arrays.asList(
                "--newline",
                "--concurrent-fragments", "4",
                "--buffer-size", "16k",
                "--no-playlist",
                "--extractor-args", "youtube:player_client=android,web"
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
                    "-o", outputTemplate,
                    videoUrl
            ));
        } else {
            // 5. Correção: Atualizado para o Enhanced Switch com sintaxe de seta (->)
            String formatRule = switch (resolutionChoice) {
                case "1080p" -> "bestvideo[height=1080]+bestaudio/bestvideo[height<=1080]+bestaudio/best";
                case "720p" -> "bestvideo[height=720]+bestaudio/bestvideo[height<=720]+bestaudio/best";
                case "480p" -> "bestvideo[height=480]+bestaudio/bestvideo[height<=480]+bestaudio/best";
                default -> "bestvideo+bestaudio/best";
            };

            command.addAll(Arrays.asList(
                    "-f", formatRule,
                    "--merge-output-format", "mp4",
                    "-o", outputTemplate,
                    videoUrl
            ));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        // 6. Correção: Process gerenciado de forma segura no bloco do download
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
        } finally {
            process.destroy();
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
            progressListener.accept(-1.0, "Juntando áudio e vídeo com FFmpeg...");
        } else if (line.contains("[ExtractAudio]") || line.contains("Converting")) {
            progressListener.accept(-1.0, "Convertendo para MP3...");
        }
    }
}