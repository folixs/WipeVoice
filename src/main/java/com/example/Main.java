package com.example;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

public class Main extends ListenerAdapter {

    private static final String GUILD_ID = "1349725346060304434";
    private static final String VOICE_CHANNEL_ID = "1538835705818513448";

    private static final String TOKEN =
            System.getenv("DISCORD_TOKEN");

    /*
     * GitHub'daki müzik/audio URL'sini buraya
     * Replit Secret olarak koy:
     *
     * MUSIC_URL=https://...
     */
    private static final String MUSIC_URL =
            System.getenv("MUSIC_URL");

    private static JDA jda;

    private static AudioPlayerManager playerManager;
    private static AudioPlayer player;

    private static ScheduledExecutorService scheduler;

    private static boolean usersWerePresent = false;

    public static void main(String[] args) throws Exception {

        if (TOKEN == null || TOKEN.isBlank()) {
            throw new IllegalStateException(
                    "DISCORD_TOKEN bulunamadı!"
            );
        }

        if (MUSIC_URL == null || MUSIC_URL.isBlank()) {
            throw new IllegalStateException(
                    "MUSIC_URL bulunamadı!"
            );
        }

        System.out.println("Bot başlatılıyor...");

        /*
         * LavaPlayer
         */
        playerManager = new DefaultAudioPlayerManager();

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        player = playerManager.createPlayer();

        /*
         * JDA
         */
        jda = JDABuilder.createDefault(TOKEN)
                .addEventListeners(new Main())
                .build();

        jda.awaitReady();

        System.out.println(
                "Discord bağlantısı başarılı."
        );

        /*
         * Ses kanalına bağlan
         */
        connectToVoiceChannel();

        /*
         * 15 saniyelik kontrol sistemi
         */
        scheduler = Executors.newScheduledThreadPool(2);

        scheduler.scheduleAtFixedRate(
                Main::checkVoiceChannel,
                5,
                15,
                TimeUnit.SECONDS
        );

        System.out.println("Bot hazır.");
    }

    private static void connectToVoiceChannel() {

        Guild guild = jda.getGuildById(GUILD_ID);

        if (guild == null) {
            System.err.println(
                    "Guild bulunamadı: " + GUILD_ID
            );
            return;
        }

        VoiceChannel channel =
                guild.getVoiceChannelById(
                        VOICE_CHANNEL_ID
                );

        if (channel == null) {
            System.err.println(
                    "Voice channel bulunamadı: "
                            + VOICE_CHANNEL_ID
            );
            return;
        }

        AudioManager audioManager =
                guild.getAudioManager();

        /*
         * LavaPlayer -> JDA audio handler
         */
        audioManager.setSendingHandler(
                new AudioPlayerSendHandler(player)
        );

        audioManager.openAudioConnection(channel);

        System.out.println(
                "Ses kanalına bağlanıldı: "
                        + channel.getName()
        );
    }

