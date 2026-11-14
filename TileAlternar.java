package com.gamereader.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Mosaico de ajustes rápidos: enciende o apaga el lector desde el panel, incluso con un juego abierto. */
public class TileAlternar extends TileService {

    @Override
    public void onStartListening() {
        refrescar();
    }

    @Override
    public void onClick() {
        if (!LectorService.estaHabilitado(this)) {
            abrirApp();
            return;
        }
        Prefs p = new Prefs(this);
        p.setActivo(!p.activo());
        refrescar();
    }

    private void refrescar() {
        Tile t = getQsTile();
        if (t == null) {
            return;
        }
        boolean servicio = LectorService.estaHabilitado(this);
        boolean activo = new Prefs(this).activo();
        t.setLabel(getString(R.string.app_name));
        t.setState(servicio && activo ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            t.setSubtitle(getString(!servicio ? R.string.tile_sin_servicio : (activo ? R.string.tile_encendido : R.string.tile_apagado)));
        }
        t.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void abrirApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
