package com.IxtalMediaDownloader.client;

import com.IxtalMediaDownloader.model.MediaInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YtDlpClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MediaInfo getVideoInfo(String videoUrl) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "yt-dlp",
                "--dump-json",
                "--no-playlist",
                "--no-warnings",
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

    public void downloadMedia(String videoUrl, Path outputDir, String formatChoice, String resolutionChoice) throws Exception {
        String outputTemplate = outputDir.resolve("%(title)s.%(ext)s").toString();

        List<String> command = new ArrayList<>();
        command.add("yt-dlp");

        // essa parada aqui é pra baixar mais rapido
        command.addAll(Arrays.asList(
                "-N", "4",                       // usa 4 conexões paralelas para download
                "--concurrent-fragments", "4",   // baixa fragmentos de vídeos DASH simultaneamente
                "--buffersize", "16M",           // aumenta o buffer em ram para gravação mais rápida no disco
                "--no-playlist",
                "--downloader", "aria2c",
                "--downloader-args", "aria2c:-j 8 -s 8 -x 8k 1M"
        ));


        // escolha de audio ou video
        if ("Apenas Áudio (MP3)".equals(formatChoice)) {
            command.addAll(Arrays.asList(
                    "-f", "bestaudio/best",
                    "-x",
                    "--audio-format", "mp3",
                    "-o", outputTemplate,
                    videoUrl
            ));
        } else {
            String formatRule;
            switch (resolutionChoice) {
                case "1080p":
                    formatRule = "bestvideo[height<=1080]+bestaudio/best[height<=1080]";
                    break;
                case "720p":
                    formatRule = "bestvideo[height<=720]+bestaudio/best[height<=720]";
                    break;
                case "480p":
                    formatRule = "bestvideo[height<=480]+bestaudio/best[height<=480]";
                    break;
                default:
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
        pb.inheritIO();
        executeProcess(pb);
    }

    private void executeProcess(ProcessBuilder pb) throws Exception {
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Falha no download. Código de erro: " + exitCode);
        }
    }
}