    private static void checkVoiceChannel() {

        try {

            Guild guild =
                    jda.getGuildById(GUILD_ID);

            if (guild == null)
                return;

            VoiceChannel channel =
                    guild.getVoiceChannelById(
                            VOICE_CHANNEL_ID
                    );

            if (channel == null)
                return;

            int count =
                    channel.getMembers().size();

            System.out.println(
                    "Aktif ses kullanıcıları: "
                            + count
            );

            /*
             * Bot hariç gerçekten kullanıcı var mı?
             */
            boolean hasUsers =
                    channel.getMembers()
                            .stream()
                            .anyMatch(member ->
                                    !member.getUser().isBot()
                            );

            /*
             * İlk kullanıcı geldiğinde müziği başlat.
             */
            if (hasUsers && !usersWerePresent) {

                System.out.println(
                        "Bir kullanıcı ses kanalına girdi."
                );

                playMusic();

            }

            usersWerePresent = hasUsers;

            /*
             * Her 15 saniyede TTS
             */
            if (hasUsers) {

                int realUserCount =
                        (int) channel.getMembers()
                                .stream()
                                .filter(member ->
                                        !member.getUser().isBot()
                                )
                                .count();

                String text =
                        "استف های فعال تو ویس "
                                + realUserCount;

                speak(text);
            }

            /*
             * Kanal tamamen boşsa müziği durdur.
             */
            if (!hasUsers) {

                if (player.getPlayingTrack() != null) {

                    player.stopTrack();

                    System.out.println(
                            "Kanal boş. Müzik durduruldu."
                    );
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static void playMusic() {

        playerManager.loadItem(
                MUSIC_URL,
                new AudioLoadResultHandler() {

                    @Override
                    public void trackLoaded(
                            AudioTrack track
                    ) {

                        System.out.println(
                                "Müzik yükleniyor: "
                                        + track.getInfo().title
                        );

                        player.playTrack(track);
                    }

                    @Override
                    public void playlistLoaded(
                            AudioPlaylist playlist
                    ) {

                        if (playlist.getTracks().isEmpty())
                            return;

                        AudioTrack track =
                                playlist.getTracks().get(0);

                        player.playTrack(track);
                    }

                    @Override
                    public void noMatches() {

                        System.err.println(
                                "MUSIC_URL için müzik bulunamadı."
                        );
                    }

                    @Override
                    public void loadFailed(
                            com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception
                    ) {

                        System.err.println(
                                "Müzik yüklenemedi: "
                                        + exception.getMessage()
                        );
                    }
                }
        );
    }

    private static void speak(String text) {

        scheduler.execute(() -> {

            try {

                /*
                 * Google Translate TTS endpoint.
                 *
                 * Farsça:
                 * tl=fa
                 */
                String encoded =
                        URLEncoder.encode(
                                text,
                                StandardCharsets.UTF_8
                        );

                String url =
                        "https://translate.google.com/translate_tts"
                                + "?ie=UTF-8"
                                + "&client=tw-ob"
                                + "&tl=fa"
                                + "&q="
                                + encoded;

                Path output =
                        Files.createTempFile(
                                "discord-tts-",
                                ".mp3"
                        );

                HttpClient client =
                        HttpClient.newHttpClient();

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header(
                                        "User-Agent",
                                        "Mozilla/5.0"
                                )
                                .GET()
                                .build();

                HttpResponse<byte[]> response =
                        client.send(
                                request,
                                HttpResponse.BodyHandlers.ofByteArray()
                        );

                if (response.statusCode() != 200) {

                    System.err.println(
                            "TTS HTTP error: "
                                    + response.statusCode()
                    );

                    return;
                }

                Files.write(
                        output,
                        response.body()
                );

                /*
                 * TTS'i Discord'a gönder.
                 */
                playerManager.loadItem(
                        output.toAbsolutePath().toString(),
                        new AudioLoadResultHandler() {

                            @Override
                            public void trackLoaded(
                                    AudioTrack track
                            ) {

                                player.playTrack(track);

                                /*
                                 * Track bittikten sonra temp dosyayı sil.
                                 */
                                CompletableFuture.delayedExecutor(
                                        Math.max(
                                                1000,
                                                track.getDuration() + 1000
                                        ),
                                        TimeUnit.MILLISECONDS
                                ).execute(() -> {

                                    try {
                                        Files.deleteIfExists(output);
                                    } catch (IOException ignored) {
                                    }

                                });
                            }

                            @Override
                            public void playlistLoaded(
                                    AudioPlaylist playlist
                            ) {
                            }

                            @Override
                            public void noMatches() {
                            }

                            @Override
                            public void loadFailed(
                                    com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception
                            ) {

                                System.err.println(
                                        "TTS audio yüklenemedi: "
                                                + exception.getMessage()
                                );
                            }
                        }
                );

            } catch (Exception e) {

                System.err.println(
                        "TTS hatası:"
                );

                e.printStackTrace();
            }
        });
    }

    /*
     * Birisi kanala girdiğinde anında kontrol et.
     */
    @Override
    public void onGuildVoiceUpdate(
            @Nonnull GuildVoiceUpdateEvent event
    ) {

        if (!event.getGuild().getId().equals(GUILD_ID))
            return;

        if (event.getChannelJoined() == null)
            return;

        if (!event.getChannelJoined()
                .getId()
                .equals(VOICE_CHANNEL_ID))
            return;

        if (event.getMember().getUser().isBot())
            return;

        System.out.println(
                event.getMember().getEffectiveName()
                        + " ses kanalına katıldı."
        );

        playMusic();
    }
}
