# Game-Reader

[![Versión](https://img.shields.io/badge/versi%C3%B3n-beta-orange)](https://github.com/Sabitsuki17/Game-Reader/releases)
[![Licencia](https://img.shields.io/badge/licencia-MIT-blue)](LICENSE)

Lector de pantalla para juegos en Android. Lee en voz alta los textos del juego, indica dónde está cada uno y puede tocarlo cuando el usuario lo pide.

> **Versión beta.** Puede contener errores y todavía no se ha probado en un teléfono real. Agradecemos los reportes.

## Características

- Lectura de textos con la voz predeterminada del teléfono.
- Reconocimiento de texto en pantalla (OCR) en el propio dispositivo, para juegos sin accesibilidad.
- Lectura solo de lo nuevo, sin repetir lo ya dicho.
- Exploración de textos uno a uno, con su posición en pantalla, y toque del elemento elegido.
- Exploración con el dedo: un dedo lee, dos toques pulsan y dos dedos deslizan.
- Control rápido: panel flotante, mosaico de ajustes rápidos o botón de accesibilidad.
- Sonidos de aviso en tres estilos, con posición izquierda–derecha al explorar.
- Cambio de voz según el idioma del texto, si esa voz está instalada.
- Ajustes generales y ajustes propios para cada juego.

## Puntos fuertes

- Actúa solo en los juegos que el usuario elige.
- Convive con TalkBack.
- El reconocimiento de texto se realiza en el dispositivo.
- Configurable: voz, velocidad, zona de pantalla, intervalo, sonidos y más.

## Limitaciones

- Versión beta, sin pruebas en teléfonos reales.
- Solo lee texto: no describe imágenes, personajes ni lo que ocurre en la partida.
- El OCR puede fallar con letra decorativa, texto pequeño o fondos en movimiento.
- El reconocimiento de pantalla consume batería.
- No puede capturar juegos con pantalla protegida.
- Solo reconoce alfabeto latino.
- TalkBack puede interferir con los toques del juego; en ese caso conviene suspenderlo mientras se juega.

## Compatibilidad con juegos

Estimación según el diseño de la app, pendiente de pruebas.

| Tipo de juego | Estado | Motivo |
|---|---|---|
| Novelas visuales y ficción interactiva | Compatible | El contenido principal es texto |
| Preguntas, palabras y trivia | Compatible | Preguntas y respuestas con texto |
| RPG y cartas por turnos | Parcial | Se leen historia y menús; el tablero es visual |
| Estrategia y construcción | Parcial | Se leen menús; el mapa es visual |
| Puzles de fichas | Parcial | El tablero es visual |
| Juegos con botones sin texto | No compatible por ahora | El lector solo actúa sobre elementos con texto |
| Acción, disparos, carreras, plataformas y ritmo | No compatible por ahora | Requieren reacción en tiempo real |

## Requisitos

- Android 8.0 o superior.
- Android 11 o superior para el reconocimiento de texto en capturas.
- Un motor de síntesis de voz instalado.

## Primer uso

1. Instala el APK.
2. Abre Game-Reader y activa el servicio en los ajustes de accesibilidad.
3. Si el interruptor aparece atenuado (Android 13 o superior), permite los ajustes restringidos desde la información de la app.
4. En **Elegir juegos**, marca los juegos deseados.
5. Abre un juego.

## Permisos y privacidad

- **Servicio de accesibilidad:** accede al contenido de la ventana, realiza capturas, toca la pantalla solo cuando el usuario lo pide y, si se activa, lee las teclas de volumen.
- El servicio actúa solo en los juegos elegidos.
- La app no envía capturas ni textos a servidores. Los permisos de red los añade la biblioteca ML Kit.
- No incluye anuncios ni analíticas.

## Reportar un error

Abre un [Issue](https://github.com/Sabitsuki17/Game-Reader/issues) con:
- Modelo del teléfono y versión de Android.
- Juego afectado.
- Qué esperabas y qué ocurrió.
- Si es posible, el registro: `adb logcat -s GameReader`.

## Autor

Desarrollado por [Sabitsuki17](https://github.com/Sabitsuki17).

## Licencia

Distribuido bajo la licencia MIT. Consulta [LICENSE](LICENSE).
