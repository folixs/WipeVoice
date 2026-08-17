const {
    Client,
    GatewayIntentBits
} = require("discord.js");

const {
    joinVoiceChannel,
    createAudioPlayer,
    createAudioResource,
    AudioPlayerStatus,
    VoiceConnectionStatus,
    StreamType
} = require("@discordjs/voice");

const {
    spawn
} = require("child_process");

const ffmpegPath = require("ffmpeg-static");


// ==============================
// CONFIG
// ==============================

const TOKEN = process.env.DISCORD_TOKEN;

const GUILD_ID = "1349725346060304434";

const VOICE_CHANNEL_ID = "1538835705818513448";

const MUSIC_URL =
    process.env.MUSIC_URL ||
    "https://dl.musicdel.ir/Music/1404/05/BLOK3-git%20-musicdel.ir.mp3";


// ==============================
// DISCORD
// ==============================

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildVoiceStates
    ]
});


// ==============================
// VOICE
// ==============================

let connection = null;

const player = createAudioPlayer();

let musicProcess = null;

let musicPlaying = false;

let ttsPlaying = false;


// ==============================
// LOGIN
// ==============================

client.once("ready", async () => {

    console.log(
        `Bot giriş yaptı: ${client.user.tag}`
    );

    const guild =
        await client.guilds.fetch(GUILD_ID);

    if (!guild) {
        console.error("Sunucu bulunamadı.");
        return;
    }

    const channel =
        await guild.channels.fetch(
            VOICE_CHANNEL_ID
        );

    if (!channel) {
        console.error(
            "Ses kanalı bulunamadı."
        );

        return;
    }

    console.log(
        `Ses kanalına bağlanılıyor: ${channel.name}`
    );


    connection = joinVoiceChannel({
        channelId: channel.id,
        guildId: guild.id,
        adapterCreator: guild.voiceAdapterCreator,
        selfDeaf: false,
        selfMute: false
    });


    connection.subscribe(player);


    connection.on(
        VoiceConnectionStatus.Ready,
        () => {

            console.log(
                "Discord ses bağlantısı hazır."
            );

            checkUsers();
        }
    );


    connection.on(
        VoiceConnectionStatus.Disconnected,
        () => {

            console.log(
                "Ses bağlantısı kesildi."
            );
        }
    );


    player.on(
        "error",
        error => {

            console.error(
                "Audio Player Error:",
                error
            );

        }
    );


    console.log("Bot hazır.");
});


// ==============================
// VOICE USER CHECK
// ==============================

async function getVoiceChannel() {

    try {

        const guild =
            await client.guilds.fetch(
                GUILD_ID
            );

        const channel =
            await guild.channels.fetch(
                VOICE_CHANNEL_ID
            );

        return channel;

    } catch (error) {

        console.error(
            "Kanal alınamadı:",
            error.message
        );

        return null;
    }
}


async function getHumanUsers() {

    const channel =
        await getVoiceChannel();

    if (!channel)
        return [];


    return channel.members.filter(
        member => !member.user.bot
    );
}


async function checkUsers() {

    const users =
        await getHumanUsers();

    console.log(
        `Aktif kullanıcı: ${users.size}`
    );


    /*
     * Birisi geldiyse müziği başlat.
     */

    if (
        users.size > 0 &&
        !musicPlaying &&
        !ttsPlaying
    ) {

        startMusic();
    }


    /*
     * Kanal boşsa müziği kapat.
     */

    if (
        users.size === 0
    ) {

        stopMusic();

        player.stop();

        return;
    }
}


// ==============================
// MUSIC
// ==============================

function startMusic() {

    if (musicPlaying)
        return;

    if (ttsPlaying)
        return;


    console.log(
        "Müzik başlatılıyor..."
    );


    try {

        musicProcess = spawn(
            ffmpegPath,
            [
                "-hide_banner",
                "-loglevel",
                "error",

                "-reconnect",
                "1",
                "-reconnect_streamed",
                "1",
                "-reconnect_delay_max",
                "5",

                "-i",
                MUSIC_URL,

                "-vn",

                "-f",
                "s16le",

                "-ar",
                "48000",

                "-ac",
                "2",

                "pipe:1"
            ],
            {
                stdio: [
                    "ignore",
                    "pipe",
                    "pipe"
                ]
            }
        );


        const resource =
            createAudioResource(
                musicProcess.stdout,
                {
                    inputType:
                        StreamType.Raw,

                    inlineVolume: true
                }
            );


        resource.volume.setVolume(
            0.75
        );


        player.play(resource);

        musicPlaying = true;


        musicProcess.stderr.on(
            "data",
            data => {

                const text =
                    data.toString().trim();

                if (text) {

                    console.error(
                        "FFmpeg:",
                        text
                    );
                }
            }
        );


        musicProcess.on(
            "close",
            code => {

                musicPlaying = false;

                musicProcess = null;


                console.log(
                    `Müzik işlemi kapandı: ${code}`
                );


                /*
                 * Kanal hâlâ doluysa
                 * müziği tekrar başlat.
                 */

                setTimeout(
                    async () => {

                        const users =
                            await getHumanUsers();

                        if (
                            users.size > 0 &&
                            !ttsPlaying
                        ) {

                            startMusic();
                        }

                    },
                    1000
                );
            }
        );


    } catch (error) {

        musicPlaying = false;

        console.error(
            "Müzik başlatılamadı:",
            error
        );
    }
}


