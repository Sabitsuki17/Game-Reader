package com.gamereader.app;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Utilidades pequeñas compartidas. */
final class Util {
    private Util() {
    }

    static int dp(Context c, int valor) {
        return (int) (valor * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressWarnings("deprecation")
    static void vibrar(Context c, boolean habilitado, int milisegundos) {
        if (!habilitado) {
            return;
        }
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            v.vibrate(VibrationEffect.createOneShot(milisegundos, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignorada) {
            // La vibración es solo un extra: si falla, no pasa nada.
        }
    }
}
