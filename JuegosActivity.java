package com.gamereader.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lista de apps instaladas para marcar cuáles son juegos que el lector debe leer. */
public class JuegosActivity extends BaseActivity {

    private static final class Entrada {
        String paquete;
        String nombre;
        boolean juego;
    }

    private Prefs prefs;
    private final List<Entrada> entradas = new ArrayList<Entrada>();
    private static final int[] INTERVALOS = {-1, 1000, 2000, 3000, 5000, 8000};
    private static final int[] AUTOS = {-1, 1, 0};

    private ListView lista;
    private TextView cargando;
    private String[] etiquetas;
    private ArrayAdapter<String> adaptador;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        prefs = new Prefs(this);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        int p = dp(12);
        raiz.setPadding(p, p, p, p);

        TextView intro = new TextView(this);
        intro.setText(R.string.juegos_intro);
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        raiz.addView(intro, ancho());

        Button detectados = new Button(this);
        detectados.setText(R.string.juegos_marcar_detectados);
        detectados.setAllCaps(false);
        detectados.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < entradas.size(); i++) {
                    if (entradas.get(i).juego) {
                        lista.setItemChecked(i, true);
                    }
                }
                guardar();
            }
        });
        raiz.addView(detectados, ancho());

        Button ninguno = new Button(this);
        ninguno.setText(R.string.juegos_quitar_todos);
        ninguno.setAllCaps(false);
        ninguno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < entradas.size(); i++) {
                    lista.setItemChecked(i, false);
                }
                guardar();
            }
        });
        raiz.addView(ninguno, ancho());

        cargando = new TextView(this);
        cargando.setText(R.string.juegos_cargando);
        cargando.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cargando.setPadding(0, dp(12), 0, dp(12));
        raiz.addView(cargando, ancho());

        lista = new ListView(this);
        lista.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        raiz.addView(lista, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(raiz);

        cargar();
    }

    private void cargar() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Entrada> encontradas = listar(app);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        mostrar(encontradas);
                    }
                });
            }
        }).start();
    }

    private static List<Entrada> listar(Context c) {
        List<Entrada> salida = new ArrayList<Entrada>();
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> vistos = new HashSet<String>();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            String paquete = r.activityInfo.packageName;
            if (paquete.equals(c.getPackageName()) || !vistos.add(paquete)) {
                continue;
            }
            Entrada e = new Entrada();
            e.paquete = paquete;
            e.nombre = String.valueOf(r.loadLabel(pm));
            e.juego = Prefs.esJuego(r.activityInfo.applicationInfo);
            salida.add(e);
        }
        Collections.sort(salida, new Comparator<Entrada>() {
            @Override
            public int compare(Entrada a, Entrada b) {
                if (a.juego != b.juego) {
                    return a.juego ? -1 : 1;
                }
                return a.nombre.compareToIgnoreCase(b.nombre);
            }
        });
        return salida;
    }

    private void mostrar(List<Entrada> encontradas) {
        entradas.clear();
        entradas.addAll(encontradas);
        etiquetas = new String[entradas.size()];
        for (int k = 0; k < entradas.size(); k++) {
            etiquetas[k] = etiquetaDe(entradas.get(k));
        }
        adaptador = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, etiquetas);
        lista.setAdapter(adaptador);
        Set<String> elegidos = prefs.juegos();
        for (int k = 0; k < entradas.size(); k++) {
            lista.setItemChecked(k, elegidos.contains(entradas.get(k).paquete));
        }
        lista.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> padre, View vista, int posicion, long id) {
                guardar();
            }
        });
        lista.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> padre, View vista, int posicion, long id) {
                mostrarPerfil(posicion);
                return true;
            }
        });
        cargando.setVisibility(View.GONE);
    }

    private String etiquetaDe(Entrada e) {
        String t = e.nombre;
        if (e.juego) {
            t += " \u2014 " + getString(R.string.juego_detectado);
        }
        if (prefs.tienePerfil(e.paquete)) {
            t += " \u2014 " + getString(R.string.juego_con_perfil);
        }
        return t;
    }

    private Spinner spinnerDialogo(LinearLayout cont, int titulo, String[] opciones, int seleccion) {
        TextView et = new TextView(this);
        et.setText(titulo);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        et.setPadding(0, dp(14), 0, dp(2));
        cont.addView(et, ancho());
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, opciones);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, Math.min(seleccion, opciones.length - 1)), false);
        sp.setMinimumHeight(dp(52));
        sp.setId(View.generateViewId());
        et.setLabelFor(sp.getId());
        cont.addView(sp, ancho());
        return sp;
    }

    private static int indiceDe(int[] valores, int valor) {
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == valor) {
                return i;
            }
        }
        return 0;
    }

    /** Ajustes solo para un juego: zona de pantalla, rapidez y lectura automática. */
    private void mostrarPerfil(final int posicion) {
        final Entrada e = entradas.get(posicion);
        LinearLayout cont = new LinearLayout(this);
        cont.setOrientation(LinearLayout.VERTICAL);
        int lado = dp(20);
        cont.setPadding(lado, dp(8), lado, dp(8));

        TextView intro = new TextView(this);
        intro.setText(getString(R.string.perfil_intro, e.nombre));
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cont.addView(intro, ancho());

        String[] zonasBase = getResources().getStringArray(R.array.opciones_zona);
        String[] zonas = new String[zonasBase.length + 1];
        zonas[0] = getString(R.string.perfil_usar_general);
        System.arraycopy(zonasBase, 0, zonas, 1, zonasBase.length);

        final Spinner sZona = spinnerDialogo(cont, R.string.perfil_zona, zonas, prefs.perfilZona(e.paquete) + 1);
        final Spinner sIntervalo = spinnerDialogo(cont, R.string.perfil_intervalo,
                getResources().getStringArray(R.array.perfil_opciones_intervalo),
                indiceDe(INTERVALOS, prefs.perfilIntervalo(e.paquete)));
        final Spinner sAuto = spinnerDialogo(cont, R.string.perfil_auto,
                getResources().getStringArray(R.array.perfil_opciones_auto),
                indiceDe(AUTOS, prefs.perfilAuto(e.paquete)));

        ScrollView desplazable = new ScrollView(this);
        desplazable.addView(cont);

        new AlertDialog.Builder(this)
                .setTitle(R.string.perfil_titulo)
                .setView(desplazable)
                .setPositiveButton(R.string.perfil_guardar, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogo, int cual) {
                        prefs.guardarPerfil(e.paquete,
                                sZona.getSelectedItemPosition() - 1,
                                INTERVALOS[sIntervalo.getSelectedItemPosition()],
                                AUTOS[sAuto.getSelectedItemPosition()]);
                        etiquetas[posicion] = etiquetaDe(e);
                        adaptador.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void guardar() {
        Set<String> elegidos = new HashSet<String>();
        for (int k = 0; k < entradas.size(); k++) {
            if (lista.isItemChecked(k)) {
                elegidos.add(entradas.get(k).paquete);
            }
        }
        prefs.setJuegos(elegidos);
    }
}
