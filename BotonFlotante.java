package com.gamereader.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel de botones flotantes sobre el juego. Arriba, encender/apagar; debajo, leer la pantalla y, en el modo completo,
 * elemento anterior, siguiente y tocar. Se arrastra con el dedo. Usa la ventana de superposición de accesibilidad,
 * así que no pide permisos extra.
 */
final class BotonFlotante {

    interface Acciones {
        void alternar();

        void leerAhora();

        void anterior();

        void siguiente();

        void tocar();

        void explorarConDedo();
    }

    private final Context ctx;
    private final Prefs prefs;
    private final Acciones acciones;
    private final WindowManager wm;
    private final int tam;
    private final int separacion;
    private final int umbral;
    private final List<ImageButton> secundarios = new ArrayList<ImageButton>();

    private LinearLayout raiz;
    private ImageButton btnPower;
    private GradientDrawable fondoPower;
    private ImageButton btnExplorar;
    private GradientDrawable fondoExplorar;
    private boolean exploracionActiva;
    private WindowManager.LayoutParams lp;

    BotonFlotante(Context c, Prefs p, Acciones a) {
        ctx = c;
        prefs = p;
        acciones = a;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        int[] tamanos = {40, 52, 68};
        tam = Util.dp(c, tamanos[prefs.botonTam()]);
        separacion = Util.dp(c, 8);
        umbral = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    boolean visible() {
        return raiz != null;
    }

    void mostrar(boolean activo) {
        if (raiz != null) {
            actualizar(activo);
            return;
        }
        try {
            raiz = new LinearLayout(ctx);
            raiz.setOrientation(LinearLayout.VERTICAL);
            secundarios.clear();
            btnExplorar = null;
            fondoExplorar = null;

            btnPower = crear(R.drawable.ic_power, ctx.getColor(R.color.verde));
            fondoPower = (GradientDrawable) btnPower.getBackground();
            btnPower.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    acciones.alternar();
                }
            });
            agregar(btnPower, false);

