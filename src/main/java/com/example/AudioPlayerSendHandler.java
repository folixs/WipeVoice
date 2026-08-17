package com.example;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

public class AudioPlayerSendHandler
        implements AudioSendHandler {

    private final AudioPlayer audioPlayer;

    public AudioPlayerSendHandler(
            AudioPlayer audioPlayer
    ) {
        this.audioPlayer = audioPlayer;
    }

    @Override
    public boolean canProvide() {

        return audioPlayer.getPlayingTrack() != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {

        AudioFrame frame =
                audioPlayer.provide();

        if (frame == null)
            return null;

        return ByteBuffer.wrap(
                frame.getData()
        );
    }

    @Override
    public boolean isOpus() {

        return true;
    }
}
