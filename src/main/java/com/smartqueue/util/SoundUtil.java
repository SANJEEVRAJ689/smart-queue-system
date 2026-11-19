package com.smartqueue.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Audio and Speech announcement utility.
 * 1. Generates an authentic synthesized chime tone (Ding-Dong) using pure Java Sound API
 *    without relying on external sound files.
 * 2. Asynchronously announces called tokens via text-to-speech.
 */
public class SoundUtil {
    private static final Logger logger = LoggerFactory.getLogger(SoundUtil.class);
    private static byte[] precomputedChime;

    static {
        try {
            precomputedChime = generateChimeSamples();
        } catch (Exception e) {
            logger.warn("Could not precompute chime audio", e);
        }
    }

    /**
     * Plays a pleasant two-tone service desk notification chime (Ding-Dong).
     * Runs asynchronously on a separate thread to ensure zero UI delay.
     */
    public static void playChimeAsync() {
        Thread chimeThread = new Thread(() -> {
            try {
                if (precomputedChime == null) {
                    precomputedChime = generateChimeSamples();
                }
                AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();
                    line.write(precomputedChime, 0, precomputedChime.length);
                    line.drain();
                }
            } catch (Exception e) {
                logger.debug("Audio playback unavailable or muted: {}", e.getMessage());
            }
        }, "Sound-Chime-Thread");
        chimeThread.setDaemon(true);
        chimeThread.start();
    }

    /**
     * Announces a token call using text-to-speech asynchronously.
     * Example: "Token A 0 0 4, please proceed to Counter 1"
     */
    public static void speakAnnouncementAsync(String tokenNumber, int counterNumber) {
        Thread speechThread = new Thread(() -> {
            try {
                // Play chime first
                playChimeAsync();
                Thread.sleep(400); // Wait for chime to start

                // Format token number spaced out for clear pronunciation: "A 0 0 4"
                String spacedToken = tokenNumber.replace("-", " ").replaceAll("(?<=\\d)(?=\\d)", " ");
                String message = String.format("Token %s, please proceed to Counter %d", spacedToken, counterNumber);

                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    // Use native Windows Speech API via lightweight background PowerShell command
                    String command = String.format(
                            "Add-Type -AssemblyName System.Speech; " +
                            "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                            "$synth.Rate = 0; " +
                            "$synth.Speak('%s');", message);

                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    process.waitFor();
                } else {
                    logger.info("Speech announcement: [{}]", message);
                }
            } catch (Exception e) {
                logger.debug("TTS announcement error: {}", e.getMessage());
            }
        }, "TTS-Speech-Thread");
        speechThread.setDaemon(true);
        speechThread.start();
    }

    /**
     * Generates a 16-bit 44.1kHz PCM sine wave with two harmonizing tones (E5 659Hz -> C5 523Hz)
     * with smooth exponential amplitude decay.
     */
    private static byte[] generateChimeSamples() {
        int sampleRate = 44100;
        int note1Duration = (int) (sampleRate * 0.25); // 250ms
        int note2Duration = (int) (sampleRate * 0.40); // 400ms

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Note 1: 659.25 Hz (E5)
        writeTone(baos, sampleRate, 659.25, note1Duration);
        // Note 2: 523.25 Hz (C5)
        writeTone(baos, sampleRate, 523.25, note2Duration);

        return baos.toByteArray();
    }

    private static void writeTone(ByteArrayOutputStream baos, int sampleRate, double freq, int samples) {
        for (int i = 0; i < samples; i++) {
            double time = (double) i / sampleRate;
            // Exponential volume decay envelope
            double envelope = Math.exp(-3.5 * ((double) i / samples));
            double angle = 2.0 * Math.PI * freq * time;
            short val = (short) (Math.sin(angle) * envelope * 16000);

            // Little-endian 16-bit
            baos.write(val & 0xFF);
            baos.write((val >> 8) & 0xFF);
        }
    }
}
