package io.github.givimad.piperjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.ConfigurationException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class PiperJNITest {

    private static PiperJNI piper;

    @BeforeAll
    public static void before() throws IOException {
        piper = new PiperJNI();
    }

    @Test
    public void getPiperVersion() {
        var version = piper.getPiperVersion();
        assertNotNull(version, "vad mode configured");
        System.out.println("Piper version: " + version);
    }

    @Test
    public void initializePiper() throws IOException {
        try {
            piper.initialize(true, false);
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void createPiperVoice() throws IOException, ConfigurationException, PiperJNI.NotInitialized {
        final String voiceModel = System.getenv("VOICE_MODEL");
        final String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        try {
            piper.initialize();
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig))) {
                assertNotNull(voice);
            }
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void createAudioData() throws IOException, ConfigurationException, PiperJNI.NotInitialized {
        final String voiceModel = System.getenv("VOICE_MODEL");
        final String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        final String textToSpeak = System.getenv("TEXT_TO_SPEAK");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        if (textToSpeak == null || textToSpeak.isBlank()) {
            throw new ConfigurationException("env var TEXT_TO_SPEAK is required");
        }
        try {
            piper.initialize(true, true);
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig), 0)) {
                assertNotNull(voice);
                final int sampleRate = voice.getSampleRate();
                final short[] samples = piper.textToAudio(voice, textToSpeak);
                assertNotEquals(0, samples.length);
                createWAVFile(List.of(samples), sampleRate, Path.of("test.wav"));
            }
        } finally {
            piper.terminate();
        }
    }

    @Test
    public void streamAudioData() throws ConfigurationException, IOException, PiperJNI.NotInitialized {
        final String voiceModel = System.getenv("VOICE_MODEL");
        final String voiceModelConfig = System.getenv("VOICE_MODEL_CONFIG");
        final String textToSpeak = System.getenv("TEXT_TO_SPEAK");
        if (voiceModel == null || voiceModel.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL is required");
        }
        if (voiceModelConfig == null || voiceModelConfig.isBlank()) {
            throw new ConfigurationException("env var VOICE_MODEL_CONFIG is required");
        }
        if (textToSpeak == null || textToSpeak.isBlank()) {
            throw new ConfigurationException("env var TEXT_TO_SPEAK is required");
        }
        try {
            piper.initialize(true, false);
            try (var voice = piper.loadVoice(Paths.get(voiceModel), Path.of(voiceModelConfig))) {
                assertNotNull(voice);
                final int sampleRate = voice.getSampleRate();
                final ArrayList<short[]> audioSamplesChunks = new ArrayList<>();
                piper.textToAudio(voice, textToSpeak, audioSamplesChunks::add);
                assertFalse(audioSamplesChunks.isEmpty());
                assertNotEquals(0, audioSamplesChunks.get(0).length);
                createWAVFile(audioSamplesChunks, sampleRate, Path.of("test-stream.wav"));
            }
        } finally {
            piper.terminate();
        }
    }

    private void createWAVFile(final List<short[]> sampleChunks,
                               final long sampleRate,
                               final Path outFilePath) {

        final int numSamples = sampleChunks.stream().map(c -> c.length).reduce(0, Integer::sum);
        final javax.sound.sampled.AudioFormat jAudioFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,
                2,
                sampleRate,
                false);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (var chunk : sampleChunks) {
            for (var sample : chunk) {
                byteBuffer.putShort(sample);
            }
        }
        final AudioInputStream audioInputStreamTemp = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()),
                jAudioFormat,
                numSamples);
        try {
            final FileOutputStream audioFileOutputStream = new FileOutputStream(outFilePath.toFile());
            AudioSystem.write(audioInputStreamTemp, AudioFileFormat.Type.WAVE, audioFileOutputStream);
            audioFileOutputStream.close();
        } catch (IOException e) {
            System.err.println("Unable to store sample: " + e.getMessage());
        }
    }
}
