package com.IxtalMediaDownloader;

import com.IxtalMediaDownloader.client.YtDlpClient;
import com.IxtalMediaDownloader.model.MediaInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Paths;

public class Main extends Application {

    private final YtDlpClient client = new YtDlpClient();
    private MediaInfo currentMediaInfo;
    private File selectedFolder;

    private TextField urlField;
    private Button fetchBtn;
    private ImageView thumbnailView;
    private Label titleLabel;
    private Label channelLabel;
    private Label durationLabel;
    private Label folderPathLabel;
    private Button selectFolderBtn;
    private Button downloadBtn;
    private ProgressBar progressBar;
    private Label statusLabel;

    private ComboBox<String> formatCombo;
    private ComboBox<String> resolutionCombo;
    private ComboBox<String> audioQualityCombo;

    @Override
    public void start(Stage stage) {
        selectedFolder = Paths.get(System.getProperty("user.home"), "Downloads").toFile();

        Label mainTitle = new Label("Ixtal Media Downloader");
        mainTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        urlField = new TextField();
        urlField.setPromptText("Cole o link do vídeo do YouTube aqui...");
        urlField.setPrefHeight(35);

        fetchBtn = new Button("Buscar");
        fetchBtn.setPrefHeight(35);
        fetchBtn.setOnAction(e -> onFetchInfo());

        // card do vídeo
        thumbnailView = new ImageView();
        thumbnailView.setFitWidth(200);
        thumbnailView.setFitHeight(120);
        thumbnailView.setPreserveRatio(true);

        titleLabel = new Label("Nenhum vídeo carregado");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setWrapText(true);

        channelLabel = new Label("Canal: -");
        durationLabel = new Label("Duração: -");

        VBox infoBox = new VBox(8, titleLabel, channelLabel, durationLabel);
        HBox cardBox = new HBox(15, thumbnailView, infoBox);
        cardBox.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 15; -fx-background-radius: 8;");
        cardBox.setAlignment(Pos.CENTER_LEFT);

        // formato
        Label formatLabel = new Label("Tipo:");
        formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("Vídeo (MP4)", "Apenas Áudio (MP3)");
        formatCombo.setValue("Vídeo (MP4)");

        // qualidade do vídeo
        Label resLabel = new Label("Qualidade:");
        resolutionCombo = new ComboBox<>();
        resolutionCombo.getItems().addAll("Melhor Qualidade (1080p+ / 4K)", "1080p", "720p", "480p");
        resolutionCombo.setValue("Melhor Qualidade (1080p+ / 4K)");

        // qualidade do áudio
        audioQualityCombo = new ComboBox<>();
        audioQualityCombo.getItems().addAll("320 kbps (Alta)", "192 kbps (Média)", "128 kbps (Normal)");
        audioQualityCombo.setValue("320 kbps (Alta)");
        audioQualityCombo.setVisible(false);
        audioQualityCombo.setManaged(false); // Remove do layout visual quando estiver em formato vídeo

        // muda dinamicamente entre as opções de vídeo e as de áudio
        formatCombo.setOnAction(e -> {
            boolean isAudio = "Apenas Áudio (MP3)".equals(formatCombo.getValue());

            resolutionCombo.setVisible(!isAudio);
            resolutionCombo.setManaged(!isAudio);

            audioQualityCombo.setVisible(isAudio);
            audioQualityCombo.setManaged(isAudio);
        });

        HBox optionsBox = new HBox(15, formatLabel, formatCombo, resLabel, resolutionCombo, audioQualityCombo);
        optionsBox.setAlignment(Pos.CENTER_LEFT);

        // seção da pasta
        Label folderTitle = new Label("Salvar em:");
        folderPathLabel = new Label(selectedFolder.getAbsolutePath());
        folderPathLabel.setStyle("-fx-font-style: italic;");

        selectFolderBtn = new Button("Alterar Pasta...");
        selectFolderBtn.setOnAction(e -> onSelectFolder(stage));

        HBox folderBox = new HBox(10, folderTitle, folderPathLabel, selectFolderBtn);
        folderBox.setAlignment(Pos.CENTER_LEFT);

        // download e barra de progresso
        downloadBtn = new Button("Baixar Mídia");
        downloadBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        downloadBtn.setPrefWidth(200);
        downloadBtn.setPrefHeight(40);
        downloadBtn.setDisable(true);
        downloadBtn.setOnAction(e -> onDownload());

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setVisible(false);

        statusLabel = new Label("Pronto.");
        statusLabel.setStyle("-fx-text-fill: #666666;");

        VBox statusBox = new VBox(5, progressBar, statusLabel);
        statusBox.setAlignment(Pos.CENTER);

        // layout principal
        HBox inputRow = new HBox(10, urlField, fetchBtn);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        HBox.setHgrow(folderPathLabel, Priority.ALWAYS);

        VBox root = new VBox(15, mainTitle, inputRow, cardBox, optionsBox, folderBox, downloadBtn, statusBox);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(root, 680, 580);
        stage.setTitle("Ixtal Media Downloader");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void onFetchInfo() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;

        setLoadingState(true, "Buscando informações do vídeo...");

        Task<MediaInfo> task = new Task<>() {
            @Override
            protected MediaInfo call() throws Exception {
                return client.getVideoInfo(url);
            }
        };

        task.setOnSucceeded(e -> {
            currentMediaInfo = task.getValue();
            titleLabel.setText(currentMediaInfo.getTitle());
            channelLabel.setText("Canal: " + currentMediaInfo.getChannel());
            durationLabel.setText("Duração: " + (currentMediaInfo.getDuration() / 60) + " min");

            if (currentMediaInfo.getThumbnail() != null) {
                thumbnailView.setImage(new Image(currentMediaInfo.getThumbnail(), true));
            }

            setLoadingState(false, "Informações carregadas!");
            downloadBtn.setDisable(false);
        });

        task.setOnFailed(e -> {
            setLoadingState(false, "Erro ao carregar vídeo.");
            Throwable exception = task.getException();
            String detalheDoErro = (exception != null) ? exception.getMessage() : "Erro desconhecido";
            showError(detalheDoErro);
        });

        new Thread(task).start();
    }

