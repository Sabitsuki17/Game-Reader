package com.gamereader.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/**
 * Capa que cubre el juego para explorarlo con el dedo, como hace TalkBack:
 * un dedo lee lo que hay debajo, dos toques seguidos pulsan, dos dedos deslizan dentro del juego
 * y un toque con dos dedos repite lo último. Mientras está activa, el juego no recibe toques directos.
 */
final class ExploracionTactil {

    interface Anfitrion {
        /** Índice del elemento que hay en ese punto de la pantalla, o -1. */
        int elementoEn(int x, int y);

        void decirElemento(int indice);

        void tocarElemento(int indice);

        void tocarPunto(int x, int y);

        void deslizar(float x1, float y1, float x2, float y2);

        void repetirUltimo();

        void pulsoSuave();

        /** Avisa de que el usuario está usando la capa (para no hablar encima). */
        void hayActividad();
    }

    private static final long TOQUE_MAX_MS = 350L;
    private static final long DOBLE_TOQUE_MS = 420L;

    private final Context ctx;
    private final Anfitrion anfitrion;
    private final WindowManager wm;
    private final int umbral;
    private final int cercania;

    private View vista;
    private WindowManager.LayoutParams lp;

    private int indiceActual = -1;
    private boolean moviendo;
    private boolean dosDedos;
    private boolean candidatoDoble;
    private float bajoX;
    private float bajoY;
    private long bajoT;
    private float arribaX;
    private float arribaY;
    private long arribaT;
    private float inicioCX;
    private float inicioCY;
    private float finCX;
    private float finCY;
    private long inicioDos;

    ExploracionTactil(Context c, Anfitrion a) {
        ctx = c;
        anfitrion = a;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        umbral = ViewConfiguration.get(c).getScaledTouchSlop();
        cercania = Util.dp(c, 56);
    }

    boolean activa() {
        return vista != null;
    }

    boolean mostrar() {
        if (vista != null) {
            return true;
        }
        try {
            View v = new View(ctx);
            GradientDrawable marco = new GradientDrawable();
            marco.setShape(GradientDrawable.RECTANGLE);
            marco.setColor(Color.TRANSPARENT);
            marco.setStroke(Util.dp(ctx, 4), 0xFFFFC107);
            v.setBackground(marco);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            v.setOnTouchListener(alTocar);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.START;
            p.setTitle(ctx.getString(R.string.exploracion_ventana));
            wm.addView(v, p);
            vista = v;
            lp = p;
            indiceActual = -1;
            dosDedos = false;
            arribaT = 0;
            return true;
        } catch (Exception e) {
            vista = null;
            lp = null;
            return false;
        }
    }

    void quitar() {
        if (vista != null) {
            try {
                wm.removeView(vista);
            } catch (Exception e) {
                // Ya no estaba.
            }
            vista = null;
            lp = null;
        }
    }

    /** Deja pasar los toques al juego (mientras el lector pulsa o desliza por el usuario) o vuelve a capturarlos. */
    void pasarToques(boolean pasar) {
        if (vista == null || lp == null) {
            return;
        }
        if (pasar) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            wm.updateViewLayout(vista, lp);
        } catch (Exception e) {
            // La capa ya no existe.
        }
    }

    private static float distancia(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private void explorarEn(int x, int y) {
        int indice = anfitrion.elementoEn(x, y);
        if (indice != indiceActual) {
            indiceActual = indice;
            if (indice >= 0) {
                anfitrion.pulsoSuave();
                anfitrion.decirElemento(indice);
            }
        }
    }

    private void activar(int x, int y) {
        int indice = anfitrion.elementoEn(x, y);
        if (indice >= 0) {
            anfitrion.tocarElemento(indice);
        } else {
            anfitrion.tocarPunto(x, y);
        }
    }

    private void terminarDosDedos(long ahora) {
        float dx = finCX - inicioCX;
        float dy = finCY - inicioCY;
        if (distancia(inicioCX, inicioCY, finCX, finCY) > umbral * 3) {
            anfitrion.deslizar(inicioCX, inicioCY, finCX, finCY);
        } else if (ahora - inicioDos < TOQUE_MAX_MS) {
            anfitrion.repetirUltimo();
        }
    }

    private final View.OnTouchListener alTocar = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            anfitrion.hayActividad();
            int[] origen = new int[2];
            v.getLocationOnScreen(origen);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dosDedos = false;
                    moviendo = false;
                    bajoX = e.getRawX();
                    bajoY = e.getRawY();
                    bajoT = e.getEventTime();
                    candidatoDoble = arribaT > 0 && bajoT - arribaT <= DOBLE_TOQUE_MS
                            && distancia(bajoX, bajoY, arribaX, arribaY) < cercania;
                    if (!candidatoDoble) {
                        explorarEn((int) bajoX, (int) bajoY);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() == 2 && !dosDedos) {
                        dosDedos = true;
                        candidatoDoble = false;
                        inicioCX = (e.getX(0) + e.getX(1)) / 2f + origen[0];
                        inicioCY = (e.getY(0) + e.getY(1)) / 2f + origen[1];
                        finCX = inicioCX;
                        finCY = inicioCY;
                        inicioDos = e.getEventTime();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dosDedos) {
                        if (e.getPointerCount() >= 2) {
                            finCX = (e.getX(0) + e.getX(1)) / 2f + origen[0];
                            finCY = (e.getY(0) + e.getY(1)) / 2f + origen[1];
                        }
                        return true;
                    }
                    float x = e.getRawX();
                    float y = e.getRawY();
                    if (!moviendo && distancia(x, y, bajoX, bajoY) > umbral) {
                        moviendo = true;
                        candidatoDoble = false;
                    }
                    if (moviendo) {
                        explorarEn((int) x, (int) y);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dosDedos) {
                        terminarDosDedos(e.getEventTime());
                        dosDedos = false;
                        arribaT = 0;
                        return true;
                    }
                    long ahora = e.getEventTime();
                    if (!moviendo && ahora - bajoT < TOQUE_MAX_MS) {
                        if (candidatoDoble) {
                            activar((int) bajoX, (int) bajoY);
                            arribaT = 0;
                        } else {
                            arribaT = ahora;
                            arribaX = bajoX;
                            arribaY = bajoY;
                        }
                    } else {
                        arribaT = 0;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dosDedos = false;
                    return true;
                default:
                    return true;
            }
        }
    };
}
