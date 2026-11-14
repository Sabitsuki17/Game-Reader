package com.gamereader.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Todos los ajustes del lector. Es el único sitio donde se guardan: el servicio, la app y el mosaico comparten estado aquí. */
final class Prefs {

    static final String ACTIVO = "activo";
    static final String SOLO_JUEGOS = "solo_juegos";
    static final String JUEGOS = "juegos";
    static final String BOTON_MODO = "boton_modo";
    static final String BOTON_TAM = "boton_tam";
    static final String BOTON_ALFA = "boton_alfa";
    static final String BOTON_X = "boton_x";
    static final String BOTON_Y = "boton_y";
    static final String VIBRAR = "vibrar";
    static final String ANUNCIAR_JUEGO = "anunciar_juego";
    static final String ACCION_ACC = "accion_acc";
    static final String IDIOMA = "idioma";
    static final String VOZ = "voz";
    static final String VELOCIDAD = "velocidad";
    static final String TONO = "tono";
    static final String VOLUMEN = "volumen";
    static final String BAJAR_JUEGO = "bajar_juego";
    static final String INTERRUMPIR = "interrumpir";
    static final String SALIDA = "salida";
    static final String LECTURA_AUTO = "lectura_auto";
    static final String INTERVALO = "intervalo";
    static final String FUENTE = "fuente";
    static final String ZONA = "zona";
    static final String LEER_NUMEROS = "leer_numeros";
    static final String MIN_CAR = "min_car";
    static final String PANEL_MODO = "panel_modo";
    static final String POSICION = "posicion";
    static final String TECLAS_VOLUMEN = "teclas_volumen";
    static final String OCR_DETALLE = "ocr_detalle";
    static final String IDIOMA_AUTO = "idioma_auto";
    static final String SONIDOS = "sonidos";
    static final String SONIDOS_ESTILO = "sonidos_estilo";
    static final String SONIDOS_VOL = "sonidos_vol";
    static final String SONIDOS_ESTADO = "sonidos_estado";
    static final String SONIDOS_POSICION = "sonidos_posicion";
    static final String SONIDOS_ACCIONES = "sonidos_acciones";
    static final String SONIDOS_NUEVO = "sonidos_nuevo";

    /** Etiquetas de idioma, en el mismo orden que el array «opciones_idioma». */
    static final String[] IDIOMAS = {"es", "es-ES", "es-MX", "es-US", "es-AR", "en-US", "sistema"};