    private void onSelectFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecione a pasta de destino");
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            selectedFolder = folder;
            folderPathLabel.setText(selectedFolder.getAbsolutePath());
        }
    }

    private void onDownload() {
        if (currentMediaInfo == null) return;

        setLoadingState(true, "Iniciando download...");
        progressBar.setProgress(0);

        String format = formatCombo.getValue();
        String resolution = resolutionCombo.getValue();
        String audioQuality = audioQualityCombo.getValue();
        String url = urlField.getText().trim();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                client.downloadMedia(url, selectedFolder.toPath(), format, resolution, audioQuality, (progress, statusText) -> {
                    Platform.runLater(() -> {
                        if (progress < 0) {
                            progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                        } else {
                            progressBar.setProgress(progress);
                        }
                        statusLabel.setText(statusText);
                    });
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setLoadingState(false, "Download concluído com sucesso!");
            progressBar.setProgress(1.0);
        });

        task.setOnFailed(e -> {
            setLoadingState(false, "Erro no download.");
            Throwable exception = task.getException();
            String detalhe = (exception != null) ? exception.getMessage() : "Erro desconhecido.";
            showError(detalhe);
        });

        new Thread(task).start();
    }

    private void setLoadingState(boolean isLoading, String status) {
        fetchBtn.setDisable(isLoading);
        downloadBtn.setDisable(isLoading || currentMediaInfo == null);
        selectFolderBtn.setDisable(isLoading);
        formatCombo.setDisable(isLoading);
        resolutionCombo.setDisable(isLoading);
        audioQualityCombo.setDisable(isLoading);
        progressBar.setVisible(isLoading);
        statusLabel.setText(status);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText("Ocorreu um problema");
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}