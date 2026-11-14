package com.gamereader.app;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;

/** Captura de pantalla con el servicio de accesibilidad (Android 11 o superior). Aislada para no cargar APIs nuevas en móviles antiguos. */
@TargetApi(Build.VERSION_CODES.R)
final class Captura {

    interface Resultado {
        void ok(Bitmap imagen);

        void error(int codigo);
    }

    private Captura() {
    }

    static void capturar(AccessibilityService servicio, final Resultado resultado) {
        servicio.takeScreenshot(Display.DEFAULT_DISPLAY, servicio.getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(AccessibilityService.ScreenshotResult captura) {
                        Bitmap copia = null;
                        HardwareBuffer buffer = captura.getHardwareBuffer();
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(buffer, captura.getColorSpace());
                            if (hw != null) {
                                copia = hw.copy(Bitmap.Config.ARGB_8888, false);
                                hw.recycle();
                            }
                        } catch (Throwable t) {
                            copia = null;
                        } finally {
                            try {
                                buffer.close();
                            } catch (Throwable ignorada) {
                                // Nada que hacer.
                            }
                        }
                        if (copia != null) {
                            resultado.ok(copia);
                        } else {
                            resultado.error(-1);
                        }
                    }

                    @Override
                    public void onFailure(int codigoError) {
                        resultado.error(codigoError);
                    }
                });
    }
}
