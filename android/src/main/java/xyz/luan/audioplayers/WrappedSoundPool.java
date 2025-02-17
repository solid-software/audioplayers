package xyz.luan.audioplayers;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import static java.io.File.createTempFile;

public class WrappedSoundPool extends Player implements SoundPool.OnLoadCompleteListener {

    private static SoundPool soundPool = createSoundPool();

    private final AudioplayersPlugin ref;

    private final String playerId;

    private String url;

    private float volume = 1.0f;

    private Integer soundId;

    private Integer streamId;

    private boolean playing = false;

    private boolean paused = false;

    private boolean looping = false;

    private boolean loading = false;

    WrappedSoundPool(AudioplayersPlugin ref, String playerId) {
        this.ref = ref;
        this.playerId = playerId;
    }

    @Override
    String getPlayerId() {
        return playerId;
    }

    @Override
    void play() {
        if (!this.loading) {
            start();
        }
        this.playing = true;
        final WrappedSoundPool player = this;
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                ref.handleIsPlaying(player);
            }
        });
    }

    @Override
    void stop() {
        if (this.playing) {
            soundPool.stop(this.streamId);
            this.playing = false;
        }
        this.paused = false;
    }

    @Override
    void release() {
        this.stop();
        soundPool.unload(this.soundId);
    }

    @Override
    void pause() {
        if (this.playing) {
            soundPool.pause(this.streamId);
            this.playing = false;
            this.paused = true;
        }
    }

    @Override
    void setUrl(final String url, final boolean isLocal) {
        if (this.url == null || !this.url.equals(url)) {

            if (this.soundId != null) {
                soundPool.unload(this.soundId);
            } else {
                soundPool.setOnLoadCompleteListener(this);
            }
            this.url = url;
            this.loading = true;

        }
        final WrappedSoundPool self = this;

        new Thread(new Runnable() {
            @Override
            public void run() {
                self.soundId = soundPool.load(getAudioPath(url, isLocal), 1);
            }
        }).start();
    }

    @Override
    void setVolume(double volume) {
        this.volume = (float) volume;
        if (this.playing) {
            soundPool.setVolume(this.streamId, this.volume, this.volume);
        }
    }

    @Override
    void configAttributes(boolean respectSilence) {
    }

    @Override
    void setReleaseMode(ReleaseMode releaseMode) {
        this.looping = releaseMode == ReleaseMode.LOOP;
        if (this.playing) {
            soundPool.setLoop(streamId, this.looping ? -1 : 0);
        }
    }

    @Override
    int getDuration() {
        throw unsupportedOperation("getDuration");
    }

    @Override
    int getCurrentPosition() {
        throw unsupportedOperation("getCurrentPosition");
    }

    @Override
    boolean isActuallyPlaying() {
        return false;
    }

    @Override
    void seek(int position) {
        throw unsupportedOperation("seek");
    }

    private static SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder().setLegacyStreamType(AudioManager.USE_DEFAULT_STREAM_TYPE)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            return new SoundPool.Builder()
                    .setAudioAttributes(attrs)
                    .setMaxStreams(100)
                    .build();
        }
        return unsafeBuildLegacySoundPool();
    }

    @SuppressWarnings("deprecation")
    private static SoundPool unsafeBuildLegacySoundPool() {
        return new SoundPool(1, AudioManager.STREAM_MUSIC, 1);
    }

    private void start() {
        if (this.paused) {
            soundPool.resume(this.streamId);
            this.paused = false;
        } else {
            this.streamId = soundPool.play(
                    soundId,
                    this.volume,
                    this.volume,
                    0,
                    this.looping ? -1 : 0,
                    1.0f);
        }
        ref.handleIsPlaying(this);
    }

    @Override
    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
        if (soundId == sampleId) {
            this.loading = false;
            if (this.playing) {
                start();
            }
            ref.handlePrepared(this);
        }
    }

    private String getAudioPath(String url, boolean isLocal) {
        if (isLocal) {
            return url;
        }
        return loadTempFileFromNetwork(url).getAbsolutePath();
    }

    private File loadTempFileFromNetwork(String url) {
        FileOutputStream fileOutputStream = null;
        try {
            byte[] bytes = downloadUrl(URI.create(url).toURL());
            File tempFile = createTempFile("sound", "");
            fileOutputStream = new FileOutputStream(tempFile);
            fileOutputStream.write(bytes);
            tempFile.deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private byte[] downloadUrl(URL url) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InputStream stream = null;
        try {
            byte[] chunk = new byte[4096];
            int bytesRead;
            stream = url.openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return outputStream.toByteArray();
    }

    private UnsupportedOperationException unsupportedOperation(String message) {
        return new UnsupportedOperationException("LOW_LATENCY mode does not support: " + message);
    }

}
