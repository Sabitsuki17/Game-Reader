package com.gamereader.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decide QUÉ se dice en cada revisión de la pantalla: descarta restos de OCR, recuerda lo ya leído para no repetirlo
 * y tolera pequeños errores de reconocimiento. No usa nada de Android, así que se puede probar en cualquier ordenador.
 */
final class FiltroTexto {

    /** Un trozo de texto visto en pantalla. */
    static final class Pieza {
        final String texto;
        /** Posición horizontal del centro, de 0 (izquierda) a 1 (derecha). NaN si no se conoce. */
        final double posX;
        /** Posición vertical del centro, de 0 (arriba) a 1 (abajo). NaN si no se conoce. */
        final double posY;

        Pieza(String texto, double posY) {
            this(texto, Double.NaN, posY);
        }

        Pieza(String texto, double posX, double posY) {
            this.texto = texto;
            this.posX = posX;
            this.posY = posY;
        }
    }

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    /** Clave de cada texto visto -> última vez (ms) que estuvo en pantalla. */
    private final Map<String, Long> vistos = new HashMap<String, Long>();

    /** Olvida todo lo leído (al cambiar de juego, al apagar y encender…). */
    void reiniciar() {
        vistos.clear();
    }

    /** Igual que {@link #nuevosIndices}, pero devuelve los textos ya limpios. */
    List<String> nuevos(List<Pieza> piezas, boolean manual, long ahora, long ventana,
                        boolean conNumeros, int minCar, double zonaIni, double zonaFin) {
        List<String> salida = new ArrayList<String>();
        for (int i : nuevosIndices(piezas, manual, ahora, ventana, conNumeros, minCar, zonaIni, zonaFin)) {
            salida.add(limpiar(piezas.get(i).texto));
        }
        return salida;
    }

    /**
     * @param manual     true si el usuario pidió «leer ahora»: se dice todo, aunque ya se haya leído
     * @param ahora      reloj en milisegundos
     * @param ventana    tiempo que un texto debe faltar de la pantalla para volver a considerarse nuevo
     * @param conNumeros si es false, «Puntos: 120» y «Puntos: 125» cuentan como el mismo texto
     * @param minCar     longitud mínima de un texto
     * @param zonaIni    franja vertical a leer, inicio (0 a 1)
     * @param zonaFin    franja vertical a leer, fin (0 a 1)
     * @return posiciones (en la lista recibida) de los textos que hay que decir ahora, en orden
     */
    List<Integer> nuevosIndices(List<Pieza> piezas, boolean manual, long ahora, long ventana,
                                boolean conNumeros, int minCar, double zonaIni, double zonaFin) {
        List<Integer> salida = new ArrayList<Integer>();
        Set<String> claves = new HashSet<String>();
        for (int i = 0; i < piezas.size(); i++) {
            Pieza p = piezas.get(i);
            if (!Double.isNaN(p.posY) && (p.posY < zonaIni || p.posY > zonaFin)) {
                continue;
            }
            String t = limpiar(p.texto);
            if (!valido(t, minCar)) {
                continue;
            }
            String clave = clave(t, conNumeros);
            if (clave.isEmpty() || !claves.add(clave)) {
                continue;
            }
            if (manual || !esConocido(clave, ahora, ventana)) {
                salida.add(i);
            }
        }
        for (String c : claves) {
            vistos.put(c, ahora);
        }
        podar(ahora, ventana * 6);
        return salida;
    }

    /** Posiciones de las piezas que sirven como elementos explorables: válidas y sin repetir, en el orden recibido. */
    static List<Integer> indicesUtiles(List<Pieza> piezas, int minCar) {
        List<Integer> salida = new ArrayList<Integer>();
        Set<String> claves = new HashSet<String>();
        for (int i = 0; i < piezas.size(); i++) {
            String t = limpiar(piezas.get(i).texto);
            if (!valido(t, minCar)) {
                continue;
            }
            String clave = clave(t, true);
            if (clave.isEmpty() || !claves.add(clave)) {
                continue;
            }
            salida.add(i);
        }
        return salida;
    }

    /** Casilla (0 a 8, fila a fila) de una rejilla de 3 × 3 sobre la pantalla. Si no se conoce, el centro. */
    static int celda(double x, double y) {
        int columna = Double.isNaN(x) ? 1 : (x < 1.0 / 3 ? 0 : (x < 2.0 / 3 ? 1 : 2));
        int fila = Double.isNaN(y) ? 1 : (y < 1.0 / 3 ? 0 : (y < 2.0 / 3 ? 1 : 2));
        return fila * 3 + columna;
    }

