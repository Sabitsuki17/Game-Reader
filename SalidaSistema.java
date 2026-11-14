package com.gamereader.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Salida experimental: en vez de hablar con voz propia, envía el texto como un «anuncio» de accesibilidad
 * para que lo pronuncie el lector de pantalla del sistema (TalkBack u otro) con SU voz y velocidad.
 */
final class SalidaSistema {
    private SalidaSistema() {
    }

    /** ¿Hay otro lector con voz activo (distinto de esta app)? */
    static boolean hayOtroLector(Context c) {
        try {
            AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) {
                return false;
            }
            List<AccessibilityServiceInfo> lista =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
            for (AccessibilityServiceInfo i : lista) {
                if (!c.getPackageName().equals(i.getResolveInfo().serviceInfo.packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Se trata como «no hay otro lector».
        }
        return false;
    }

    /** @return true si el anuncio se envió; false si hay que usar la voz propia. */
    @SuppressWarnings("deprecation")
    static boolean enviar(Context c, String texto) {
        try {
            if (!hayOtroLector(c)) {
                return false;
            }
            AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            AccessibilityEvent ev = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            ev.setPackageName(c.getPackageName());
            ev.setClassName(SalidaSistema.class.getName());
            ev.getText().add(texto);
            am.sendAccessibilityEvent(ev);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
