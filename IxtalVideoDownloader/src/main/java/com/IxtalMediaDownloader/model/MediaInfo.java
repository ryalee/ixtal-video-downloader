package com.IxtalMediaDownloader.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaInfo {

    @JsonProperty("title")
    private String title;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("webpage_url")
    private String url;

    @JsonProperty("thumbnail")
    private String thumbnail;

    @JsonProperty("duration")
    private long duration;

    public MediaInfo() {}

    public MediaInfo(String title, String channel, String url, String thumbnail, long duration) {
        this.title = title;
        this.channel = channel;
        this.url = url;
        this.thumbnail = thumbnail;
        this.duration = duration;
    }

    public String getTitle() {
        return title;
    }
    public long getDuration() {
        return duration;
    }
    public String getChannel() {
        return channel;
    }
    public String getUrl() {
        return url;
    }
    public String getThumbnail() {
        return thumbnail;
    }
}