function stopMusic() {

    if (
        musicProcess
    ) {

        try {

            musicProcess.kill(
                "SIGKILL"
            );

        } catch (_) {
        }

        musicProcess = null;
    }

    musicPlaying = false;
}


// ==============================
// TTS
// ==============================

async function speak(text) {

    if (ttsPlaying)
        return;


    const users =
        await getHumanUsers();

    if (users.size === 0)
        return;


    ttsPlaying = true;


    console.log(
        `TTS: ${text}`
    );


    try {

        const encoded =
            encodeURIComponent(text);


        const url =
            "https://translate.google.com/translate_tts" +
            "?ie=UTF-8" +
            "&client=tw-ob" +
            "&tl=fa" +
            "&q=" +
            encoded;


        const response =
            await fetch(
                url,
                {
                    headers: {
                        "User-Agent":
                            "Mozilla/5.0"
                    }
                }
            );


        if (!response.ok) {

            throw new Error(
                `TTS HTTP ${response.status}`
            );
        }


        const buffer =
            Buffer.from(
                await response.arrayBuffer()
            );


        /*
         * MP3 -> PCM
         */

        const process =
            spawn(
                ffmpegPath,
                [
                    "-hide_banner",
                    "-loglevel",
                    "error",

                    "-i",
                    "pipe:0",

                    "-f",
                    "s16le",

                    "-ar",
                    "48000",

                    "-ac",
                    "2",

                    "pipe:1"
                ],
                {
                    stdio: [
                        "pipe",
                        "pipe",
                        "pipe"
                    ]
                }
            );


        process.stdin.write(
            buffer
        );

        process.stdin.end();


        const resource =
            createAudioResource(
                process.stdout,
                {
                    inputType:
                        StreamType.Raw
                }
            );


        /*
         * Müziği geçici olarak kes.
         */

        stopMusic();

        player.play(resource);


        await new Promise(
            resolve => {

                const timeout =
                    setTimeout(
                        resolve,
                        10000
                    );


                player.once(
                    AudioPlayerStatus.Idle,
                    () => {

                        clearTimeout(
                            timeout
                        );

                        resolve();
                    }
                );
            }
        );


        try {
            process.kill(
                "SIGKILL"
            );
        } catch (_) {
        }


    } catch (error) {

        console.error(
            "TTS hatası:",
            error
        );

    } finally {

        ttsPlaying = false;


        /*
         * TTS bittikten sonra
         * kanalda biri varsa müzik devam eder.
         */

        const users =
            await getHumanUsers();

        if (
            users.size > 0
        ) {

            setTimeout(
                () => {

                    if (
                        !ttsPlaying &&
                        !musicPlaying
                    ) {

                        startMusic();
                    }

                },
                300
            );
        }
    }
}


// ==============================
// EVERY 15 SECONDS
// ==============================

setInterval(
    async () => {

        try {

            const users =
                await getHumanUsers();


            if (users.size === 0) {

                stopMusic();

                return;
            }


            const count =
                users.size;


            const text =
                `استف های فعال تو ویس ${count}`;


            await speak(text);


        } catch (error) {

            console.error(
                "15 saniyelik kontrol:",
                error
            );
        }

    },
    15000
);


// ==============================
// VOICE STATE
// ==============================

client.on(
    "voiceStateUpdate",
    async (oldState, newState) => {

        /*
         * Sadece hedef voice channel.
         */

        if (
            newState.channelId !==
            VOICE_CHANNEL_ID
        ) {

            return;
        }


        /*
         * Bot kendisi girdiyse
         * işlem yapma.
         */

        if (
            newState.member?.user.bot
        ) {

            return;
        }


        console.log(
            `${newState.member.user.tag} ses kanalına girdi.`
        );


        /*
         * Birisi geldiğinde hemen müzik.
         */

        const users =
            await getHumanUsers();


        if (
            users.size > 0 &&
            !musicPlaying &&
            !ttsPlaying
        ) {

            startMusic();
        }
    }
);


// ==============================
// ERROR HANDLING
// ==============================

process.on(
    "unhandledRejection",
    error => {

        console.error(
            "Unhandled rejection:",
            error
        );
    }
);


// ==============================
// LOGIN
// ==============================

if (!TOKEN) {

    console.error(
        "DISCORD_TOKEN bulunamadı!"
    );

    process.exit(1);
}


client.login(TOKEN);
