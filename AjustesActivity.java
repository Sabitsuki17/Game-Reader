package com.gamereader.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Todos los ajustes del lector, agrupados por temas. Se guardan al instante y el servicio los aplica sin reiniciarse. */
public class AjustesActivity extends BaseActivity {

    private Prefs prefs;
    private Voz voz;
    private Button btnVoz;
    private TextView txtMotor;
    private Sonidos sonidos;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        prefs = new Prefs(this);
        crearPantalla();
        voz = new Voz(this, prefs);
        sonidos = new Sonidos(this, prefs);
        construir();
        voz.iniciar(new Voz.Oyente() {
            @Override
            public void alEstarLista(boolean ok) {
                actualizarMotor(ok);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (voz != null) {
            voz.apagar();
            if (sonidos != null) {
                sonidos.cerrar();
            }
        }
        super.onDestroy();
    }

    private void construir() {
        // ---------------- General ----------------
        encabezado(R.string.sec_general);
        interruptor(R.string.aj_solo_juegos, R.string.aj_solo_juegos_desc, prefs.soloJuegos(),
                v -> prefs.putBool(Prefs.SOLO_JUEGOS, v));
        boton(R.string.aj_elegir_juegos, v -> startActivity(new Intent(this, JuegosActivity.class)));
        selector(R.string.aj_boton_modo, R.array.opciones_boton_modo, prefs.botonModo(),
                i -> prefs.putInt(Prefs.BOTON_MODO, i));
        selector(R.string.aj_boton_tam, R.array.opciones_boton_tam, prefs.botonTam(),
                i -> prefs.putInt(Prefs.BOTON_TAM, i));
        deslizador(R.string.aj_boton_alfa, 30, 100, 10, prefs.botonAlfa(),
                v -> v + " %", v -> prefs.putInt(Prefs.BOTON_ALFA, v));
        selector(R.string.aj_accion_acc, R.array.opciones_accion_acc, prefs.accionAcc(),
                i -> prefs.putInt(Prefs.ACCION_ACC, i));
        interruptor(R.string.aj_vibrar, R.string.aj_vibrar_desc, prefs.vibrar(),
                v -> prefs.putBool(Prefs.VIBRAR, v));
        interruptor(R.string.aj_anunciar_juego, R.string.aj_anunciar_juego_desc, prefs.anunciarJuego(),
                v -> prefs.putBool(Prefs.ANUNCIAR_JUEGO, v));

        // ---------------- Voz ----------------
        encabezado(R.string.sec_voz);
        selector(R.string.aj_idioma, R.array.opciones_idioma, prefs.indiceIdioma(), i -> {
            prefs.putString(Prefs.IDIOMA, Prefs.IDIOMAS[i]);
            prefs.putString(Prefs.VOZ, "");
            voz.aplicar();
            actualizarBotonVoz();
        });
        interruptor(R.string.aj_idioma_auto, R.string.aj_idioma_auto_desc, prefs.idiomaAuto(),
                v -> prefs.putBool(Prefs.IDIOMA_AUTO, v));
        btnVoz = boton(R.string.aj_elegir_voz, v -> elegirVoz());
        actualizarBotonVoz();
        deslizador(R.string.aj_velocidad, 50, 300, 10, Math.round(prefs.velocidad() * 100),
                v -> String.format(Locale.getDefault(), "%.1fx", v / 100.0),
                v -> prefs.putInt(Prefs.VELOCIDAD, v));
        deslizador(R.string.aj_tono, 50, 200, 10, Math.round(prefs.tono() * 100),
                v -> String.format(Locale.getDefault(), "%.1f", v / 100.0),
                v -> prefs.putInt(Prefs.TONO, v));
        deslizador(R.string.aj_volumen, 10, 100, 10, Math.round(prefs.volumen() * 100),
                v -> v + " %", v -> prefs.putInt(Prefs.VOLUMEN, v));
        interruptor(R.string.aj_bajar_juego, R.string.aj_bajar_juego_desc, prefs.bajarJuego(),
                v -> prefs.putBool(Prefs.BAJAR_JUEGO, v));
        interruptor(R.string.aj_interrumpir, R.string.aj_interrumpir_desc, prefs.interrumpir(),
                v -> prefs.putBool(Prefs.INTERRUMPIR, v));
        selector(R.string.aj_salida, R.array.opciones_salida, prefs.salidaSistema() ? 1 : 0,
                i -> prefs.putInt(Prefs.SALIDA, i));
        boton(R.string.aj_probar, v -> probar());
        txtMotor = parrafo(getString(R.string.aj_motor_cargando));
        boton(R.string.aj_cambiar_motor, v -> abrirAjustesVoz());

        // ---------------- Qué leer ----------------
        encabezado(R.string.sec_leer);
        interruptor(R.string.aj_lectura_auto, R.string.aj_lectura_auto_desc, prefs.lecturaAuto(),
                v -> prefs.putBool(Prefs.LECTURA_AUTO, v));
        deslizador(R.string.aj_intervalo, 1000, 8000, 500, prefs.intervaloMs(),
                v -> String.format(Locale.getDefault(), "%.1f s", v / 1000.0),
                v -> prefs.putInt(Prefs.INTERVALO, v));
        selector(R.string.aj_fuente, R.array.opciones_fuente, prefs.fuente(),
                i -> prefs.putInt(Prefs.FUENTE, i));
        selector(R.string.aj_zona, R.array.opciones_zona, prefs.raw().getInt(Prefs.ZONA, 0),
                i -> prefs.putInt(Prefs.ZONA, i));
        interruptor(R.string.aj_leer_numeros, R.string.aj_leer_numeros_desc, prefs.leerNumeros(),
                v -> prefs.putBool(Prefs.LEER_NUMEROS, v));
        deslizador(R.string.aj_min_car, 1, 10, 1, prefs.minCaracteres(),
                v -> getResources().getQuantityString(R.plurals.n_caracteres, v, v),
                v -> prefs.putInt(Prefs.MIN_CAR, v));

        // ---------------- Explorar y tocar ----------------
        encabezado(R.string.sec_explorar);
        selector(R.string.aj_panel_modo, R.array.opciones_panel_modo, prefs.panelModo(),
                i -> prefs.putInt(Prefs.PANEL_MODO, i));
        selector(R.string.aj_posicion, R.array.opciones_posicion, prefs.posicion(),
                i -> prefs.putInt(Prefs.POSICION, i));
        interruptor(R.string.aj_teclas, R.string.aj_teclas_desc, prefs.teclasVolumen(),
                v -> prefs.putBool(Prefs.TECLAS_VOLUMEN, v));
        selector(R.string.aj_ocr_detalle, R.array.opciones_ocr_detalle, prefs.ocrDetalle(),
                i -> prefs.putInt(Prefs.OCR_DETALLE, i));

        // ---------------- Ayuda ----------------
        encabezado(R.string.sec_sonidos);
        interruptor(R.string.aj_sonidos, R.string.aj_sonidos_desc, prefs.sonidos(),
                v -> prefs.putBool(Prefs.SONIDOS, v));
        selector(R.string.aj_sonidos_estilo, R.array.opciones_sonidos_estilo, prefs.sonidosEstilo(),
                i -> prefs.putInt(Prefs.SONIDOS_ESTILO, i));
        deslizador(R.string.aj_sonidos_vol, 10, 100, 10, prefs.sonidosVol(),
                v -> v + " %", v -> prefs.putInt(Prefs.SONIDOS_VOL, v));
        interruptor(R.string.aj_sonidos_estado, 0, prefs.sonidosEstado(),
                v -> prefs.putBool(Prefs.SONIDOS_ESTADO, v));
        interruptor(R.string.aj_sonidos_posicion, R.string.aj_sonidos_posicion_desc, prefs.sonidosPosicion(),
                v -> prefs.putBool(Prefs.SONIDOS_POSICION, v));
        interruptor(R.string.aj_sonidos_acciones, 0, prefs.sonidosAcciones(),
                v -> prefs.putBool(Prefs.SONIDOS_ACCIONES, v));
        interruptor(R.string.aj_sonidos_nuevo, R.string.aj_sonidos_nuevo_desc, prefs.sonidosNuevo(),
                v -> prefs.putBool(Prefs.SONIDOS_NUEVO, v));
        boton(R.string.aj_sonidos_probar, v -> sonidos.probar());

        encabezado(R.string.sec_ayuda);
        boton(R.string.abrir_accesibilidad, v -> abrirAccesibilidad());
        boton(R.string.ayuda_restringidos, v -> abrirInfoApp());
        boton(R.string.guia_titulo, v -> mostrarGuia());
        boton(R.string.aj_restablecer, v -> confirmarRestablecer());
        parrafo(getString(R.string.version, version()));
    }

    private void actualizarBotonVoz() {
        if (btnVoz == null) {
            return;
        }
        String nombre = prefs.voz();
        btnVoz.setText(getString(R.string.aj_voz_actual, nombre.isEmpty() ? getString(R.string.aj_voz_auto) : nombre));
    }

    private void actualizarMotor(boolean ok) {
        if (txtMotor == null) {
            return;
        }
        txtMotor.setText(ok ? getString(R.string.aj_motor_actual, voz.motor()) : getString(R.string.aj_motor_error));
    }

    private void elegirVoz() {
        if (!voz.estaLista()) {
            aviso(R.string.voz_no_lista);
            return;
        }
        final List<Voice> voces = voz.voces(prefs.localeVoz().getLanguage());
        String[] items = new String[voces.size() + 1];
        items[0] = getString(R.string.aj_voz_auto);
        int marcada = 0;
        String actual = prefs.voz();
        for (int i = 0; i < voces.size(); i++) {
            items[i + 1] = voz.describir(voces.get(i));
            if (voces.get(i).getName().equals(actual)) {
                marcada = i + 1;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.aj_elegir_voz)
                .setSingleChoiceItems(items, marcada, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogo, int cual) {
                        prefs.putString(Prefs.VOZ, cual == 0 ? "" : voces.get(cual - 1).getName());
                        voz.aplicar();
                        actualizarBotonVoz();
                        dialogo.dismiss();
                        probar();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void probar() {
        String texto = getString(R.string.texto_prueba);
        if (prefs.salidaSistema() && SalidaSistema.enviar(this, texto)) {
            return;
        }
        if (!voz.estaLista()) {
            aviso(R.string.voz_no_lista);
            return;
        }
        voz.decir(texto, true);
        if (!voz.idiomaDisponible()) {
            aviso(R.string.voz_idioma_no_instalado);
        }
    }

    private void confirmarRestablecer() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.aj_restablecer)
                .setMessage(R.string.aj_restablecer_pregunta)
                .setPositiveButton(R.string.si, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogo, int cual) {
                        prefs.restablecer();
                        recreate();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
