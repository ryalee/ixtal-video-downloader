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
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("\\[download\\]\\s+([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+|[0-9]+)%");

    public MediaInfo getVideoInfo(String videoUrl) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp",
                "--dump-json",
                "--no-playlist",
                "--no-warnings",
                "--extractor-args", "youtube:player_client=android,web",
                videoUrl
        );

        Process process = processBuilder.start();

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
            throw new RuntimeException("Erro ao buscar informações do vídeo:\n" + errorOutput.toString());
        }

        if (jsonOutput.length() == 0) {
            throw new RuntimeException("Não foi possível extrair os dados do vídeo. Verifique se o link é válido.");
        }

        return objectMapper.readValue(jsonOutput.toString(), MediaInfo.class);
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
            // Trata a qualidade do áudio baseada na seleção (ex: 320k, 192k, 128k)
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
            // Regras estritas de resolução (força a altura solicitada ou a mais próxima abaixo dela)
            String formatRule;
            switch (resolutionChoice) {
                case "1080p":
                    formatRule = "bestvideo[height=1080]+bestaudio/bestvideo[height<=1080]+bestaudio/best";
                    break;
                case "720p":
                    formatRule = "bestvideo[height=720]+bestaudio/bestvideo[height<=720]+bestaudio/best";
                    break;
                case "480p":
                    formatRule = "bestvideo[height=480]+bestaudio/bestvideo[height<=480]+bestaudio/best";
                    break;
                default: // "Melhor Qualidade (1080p+ / 4K)"
                    formatRule = "bestvideo+bestaudio/best";
                    break;
            }

            command.addAll(Arrays.asList(
                    "-f", formatRule,
                    "--merge-output-format", "mp4",
                    "-o", outputTemplate,
                    videoUrl
            ));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

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