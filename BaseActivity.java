package com.gamereader.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Base de las pantallas: construye la interfaz por código con controles estándar (compatibles con TalkBack). */
abstract class BaseActivity extends Activity {

    interface Formato {
        String de(int valor);
    }

    interface AccionBool {
        void con(boolean valor);
    }

    interface AccionInt {
        void con(int valor);
    }

    /** Columna donde se van añadiendo los controles. */
    protected LinearLayout col;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        if (getActionBar() != null && getParentActivityIntent() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void crearPantalla() {
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        col.setPadding(p, p, p, p);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
    }

    protected int dp(int valor) {
        return Util.dp(this, valor);
    }

    protected LinearLayout.LayoutParams ancho() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // ---------- Controles ----------

    protected TextView encabezado(int texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(22), 0, dp(6));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            t.setAccessibilityHeading(true);
        }
        col.addView(t, ancho());
        return t;
    }

    protected TextView parrafo(CharSequence texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(0, dp(4), 0, dp(4));
        col.addView(t, ancho());
        return t;
    }

    private TextView etiqueta(CharSequence texto) {
        TextView t = new TextView(this);
        t.setText(texto);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(2));
        col.addView(t, ancho());
        return t;
    }

    protected GradientDrawable redondeado(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(12));
        g.setColor(color);
        return g;
    }

    protected Button boton(int texto, View.OnClickListener alPulsar) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setOnClickListener(alPulsar);
        LinearLayout.LayoutParams lp = ancho();
        lp.topMargin = dp(8);
        col.addView(b, lp);
        return b;
    }

    protected Switch interruptor(int titulo, int descripcion, boolean valor, final AccionBool accion) {
        Switch s = new Switch(this);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String t = getString(titulo);
        sb.append(t);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (descripcion != 0) {
            sb.append("\n");
            int inicio = sb.length();
            sb.append(getString(descripcion));
            sb.setSpan(new RelativeSizeSpan(0.85f), inicio, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        s.setText(sb);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setMinHeight(dp(56));
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(valor);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton boton, boolean marcado) {
                accion.con(marcado);
            }
        });
        col.addView(s, ancho());
        return s;
    }

    protected Spinner selector(int titulo, int arrayId, int seleccionado, final AccionInt accion) {
        TextView et = etiqueta(getString(titulo));
        String[] opciones = getResources().getStringArray(arrayId);
        final int inicial = Math.max(0, Math.min(seleccionado, opciones.length - 1));
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adaptador = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, opciones);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adaptador);
        sp.setSelection(inicial, false);
        sp.setMinimumHeight(dp(52));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private int ultimo = inicial;

            @Override
            public void onItemSelected(AdapterView<?> padre, View vista, int posicion, long id) {
                if (posicion != ultimo) {
                    ultimo = posicion;
                    accion.con(posicion);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> padre) {
            }
        });
        sp.setId(View.generateViewId());
        et.setLabelFor(sp.getId());
        col.addView(sp, ancho());
        return sp;
    }

    protected SeekBar deslizador(int titulo, final int min, int max, final int paso, int valor,
                                 final Formato formato, final AccionInt accion) {
        final String base = getString(titulo);
        final TextView et = etiqueta(base + ": " + formato.de(valor));
        SeekBar sb = new SeekBar(this);
        sb.setMax((max - min) / paso);
        sb.setProgress((valor - min) / paso);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar barra, int progreso, boolean delUsuario) {
                int v = min + progreso * paso;
                String txt = formato.de(v);
                et.setText(base + ": " + txt);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    barra.setStateDescription(txt);
                }
                if (delUsuario) {
                    accion.con(v);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar barra) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar barra) {
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.setStateDescription(formato.de(valor));
        }
        sb.setId(View.generateViewId());
        et.setLabelFor(sb.getId());
        LinearLayout.LayoutParams lp = ancho();
        lp.topMargin = dp(2);
        col.addView(sb, lp);
        return sb;
    }

    // ---------- Atajos a pantallas del sistema ----------

    protected void aviso(int texto) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
    }

    protected void abrirAccesibilidad() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            aviso(R.string.no_se_pudo_abrir);
        }
    }

    /** Pantalla «Información de la app»: ahí está «Permitir ajustes restringidos» en Android 13 o superior. */
    protected void abrirInfoApp() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            aviso(R.string.no_se_pudo_abrir);
        }
    }

    protected void abrirAjustesVoz() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                aviso(R.string.no_se_pudo_abrir);
            }
        }
    }

    protected void mostrarGuia() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.guia_titulo)
                .setMessage(R.string.guia_texto)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    protected static int blanco() {
        return Color.WHITE;
    }
}