            agregarSecundario(R.drawable.ic_leer, R.color.azul, R.string.boton_leer, new Runnable() {
                @Override
                public void run() {
                    acciones.leerAhora();
                }
            });
            if (prefs.panelModo() == 1) {
                agregarSecundario(R.drawable.ic_anterior, R.color.gris, R.string.boton_anterior, new Runnable() {
                    @Override
                    public void run() {
                        acciones.anterior();
                    }
                });
                agregarSecundario(R.drawable.ic_siguiente, R.color.gris, R.string.boton_siguiente, new Runnable() {
                    @Override
                    public void run() {
                        acciones.siguiente();
                    }
                });
                agregarSecundario(R.drawable.ic_tocar, R.color.morado, R.string.boton_tocar, new Runnable() {
                    @Override
                    public void run() {
                        acciones.tocar();
                    }
                });
                btnExplorar = agregarSecundario(R.drawable.ic_explorar, R.color.gris, R.string.boton_explorar, new Runnable() {
                    @Override
                    public void run() {
                        acciones.explorarConDedo();
                    }
                });
                fondoExplorar = (GradientDrawable) btnExplorar.getBackground();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                raiz.setAccessibilityPaneTitle(ctx.getString(R.string.boton_ventana));
            }

            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle(ctx.getString(R.string.boton_ventana));
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int x = prefs.botonX();
            int y = prefs.botonY();
            lp.x = x >= 0 ? x : dm.widthPixels - tam - Util.dp(ctx, 12);
            lp.y = y >= 0 ? y : dm.heightPixels / 4;
            actualizarContenido(activo);
            acotar();
            wm.addView(raiz, lp);
        } catch (Exception e) {
            raiz = null;
        }
    }

    void quitar() {
        if (raiz != null) {
            try {
                wm.removeView(raiz);
            } catch (Exception e) {
                // Ya no estaba.
            }
            raiz = null;
        }
    }

    void actualizar(boolean activo) {
        if (raiz == null) {
            return;
        }
        actualizarContenido(activo);
        acotar();
        try {
            wm.updateViewLayout(raiz, lp);
        } catch (Exception e) {
            // Si la ventana ya no existe, se recreará al volver a mostrarla.
        }
    }

    /** Recoloca el panel dentro de la pantalla (por ejemplo, tras girarla). */
    void reajustar() {
        if (raiz == null) {
            return;
        }
        acotar();
        try {
            wm.updateViewLayout(raiz, lp);
        } catch (Exception e) {
            // Ignorado.
        }
    }

    private void agregar(ImageButton b, boolean conSeparacion) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(tam, tam);
        if (conSeparacion) {
            p.topMargin = separacion;
        }
        raiz.addView(b, p);
    }

    private ImageButton agregarSecundario(int icono, int color, int descripcion, final Runnable accion) {
        ImageButton b = crear(icono, ctx.getColor(color));
        b.setContentDescription(ctx.getString(descripcion));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accion.run();
            }
        });
        secundarios.add(b);
        agregar(b, true);
        return b;
    }

    /** Refleja en el botón de explorar si la exploración con el dedo está activa. */
    void marcarExploracion(boolean activa) {
        exploracionActiva = activa;
        if (btnExplorar != null && fondoExplorar != null) {
            fondoExplorar.setColor(ctx.getColor(activa ? R.color.naranja : R.color.gris));
            btnExplorar.setContentDescription(ctx.getString(activa ? R.string.boton_explorar_off : R.string.boton_explorar));
        }
    }

    private void actualizarContenido(boolean activo) {
        fondoPower.setColor(ctx.getColor(activo ? R.color.verde : R.color.rojo));
        btnPower.setContentDescription(ctx.getString(activo ? R.string.boton_apagar : R.string.boton_encender));
        for (ImageButton b : secundarios) {
            b.setVisibility(activo ? View.VISIBLE : View.GONE);
        }
        raiz.setAlpha(prefs.botonAlfa() / 100f);
        marcarExploracion(exploracionActiva);
    }

    private ImageButton crear(int icono, int color) {
        ImageButton b = new ImageButton(ctx);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setShape(GradientDrawable.OVAL);
        fondo.setColor(color);
        fondo.setStroke(Util.dp(ctx, 2), Color.WHITE);
        b.setBackground(fondo);
        b.setImageResource(icono);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int relleno = tam / 5;
        b.setPadding(relleno, relleno, relleno, relleno);
        b.setOnTouchListener(arrastrar);
        return b;
    }

    private void acotar() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int visibles = 1;
        for (ImageButton b : secundarios) {
            if (b.getVisibility() == View.VISIBLE) {
                visibles++;
            }
        }
        int alto = visibles * tam + (visibles - 1) * separacion;
        lp.x = Math.max(0, Math.min(lp.x, dm.widthPixels - tam));
        lp.y = Math.max(0, Math.min(lp.y, dm.heightPixels - alto));
    }

    private final View.OnTouchListener arrastrar = new View.OnTouchListener() {
        private float baseX;
        private float baseY;
        private int inicioX;
        private int inicioY;
        private boolean moviendo;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (raiz == null || lp == null) {
                return false;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    baseX = e.getRawX();
                    baseY = e.getRawY();
                    inicioX = lp.x;
                    inicioY = lp.y;
                    moviendo = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - baseX;
                    float dy = e.getRawY() - baseY;
                    if (!moviendo && (Math.abs(dx) > umbral || Math.abs(dy) > umbral)) {
                        moviendo = true;
                    }
                    if (moviendo) {
                        lp.x = inicioX + Math.round(dx);
                        lp.y = inicioY + Math.round(dy);
                        acotar();
                        try {
                            wm.updateViewLayout(raiz, lp);
                        } catch (Exception ex) {
                            // Ignorado.
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moviendo) {
                        prefs.guardarPosicionBoton(lp.x, lp.y);
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        }
    };
}