    private boolean esConocido(String clave, long ahora, long ventana) {
        Long visto = vistos.get(clave);
        if (visto != null && ahora - visto <= ventana) {
            return true;
        }
        for (Map.Entry<String, Long> e : vistos.entrySet()) {
            if (ahora - e.getValue() <= ventana && parecidos(clave, e.getKey())) {
                return true;
            }
        }
        return false;
    }

    private void podar(long ahora, long edadMaxima) {
        Iterator<Map.Entry<String, Long>> it = vistos.entrySet().iterator();
        while (it.hasNext()) {
            if (ahora - it.next().getValue() > edadMaxima) {
                it.remove();
            }
        }
    }

    static String limpiar(String s) {
        return ESPACIOS.matcher(s).replaceAll(" ").trim();
    }

    /** Descarta restos de OCR: textos demasiado cortos o con casi todo símbolos. */
    static boolean valido(String t, int minCar) {
        int n = t.length();
        if (n < minCar) {
            return false;
        }
        int utiles = 0;
        for (int i = 0; i < n; i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) {
                utiles++;
            }
        }
        return utiles > 0 && utiles * 2 >= n;
    }

    /** Clave para comparar textos: sin tildes ni mayúsculas y, salvo que se pida lo contrario, sin fijarse en los números. */
    static String clave(String t, boolean conNumeros) {
        String n = MARCAS.matcher(Normalizer.normalize(t, Normalizer.Form.NFD)).replaceAll("");
        n = n.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(n.length());
        boolean espacio = true;
        char ultimo = ' ';
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(c);
                ultimo = c;
                espacio = false;
            } else if (Character.isDigit(c)) {
                if (conNumeros) {
                    sb.append(c);
                    ultimo = c;
                } else if (ultimo != '#') {
                    sb.append('#');
                    ultimo = '#';
                }
                espacio = false;
            } else if (!espacio) {
                sb.append(' ');
                ultimo = ' ';
                espacio = true;
            }
        }
        return sb.toString().trim();
    }

    /** El OCR a veces confunde una letra entre un fotograma y otro: se tratan como el mismo texto si casi coinciden. */
    static boolean parecidos(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max < 6) {
            return false;
        }
        // Un número distinto es información nueva, no un error de OCR.
        if (!digitos(a).equals(digitos(b))) {
            return false;
        }
        if (Math.abs(a.length() - b.length()) * 5 > max) {
            return false;
        }
        return distancia(a, b) * 100 <= max * 15;
    }

    private static String digitos(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previa[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int coste = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + coste);
            }
            int[] tmp = previa;
            previa = actual;
            actual = tmp;
        }
        return previa[b.length()];
    }

    /** Junta varios textos en una sola frase, con pausas naturales. */
    static String unir(List<String> partes) {
        StringBuilder sb = new StringBuilder();
        for (String s : partes) {
            if (sb.length() > 0) {
                char ultimo = sb.charAt(sb.length() - 1);
                sb.append(".!?:;\u2026".indexOf(ultimo) >= 0 ? " " : ". ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Junta textos consecutivos del mismo idioma para hablarlos de una vez.
     * Cada resultado es {idioma, texto}; el idioma va vacío cuando se usa el de los ajustes.
     */
    static List<String[]> agruparPorIdioma(List<String> textos, List<String> idiomas) {
        List<String[]> salida = new ArrayList<String[]>();
        List<String> actual = new ArrayList<String>();
        String idiomaActual = null;
        for (int i = 0; i < textos.size(); i++) {
            String id = (i < idiomas.size() && idiomas.get(i) != null) ? idiomas.get(i) : "";
            if (idiomaActual != null && !id.equals(idiomaActual)) {
                salida.add(new String[]{idiomaActual, unir(actual)});
                actual.clear();
            }
            idiomaActual = id;
            actual.add(textos.get(i));
        }
        if (idiomaActual != null) {
            salida.add(new String[]{idiomaActual, unir(actual)});
        }
        return salida;
    }

    /** Divide textos muy largos para no pasarse del límite del motor de voz. */
    static List<String> trocear(String texto, int max) {
        List<String> salida = new ArrayList<String>();
        String resto = texto.trim();
        while (resto.length() > max) {
            int corte = -1;
            for (int i = max; i > max / 2; i--) {
                char c = resto.charAt(i - 1);
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    corte = i;
                    break;
                }
            }
            if (corte < 0) {
                int espacio = resto.lastIndexOf(' ', max);
                corte = espacio > 0 ? espacio : max;
            }
            String parte = resto.substring(0, corte).trim();
            if (!parte.isEmpty()) {
                salida.add(parte);
            }
            resto = resto.substring(corte).trim();
        }
        if (!resto.isEmpty()) {
            salida.add(resto);
        }
        return salida;
    }
}
