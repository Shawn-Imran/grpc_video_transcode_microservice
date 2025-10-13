package com.example.transcoder.model;

import lombok.Data;
import lombok.Builder;

/**
 * Represents a video output format configuration.
 */
@Data
@Builder
public class VideoFormat {
    /**
     * Format name (e.g., "1080p", "720p").
     */
    private String name;
    
    /**
     * Width in pixels.
     */
    private int width;
    
    /**
     * Height in pixels.
     */
    private int height;
    
    /**
     * Video codec (e.g., "h264", "h265").
     */
    private String videoCodec;
    
    /**
     * Video bitrate in kbps.
     */
    private int bitrate;

    /**
     * Creates a standard format with predefined settings.
     *
     * @param name Format name
     * @return VideoFormat instance
     */
    public static VideoFormat createStandard(String name) {
        VideoFormat.VideoFormatBuilder builder = VideoFormat.builder()
                .name(name)
                .videoCodec("libx264");
        
        switch (name) {
            case "1080p":
                return builder.width(1920).height(1080).bitrate(5000).build();
            case "720p":
                return builder.width(1280).height(720).bitrate(2500).build();
            case "480p":
                return builder.width(854).height(480).bitrate(1000).build();
            case "360p":
                return builder.width(640).height(360).bitrate(750).build();
            default:
                throw new IllegalArgumentException("Unknown format: " + name);
        }
    }
}

