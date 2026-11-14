package com.gamereader.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Pantalla principal: estado del lector, botón grande de encender/apagar y accesos a lo demás. */
public class MainActivity extends BaseActivity {

    private Prefs prefs;
    private Voz voz;
    private TextView estado;
    private Button btnPower;
    private TextView avisoVoz;
    private TextView resumen;
    private int estadoVoz;

    private final SharedPreferences.OnSharedPreferenceChangeListener alCambiar =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String clave) {
                    refrescar();
                }
            };

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        prefs = new Prefs(this);
        crearPantalla();
        construir();
        voz = new Voz(this, prefs);
        voz.iniciar(new Voz.Oyente() {
            @Override
            public void alEstarLista(boolean ok) {
                avisoVoz.setVisibility(ok ? View.GONE : View.VISIBLE);
                estadoVoz = ok ? 1 : 2;
                refrescar();
            }
        });
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Prefs.inicializarJuegos(app);
                } catch (Exception e) {
                    // Si falla, el usuario puede marcar los juegos a mano.
                }
            }
        }).start();
    }

    private void construir() {
        TextView titulo = encabezado(R.string.app_name);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        parrafo(getString(R.string.main_intro));

        estado = parrafo("");
        estado.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);

        btnPower = boton(R.string.boton_activar_servicio, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alPulsarPrincipal();
            }
        });
        btnPower.setMinHeight(dp(72));
        btnPower.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

        avisoVoz = parrafo(getString(R.string.aj_motor_error));
        avisoVoz.setVisibility(View.GONE);

        encabezado(R.string.sec_estado);
        resumen = parrafo("");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            parrafo(getString(R.string.aviso_android_antiguo));
        }

        boton(R.string.abrir_accesibilidad, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirAccesibilidad();
            }
        });
        boton(R.string.ayuda_restringidos, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirInfoApp();
            }
        });

        encabezado(R.string.sec_herramientas);
        boton(R.string.probar_voz, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                probar();
            }
        });
        boton(R.string.ajustes_titulo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AjustesActivity.class));
            }
        });
        boton(R.string.juegos_titulo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, JuegosActivity.class));
            }
        });
        boton(R.string.guia_titulo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mostrarGuia();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.raw().registerOnSharedPreferenceChangeListener(alCambiar);
        refrescar();
    }

    @Override
    protected void onPause() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(alCambiar);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (voz != null) {
            voz.apagar();
        }
        super.onDestroy();
    }

    private void alPulsarPrincipal() {
        if (!LectorService.estaHabilitado(this)) {
            abrirAccesibilidad();
            return;
        }
        prefs.setActivo(!prefs.activo());
        refrescar();
    }

    private void refrescar() {
        boolean servicio = LectorService.estaHabilitado(this);
        boolean encendido = prefs.activo();
        int color;
        if (!servicio) {
            estado.setText(R.string.estado_sin_servicio);
            btnPower.setText(R.string.boton_activar_servicio);
            color = R.color.azul;
        } else if (encendido) {
            estado.setText(R.string.estado_encendido);
            btnPower.setText(R.string.boton_apagar_lector);
            color = R.color.rojo;
        } else {
            estado.setText(R.string.estado_apagado);
            btnPower.setText(R.string.boton_encender_lector);
            color = R.color.verde;
        }
        btnPower.setBackground(redondeado(getColor(color)));
        btnPower.setTextColor(blanco());

        int vozTexto = estadoVoz == 1 ? R.string.res_voz_si : (estadoVoz == 2 ? R.string.res_voz_no : R.string.res_voz_comprobando);
        int numJuegos = prefs.juegos().size();
        resumen.setText(getString(servicio ? R.string.res_servicio_si : R.string.res_servicio_no)
                + "\n" + getString(vozTexto)
                + "\n" + getResources().getQuantityString(R.plurals.n_juegos, numJuegos, numJuegos)
                + "\n" + getString(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? R.string.res_ocr_si : R.string.res_ocr_no));
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
}
