package io.github.givimad.piperjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.ConfigurationException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
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
        final String version = piper.getPiperVersion();
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
        final String textSpeed = System.getenv().getOrDefault("TEXT_SPEED", "1.0");
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
                createWAVFile(List.of(samples), sampleRate, Path.of("test.wav"), textSpeed);
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
        final String textSpeed = System.getenv().getOrDefault("TEXT_SPEED", "1.0");
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
                createWAVFile(audioSamplesChunks, sampleRate, Path.of("test-stream.wav"), textSpeed);
            }
        } finally {
            piper.terminate();
        }
    }

    private void createWAVFile(final List<short[]> sampleChunks,
                               final long sampleRate,
                               final Path outFilePath,
                               final String textSpeed) {

        final int numSamples = sampleChunks.stream()
                .map(c -> c.length)
                .reduce(0, Integer::sum);

        final double multiplication = Double.parseDouble(textSpeed);
        float speededSampleRate = sampleRate;
        //Range: 0.25 to 1.75 -- in 0.25 steps
        if ((multiplication > 0.24 && multiplication < 1.76) && (multiplication * 100) % 25 == 0) {
            //valid multiplication for the sample rate speed, as we use the original sample rate
            speededSampleRate = (float) ((float) sampleRate * multiplication);
        }
        final AudioFormat jAudioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                speededSampleRate,
                16,
                2,
                2,
                sampleRate,
                false);

        final ByteBuffer byteBuffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short[] chunk : sampleChunks) {
            for (short sample : chunk) {
                byteBuffer.putShort(sample);
            }
        }

        final AudioInputStream audioInputStreamTemp = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()),
                jAudioFormat,
                numSamples);

        try (final FileOutputStream audioFileOutputStream = new FileOutputStream(outFilePath.toFile())) {
            AudioSystem.write(audioInputStreamTemp, AudioFileFormat.Type.WAVE, audioFileOutputStream);
        } catch (IOException e) {
            System.err.println("Unable to store sample: " + e.getMessage());
        }
    }
}