    /** Franjas verticales de la pantalla (inicio, fin) en el mismo orden que «opciones_zona». */
    private static final double[][] ZONAS = {
            {0.0, 1.0}, {0.0, 0.5}, {0.5, 1.0}, {0.67, 1.0}, {0.25, 0.75}
    };

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("game_reader", Context.MODE_PRIVATE);
    }

    SharedPreferences raw() {
        return sp;
    }

    private static int limitar(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------- Escritura genérica ----------
    void putBool(String clave, boolean valor) {
        sp.edit().putBoolean(clave, valor).apply();
    }

    void putInt(String clave, int valor) {
        sp.edit().putInt(clave, valor).apply();
    }

    void putString(String clave, String valor) {
        sp.edit().putString(clave, valor).apply();
    }

    // ---------- General ----------
    boolean activo() {
        return sp.getBoolean(ACTIVO, true);
    }

    void setActivo(boolean valor) {
        putBool(ACTIVO, valor);
    }

    boolean soloJuegos() {
        return sp.getBoolean(SOLO_JUEGOS, true);
    }

    Set<String> juegos() {
        Set<String> s = sp.getStringSet(JUEGOS, null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    void setJuegos(Set<String> s) {
        sp.edit().putStringSet(JUEGOS, new HashSet<String>(s)).apply();
    }

    boolean tieneListaDeJuegos() {
        return sp.contains(JUEGOS);
    }

    /** 0 = siempre, 1 = solo en juegos, 2 = oculto. */
    int botonModo() {
        return limitar(sp.getInt(BOTON_MODO, 1), 0, 2);
    }

    /** 0 = pequeño, 1 = mediano, 2 = grande. */
    int botonTam() {
        return limitar(sp.getInt(BOTON_TAM, 1), 0, 2);
    }

    int botonAlfa() {
        return limitar(sp.getInt(BOTON_ALFA, 90), 30, 100);
    }

    int botonX() {
        return sp.getInt(BOTON_X, -1);
    }

    int botonY() {
        return sp.getInt(BOTON_Y, -1);
    }

    void guardarPosicionBoton(int x, int y) {
        sp.edit().putInt(BOTON_X, x).putInt(BOTON_Y, y).apply();
    }

    boolean vibrar() {
        return sp.getBoolean(VIBRAR, true);
    }

    boolean anunciarJuego() {
        return sp.getBoolean(ANUNCIAR_JUEGO, true);
    }

    /** 0 = encender/apagar, 1 = leer pantalla, 2 = siguiente elemento, 3 = tocar el elemento, 4 = explorar con el dedo, 5 = repetir. */
    int accionAcc() {
        return limitar(sp.getInt(ACCION_ACC, 0), 0, 5);
    }

    // ---------- Voz ----------
    String idiomaTag() {
        String t = sp.getString(IDIOMA, "es");
        return t == null || t.isEmpty() ? "es" : t;
    }

    int indiceIdioma() {
        String t = idiomaTag();
        for (int i = 0; i < IDIOMAS.length; i++) {
            if (IDIOMAS[i].equals(t)) {
                return i;
            }
        }
        return 0;
    }

    Locale localeVoz() {
        String t = idiomaTag();
        if ("sistema".equals(t)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(t);
    }

    String voz() {
        String v = sp.getString(VOZ, "");
        return v == null ? "" : v;
    }

    float velocidad() {
        return limitar(sp.getInt(VELOCIDAD, 100), 50, 300) / 100f;
    }

    float tono() {
        return limitar(sp.getInt(TONO, 100), 50, 200) / 100f;
    }

    float volumen() {
        return limitar(sp.getInt(VOLUMEN, 100), 10, 100) / 100f;
    }

    boolean bajarJuego() {
        return sp.getBoolean(BAJAR_JUEGO, true);
    }

    boolean interrumpir() {
        return sp.getBoolean(INTERRUMPIR, true);
    }

    /** true = enviar el texto al lector del sistema (TalkBack, experimental). */
    boolean salidaSistema() {
        return sp.getInt(SALIDA, 0) == 1;
    }

    // ---------- Qué leer ----------
    boolean lecturaAuto() {
        return sp.getBoolean(LECTURA_AUTO, true);
    }

    int intervaloMs() {
        return limitar(sp.getInt(INTERVALO, 2000), 1000, 8000);
    }

    /** 0 = nodos y OCR, 1 = solo OCR, 2 = solo nodos. */
    int fuente() {
        return limitar(sp.getInt(FUENTE, 0), 0, 2);
    }

    double[] zona() {
        return ZONAS[limitar(sp.getInt(ZONA, 0), 0, ZONAS.length - 1)];
    }

    boolean leerNumeros() {
        return sp.getBoolean(LEER_NUMEROS, false);
    }

    int minCaracteres() {
        return limitar(sp.getInt(MIN_CAR, 2), 1, 10);
    }

    // ---------- Explorar y tocar ----------

    /** 0 = básico (apagar y leer), 1 = completo (con explorar y tocar). */
    int panelModo() {
        return limitar(sp.getInt(PANEL_MODO, 1), 0, 1);
    }

    /** 0 = nunca, 1 = solo al explorar, 2 = siempre. */
    int posicion() {
        return limitar(sp.getInt(POSICION, 1), 0, 2);
    }

    /** Cambia a la voz del idioma del texto cuando es distinto del elegido (por ejemplo, un juego en inglés). */
    boolean sonidos() {
        return sp.getBoolean(SONIDOS, true);
    }

    /** 0 = suave, 1 = retro, 2 = cristal. */
    int sonidosEstilo() {
        return limitar(sp.getInt(SONIDOS_ESTILO, 0), 0, 2);
    }

    int sonidosVol() {
        return limitar(sp.getInt(SONIDOS_VOL, 60), 10, 100);
    }

    boolean sonidosEstado() {
        return sp.getBoolean(SONIDOS_ESTADO, true);
    }

    boolean sonidosPosicion() {
        return sp.getBoolean(SONIDOS_POSICION, true);
    }

    boolean sonidosAcciones() {
        return sp.getBoolean(SONIDOS_ACCIONES, true);
    }

    boolean sonidosNuevo() {
        return sp.getBoolean(SONIDOS_NUEVO, false);
    }

    boolean idiomaAuto() {
        return sp.getBoolean(IDIOMA_AUTO, true);
    }

    boolean teclasVolumen() {
        return sp.getBoolean(TECLAS_VOLUMEN, false);
    }

    /** 0 = bloques de texto, 1 = líneas sueltas. */
    int ocrDetalle() {
        return limitar(sp.getInt(OCR_DETALLE, 0), 0, 1);
    }

    // ---------- Ajustes propios de cada juego (-1 = usar el ajuste general) ----------

    private static String clavePerfil(String paquete, String campo) {
        return "p." + campo + "." + paquete;
    }

    int perfilZona(String paquete) {
        return sp.getInt(clavePerfil(paquete, "zona"), -1);
    }

    int perfilIntervalo(String paquete) {
        return sp.getInt(clavePerfil(paquete, "intervalo"), -1);
    }

    int perfilAuto(String paquete) {
        return sp.getInt(clavePerfil(paquete, "auto"), -1);
    }

    void guardarPerfil(String paquete, int zona, int intervalo, int auto) {
        SharedPreferences.Editor e = sp.edit();
        poner(e, clavePerfil(paquete, "zona"), zona);
        poner(e, clavePerfil(paquete, "intervalo"), intervalo);
        poner(e, clavePerfil(paquete, "auto"), auto);
        e.apply();
    }

    private static void poner(SharedPreferences.Editor e, String clave, int valor) {
        if (valor < 0) {
            e.remove(clave);
        } else {
            e.putInt(clave, valor);
        }
    }

    boolean tienePerfil(String paquete) {
        return perfilZona(paquete) >= 0 || perfilIntervalo(paquete) >= 0 || perfilAuto(paquete) >= 0;
    }

    /** Franja de pantalla que se lee en ese juego. */
    double[] zonaPara(String paquete) {
        int z = paquete == null ? -1 : perfilZona(paquete);
        if (z < 0 || z >= ZONAS.length) {
            return zona();
        }
        return ZONAS[z];
    }

    int intervaloPara(String paquete) {
        int v = paquete == null ? -1 : perfilIntervalo(paquete);
        return v >= 1000 ? limitar(v, 1000, 8000) : intervaloMs();
    }

    boolean autoPara(String paquete) {
        int a = paquete == null ? -1 : perfilAuto(paquete);
        return a < 0 ? lecturaAuto() : a == 1;
    }

    /** Vuelve a los valores de fábrica, pero conserva la lista de juegos elegidos. */
    void restablecer() {
        Set<String> j = juegos();
        sp.edit().clear().putStringSet(JUEGOS, j).apply();
    }

    // ---------- Detección de juegos ----------
    @SuppressWarnings("deprecation")
    static boolean esJuego(ApplicationInfo ai) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME) {
            return true;
        }
        return (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
    }

    static Set<String> detectarJuegos(Context c) {
        Set<String> salida = new HashSet<String>();
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> lista = pm.queryIntentActivities(i, 0);
        for (ResolveInfo r : lista) {
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (esJuego(ai) && !ai.packageName.equals(c.getPackageName())) {
                salida.add(ai.packageName);
            }
        }
        return salida;
    }

    /** La primera vez, deja marcados los juegos que Android reconoce como tales. */
    static void inicializarJuegos(Context c) {
        Prefs p = new Prefs(c);
        if (p.tieneListaDeJuegos()) {
            return;
        }
        p.setJuegos(detectarJuegos(c));
    }
}
