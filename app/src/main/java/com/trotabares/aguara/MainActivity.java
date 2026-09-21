package com.trotabares.aguara;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Random;
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends Activity implements PlaybackService.PlaybackListener {

    private static final int REQUEST_FOLDER = 1001;
    private static final int REQUEST_AUDIO = 1002;
    private static final int REQUEST_FOLDER_FROM_OPEN = 1003;

    private Uri treeUri;
    private Uri currentFolderUri;

    private final ArrayList<Uri> folderStack = new ArrayList<>();
    private final ArrayList<Uri> currentAudioList = new ArrayList<>();
    private final ArrayList<Uri> listaOriginal = new ArrayList<>();
    private int currentAudioIndex = -1;

    private TextView cancion;
    private TextView artista;
    private TextView album;
    private ImageView portada;
    private LinearLayout miniPlayer;
    private TextView miniTitulo;
    private Button miniPlay;
    private TextView estadoCola;
    private Button favorito;
    private Button play;
    private SeekBar progreso;
    private AguaraPcmPlayer reproductor;

    private PlaybackService playbackService;
    private boolean servicioConectado = false;

    private final ServiceConnection conexionServicio =
            new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder =
                    (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            servicioConectado = true;
            playbackService.setPlaybackListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            servicioConectado = false;
            playbackService = null;
        }
    };

    private Button aleatorio;
    private Button repetir;

    private boolean modoAleatorio = false;
    private int modoRepeticion = 0;

    private final Random random = new Random();
    private final Handler handler = new Handler();

    private Uri audioActualUri;
    private boolean actualEsFavorito = false;
    private int posicionReanudar = 0;
    private boolean reanudarDesdeGuardado = false;
    private boolean iniciarReproduccionAlPreparar = true;

    private final Runnable actualizarProgreso = new Runnable() {
        @Override
        public void run() {
            if (reproductor != null && reproductor.isPlaying()) {
                progreso.setProgress(reproductor.getCurrentPosition());
                handler.postDelayed(this, 500);
            }
        }
    };

    private LinearLayout root;

    private final int fondo = Color.rgb(18, 18, 18);
    private final int blanco = Color.WHITE;
    private final int gris = Color.rgb(185, 185, 185);
    private final int dorado = Color.rgb(224, 166, 74);

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String saved = getSharedPreferences("aguara", MODE_PRIVATE)
                .getString("music_tree_uri", null);

        if (saved != null) {
            treeUri = Uri.parse(saved);
            currentFolderUri = treeUri;
        }

        construirInterfazPrincipal();

        if (treeUri != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
                cargarCarpetaCompleta(treeUri);
            } catch (Exception ignored) {
                treeUri = null;
                currentFolderUri = null;
            }
        }
    }

    private void construirInterfazPrincipal() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(fondo);
        root.addView(construirReproductor(),
                new LinearLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private LinearLayout construirReproductor() {
        LinearLayout vista = new LinearLayout(this);
        vista.setOrientation(LinearLayout.VERTICAL);
        vista.setGravity(Gravity.CENTER_HORIZONTAL);
        vista.setPadding(dp(22), dp(28), dp(22), dp(20));
        vista.setBackgroundColor(fondo);

        TextView titulo = text("AGUARÁ", 30, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        vista.addView(titulo, new LinearLayout.LayoutParams(-1, dp(50)));
        vista.addView(text("REPRODUCTOR MUSICAL", 12, gris),
                new LinearLayout.LayoutParams(-1, dp(30)));

        Button cola = new Button(this);
        cola.setText("☰  COLA");
        cola.setTextColor(blanco);
        cola.setOnClickListener(v -> mostrarCola());
        LinearLayout.LayoutParams colaParams =
                new LinearLayout.LayoutParams(-1, dp(50));
        colaParams.topMargin = dp(4);
        vista.addView(cola, colaParams);

        Button historialBtn = new Button(this);
        historialBtn.setText("🕘  HISTORIAL");
        historialBtn.setOnClickListener(v -> mostrarHistorial());
        LinearLayout.LayoutParams hbp =
                new LinearLayout.LayoutParams(-1, dp(52));
        hbp.topMargin = dp(4);
        vista.addView(historialBtn, hbp);

        Button continuarBtn = new Button(this);
        continuarBtn.setText("▶  CONTINUAR ESCUCHANDO");
        continuarBtn.setOnClickListener(v -> continuarUltimaCancion());
        LinearLayout.LayoutParams cbp =
                new LinearLayout.LayoutParams(-1, dp(52));
        cbp.topMargin = dp(4);
        vista.addView(continuarBtn, cbp);

        actualizarTextoModos();
        actualizarBotonFavorito();
        return vista;
    }

    private void alternarAleatorio() {
        if (currentAudioList.isEmpty()) return;

        Uri actual = null;
        if (currentAudioIndex >= 0 &&
                currentAudioIndex < currentAudioList.size()) {
            actual = currentAudioList.get(currentAudioIndex);
        }

        modoAleatorio = !modoAleatorio;

        if (modoAleatorio) {
            ArrayList<Uri> mezcla = new ArrayList<>(currentAudioList);
            if (actual != null) mezcla.remove(actual);
            Collections.shuffle(mezcla, random);
            currentAudioList.clear();
            if (actual != null) currentAudioList.add(actual);
            currentAudioList.addAll(mezcla);
            currentAudioIndex = actual == null ? 0 : 0;
        } else {
            currentAudioList.clear();
            currentAudioList.addAll(listaOriginal);
            if (actual != null) {
                int indice = currentAudioList.indexOf(actual);
                currentAudioIndex = indice >= 0 ? indice : 0;
            } else {
                currentAudioIndex = currentAudioList.isEmpty() ? -1 : 0;
            }
        }
        actualizarTextoModos();
    }

    private void alternarRepetir() {
        modoRepeticion++;
        if (modoRepeticion > 2) modoRepeticion = 0;
        actualizarTextoModos();
    }

    private void actualizarTextoModos() {
        if (aleatorio != null) {
            aleatorio.setText(modoAleatorio ? "🔀 ON" : "🔀");
        }
        if (repetir != null) {
            if (modoRepeticion == 1) repetir.setText("🔂");
            else if (modoRepeticion == 2) repetir.setText("🔁");
            else repetir.setText("↪");
        }
    }

    private void mostrarCola() {
        if (currentAudioList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Cola de reproducción")
                    .setMessage("Todavía no hay canciones cargadas.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        String[] nombres = new String[currentAudioList.size()];
        for (int i = 0; i < currentAudioList.size(); i++) {
            String nombre = obtenerNombreElemento(currentAudioList.get(i));
            if (nombre == null || nombre.trim().isEmpty()) nombre = "Audio";
            String prefijo = (i == currentAudioIndex) ? "▶  " : "    ";
            nombres[i] = prefijo + (i + 1) + ". " + nombre;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cola · " + currentAudioList.size() + " canciones" +
                        (currentAudioIndex >= 0
                                ? " · " + (currentAudioIndex + 1) + "/" + currentAudioList.size()
                                : ""))
                .setItems(nombres, null)
                .setNegativeButton("CERRAR", null).create();

        dialog.setOnShowListener(d -> {
            android.widget.ListView lista = dialog.getListView();
            lista.setOnItemClickListener((parent, view, position, id) -> {
                currentAudioIndex = position;
                reproducirAudio(currentAudioList.get(position));
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void mostrarDialogoAbrir() {
        String[] opciones = {
                "🎵 Archivo de audio",
                "📂 Elegir carpeta"
        };

        new AlertDialog.Builder(this)
                .setTitle("¿Qué querés abrir?")
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.setType("audio/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(i, REQUEST_AUDIO);
                        return;
                    }

                    abrirSelectorCarpeta();
                })
                .show();
    }

    private void abrirSelectorCarpeta() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        if (treeUri != null) {
            try {
                i.putExtra(
                        "android.provider.extra.INITIAL_URI",
                        treeUri
                );
            } catch (Exception ignored) {
            }
        }

        startActivityForResult(i, REQUEST_FOLDER_FROM_OPEN);
    }

    private void guardarCarpeta(Uri carpeta) {
        treeUri = carpeta;
        currentFolderUri = carpeta;

        try {
            getContentResolver().takePersistableUriPermission(
                    carpeta,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        getSharedPreferences("aguara", MODE_PRIVATE)
                .edit()
                .putString("music_tree_uri", carpeta.toString())
                .apply();
    }

    private ArrayList<Uri> obtenerElementosCarpeta(Uri carpeta) {
        ArrayList<Uri> elementos = new ArrayList<>();

        try {
            String id = DocumentsContract.isTreeUri(carpeta)
                    ? DocumentsContract.getTreeDocumentId(carpeta)
                    : DocumentsContract.getDocumentId(carpeta);

            Uri baseTree = DocumentsContract.isTreeUri(carpeta)
                    ? carpeta
                    : treeUri;

            if (baseTree == null) return elementos;

            Uri children =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                            baseTree, id);

            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
            };

            try (Cursor cursor = getContentResolver().query(
                    children, projection, null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        elementos.add(
                                DocumentsContract.buildDocumentUriUsingTree(
                                        baseTree, cursor.getString(0)));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return elementos;
    }

    private String obtenerNombreElemento(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {}
        return "Sin nombre";
    }

    private boolean esCarpeta(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(
                        cursor.getString(0));
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean esAudio(Uri uri) {
        String nombre = obtenerNombreElemento(uri).toLowerCase(Locale.ROOT);
        return nombre.endsWith(".mp3") || nombre.endsWith(".m4a") ||
                nombre.endsWith(".flac") || nombre.endsWith(".wav") ||
                nombre.endsWith(".ogg") || nombre.endsWith(".aac") ||
                nombre.endsWith(".opus") || nombre.endsWith(".wma") ||
                nombre.endsWith(".aiff") || nombre.endsWith(".aif");
    }

    private void escanearRecursivamente(Uri carpeta, ArrayList<Uri> destino) {
        Uri baseTree = DocumentsContract.isTreeUri(carpeta) ? carpeta : treeUri;
        if (baseTree == null) return;

        ArrayList<String> pendientes = new ArrayList<>();
        String raizId = DocumentsContract.isTreeUri(carpeta)
                ? DocumentsContract.getTreeDocumentId(carpeta)
                : DocumentsContract.getDocumentId(carpeta);
        pendientes.add(raizId);

        while (!pendientes.isEmpty()) {
            String parentId = pendientes.remove(pendientes.size() - 1);
            try {
                Uri children =
                        DocumentsContract.buildChildDocumentsUriUsingTree(
                                baseTree, parentId);

                String[] projection = {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                };

                try (Cursor cursor = getContentResolver().query(
                        children, projection, null, null, null)) {
                    if (cursor == null) continue;

                    int idIndex = cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                    int mimeIndex = cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_MIME_TYPE);
                    int nameIndex = cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME);

                    while (cursor.moveToNext()) {
                        String documentId = cursor.getString(idIndex);
                        String mimeType = cursor.getString(mimeIndex);
                        String nombre = cursor.getString(nameIndex);

                        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                            pendientes.add(documentId);
                        } else if (nombre != null && esAudioPorNombre(nombre)) {
                            destino.add(
                                    DocumentsContract.buildDocumentUriUsingTree(
                                            baseTree, documentId));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean esAudioPorNombre(String nombre) {
        String n = nombre.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".m4a") ||
                n.endsWith(".flac") || n.endsWith(".wav") ||
                n.endsWith(".ogg") || n.endsWith(".aac") ||
                n.endsWith(".opus") || n.endsWith(".wma") ||
                n.endsWith(".aiff") || n.endsWith(".aif");
    }

    private void ordenarListaActual() {
        if (currentAudioList.isEmpty()) return;
        Comparator<Uri> comparator = (a, b) -> {
            String na = obtenerNombreElemento(a);
            String nb = obtenerNombreElemento(b);
            if (na == null) na = "";
            if (nb == null) nb = "";
            return na.compareToIgnoreCase(nb);
        };
        Collections.sort(currentAudioList, comparator);
        currentAudioIndex = 0;
    }

    private void cargarCarpetaCompleta(Uri carpeta) {
        listaOriginal.clear();
        currentAudioList.clear();
        currentAudioIndex = -1;

        if (estadoCola != null) estadoCola.setText("🔎 Analizando música...");

        new Thread(() -> {
            ArrayList<Uri> encontrados = new ArrayList<>();
            try {
                escanearRecursivamente(carpeta, encontrados);
                runOnUiThread(() ->
                        estadoCola.setText("🔎 Escaneo terminado: " +
                                encontrados.size() + " canciones"));

                Collections.sort(encontrados,
                        (a, b) -> a.toString().compareToIgnoreCase(b.toString()));

                runOnUiThread(() -> {
                    listaOriginal.clear();
                    listaOriginal.addAll(encontrados);
                    currentAudioList.clear();
                    currentAudioList.addAll(encontrados);

                    if (modoAleatorio && !currentAudioList.isEmpty()) {
                        Collections.shuffle(currentAudioList, random);
                    }

                    if (!currentAudioList.isEmpty()) {
                        String ultimaUriGuardada =
                                getSharedPreferences("aguara", MODE_PRIVATE)
                                        .getString("last_audio_uri", null);

                        int indiceUltima = -1;

                        if (ultimaUriGuardada != null &&
                                !ultimaUriGuardada.trim().isEmpty()) {
                            for (int i = 0; i < currentAudioList.size(); i++) {
                                if (ultimaUriGuardada.equals(
                                        currentAudioList.get(i).toString())) {
                                    indiceUltima = i;
                                    break;
                                }
                            }
                        }

                        if (indiceUltima >= 0) {
                            currentAudioIndex = indiceUltima;
                            posicionReanudar =
                                    getSharedPreferences("aguara", MODE_PRIVATE)
                                            .getInt("last_position", 0);
                            reanudarDesdeGuardado = true;
                            iniciarReproduccionAlPreparar = false;
                        } else {
                            currentAudioIndex = 0;
                            posicionReanudar = 0;
                            reanudarDesdeGuardado = false;
                            iniciarReproduccionAlPreparar = false;
                        }

                        if (estadoCola != null) {
                            estadoCola.setText(
                                    currentAudioList.size() + " canciones cargadas");
                        }

                        reproducirAudio(currentAudioList.get(currentAudioIndex));
                    } else {
                        currentAudioIndex = -1;
                        if (estadoCola != null) {
                            estadoCola.setText(
                                    "No se encontraron archivos de audio");
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (estadoCola != null) {
                        estadoCola.setText("Error al analizar la carpeta");
                    }
                });
            }
        }).start();
    }

    private void reproducirAudio(Uri audio) {
        try {
            audioActualUri = audio;
            if (!reanudarDesdeGuardado) posicionReanudar = 0;
            reanudarDesdeGuardado = false;

            if (reproductor != null) reproductor.release();

            reproductor = new AguaraPcmPlayer(this);
            reproductor.setDataSource(this, audio);

            reproductor.setOnPreparedListener(mp -> {
                progreso.setMax(mp.getDuration());

                if (posicionReanudar > 0 &&
                        posicionReanudar < mp.getDuration()) {
                    mp.seekTo(posicionReanudar);
                    progreso.setProgress(posicionReanudar);
                } else {
                    progreso.setProgress(0);
                }

                guardarEnHistorial(audioActualUri);

                if (iniciarReproduccionAlPreparar) {
                    guardarUltimaPosicion();
                    mp.start();
                    play.setText("⏸");
                    handler.removeCallbacks(actualizarProgreso);
                    handler.post(actualizarProgreso);
                } else {
                    play.setText("▶");
                }

                posicionReanudar = 0;
                iniciarReproduccionAlPreparar = true;
            });

            reproductor.setOnCompletionListener(mp -> {
                progreso.setProgress(0);
                reproducirSiguienteAutomatico();
            });

            reproductor.setOnErrorListener((mp, what, extra) -> {
                play.setText("▶");
                return false;
            });

            actualizarDatosAudio(audio);
            setContentView(root);
            reproductor.prepareAsync();
        } catch (Exception e) {
            if (reproductor != null) {
                reproductor.release();
                reproductor = null;
            }
            play.setText("▶");
        }
    }

    private void abrirDonacion() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://link.mercadopago.com.ar/aguara"));
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void abrirEcualizador() {
        if (reproductor == null) {
            new AlertDialog.Builder(this)
                    .setTitle("ECUALIZADOR")
                    .setMessage("Primero seleccioná una canción.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Intent intent = new Intent(this, EqualizerActivity.class);
        startActivity(intent);
    }

    private void alternarReproduccion() {
        if (reproductor == null) return;
        try {
            if (reproductor.isPlaying()) {
                reproductor.pause();
                guardarUltimaPosicion();
                play.setText("▶");
            } else {
                reproductor.start();
                play.setText("⏸");
                handler.post(actualizarProgreso);
            }
        } catch (Exception ignored) {}
    }

    private void reproducirAnterior() {
        if (reproductor != null) {
            try {
                if (reproductor.getCurrentPosition() > 3000) {
                    reproductor.seekTo(0);
                    progreso.setProgress(0);
                    return;
                }
            } catch (Exception ignored) {}
        }

        if (currentAudioList.size() <= 1 || currentAudioIndex <= 0) {
            if (reproductor != null) {
                try {
                    reproductor.seekTo(0);
                    progreso.setProgress(0);
                } catch (Exception ignored) {}
            }
            return;
        }

        currentAudioIndex--;
        reproducirAudio(currentAudioList.get(currentAudioIndex));
    }

    private void reproducirSiguiente() {
        if (currentAudioList.isEmpty()) return;

        if (currentAudioIndex + 1 < currentAudioList.size()) {
            currentAudioIndex++;
            reproducirAudio(currentAudioList.get(currentAudioIndex));
            return;
        }

        if (modoRepeticion == 2) {
            currentAudioIndex = 0;
            reproducirAudio(currentAudioList.get(currentAudioIndex));
        } else {
            currentAudioIndex = -1;
            play.setText("▶");
            if (estadoCola != null) estadoCola.setText("Fin de la cola");
        }
    }

    private void reproducirSiguienteAutomatico() {
        if (currentAudioList.isEmpty()) {
            play.setText("▶");
            if (estadoCola != null) estadoCola.setText("Sin cola cargada");
            return;
        }

        if (modoRepeticion == 1) {
            if (currentAudioIndex < 0) currentAudioIndex = 0;
            reproducirAudio(currentAudioList.get(currentAudioIndex));
            return;
        }

        if (currentAudioIndex + 1 < currentAudioList.size()) {
            currentAudioIndex++;
            reproducirAudio(currentAudioList.get(currentAudioIndex));
            return;
        }

        if (modoRepeticion == 2) {
            currentAudioIndex = 0;
            reproducirAudio(currentAudioList.get(currentAudioIndex));
        } else {
            currentAudioIndex = -1;
            play.setText("▶");
            if (estadoCola != null) estadoCola.setText("Fin de la cola");
        }
    }

    private void scrollPlayer() {
        actualizarMiniPlayer();
    }

    private void actualizarMiniPlayer() {
        if (miniPlayer == null || cancion == null) return;

        String titulo = cancion.getText().toString();
        if (titulo.trim().isEmpty()) titulo = "Sin canción";
        if (miniTitulo != null) miniTitulo.setText(titulo);

        if (miniPlay != null) {
            miniPlay.setText(
                    reproductor != null && reproductor.isPlaying()
                            ? "⏸" : "▶");
        }
    }

    private void actualizarDatosAudio(Uri audio) {
        String fallback = obtenerNombreElemento(audio);
        if (cancion != null) {
            cancion.setText(
                    fallback == null || fallback.trim().isEmpty()
                            ? "Audio" : fallback);
        }
        if (artista != null) artista.setText("Desconocido");
        if (album != null) album.setText("Álbum desconocido");

        new Thread(() -> {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(this, audio);
                String title = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_TITLE);
                String artist = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String albumName = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ALBUM);
                byte[] artwork = mmr.getEmbeddedPicture();

                runOnUiThread(() -> {
                    if (cancion != null && title != null &&
                            !title.trim().isEmpty()) cancion.setText(title);
                    if (artista != null && artist != null &&
                            !artist.trim().isEmpty()) artista.setText(artist);
                    if (album != null && albumName != null &&
                            !albumName.trim().isEmpty()) album.setText(albumName);

                    if (portada != null && artwork != null && artwork.length > 0) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(
                                artwork, 0, artwork.length);
                        if (bitmap != null) {
                            portada.setImageBitmap(bitmap);
                            portada.setVisibility(View.VISIBLE);
                        }
                    } else if (portada != null) {
                        portada.setImageResource(R.drawable.aguara_cover);
                        portada.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (portada != null) {
                        portada.setImageResource(R.drawable.aguara_cover);
                        portada.setVisibility(View.VISIBLE);
                    }
                });
            } finally {
                try { mmr.release(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private Set<String> obtenerFavoritos() {
        return new LinkedHashSet<>(
                getSharedPreferences("aguara", MODE_PRIVATE)
                        .getStringSet("favoritos", new LinkedHashSet<>()));
    }

    private void guardarFavoritos(Set<String> favoritos) {
        getSharedPreferences("aguara", MODE_PRIVATE)
                .edit()
                .putStringSet("favoritos", new LinkedHashSet<>(favoritos))
                .apply();
    }

    private void alternarFavoritoActual() {
        if (audioActualUri == null) return;
        Set<String> favoritos = obtenerFavoritos();
        String uri = audioActualUri.toString();

        if (favoritos.contains(uri)) favoritos.remove(uri);
        else favoritos.add(uri);

        guardarFavoritos(favoritos);
        actualizarBotonFavorito();
    }

    private void actualizarBotonFavorito() {
        if (favorito == null) return;
        actualEsFavorito =
                audioActualUri != null &&
                obtenerFavoritos().contains(audioActualUri.toString());
        favorito.setText(actualEsFavorito ? "♥  FAVORITO" : "♡  FAVORITO");
    }

    private ArrayList<String> obtenerHistorial() {
        String guardado = getSharedPreferences("aguara", MODE_PRIVATE)
                .getString("historial", "");
        ArrayList<String> historial = new ArrayList<>();
        if (guardado == null || guardado.trim().isEmpty()) return historial;
        String[] partes = guardado.split("\n");
        for (String parte : partes) {
            if (!parte.trim().isEmpty()) historial.add(parte);
        }
        return historial;
    }

    private void guardarEnHistorial(Uri uri) {
        if (uri == null) return;
        ArrayList<String> historial = obtenerHistorial();
        String valor = uri.toString();
        historial.remove(valor);
        historial.add(0, valor);
        while (historial.size() > 50) historial.remove(historial.size() - 1);

        getSharedPreferences("aguara", MODE_PRIVATE)
                .edit()
                .putString("historial", String.join("\n", historial))
                .apply();
    }

    private void mostrarFavoritos() {
        Set<String> favoritos = obtenerFavoritos();
        if (favoritos.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("♥ FAVORITOS")
                    .setMessage("Todavía no tenés canciones favoritas.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        ArrayList<String> uris = new ArrayList<>(favoritos);
        String[] nombres = new String[uris.size()];
        for (int i = 0; i < uris.size(); i++) {
            nombres[i] = obtenerNombreElemento(Uri.parse(uris.get(i)));
            if (nombres[i] == null || nombres[i].trim().isEmpty()) nombres[i] = "Audio";
        }

        new AlertDialog.Builder(this)
                .setTitle("♥ FAVORITOS")
                .setItems(nombres, (dialog, which) -> {
                    Uri uri = Uri.parse(uris.get(which));
                    currentAudioList.clear();
                    currentAudioList.add(uri);
                    listaOriginal.clear();
                    listaOriginal.add(uri);
                    currentAudioIndex = 0;
                    reproducirAudio(uri);
                    dialog.dismiss();
                })
                .setNegativeButton("CERRAR", null).show();
    }

    private void mostrarHistorial() {
        ArrayList<String> historial = obtenerHistorial();
        if (historial.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("🕘 HISTORIAL")
                    .setMessage("Todavía no hay canciones reproducidas.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        String[] nombres = new String[historial.size()];
        for (int i = 0; i < historial.size(); i++) {
            nombres[i] = obtenerNombreElemento(Uri.parse(historial.get(i)));
            if (nombres[i] == null || nombres[i].trim().isEmpty()) nombres[i] = "Audio";
        }

        new AlertDialog.Builder(this)
                .setTitle("🕘 HISTORIAL")
                .setItems(nombres, (dialog, which) -> {
                    Uri uri = Uri.parse(historial.get(which));
                    currentAudioList.clear();
                    currentAudioList.add(uri);
                    listaOriginal.clear();
                    listaOriginal.add(uri);
                    currentAudioIndex = 0;
                    reproducirAudio(uri);
                    dialog.dismiss();
                })
                .setNegativeButton("CERRAR", null).show();
    }

    private void guardarUltimaPosicion() {
        if (audioActualUri == null || reproductor == null) return;
        try {
            getSharedPreferences("aguara", MODE_PRIVATE)
                    .edit()
                    .putString("last_audio_uri", audioActualUri.toString())
                    .putInt("last_position",
                            Math.max(0, reproductor.getCurrentPosition()))
                    .apply();
        } catch (Exception ignored) {}
    }

    private void continuarUltimaCancion() {
        String uriGuardada =
                getSharedPreferences("aguara", MODE_PRIVATE)
                        .getString("last_audio_uri", null);
        int posicion =
                getSharedPreferences("aguara", MODE_PRIVATE)
                        .getInt("last_position", 0);

        if (uriGuardada == null || uriGuardada.trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("CONTINUAR ESCUCHANDO")
                    .setMessage("Todavía no hay una reproducción guardada.")
                    .setPositiveButton("OK", null).show();
            return;
        }

        Uri uri = Uri.parse(uriGuardada);
        currentAudioList.clear();
        currentAudioList.add(uri);
        listaOriginal.clear();
        listaOriginal.add(uri);
        currentAudioIndex = 0;
        posicionReanudar = posicion;
        reanudarDesdeGuardado = true;
        reproducirAudio(uri);
    }

    private String[] obtenerInfoAudio(Uri uri) {
        String titulo = null;
        String artistaNombre = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            titulo = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE);
            artistaNombre = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST);
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        return new String[]{titulo, artistaNombre};
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri seleccion = data.getData();

        if (requestCode == REQUEST_AUDIO) {
            currentAudioList.clear();
            currentAudioList.add(seleccion);
            currentAudioIndex = 0;
            reproducirAudio(seleccion);
            return;
        }

        if (requestCode == REQUEST_FOLDER || requestCode == REQUEST_FOLDER_FROM_OPEN) {
            guardarCarpeta(seleccion);
            folderStack.clear();
            cargarCarpetaCompleta(seleccion);
        }
    }

    @Override
    public void onPlaybackPrepared(int duracion) {
        progreso.setMax(duracion);
        progreso.setProgress(0);
    }

    @Override
    public void onPlaybackCompleted() {
        reproducirSiguienteAutomatico();
    }

    @Override
    public void onPlaybackError() {
        play.setText("▶");
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(actualizarProgreso);
        guardarUltimaPosicion();

        if (reproductor != null) {
            try { reproductor.release(); } catch (Exception ignored) {}
            reproductor = null;
        }

        super.onDestroy();
    }
}
