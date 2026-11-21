# BeDivine — notas de cada versión

La primera sección (`## ...`) es la de la próxima release: el workflow la toma
tal cual, le pone el número de build real y genera con ella el texto de la
pantalla de actualización y el `changelog.json`. Las secciones viejas quedan
abajo como historial. Escribe los cambios como los leería Daniel: qué mejora
o qué se arregló, en su idioma, sin hablar de builds ni de APKs.

## 9.0.0 build#29

### Arreglos

- La portada a pantalla completa ahora sí crece: el crecimiento se hace en la misma capa que dibuja la portada (como el zoom al pausar y la barra que se desvanece, que sí funcionaban), así la canvas se hace más grande hasta casi toda la pantalla y solo quedan el título, el artista, el botón de me gusta y los botones de reproducir

## 9.0.0 build#28

### Arreglos

- La portada a pantalla completa ahora sí crece: en la versión anterior el valor llegaba pero la portada nunca se redibujaba más grande; ahora la canvas crece hasta casi toda la pantalla y solo quedan el título, el artista, el botón de me gusta y los botones de reproducir
- La parte de abajo se oscurece un poco más para que el título y los botones se lean bien sobre portadas claras

## 9.0.0 build#27

### Mejoras

- La portada ahora crece cuando el reproductor se pone en pantalla completa: a los pocos segundos de estar sonando una canción sin tocar nada, la canvas se hace más grande y solo quedan el título, el artista, el botón de me gusta y los botones de reproducir; un toque trae todo lo demás de vuelta

### Arreglos

- La actualización desde la app ya descarga la versión nueva de verdad: cada versión tiene su propio nombre de archivo, así ya no reutiliza la descarga vieja y ya no hay que ir a GitHub

## 9.0.0 build#26

### Mejoras

- El reproductor ahora se pone en pantalla completa solo: a los pocos segundos de estar sonando una canción sin tocar nada, el título, la barra y los botones se desvanecen y queda solo la canvas (o la portada) ocupando toda la pantalla; un toque trae los controles de vuelta

## 9.0.0 build#25

### Arreglos

- La actualización ya no se queda pidiendo instalarse una y otra vez: cada versión descarga su propio archivo y no reutiliza el de la anterior

## 9.0.0 build#24

### Mejoras

- Las animaciones entre pantallas ahora van fluidas: al abrir un álbum, una playlist o un artista, y al volver, la transición termina antes y sin tirones
- El reproductor se abre y se cierra más rápido al tocar el mini reproductor, sin perder su animación

## 9.0.0 build#22

### Mejoras

- La pantalla de actualización ahora muestra qué hay de nuevo en cada versión, con las mejoras y los arreglos bien ordenados
- El canvas de Spotify va primero siempre que esté conectado, al reproducir y al descargar
- Al descargar una canción se guardan su portada en HD, su letra y su canvas de Spotify
- Las descargas viejas completan su portada en HD, su canvas y su letra al abrir la app con internet
- Las portadas y las fotos de las playlists ya se ven sin internet

## 9.0.0 build#21

### Mejoras

- Canvas de Spotify: con tu sesión conectada, sus canvas van primeros al reproducir y se guardan con la descarga
- Ajuste nuevo en Comportamiento de la app para conectar Spotify con tu cookie `sp_dc`

## 9.0.0 build#19

### Mejoras

- Sistema offline: las descargas viejas completan su portada en HD, su canvas y su letra al abrir la app con internet
- Las portadas ya se ven sin internet en las listas, la biblioteca y las playlists
