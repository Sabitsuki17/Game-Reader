package com.gamereader.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Voz del lector. Usa el motor de síntesis PREDETERMINADO del sistema (el que ya tengas configurado),
 * por la salida de accesibilidad, y baja el volumen del juego mientras habla.
 */
final class Voz implements TextToSpeech.OnInitListener {

    interface Oyente {
        void alEstarLista(boolean ok);
    }

    private final Context ctx;
    private final Prefs prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioAttributes atributos = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private TextToSpeech tts;
    private Oyente oyente;
    private boolean lista;
    private boolean idiomaOk = true;
    private String pendiente;
    private boolean pendienteInterrumpir;
    private AudioFocusRequest foco;
    private boolean tieneFoco;
    private int contador;
    private String idiomaPuesto = "";
    private int esperas;

    Voz(Context c, Prefs p) {
        ctx = c.getApplicationContext();
        prefs = p;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    void iniciar(Oyente o) {
        oyente = o;
        try {
            tts = new TextToSpeech(ctx, this);
        } catch (Exception e) {
            tts = null;
            if (o != null) {
                o.alEstarLista(false);
            }
        }
    }

    @Override
    public void onInit(int estado) {
        if (estado != TextToSpeech.SUCCESS || tts == null) {
            lista = false;
            if (oyente != null) {
                oyente.alEstarLista(false);
            }
            return;
        }
        try {
            tts.setAudioAttributes(atributos);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    liberarPronto();
                }

                @Override
                public void onError(String id) {
                    liberarPronto();
                }

                @Override
                public void onStop(String id, boolean interrumpida) {
                    liberarPronto();
                }
            });
        } catch (Exception e) {
            // Seguimos: la voz funcionará aunque no se pueda registrar el oyente.
        }
        lista = true;
        aplicar();
        if (oyente != null) {
            oyente.alEstarLista(true);
        }
        if (pendiente != null) {
            String p = pendiente;
            pendiente = null;
            decir(p, pendienteInterrumpir);
        }
    }

    boolean estaLista() {
        return lista;
    }

    boolean idiomaDisponible() {
        return idiomaOk;
    }

    /** Aplica idioma y voz concreta elegidos en los ajustes. */
    void aplicar() {
        if (tts == null || !lista) {
            return;
        }
        idiomaPuesto = "";
        try {
            int r = tts.setLanguage(prefs.localeVoz());
            idiomaOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            String nombre = prefs.voz();
            if (!nombre.isEmpty()) {
                Set<Voice> todas = tts.getVoices();
                if (todas != null) {
                    for (Voice v : todas) {
                        if (nombre.equals(v.getName())) {
                            tts.setVoice(v);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Algunos motores fallan al consultar voces; se usa la configuración por defecto del motor.
        }
    }

    void decir(String texto, boolean interrumpir) {
        decir(texto, interrumpir, null);
    }

    /** @param idioma idioma solo para este texto; null = el de los ajustes */
    void decir(String texto, boolean interrumpir, Locale idioma) {
        if (texto == null || texto.trim().isEmpty()) {
            return;
        }
        if (!lista || tts == null) {
            pendiente = texto;
            pendienteInterrumpir = interrumpir;
            return;
        }
        try {
            ponerIdioma(idioma);
            tts.setSpeechRate(prefs.velocidad());
            tts.setPitch(prefs.tono());
            pedirFoco();
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.volumen());
            int maximo = Math.max(500, Math.min(3000, TextToSpeech.getMaxSpeechInputLength() - 100));
            int modo = interrumpir ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            for (String trozo : FiltroTexto.trocear(texto, maximo)) {
                contador++;
                tts.speak(trozo, modo, params, "lj" + contador);
                modo = TextToSpeech.QUEUE_ADD;
            }
        } catch (Exception e) {
            soltarFoco();
        }
    }

    /** Cambia de idioma solo si hace falta; null vuelve al idioma y la voz elegidos en los ajustes. */
    private void ponerIdioma(Locale loc) {
        String etiqueta = loc == null ? "" : loc.toLanguageTag();
        if (etiqueta.equals(idiomaPuesto)) {
            return;
        }
        try {
            if (loc != null && tts.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(loc);
                idiomaPuesto = etiqueta;
            } else {
                aplicar();
            }
        } catch (Exception e) {
            idiomaPuesto = "";
        }
    }

    void detener() {
        pendiente = null;
        try {
            if (tts != null) {
                tts.stop();
            }
        } catch (Exception e) {
            // Nada que hacer.
        }
        liberarPronto();
    }

    void apagar() {
        h.removeCallbacks(liberar);
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception e) {
            // Nada que hacer.
        }
        tts = null;
        lista = false;
        soltarFoco();
    }

    // ---------- Información del motor y las voces ----------

    /** Nombre del motor de voz predeterminado del sistema. */
    String motor() {
        try {
            String paquete = tts.getDefaultEngine();
            PackageManager pm = ctx.getPackageManager();
            try {
                return pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                return paquete;
            }
        } catch (Exception e) {
            return ctx.getString(R.string.desconocido);
        }
    }

    /** Voces instaladas del idioma indicado ("es", "en"...), ordenadas por nombre. */
    List<Voice> voces(String idioma) {
        List<Voice> salida = new ArrayList<Voice>();
        try {
            Set<Voice> todas = tts == null ? null : tts.getVoices();
            if (todas != null) {
                for (Voice v : todas) {
                    boolean instalada = !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
                    if (instalada && v.getLocale().getLanguage().equals(idioma)) {
                        salida.add(v);
                    }
                }
            }
        } catch (Exception e) {
            // Devolvemos lo que haya.
        }
        Collections.sort(salida, new Comparator<Voice>() {
            @Override
            public int compare(Voice a, Voice b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return salida;
    }

    String describir(Voice v) {
        String lugar = v.getLocale().getDisplayName(Locale.forLanguageTag("es"));
        String red = v.isNetworkConnectionRequired() ? ctx.getString(R.string.voz_en_linea) : ctx.getString(R.string.voz_sin_conexion);
        return lugar + " · " + red + " · " + v.getName();
    }

    // ---------- Foco de audio (baja el volumen del juego mientras habla) ----------

    private void pedirFoco() {
        h.removeCallbacks(liberar);
        esperas = 0;
        if (!prefs.bajarJuego() || tieneFoco || audio == null) {
            return;
        }
        foco = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(atributos)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build();
        tieneFoco = audio.requestAudioFocus(foco) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void soltarFoco() {
        if (tieneFoco && foco != null && audio != null) {
            try {
                audio.abandonAudioFocusRequest(foco);
            } catch (Exception e) {
                // Nada que hacer.
            }
        }
        tieneFoco = false;
    }

    private void liberarPronto() {
        h.post(new Runnable() {
            @Override
            public void run() {
                h.removeCallbacks(liberar);
                h.postDelayed(liberar, 350);
            }
        });
    }

    private final Runnable liberar = new Runnable() {
        @Override
        public void run() {
            boolean hablando = false;
            try {
                hablando = tts != null && tts.isSpeaking();
            } catch (Exception e) {
                hablando = false;
            }
            esperas++;
            if (hablando && esperas < 120) {
                h.postDelayed(this, 500);
                return;
            }
            soltarFoco();
        }
    };
}
