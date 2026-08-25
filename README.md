# 📥 Ixtal Media Downloader

O **Ixtal Media Downloader** é uma aplicação desktop moderna, rápida e intuitiva desenvolvida em JavaFX para download de áudios e vídeos do YouTube. Utilizando a potência do `yt-dlp` e `ffmpeg`, o programa permite obter mídias com máxima qualidade sem complicações.

![JavaFX](https://img.shields.io/badge/JavaFX-17%2B-orange?style=for-the-badge&logo=openjdk)
![yt-dlp](https://img.shields.io/badge/Engine-yt--dlp-red?style=for-the-badge&logo=youtube)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

---

## ✨ Funcionalidades

- 🎥 **Download de Vídeo**: Baixe vídeos em MP4 em várias resoluções (4K, 1080p, 720p, 480p). obs: ainda estou implementando e melhorando a lógica de seleção de qualidade então pode ser que ainda não funcione bem 
- 🎵 **Download de Áudio**: Extraia áudios em MP3 de alta qualidade (320 kbps, 192 kbps, 128 kbps).
- 🖼️ **Preview do Vídeo**: Exibe título, canal, duração e thumbnail antes de baixar.
- 📁 **Escolha de Destino**: Altere facilmente a pasta de destino dos arquivos baixados.
- ⚡ **Execução Assíncrona**: Interface leve que não trava durante o carregamento ou download.
- 📦 **100% Autônomo**: Não requer instalação prévia do Java ou ferramentas extras no computador do usuário final (quando empacotado).

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem**: Java (JDK 17+)
- **Interface Gráfica**: JavaFX
- **Gerenciador de Dependências**: Maven
- **Motor de Download**: [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- **Processador de Mídia**: [FFmpeg](https://ffmpeg.org/)