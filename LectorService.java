package com.gamereader.app;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Servicio de accesibilidad del lector de juegos.
 *
 * - Solo actúa en los juegos elegidos (o en cualquier app si el usuario lo decide).
 * - Lee el texto que el juego expone (nodos de accesibilidad) y, si no hay, lo reconoce en una captura (OCR local).
 * - Habla con la voz predeterminada del sistema y solo dice lo NUEVO, para no repetir.
 * - Permite explorar los textos de la pantalla uno a uno, saber dónde están y tocarlos.
 * - Se puede apagar/encender desde la app, el panel flotante, los ajustes rápidos o el botón de accesibilidad.
 */
public class LectorService extends AccessibilityService
        implements BotonFlotante.Acciones, ExploracionTactil.Anfitrion {

    private static final String TAG = "GameReader";
    private static final int UMBRAL_FALLOS = 5;

    /** Revisión periódica: recorta a la zona elegida y solo dice lo nuevo. */
    private static final int MODO_AUTO = 0;
    /** «Leer la pantalla ahora»: pantalla completa y lo dice todo. */
    private static final int MODO_LEER = 1;
    /** Prepara la lista de elementos para explorar, sin hablar. */
    private static final int MODO_EXPLORAR = 2;
    /** Pantalla completa y solo dice lo nuevo (después de tocar un elemento). */
    private static final int MODO_CAMBIOS = 3;

    private static final long VIGENCIA_LISTA = 3000L;
    private static final long PAUSA_TRAS_EXPLORAR = 4000L;
    private static final long PRESION_LARGA = 550L;

    static volatile LectorService instancia;

    /** Un trozo de texto con su posición en pantalla. */
    private static final class Fragmento {
        final String texto;
        final Rect caja;
        /** Idioma detectado por el OCR (por ejemplo «en»), o null si no se conoce. */
        final String idioma;

        Fragmento(String texto, Rect caja) {
            this(texto, caja, null);
        }

        Fragmento(String texto, Rect caja, String idioma) {
            this.texto = texto;
            this.caja = caja;
            this.idioma = idioma;
        }
    }

    /** Orden de lectura: de arriba abajo por filas y, dentro de la fila, de izquierda a derecha. */
    private static final Comparator<Fragmento> POR_POSICION = new Comparator<Fragmento>() {
        @Override
        public int compare(Fragmento a, Fragmento b) {
            int ta = a.caja == null ? 0 : a.caja.top;
            int tb = b.caja == null ? 0 : b.caja.top;
            int fa = ta / 40;
            int fb = tb / 40;
            if (fa != fb) {
                return Integer.compare(fa, fb);
            }
            int la = a.caja == null ? 0 : a.caja.left;
            int lb = b.caja == null ? 0 : b.caja.left;
            return Integer.compare(la, lb);
        }
    };

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Set<String> ignorados = new HashSet<String>();
    private final FiltroTexto filtro = new FiltroTexto();
    private final List<Fragmento> elementos = new ArrayList<Fragmento>();
    private final boolean[] teclaAbajo = new boolean[2];
    private final boolean[] teclaLarga = new boolean[2];
    private final Runnable[] alMantener = new Runnable[2];

    private Prefs prefs;
    private Voz voz;
    private BotonFlotante boton;
    private TextRecognizer reconocedor;
    private AccessibilityButtonController.AccessibilityButtonCallback cbBoton;

    private boolean conectado;
    private String paquete;
    private long ignoradosActualizados;
    private int[] miniAnterior;
    private boolean escaneando;
    private int pendienteModo = -1;
    private Runnable pendienteFin;
    private int escaneoId;
    private int fallos;
    private int estaticos;
    private boolean avisoFalloDado;
    private long ultimoAnuncioJuego;
    private int anchoCaptura;
    private int alturaCaptura;
    private int cursor = -1;
    private long listaDesde;
    private boolean listaValida;
    private long ultimaExploracion;
    private ExploracionTactil exploracion;
    private Sonidos sonidos;
    private boolean acordeHecho;
    private List<String> ultimosTextos = new ArrayList<String>();
    private List<String> ultimosIdiomas = new ArrayList<String>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!debeEscanear()) {
                return;
            }
            if (SystemClock.uptimeMillis() - ultimaExploracion < PAUSA_TRAS_EXPLORAR) {
                programar(1500);
                return;
            }
            escanear(MODO_AUTO, null);
        }
    };

    private final Runnable leerTrasTocar = new Runnable() {
        @Override
        public void run() {
            if (listo()) {
                escanear(MODO_CAMBIOS, null);
            }
        }
    };

    private final Runnable resincronizar = new Runnable() {
        @Override
        public void run() {
            if (!conectado) {
                return;
            }
            if (!pantallaEncendida()) {
                detenerExploracion(false);
            } else if (prefs.activo()) {
                sincronizarPaquete(false);
            }
            h.postDelayed(this, 5000);
        }
    };

    /** Mientras la exploración con el dedo está activa: refresca la lista de textos y se apaga sola si no se usa. */
    private final Runnable refrescoExploracion = new Runnable() {
        @Override
        public void run() {
            if (!conectado || exploracion == null || !exploracion.activa()) {
                return;
            }
            if (SystemClock.uptimeMillis() - ultimaExploracion > 300000L) {
                detenerExploracion(true);
                return;
            }
            if (!escaneando) {
                escanear(MODO_EXPLORAR, null);
            }
            h.postDelayed(this, 2500);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener alCambiarAjustes =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String clave) {
                    if (!conectado) {
                        return;
                    }
                    if (clave == null) {
                        reconstruirBoton();
                        aplicarTeclas();
                        miniAnterior = null;
                        programar(500);
                        return;
                    }
                    switch (clave) {
                        case Prefs.ACTIVO:
                            alCambiarActivo();
                            break;
                        case Prefs.BOTON_MODO:
                        case Prefs.BOTON_TAM:
                        case Prefs.BOTON_ALFA:
                        case Prefs.PANEL_MODO:
                            reconstruirBoton();
                            break;
                        case Prefs.IDIOMA:
                        case Prefs.VOZ:
                            if (voz != null) {
                                voz.aplicar();
                            }
                            break;
                        case Prefs.TECLAS_VOLUMEN:
                            aplicarTeclas();
                            break;
                        case Prefs.SOLO_JUEGOS:
                        case Prefs.JUEGOS:
                            actualizarBoton();
                            programar(500);
                            break;
                        case Prefs.ZONA:
                        case Prefs.OCR_DETALLE:
                            miniAnterior = null;
                            programar(500);
                            break;
                        case Prefs.LECTURA_AUTO:
                        case Prefs.INTERVALO:
                            programar(500);
                            break;
                        default:
                            if (clave.startsWith("p.")) {
                                miniAnterior = null;
                                programar(500);
                            }
                            break;
                    }
                }
            };

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    /** ¿Está el servicio activado en los ajustes de accesibilidad de Android? */
    static boolean estaHabilitado(Context c) {
        if (instancia != null) {
            return true;
        }
        try {
            AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> lista =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo i : lista) {
                ServiceInfo si = i.getResolveInfo().serviceInfo;
                if (si.packageName.equals(c.getPackageName()) && si.name.equals(LectorService.class.getName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo consultar el estado del servicio", e);
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instancia = this;
        conectado = true;
        prefs = new Prefs(this);
        voz = new Voz(this, prefs);
        voz.iniciar(null);
        sonidos = new Sonidos(this, prefs);
        boton = new BotonFlotante(this, prefs, this);
        prefs.raw().registerOnSharedPreferenceChangeListener(alCambiarAjustes);
        registrarBotonAccesibilidad();
        aplicarTeclas();
        refrescarIgnorados(true);
        sincronizarPaquete(false);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Prefs.inicializarJuegos(app);
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo detectar la lista de juegos", e);
                }
            }
        }).start();
        actualizarBoton();
        // Confirmación hablada cuando el usuario acaba de activar el servicio (no en cada arranque del teléfono).
        boolean saludo = prefs.activo() && SystemClock.elapsedRealtime() > 180000L;
        if (saludo) {
            decir(getString(R.string.hablar_listo), true);
        }
        programar(saludo ? 2500 : 800);
        h.postDelayed(resincronizar, 5000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (!conectado || prefs == null || ev == null) {
            return;
        }
        if (ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence p = ev.getPackageName();
        if (p == null) {
            return;
        }
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) {
            // Nuestras pantallas cuentan como «no estoy en un juego»; la ventana flotante no.
            CharSequence clase = ev.getClassName();
            if (clase == null || !clase.toString().startsWith(getPackageName() + ".")) {
                return;
            }
        } else if (esIgnorado(pkg)) {
            return;
        }
        if (!pkg.equals(paquete)) {
            paquete = pkg;
            alCambiarPaquete(true);
        }
    }

    @Override
    public void onInterrupt() {
        if (voz != null) {
            voz.detener();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration nueva) {
        super.onConfigurationChanged(nueva);
        if (boton != null) {
            boton.reajustar();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        limpiar();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        limpiar();
        super.onDestroy();
    }

    private void limpiar() {
        conectado = false;
        h.removeCallbacksAndMessages(null);
        detenerExploracion(false);
        exploracion = null;
        if (sonidos != null) {
            sonidos.cerrar();
            sonidos = null;
        }
        if (prefs != null) {
            prefs.raw().unregisterOnSharedPreferenceChangeListener(alCambiarAjustes);
        }
        if (boton != null) {
            boton.quitar();
            boton = null;
        }
        if (voz != null) {
            voz.apagar();
            voz = null;
        }
        if (reconocedor != null) {
            try {
                reconocedor.close();
            } catch (Exception e) {
                // Nada que hacer.
            }
            reconocedor = null;
        }
        if (cbBoton != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                getAccessibilityButtonController().unregisterAccessibilityButtonCallback(cbBoton);
            } catch (Exception e) {
                // Nada que hacer.
            }
            cbBoton = null;
        }
        teclaAbajo[0] = false;
        teclaAbajo[1] = false;
        teclaLarga[0] = false;
        teclaLarga[1] = false;
        escaneando = false;
        pendienteModo = -1;
        pendienteFin = null;
        elementos.clear();
        listaValida = false;
        cursor = -1;
        if (instancia == this) {
            instancia = null;
        }
    }

    private void registrarBotonAccesibilidad() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            AccessibilityButtonController control = getAccessibilityButtonController();
            cbBoton = new AccessibilityButtonController.AccessibilityButtonCallback() {
                @Override
                public void onClicked(AccessibilityButtonController controlador) {
                    alPulsarBotonAccesibilidad();
                }
            };
            control.registerAccessibilityButtonCallback(cbBoton);
        } catch (Exception e) {
            Log.w(TAG, "Botón de accesibilidad no disponible", e);
        }
    }

    private void alPulsarBotonAccesibilidad() {
        int accion = prefs.accionAcc();
        if (accion == 0 || !prefs.activo()) {
            alternar();
        } else if (accion == 1) {
            leerAhora();
        } else if (accion == 2) {
            siguiente();
        } else if (accion == 3) {
            tocar();
        } else if (accion == 4) {
            explorarConDedo();
        } else {
            repetirUltimo();
        }
    }

    // =====================================================================
    // Teclas de volumen (opcional)
    // =====================================================================

    /** Pide o deja de pedir al sistema las teclas, según el ajuste. */
    private void aplicarTeclas() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                return;
            }
            if (prefs.teclasVolumen()) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            } else {
                info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            }
            setServiceInfo(info);
        } catch (Exception e) {
            Log.w(TAG, "No se pudieron configurar las teclas", e);
        }
    }

    /**
     * Subir volumen: siguiente elemento. Bajar volumen: elemento anterior.
     * Manteniendo: subir = tocar el elemento; bajar = leer toda la pantalla.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        if (!conectado || prefs == null || !prefs.teclasVolumen() || !prefs.activo()) {
            return false;
        }
        int codigo = ev.getKeyCode();
        if (codigo != KeyEvent.KEYCODE_VOLUME_UP && codigo != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }
        if (!esJuego(paquete) || !pantallaEncendida()) {
            return false;
        }
        final int k = codigo == KeyEvent.KEYCODE_VOLUME_UP ? 0 : 1;
        final int otra = 1 - k;
        if (ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (!teclaAbajo[k]) {
                teclaAbajo[k] = true;
                teclaLarga[k] = false;
                if (teclaAbajo[otra] && !teclaLarga[otra]) {
                    // Las dos teclas a la vez: activa o desactiva la exploración con el dedo.
                    acordeHecho = true;
                    if (alMantener[otra] != null) {
                        h.removeCallbacks(alMantener[otra]);
                    }
                    Util.vibrar(this, prefs.vibrar(), 20);
                    explorarConDedo();
                    return true;
                }
                alMantener[k] = new Runnable() {
                    @Override
                    public void run() {
                        if (teclaAbajo[k]) {
                            teclaLarga[k] = true;
                            accionTecla(k, true);
                        }
                    }
                };
                h.postDelayed(alMantener[k], PRESION_LARGA);
            }
            return true;
        }
        if (ev.getAction() == KeyEvent.ACTION_UP) {
            boolean estaba = teclaAbajo[k];
            boolean eraLarga = teclaLarga[k];
            if (alMantener[k] != null) {
                h.removeCallbacks(alMantener[k]);
            }
            teclaAbajo[k] = false;
            teclaLarga[k] = false;
            if (acordeHecho) {
                if (!teclaAbajo[otra]) {
                    acordeHecho = false;
                }
                return true;
            }
            if (estaba && !eraLarga) {
                accionTecla(k, false);
            }
            return true;
        }
        return false;
    }

    private void accionTecla(int tecla, boolean larga) {
        Util.vibrar(this, prefs.vibrar(), 20);
        if (tecla == 0) {
            if (larga) {
                tocar();
            } else {
                siguiente();
            }
        } else {
            if (larga) {
                leerAhora();
            } else {
                anterior();
            }
        }
    }

    // =====================================================================
    // Acciones (panel flotante, botón de accesibilidad, teclas)
    // =====================================================================

    private void sonar(int evento) {
        if (sonidos != null) {
            sonidos.reproducir(evento, Double.NaN, Double.NaN);
        }
    }

    private void sonar(int evento, Rect caja) {
        if (sonidos != null) {
            sonidos.reproducir(evento, fraccionX(caja, anchoPantalla()), fraccionY(caja, altoPantalla()));
        }
    }

    private boolean listo() {
        return conectado && prefs != null && prefs.activo();
    }

    @Override
    public void alternar() {
        prefs.setActivo(!prefs.activo());
    }

    @Override
    public void leerAhora() {
        if (!listo()) {
            return;
        }
        ultimaExploracion = SystemClock.uptimeMillis();
        escanear(MODO_LEER, null);
    }

    @Override
    public void siguiente() {
        explorar(1);
    }

    @Override
    public void anterior() {
        explorar(-1);
    }

    /** Toca el centro del elemento elegido. Solo se hace cuando el usuario lo pide. */
    @Override
    public void tocar() {
        if (!listo()) {
            return;
        }
        ultimaExploracion = SystemClock.uptimeMillis();
        if (cursor < 0 || cursor >= elementos.size() || elementos.get(cursor).caja == null) {
            sonar(Sonidos.ERROR);
            decir(getString(R.string.hablar_elige_elemento), true);
            return;
        }
        Rect c = elementos.get(cursor).caja;
        tocarYLeer(c.centerX(), c.centerY());
    }

    /** Toca un punto y, un momento después, dice lo que ha cambiado. */
    private void tocarYLeer(float x, float y) {
        ultimaExploracion = SystemClock.uptimeMillis();
        if (!tocarEn(x, y)) {
        sonar(Sonidos.ERROR);
            decir(getString(R.string.hablar_no_toca), true);
            return;
        }
        Util.vibrar(this, prefs.vibrar(), 30);
        sonar(Sonidos.TOQUE);
        cursor = -1;
        listaValida = false;
        h.removeCallbacks(leerTrasTocar);
        h.postDelayed(leerTrasTocar, 900);
    }

    private boolean tocarEn(float x, float y) {
        try {
            Path camino = new Path();
            camino.moveTo(x, y);
            return enviarGesto(new GestureDescription.StrokeDescription(camino, 0, 70));
        } catch (Exception e) {
            Log.w(TAG, "No se pudo tocar la pantalla", e);
            return false;
        }
    }

    private boolean deslizarEn(float x1, float y1, float x2, float y2) {
        try {
            Path camino = new Path();
            camino.moveTo(x1, y1);
            camino.lineTo(x2, y2);
            return enviarGesto(new GestureDescription.StrokeDescription(camino, 0, 220));
        } catch (Exception e) {
            Log.w(TAG, "No se pudo deslizar", e);
            return false;
        }
    }

    /** Envía el gesto al juego; si la capa de exploración está puesta, la deja pasar los toques mientras tanto. */
    private boolean enviarGesto(GestureDescription.StrokeDescription trazo) {
        final boolean capa = exploracion != null && exploracion.activa();
        if (capa) {
            exploracion.pasarToques(true);
        }
        GestureDescription gesto = new GestureDescription.Builder().addStroke(trazo).build();
        boolean enviado = dispatchGesture(gesto, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                soltarCapa();
            }

            @Override
            public void onCancelled(GestureDescription g) {
                soltarCapa();
            }
        }, h);
        if (capa) {
            if (enviado) {
                // Seguro: si el sistema no avisa, la capa vuelve a capturar toques igualmente.
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        soltarCapa();
                    }
                }, 900);
            } else {
                soltarCapa();
            }
        }
        return enviado;
    }

    private void soltarCapa() {
        if (exploracion != null && exploracion.activa()) {
            exploracion.pasarToques(false);
        }
    }

    private void explorar(final int paso) {
        if (!listo()) {
            return;
        }
        ultimaExploracion = SystemClock.uptimeMillis();
        if (listaValida && ultimaExploracion - listaDesde < VIGENCIA_LISTA) {
            moverCursor(paso);
            return;
        }
        escanear(MODO_EXPLORAR, new Runnable() {
            @Override
            public void run() {
                moverCursor(paso);
            }
        });
    }

    private void moverCursor(int paso) {
        if (elementos.isEmpty()) {
            sonar(Sonidos.ERROR);
            decir(getString(R.string.hablar_sin_texto), true);
            return;
        }
        int nuevo;
        if (cursor < 0) {
            nuevo = paso > 0 ? 0 : elementos.size() - 1;
        } else {
            nuevo = cursor + paso;
        }
        if (nuevo < 0 || nuevo >= elementos.size()) {
            sonar(Sonidos.ERROR);
            decir(getString(nuevo < 0 ? R.string.hablar_primer_elemento : R.string.hablar_ultimo_elemento), true);
            return;
        }
        cursor = nuevo;
        sonar(Sonidos.ELEMENTO, elementos.get(cursor).caja);
        Fragmento e = elementos.get(cursor);
        List<String> textos = new ArrayList<String>();
        List<String> idiomas = new ArrayList<String>();
        textos.add(FiltroTexto.limpiar(e.texto));
        idiomas.add(etiquetaIdioma(e.idioma));
        StringBuilder resto = new StringBuilder();
        if (prefs.posicion() >= 1) {
            String p = posicionHablada(e.caja);
            if (!p.isEmpty()) {
                resto.append(p).append(". ");
            }
        }
        resto.append(getString(R.string.hablar_n_de_total, cursor + 1, elementos.size()));
        textos.add(resto.toString());
        idiomas.add("");
        hablar(textos, idiomas, true);
    }

    /** Rehace la lista de elementos explorables a partir de una lectura de pantalla completa. */
    private void construirElementos(List<Fragmento> fragmentos) {
        String anterior = (cursor >= 0 && cursor < elementos.size())
                ? FiltroTexto.clave(elementos.get(cursor).texto, true) : null;
        List<Fragmento> orden = new ArrayList<Fragmento>(fragmentos);
        Collections.sort(orden, POR_POSICION);
        int ancho = anchoPantalla();
        int alto = altoPantalla();
        List<FiltroTexto.Pieza> piezas = new ArrayList<FiltroTexto.Pieza>();
        for (Fragmento f : orden) {
            piezas.add(new FiltroTexto.Pieza(f.texto, fraccionX(f.caja, ancho), fraccionY(f.caja, alto)));
        }
        elementos.clear();
        for (int i : FiltroTexto.indicesUtiles(piezas, prefs.minCaracteres())) {
            elementos.add(orden.get(i));
        }
        cursor = -1;
        if (anterior != null) {
            for (int i = 0; i < elementos.size(); i++) {
                if (FiltroTexto.clave(elementos.get(i).texto, true).equals(anterior)) {
                    cursor = i;
                    break;
                }
            }
        }
        listaDesde = SystemClock.uptimeMillis();
        listaValida = true;
    }

    private void olvidarElementos() {
        elementos.clear();
        cursor = -1;
        listaValida = false;
    }

    private int anchoPantalla() {
        return anchoCaptura > 0 ? anchoCaptura : getResources().getDisplayMetrics().widthPixels;
    }

    private int altoPantalla() {
        return alturaCaptura > 0 ? alturaCaptura : getResources().getDisplayMetrics().heightPixels;
    }

    private static double fraccionX(Rect c, int ancho) {
        return (c == null || ancho <= 0) ? Double.NaN : c.centerX() / (double) ancho;
    }

    private static double fraccionY(Rect c, int alto) {
        return (c == null || alto <= 0) ? Double.NaN : c.centerY() / (double) alto;
    }

    /** «arriba a la izquierda», «en el centro», «abajo»… según la rejilla de 3 × 3. */
    private String posicionHablada(Rect caja) {
        if (caja == null) {
            return "";
        }
        int celda = FiltroTexto.celda(fraccionX(caja, anchoPantalla()), fraccionY(caja, altoPantalla()));
        return getResources().getStringArray(R.array.posiciones)[celda];
    }

    private void alCambiarActivo() {
        boolean encendido = prefs.activo();
        filtro.reiniciar();
        miniAnterior = null;
        estaticos = 0;
        olvidarElementos();
        if (!encendido) {
            detenerExploracion(false);
        }
        if (!encendido && voz != null) {
            voz.detener();
        }
        actualizarBoton();
        Util.vibrar(this, prefs.vibrar(), encendido ? 60 : 140);
        sonar(encendido ? Sonidos.ENCENDER : Sonidos.APAGAR);
        decir(getString(encendido ? R.string.hablar_activado : R.string.hablar_apagado), true);
        programar(encendido ? 2000 : 0);
    }

    // =====================================================================
    // Qué app está en primer plano
    // =====================================================================

    /**
     * Comprueba cuál es la app en primer plano consultando la ventana activa. Sirve al arrancar y como red de seguridad
     * (por ejemplo, cuando un panel flotante del móvil se cierra y no llega ningún evento nuevo).
     */
    private void sincronizarPaquete(boolean anunciar) {
        try {
            AccessibilityNodeInfo raiz = getRootInActiveWindow();
            if (raiz == null || raiz.getPackageName() == null) {
                return;
            }
            String p = raiz.getPackageName().toString();
            if (p.equals(getPackageName()) || esIgnorado(p)) {
                return;
            }
            if (!p.equals(paquete)) {
                paquete = p;
                alCambiarPaquete(anunciar);
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo detectar la app en primer plano", e);
        }
    }

    private boolean esIgnorado(String pkg) {
        refrescarIgnorados(false);
        return ignorados.contains(pkg);
    }

    /** Paquetes que nunca cuentan como «la app en primer plano»: barra del sistema, teclados y otros lectores. */
    private void refrescarIgnorados(boolean forzar) {
        long ahora = SystemClock.uptimeMillis();
        if (!forzar && ahora - ignoradosActualizados < 5000) {
            return;
        }
        ignoradosActualizados = ahora;
        Set<String> nuevos = new HashSet<String>();
        nuevos.add("com.android.systemui");
        nuevos.add("android");
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                for (AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    String pk = i.getResolveInfo().serviceInfo.packageName;
                    if (!pk.equals(getPackageName())) {
                        nuevos.add(pk);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Lista de lectores", e);
        }
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
                    nuevos.add(i.getPackageName());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Lista de teclados", e);
        }
        ignorados.clear();
        ignorados.addAll(nuevos);
    }

    private void alCambiarPaquete(boolean anunciar) {
        filtro.reiniciar();
        miniAnterior = null;
        anchoCaptura = 0;
        alturaCaptura = 0;
        fallos = 0;
        estaticos = 0;
        avisoFalloDado = false;
        olvidarElementos();
        if (!esJuego(paquete)) {
            detenerExploracion(false);
        }
        actualizarBoton();
        boolean hablado = false;
        if (anunciar && prefs.activo() && prefs.anunciarJuego() && esJuegoElegido(paquete)) {
            hablado = anunciarJuego(paquete);
            if (hablado) {
                sonar(Sonidos.JUEGO);
            }
        }
        // Si acabamos de decir el nombre del juego, esperamos a que termine antes de empezar a leer.
        programar(hablado ? 2500 : 900);
    }

    private boolean anunciarJuego(String pkg) {
        long ahora = SystemClock.uptimeMillis();
        if (ahora - ultimoAnuncioJuego < 4000) {
            return false;
        }
        ultimoAnuncioJuego = ahora;
        decir(getString(R.string.hablar_juego, etiqueta(pkg)), true);
        return true;
    }

    private String etiqueta(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** ¿Está en la lista de juegos elegidos? */
    private boolean esJuegoElegido(String pkg) {
        return pkg != null && prefs.juegos().contains(pkg);
    }

    /** ¿Debe el lector actuar en esta app? */
    private boolean esJuego(String pkg) {
        if (pkg == null || pkg.equals(getPackageName())) {
            return false;
        }
        return !prefs.soloJuegos() || prefs.juegos().contains(pkg);
    }

    private boolean pantallaEncendida() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    // =====================================================================
    // Panel flotante
    // =====================================================================

    private void actualizarBoton() {
        if (boton == null || prefs == null) {
            return;
        }
        int modo = prefs.botonModo();
        boolean mostrar = modo == 0 || (modo == 1 && esJuegoElegido(paquete));
        if (mostrar) {
            boton.mostrar(prefs.activo());
            boton.marcarExploracion(exploracion != null && exploracion.activa());
        } else {
            boton.quitar();
        }
    }

    private void reconstruirBoton() {
        if (boton == null) {
            return;
        }
        boton.quitar();
        boton = new BotonFlotante(this, prefs, this);
        actualizarBoton();
    }

    // =====================================================================
    // Bucle de lectura
    // =====================================================================

    private boolean debeEscanear() {
        if (!conectado || prefs == null || !prefs.activo()) {
            return false;
        }
        if (!esJuego(paquete) || !prefs.autoPara(paquete)) {
            return false;
        }
        return pantallaEncendida();
    }

    private void programar(long retardo) {
        h.removeCallbacks(tick);
        if (debeEscanear()) {
            h.postDelayed(tick, retardo);
        }
    }

    /** Si la pantalla no cambia, se revisa cada vez con menos frecuencia para ahorrar batería. */
    private long esperaAuto() {
        long base = prefs.intervaloPara(paquete);
        if (fallos >= UMBRAL_FALLOS) {
            return Math.max(6000L, 3L * base);
        }
        if (estaticos >= 8) {
            return base * 3;
        }
        if (estaticos >= 4) {
            return base * 2;
        }
        return base;
    }

    private void escanear(final int modo, final Runnable fin) {
        if (!conectado) {
            return;
        }
        if (escaneando) {
            if (modo != MODO_AUTO) {
                pendienteModo = modo;
                pendienteFin = fin;
            }
            return;
        }
        escaneando = true;
        final int id = ++escaneoId;
        // Vigilante: si algo se queda colgado, el lector no se bloquea para siempre.
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (escaneando && escaneoId == id) {
                    Log.w(TAG, "Escaneo sin respuesta; se reinicia");
                    terminar(id, null, modo, null);
                }
            }
        }, 12000);

        final List<Fragmento> fragmentos = new ArrayList<Fragmento>();
        int fuente = prefs.fuente();
        if (fuente != 1) {
            try {
                fragmentos.addAll(leerNodos());
            } catch (Exception e) {
                Log.w(TAG, "Lectura de nodos", e);
            }
        }
        boolean usarOcr = fuente != 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        if (!usarOcr) {
            terminar(id, fragmentos, modo, fin);
            return;
        }
        try {
            Captura.capturar(this, new Captura.Resultado() {
                @Override
                public void ok(Bitmap imagen) {
                    reconocer(id, imagen, fragmentos, modo, fin);
                }

                @Override
                public void error(int codigo) {
                    registrarFallo(codigo);
                    terminar(id, fragmentos, modo, fin);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "No se pudo capturar la pantalla", t);
            registrarFallo(-2);
            terminar(id, fragmentos, modo, fin);
        }
    }

    private void terminar(int id, List<Fragmento> fragmentos, int modo, Runnable fin) {
        if (id != escaneoId || !escaneando) {
            return;
        }
        escaneando = false;
        if (fragmentos != null && conectado) {
            try {
                if (modo != MODO_EXPLORAR) {
                    procesar(fragmentos, modo);
                }
                if (modo != MODO_AUTO) {
                    construirElementos(fragmentos);
                }
                if (fin != null) {
                    fin.run();
                }
            } catch (Exception e) {
                Log.w(TAG, "Procesado del texto", e);
            }
        }
        if (!conectado) {
            return;
        }
        if (pendienteModo >= 0) {
            int m = pendienteModo;
            Runnable f = pendienteFin;
            pendienteModo = -1;
            pendienteFin = null;
            escanear(m, f);
            return;
        }
        programar(esperaAuto());
    }

    private void registrarFallo(int codigo) {
        fallos++;
        Log.w(TAG, "Captura fallida, código " + codigo);
        if (fallos >= UMBRAL_FALLOS && !avisoFalloDado) {
            avisoFalloDado = true;
            if (prefs.activo()) {
                sonar(Sonidos.ERROR);
                decir(getString(R.string.hablar_sin_captura), false);
            }
        }
    }

    private void reconocer(final int id, final Bitmap original, final List<Fragmento> acumulado,
                           final int modo, final Runnable fin) {
        if (id != escaneoId || !escaneando) {
            original.recycle();
            return;
        }
        fallos = 0;
        avisoFalloDado = false;
        anchoCaptura = original.getWidth();
        alturaCaptura = original.getHeight();

        // En la revisión automática solo se analiza la zona elegida: menos trabajo y menos ruido.
        Bitmap objetivo = original;
        int desplazamiento = 0;
        if (modo == MODO_AUTO) {
            double[] zona = prefs.zonaPara(paquete);
            if (zona[0] > 0.001 || zona[1] < 0.999) {
                int y0 = (int) (zona[0] * alturaCaptura);
                int y1 = (int) (zona[1] * alturaCaptura);
                if (y1 - y0 >= 48) {
                    try {
                        objetivo = Bitmap.createBitmap(original, 0, y0, anchoCaptura, y1 - y0);
                        desplazamiento = y0;
                    } catch (Exception e) {
                        objetivo = original;
                        desplazamiento = 0;
                    }
                }
            }
        }
        if (objetivo != original) {
            original.recycle();
        }
        final Bitmap imagen = objetivo;
        final int dy = desplazamiento;

        if (modo == MODO_AUTO) {
            if (!haCambiado(imagen)) {
                imagen.recycle();
                estaticos++;
                terminar(id, acumulado, modo, fin);
                return;
            }
            estaticos = 0;
        }

        try {
            if (reconocedor == null) {
                reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            }
            reconocedor.process(InputImage.fromBitmap(imagen, 0))
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text texto) {
                            imagen.recycle();
                            List<Fragmento> ocr = new ArrayList<Fragmento>();
                            try {
                                boolean lineas = prefs.ocrDetalle() == 1;
                                for (Text.TextBlock bloque : texto.getTextBlocks()) {
                                    if (lineas) {
                                        for (Text.Line linea : bloque.getLines()) {
                                            agregarOcr(ocr, linea.getText(), linea.getBoundingBox(), dy, linea.getRecognizedLanguage());
                                        }
                                    } else {
                                        agregarOcr(ocr, bloque.getText(), bloque.getBoundingBox(), dy, bloque.getRecognizedLanguage());
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Resultado del OCR", e);
                            }
                            Collections.sort(ocr, POR_POSICION);
                            acumulado.addAll(ocr);
                            terminar(id, acumulado, modo, fin);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.w(TAG, "El OCR falló", e);
                            imagen.recycle();
                            terminar(id, acumulado, modo, fin);
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "No se pudo iniciar el OCR", t);
            imagen.recycle();
            terminar(id, acumulado, modo, fin);
        }
    }

    private static void agregarOcr(List<Fragmento> destino, String texto, Rect caja, int dy, String idioma) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        Rect r = null;
        if (caja != null) {
            r = new Rect(caja);
            r.offset(0, dy);
        }
        destino.add(new Fragmento(texto, r, idioma));
    }

    /** Compara una miniatura con la de la última captura analizada: si la pantalla no cambió, se ahorra el OCR (y batería). */
    private boolean haCambiado(Bitmap imagen) {
        int[] px = new int[48 * 48];
        Bitmap mini = Bitmap.createScaledBitmap(imagen, 48, 48, true);
        mini.getPixels(px, 0, 48, 0, 0, 48, 48);
        if (mini != imagen) {
            mini.recycle();
        }
        if (miniAnterior != null) {
            long dif = 0;
            for (int i = 0; i < px.length; i++) {
                int a = px[i];
                int b = miniAnterior[i];
                dif += Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                        + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                        + Math.abs((a & 255) - (b & 255));
            }
            double media = dif / (3.0 * px.length);
            if (media < 1.5) {
                return false;
            }
        }
        miniAnterior = px;
        return true;
    }

    // =====================================================================
    // Lectura de nodos de accesibilidad
    // =====================================================================

    private List<Fragmento> leerNodos() {
        List<Fragmento> salida = new ArrayList<Fragmento>();
        AccessibilityNodeInfo raiz = getRootInActiveWindow();
        if (raiz == null) {
            return salida;
        }
        if (raiz.getPackageName() != null && raiz.getPackageName().toString().equals(getPackageName())) {
            return salida;
        }
        recorrer(raiz, salida, 0, new int[]{0}, SystemClock.uptimeMillis() + 200);
        return salida;
    }

    private void recorrer(AccessibilityNodeInfo nodo, List<Fragmento> salida, int profundidad, int[] cuenta, long limite) {
        if (nodo == null || profundidad > 40 || cuenta[0] > 800 || SystemClock.uptimeMillis() > limite) {
            return;
        }
        cuenta[0]++;
        if (!nodo.isVisibleToUser()) {
            return;
        }
        if (!nodo.isPassword()) {
            CharSequence t = nodo.getText();
            CharSequence d = nodo.getContentDescription();
            CharSequence s = (t != null && t.length() > 0) ? t : d;
            if (s != null && s.length() > 0) {
                Rect r = new Rect();
                nodo.getBoundsInScreen(r);
                salida.add(new Fragmento(s.toString(), r));
            }
        }
        int hijos = nodo.getChildCount();
        for (int i = 0; i < hijos; i++) {
            recorrer(nodo.getChild(i), salida, profundidad + 1, cuenta, limite);
        }
    }

    // =====================================================================
    // Filtrado, no repetir y hablar
    // =====================================================================

    private void procesar(List<Fragmento> fragmentos, int modo) {
        boolean manual = modo == MODO_LEER;
        double[] zona = modo == MODO_AUTO ? prefs.zonaPara(paquete) : new double[]{0.0, 1.0};
        int ancho = anchoPantalla();
        int alto = altoPantalla();
        long ahora = SystemClock.uptimeMillis();
        long ventana = Math.max(3L * prefs.intervaloPara(paquete), 4000L);

        List<FiltroTexto.Pieza> piezas = new ArrayList<FiltroTexto.Pieza>();
        for (Fragmento f : fragmentos) {
            piezas.add(new FiltroTexto.Pieza(f.texto, fraccionX(f.caja, ancho), fraccionY(f.caja, alto)));
        }
        List<Integer> indices = filtro.nuevosIndices(piezas, manual, ahora, ventana,
                prefs.leerNumeros(), prefs.minCaracteres(), zona[0], zona[1]);

        if (indices.isEmpty()) {
            if (manual) {
                sonar(Sonidos.ERROR);
                decir(getString(R.string.hablar_sin_texto), true);
            }
            return;
        }
        List<String> dichos = new ArrayList<String>();
        List<String> idiomas = new ArrayList<String>();
        for (int i : indices) {
            Fragmento f = fragmentos.get(i);
            dichos.add(FiltroTexto.limpiar(f.texto));
            idiomas.add(etiquetaIdioma(f.idioma));
            if (prefs.posicion() == 2) {
                String donde = posicionHablada(f.caja);
                if (!donde.isEmpty()) {
                    dichos.add(donde);
                    idiomas.add("");
                }
            }
        }
        if (!manual) {
            sonar(Sonidos.NUEVO);
        }
        hablar(dichos, idiomas, manual || prefs.interrumpir());
    }

    private void decir(String texto, boolean interrumpir) {
        decirEn(texto, interrumpir, null);
    }

    private void decirEn(String texto, boolean interrumpir, Locale idioma) {
        if (texto == null || texto.isEmpty() || prefs == null) {
            return;
        }
        if (prefs.salidaSistema() && SalidaSistema.enviar(this, texto)) {
            return;
        }
        if (voz != null) {
            voz.decir(texto, interrumpir, idioma);
        }
    }

    /** Idioma (código de dos letras) en el que hay que hablar un texto; vacío = el de los ajustes. */
    private String etiquetaIdioma(String detectado) {
        if (detectado == null || !prefs.idiomaAuto()) {
            return "";
        }
        String lang = detectado.toLowerCase(Locale.ROOT);
        int guion = lang.indexOf('-');
        if (guion > 0) {
            lang = lang.substring(0, guion);
        }
        if (lang.length() < 2 || lang.equals("und") || lang.equals(prefs.localeVoz().getLanguage())) {
            return "";
        }
        return lang;
    }

    /** Habla varios textos, cada uno en su idioma, y los recuerda para poder repetirlos. */
    private void hablar(List<String> textos, List<String> idiomas, boolean interrumpir) {
        if (textos.isEmpty() || prefs == null) {
            return;
        }
        ultimosTextos = new ArrayList<String>(textos);
        ultimosIdiomas = new ArrayList<String>(idiomas);
        boolean primero = true;
        for (String[] grupo : FiltroTexto.agruparPorIdioma(textos, idiomas)) {
            Locale idioma = grupo[0].isEmpty() ? null : Locale.forLanguageTag(grupo[0]);
            decirEn(grupo[1], primero && interrumpir, idioma);
            primero = false;
        }
    }

    // =====================================================================
    // Exploración con el dedo
    // =====================================================================

    @Override
    public void explorarConDedo() {
        if (!listo()) {
            return;
        }
        if (exploracion != null && exploracion.activa()) {
            detenerExploracion(true);
            return;
        }
        if (exploracion == null) {
            exploracion = new ExploracionTactil(this, this);
        }
        if (!exploracion.mostrar()) {
            decir(getString(R.string.hablar_no_toca), true);
            return;
        }
        ultimaExploracion = SystemClock.uptimeMillis();
        // El panel se vuelve a poner encima de la capa para poder apagarla.
        reconstruirBoton();
        sonar(Sonidos.EXPLORAR_ON);
        decir(getString(R.string.hablar_exploracion_on), true);
        escanear(MODO_EXPLORAR, null);
        h.removeCallbacks(refrescoExploracion);
        h.postDelayed(refrescoExploracion, 2500);
    }

    private void detenerExploracion(boolean avisar) {
        h.removeCallbacks(refrescoExploracion);
        boolean estaba = exploracion != null && exploracion.activa();
        if (estaba && conectado) {
            sonar(Sonidos.EXPLORAR_OFF);
        }
        if (exploracion != null) {
            exploracion.quitar();
        }
        if (boton != null) {
            boton.marcarExploracion(false);
        }
        if (estaba && avisar && conectado) {
            decir(getString(R.string.hablar_exploracion_off), true);
        }
    }

    @Override
    public int elementoEn(int x, int y) {
        int margen = Util.dp(this, 8);
        int mejor = -1;
        long mejorArea = Long.MAX_VALUE;
        for (int i = 0; i < elementos.size(); i++) {
            Rect c = elementos.get(i).caja;
            if (c == null) {
                continue;
            }
            if (x >= c.left - margen && x <= c.right + margen && y >= c.top - margen && y <= c.bottom + margen) {
                long area = (long) Math.max(1, c.right - c.left) * Math.max(1, c.bottom - c.top);
                if (area < mejorArea) {
                    mejorArea = area;
                    mejor = i;
                }
            }
        }
        return mejor;
    }

    @Override
    public void decirElemento(int indice) {
        if (!listo() || indice < 0 || indice >= elementos.size()) {
            return;
        }
        Fragmento e = elementos.get(indice);
        sonar(Sonidos.ELEMENTO, e.caja);
        List<String> textos = new ArrayList<String>();
        List<String> idiomas = new ArrayList<String>();
        textos.add(FiltroTexto.limpiar(e.texto));
        idiomas.add(etiquetaIdioma(e.idioma));
        if (prefs.posicion() == 2) {
            String donde = posicionHablada(e.caja);
            if (!donde.isEmpty()) {
                textos.add(donde);
                idiomas.add("");
            }
        }
        hablar(textos, idiomas, true);
    }

    @Override
    public void tocarElemento(int indice) {
        if (!listo() || indice < 0 || indice >= elementos.size() || elementos.get(indice).caja == null) {
            return;
        }
        Rect c = elementos.get(indice).caja;
        tocarYLeer(c.centerX(), c.centerY());
    }

    @Override
    public void tocarPunto(int x, int y) {
        if (listo()) {
            tocarYLeer(x, y);
        }
    }

    /** Desliza dentro del juego en la dirección que el usuario dibujó con dos dedos. */
    @Override
    public void deslizar(float x1, float y1, float x2, float y2) {
        if (!listo()) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float largo = (float) Math.hypot(dx, dy);
        if (largo < 1f) {
            return;
        }
        float factor = Math.max(largo, Util.dp(this, 160)) / largo;
        float fx = Math.max(0f, Math.min((float) anchoPantalla(), x1 + dx * factor));
        float fy = Math.max(0f, Math.min((float) altoPantalla(), y1 + dy * factor));
        ultimaExploracion = SystemClock.uptimeMillis();
        if (!deslizarEn(x1, y1, fx, fy)) {
            decir(getString(R.string.hablar_no_toca), true);
            return;
        }
        Util.vibrar(this, prefs.vibrar(), 20);
        cursor = -1;
        listaValida = false;
        sonar(Sonidos.TOQUE);
        h.removeCallbacks(leerTrasTocar);
        h.postDelayed(leerTrasTocar, 1100);
    }

    @Override
    public void repetirUltimo() {
        if (!listo()) {
            return;
        }
        ultimaExploracion = SystemClock.uptimeMillis();
        if (ultimosTextos.isEmpty()) {
            sonar(Sonidos.ERROR);
            decir(getString(R.string.hablar_nada_que_repetir), true);
            return;
        }
        hablar(ultimosTextos, ultimosIdiomas, true);
    }

    @Override
    public void pulsoSuave() {
        if (prefs != null) {
            Util.vibrar(this, prefs.vibrar(), 12);
        }
    }

    @Override
    public void hayActividad() {
        ultimaExploracion = SystemClock.uptimeMillis();
    }
}
