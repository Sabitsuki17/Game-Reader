package com.gamereader.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sonidos de aviso del lector. Se sintetizan en el momento (no hay archivos de audio), en tres estilos.
 * Al explorar, el sonido sale por la izquierda o la derecha según dónde esté el elemento y es más agudo cuanto más arriba.
 */
final class Sonidos {

    static final int ENCENDER = 0;
    static final int APAGAR = 1;
    static final int ELEMENTO = 2;
    static final int TOQUE = 3;
    static final int NUEVO = 4;
    static final int ERROR = 5;
    static final int EXPLORAR_ON = 6;
    static final int EXPLORAR_OFF = 7;
    static final int JUEGO = 8;

    private static final int FRECUENCIA = 22050;

    /** Notas de la parte alta a la baja (pentatónica), para convertir la altura en pantalla en tono. */
    private static final double[] ESCALA = {1046.50, 880.00, 783.99, 659.25, 587.33, 523.25, 440.00, 392.00};

    private final Prefs prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private boolean cerrado;

    Sonidos(Context c, Prefs p) {
        prefs = p;
    }

    /**
     * @param x posición horizontal del elemento (0 izquierda, 1 derecha) o NaN
     * @param y posición vertical del elemento (0 arriba, 1 abajo) o NaN
     */
    void reproducir(int evento, double x, double y) {
        if (cerrado || !permitido(evento)) {
            return;
        }
        lanzar(evento, x, y);
    }

    /** Reproduce una muestra de cada tipo, aunque los avisos estén desactivados, para poder elegir el estilo. */
    void probar() {
        final int[] eventos = {ENCENDER, ELEMENTO, ELEMENTO, TOQUE, ERROR, EXPLORAR_ON, APAGAR};
        final double[] xs = {Double.NaN, 0.05, 0.95, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        final double[] ys = {Double.NaN, 0.05, 0.95, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        long retraso = 0;
        for (int i = 0; i < eventos.length; i++) {
            final int e = eventos[i];
            final double x = xs[i];
            final double y = ys[i];
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!cerrado) {
                        lanzar(e, x, y);
                    }
                }
            }, retraso);
            retraso += 650;
        }
    }

    void cerrar() {
        cerrado = true;
        hilo.shutdown();
    }

    private boolean permitido(int evento) {
        if (!prefs.sonidos()) {
            return false;
        }
        switch (evento) {
            case ENCENDER:
            case APAGAR:
            case EXPLORAR_ON:
            case EXPLORAR_OFF:
            case JUEGO:
                return prefs.sonidosEstado();
            case ELEMENTO:
                return prefs.sonidosPosicion();
            case TOQUE:
            case ERROR:
                return prefs.sonidosAcciones();
            case NUEVO:
                return prefs.sonidosNuevo();
            default:
                return false;
        }
    }

    private void lanzar(final int evento, final double x, final double y) {
        final int estilo = prefs.sonidosEstilo();
        final float volumen = prefs.sonidosVol() / 100f;
        try {
            hilo.execute(new Runnable() {
                @Override
                public void run() {
                    sonar(evento, x, y, estilo, volumen);
                }
            });
        } catch (Exception e) {
            // El lector se ha cerrado: no hay nada que sonar.
        }
    }

    private void sonar(int evento, double x, double y, int estilo, float volumen) {
        try {
            double pan = Double.isNaN(x) ? 0.0 : Math.max(-1.0, Math.min(1.0, (x - 0.5) * 2.0));
            reproducirBuffer(sintetizar(notasDe(evento, y), estilo, pan), volumen);
        } catch (Throwable t) {
            // Un sonido que falla nunca debe afectar al lector.
        }
    }

    /** Cada nota es {frecuencia en Hz, duración en ms}. */
    private static double[][] notasDe(int evento, double y) {
        switch (evento) {
            case ENCENDER:
                return new double[][]{{523.25, 90}, {783.99, 150}};
            case APAGAR:
                return new double[][]{{783.99, 90}, {392.00, 170}};
            case ELEMENTO:
                return new double[][]{{notaPorAltura(y), 75}};
            case TOQUE:
                return new double[][]{{880.00, 30}, {1174.66, 55}};
            case NUEVO:
                return new double[][]{{987.77, 60}};
            case ERROR:
                return new double[][]{{233.08, 110}, {174.61, 170}};
            case EXPLORAR_ON:
                return new double[][]{{440.00, 70}, {554.37, 70}, {659.25, 110}};
            case EXPLORAR_OFF:
                return new double[][]{{659.25, 70}, {554.37, 70}, {440.00, 110}};
            default:
                return new double[][]{{523.25, 80}, {659.25, 80}, {783.99, 80}, {1046.50, 140}};
        }
    }

    private static double notaPorAltura(double y) {
        if (Double.isNaN(y)) {
            return ESCALA[3];
        }
        int i = (int) Math.floor(Math.max(0.0, Math.min(0.999, y)) * ESCALA.length);
        return ESCALA[i];
    }

    /** Genera las muestras estéreo de 16 bits. pan: -1 todo a la izquierda, 1 todo a la derecha. */
    private static short[] sintetizar(double[][] notas, int estilo, double pan) {
        int total = 0;
        for (double[] n : notas) {
            total += (int) (FRECUENCIA * n[1] / 1000.0);
        }
        short[] salida = new short[total * 2];
        double izquierda = Math.sqrt((1.0 - pan) / 2.0);
        double derecha = Math.sqrt((1.0 + pan) / 2.0);
        int pos = 0;
        for (double[] n : notas) {
            int muestras = (int) (FRECUENCIA * n[1] / 1000.0);
            for (int i = 0; i < muestras; i++) {
                double fase = 2.0 * Math.PI * n[0] * (i / (double) FRECUENCIA);
                double onda;
                if (estilo == 1) {
                    onda = Math.sin(fase) >= 0 ? 0.6 : -0.6;
                } else if (estilo == 2) {
                    onda = 0.6 * Math.sin(fase) + 0.25 * Math.sin(2.0 * fase) + 0.1 * Math.sin(3.01 * fase);
                } else {
                    onda = Math.sin(fase);
                }
                double progreso = i / (double) muestras;
                double ataque = Math.min(1.0, i / (FRECUENCIA * 0.004));
                double caida = estilo == 1 ? (1.0 - progreso) : Math.pow(1.0 - progreso, 2.0);
                short s = (short) Math.round(onda * ataque * caida * 0.5 * Short.MAX_VALUE);
                salida[pos++] = (short) Math.round(s * izquierda);
                salida[pos++] = (short) Math.round(s * derecha);
            }
        }
        return salida;
    }

    private void reproducirBuffer(short[] muestras, float volumen) {
        final AudioTrack pista = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(FRECUENCIA)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setBufferSizeInBytes(muestras.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        pista.write(muestras, 0, muestras.length);
        pista.setVolume(volumen);
        pista.play();
        long duracion = (long) (muestras.length / 2) * 1000L / FRECUENCIA;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    pista.release();
                } catch (Exception e) {
                    // Ya liberada.
                }
            }
        }, duracion + 250);
    }
}
