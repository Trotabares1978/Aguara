package com.trotabares.aguara;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
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

public class MainActivity extends Activity {

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
    private Button play;
    private SeekBar progreso;
    private MediaPlayer reproductor;

    private Button aleatorio;
    private Button repetir;

    private boolean modoAleatorio = false;
    // 0 = sin repetir, 1 = repetir canción, 2 = repetir lista
    private int modoRepeticion = 0;

    private final Random random = new Random();
    private final Handler handler = new Handler();

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
    }

    private void construirInterfazPrincipal() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(fondo);

        root.addView(
                construirReproductor(),
                new LinearLayout.LayoutParams(-1, -1)
        );

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

        vista.addView(
                text("REPRODUCTOR MUSICAL", 12, gris),
                new LinearLayout.LayoutParams(-1, dp(30))
        );

        ImageView portada = new ImageView(this);
        portada.setImageResource(R.drawable.aguara_cover);
        portada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vista.addView(
                portada,
                new LinearLayout.LayoutParams(dp(280), dp(280))
        );

        cancion = text("Ninguna canción", 23, blanco);
        cancion.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams cp =
                new LinearLayout.LayoutParams(-1, dp(45));
        cp.topMargin = dp(18);
        vista.addView(cancion, cp);

        artista = text("Seleccioná música para comenzar", 15, gris);
        vista.addView(
                artista,
                new LinearLayout.LayoutParams(-1, dp(30))
        );

        progreso = new SeekBar(this);
        progreso.setMax(100);
        progreso.setProgressTintList(
                android.content.res.ColorStateList.valueOf(dorado)
        );

        progreso.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser) {
                        if (fromUser && reproductor != null) {
                            try {
                                reproductor.seekTo(progress);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(-1, dp(45));
        sp.topMargin = dp(10);
        vista.addView(progreso, sp);

        LinearLayout controles = new LinearLayout(this);
        controles.setGravity(Gravity.CENTER);

        Button anterior = new Button(this);
        anterior.setText("⏮");
        anterior.setOnClickListener(v -> reproducirAnterior());

        play = new Button(this);
        play.setText("▶");
        play.setOnClickListener(v -> alternarReproduccion());

        Button siguiente = new Button(this);
        siguiente.setText("⏭");
        siguiente.setOnClickListener(v -> reproducirSiguiente());

        controles.addView(
                anterior,
                new LinearLayout.LayoutParams(dp(70), dp(60))
        );
        controles.addView(
                play,
                new LinearLayout.LayoutParams(dp(90), dp(60))
        );
        controles.addView(
                siguiente,
                new LinearLayout.LayoutParams(dp(70), dp(60))
        );

        vista.addView(controles);

        LinearLayout modos = new LinearLayout(this);
        modos.setGravity(Gravity.CENTER);

        aleatorio = new Button(this);
        aleatorio.setText("🔀");
        aleatorio.setOnClickListener(v -> alternarAleatorio());

        repetir = new Button(this);
        repetir.setText("🔁");
        repetir.setOnClickListener(v -> alternarRepetir());

        modos.addView(
                aleatorio,
                new LinearLayout.LayoutParams(dp(80), dp(52))
        );
        modos.addView(
                repetir,
                new LinearLayout.LayoutParams(dp(80), dp(52))
        );

        vista.addView(modos);

        Button cola = new Button(this);
        cola.setText("☰  COLA");
        cola.setTextColor(blanco);
        cola.setOnClickListener(v -> mostrarCola());

        LinearLayout.LayoutParams colaParams =
                new LinearLayout.LayoutParams(-1, dp(50));
        colaParams.topMargin = dp(4);
        vista.addView(cola, colaParams);

        Button biblioteca = new Button(this);
        biblioteca.setText("🎵  BIBLIOTECA");
        biblioteca.setTextColor(blanco);
        biblioteca.setOnClickListener(v -> abrirBiblioteca());

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, dp(55));
        bp.topMargin = dp(6);
        vista.addView(biblioteca, bp);

        Button abrir = new Button(this);
        abrir.setText("📂  ABRIR");
        abrir.setTextColor(blanco);
        abrir.setOnClickListener(v -> mostrarDialogoAbrir());

        LinearLayout.LayoutParams ap =
                new LinearLayout.LayoutParams(-1, dp(55));
        ap.topMargin = dp(8);
        vista.addView(abrir, ap);

        actualizarTextoModos();

        return vista;
    }

    private void alternarAleatorio() {
        if (currentAudioList.isEmpty()) {
            return;
        }

        Uri actual = null;
        if (currentAudioIndex >= 0 &&
                currentAudioIndex < currentAudioList.size()) {
            actual = currentAudioList.get(currentAudioIndex);
        }

        modoAleatorio = !modoAleatorio;

        if (modoAleatorio) {
            ArrayList<Uri> mezcla = new ArrayList<>(currentAudioList);

            if (actual != null) {
                mezcla.remove(actual);
            }

            Collections.shuffle(mezcla, random);

            currentAudioList.clear();

            if (actual != null) {
                currentAudioList.add(actual);
            }

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

        if (modoRepeticion > 2) {
            modoRepeticion = 0;
        }

        actualizarTextoModos();
    }



    private void actualizarTextoModos() {
        if (aleatorio != null) {
            aleatorio.setText(
                    modoAleatorio ? "🔀 ON" : "🔀"
            );
        }

        if (repetir != null) {
            if (modoRepeticion == 1) {
                repetir.setText("🔂");
            } else if (modoRepeticion == 2) {
                repetir.setText("🔁");
            } else {
                repetir.setText("🔁");
            }
        }
    }

    private void mostrarCola() {
        if (currentAudioList.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Cola de reproducción")
                    .setMessage("Todavía no hay canciones cargadas.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] nombres = new String[currentAudioList.size()];

        for (int i = 0; i < currentAudioList.size(); i++) {
            String nombre = obtenerNombreElemento(
                    currentAudioList.get(i)
            );

            if (nombre == null || nombre.trim().isEmpty()) {
                nombre = "Audio";
            }

            String prefijo = (i == currentAudioIndex)
                    ? "▶  "
                    : "    ";

            nombres[i] = prefijo + (i + 1) + ". " + nombre;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        "Cola · " + currentAudioList.size() + " canciones"
                )
                .setItems(nombres, null)
                .setNegativeButton("CERRAR", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.ListView lista =
                    dialog.getListView();

            lista.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        currentAudioIndex = position;
                        reproducirAudio(
                                currentAudioList.get(position)
                        );
                        dialog.dismiss();
                    }
            );
        });

        dialog.show();
    }



    private void abrirBiblioteca() {
        if (treeUri != null) {
            folderStack.clear();
            mostrarCarpeta(treeUri);
        } else {
            elegirCarpeta(REQUEST_FOLDER);
        }
    }

    private void mostrarDialogoAbrir() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("¿Qué querés abrir?")
                .setItems(
                        new String[]{"🎵 Archivo de audio", "📂 Carpeta"},
                        (dialog, which) -> {
                            if (which == 0) {
                                Intent i =
                                        new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                i.setType("audio/*");
                                i.addCategory(Intent.CATEGORY_OPENABLE);
                                i.addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                );
                                startActivityForResult(i, REQUEST_AUDIO);
                            } else {
                                elegirCarpeta(REQUEST_FOLDER_FROM_OPEN);
                            }
                        })
                .show();
    }

    private void elegirCarpeta(int requestCode) {
        Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );

        startActivityForResult(i, requestCode);
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
            // ACTION_OPEN_DOCUMENT_TREE devuelve una Tree URI para la
            // carpeta raíz. Para esa URI hay que obtener el ID con
            // getTreeDocumentId(); para las carpetas hijas usamos
            // getDocumentId(), porque son Document URI.
            String id;

            if (DocumentsContract.isTreeUri(carpeta)) {
                id = DocumentsContract.getTreeDocumentId(carpeta);
            } else {
                id = DocumentsContract.getDocumentId(carpeta);
            }

            Uri children =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                            carpeta,
                            id
                    );

            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
            };

            try (Cursor cursor =
                         getContentResolver().query(
                                 children,
                                 projection,
                                 null,
                                 null,
                                 null)) {

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        elementos.add(
                                DocumentsContract.buildDocumentUriUsingTree(
                                        carpeta,
                                        cursor.getString(0)
                                )
                        );
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return elementos;
    }

    private String obtenerNombreElemento(Uri uri) {
        try (Cursor cursor =
                     getContentResolver().query(
                             uri,
                             new String[]{
                                     DocumentsContract.Document.COLUMN_DISPLAY_NAME
                             },
                             null,
                             null,
                             null)) {

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
        }

        return "Sin nombre";
    }

    private boolean esCarpeta(Uri uri) {
        try (Cursor cursor =
                     getContentResolver().query(
                             uri,
                             new String[]{
                                     DocumentsContract.Document.COLUMN_MIME_TYPE
                             },
                             null,
                             null,
                             null)) {

            if (cursor != null && cursor.moveToFirst()) {
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(
                        cursor.getString(0)
                );
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean esAudio(Uri uri) {
        String nombre =
                obtenerNombreElemento(uri).toLowerCase(Locale.ROOT);

        return nombre.endsWith(".mp3") ||
                nombre.endsWith(".m4a") ||
                nombre.endsWith(".flac") ||
                nombre.endsWith(".wav") ||
                nombre.endsWith(".ogg") ||
                nombre.endsWith(".aac") ||
                nombre.endsWith(".opus") ||
                nombre.endsWith(".wma") ||
                nombre.endsWith(".aiff") ||
                nombre.endsWith(".aif");
    }

    private void escanearRecursivamente(
            Uri carpeta,
            ArrayList<Uri> destino) {

        for (Uri elemento : obtenerElementosCarpeta(carpeta)) {
            if (esCarpeta(elemento)) {
                escanearRecursivamente(elemento, destino);
            } else if (esAudio(elemento)) {
                destino.add(elemento);
            }
        }
    }

    private void cargarCarpetaCompleta(Uri carpeta) {
        listaOriginal.clear();
        currentAudioList.clear();
        currentAudioIndex = -1;

        escanearRecursivamente(carpeta, listaOriginal);

        Comparator<Uri> cmp =
                (a, b) -> obtenerNombreElemento(a)
                        .compareToIgnoreCase(
                                obtenerNombreElemento(b)
                        );

        Collections.sort(listaOriginal, cmp);
        currentAudioList.addAll(listaOriginal);

        if (modoAleatorio && !currentAudioList.isEmpty()) {
            Collections.shuffle(currentAudioList, random);
        }

        if (!currentAudioList.isEmpty()) {
            currentAudioIndex = 0;
            reproducirAudio(currentAudioList.get(0));
        }
    }



    private void mostrarCarpeta(Uri carpeta) {
        currentFolderUri = carpeta;

        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(dp(12), dp(16), dp(12), dp(20));
        lista.setBackgroundColor(fondo);

        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);

        Button volver = new Button(this);
        volver.setText("‹");
        volver.setTextSize(22);

        volver.setOnClickListener(v -> {
            if (!folderStack.isEmpty()) {
                mostrarCarpeta(
                        folderStack.remove(folderStack.size() - 1)
                );
            } else {
                mostrarReproductor();
            }
        });

        barra.addView(
                volver,
                new LinearLayout.LayoutParams(dp(55), dp(55))
        );

        TextView nombre = text(
                obtenerNombreElemento(carpeta),
                20,
                blanco
        );

        nombre.setGravity(
                Gravity.CENTER_VERTICAL | Gravity.LEFT
        );

        barra.addView(
                nombre,
                new LinearLayout.LayoutParams(0, dp(55), 1)
        );

        Button cargar = new Button(this);
        cargar.setText("▶");
        cargar.setOnClickListener(v -> cargarCarpetaCompleta(carpeta));

        barra.addView(
                cargar,
                new LinearLayout.LayoutParams(dp(55), dp(55))
        );

        Button cambiar = new Button(this);
        cambiar.setText("📂");
        cambiar.setOnClickListener(
                v -> elegirCarpeta(REQUEST_FOLDER)
        );

        barra.addView(
                cambiar,
                new LinearLayout.LayoutParams(dp(55), dp(55))
        );

        lista.addView(barra);

        ArrayList<Uri> carpetas = new ArrayList<>();
        ArrayList<Uri> audios = new ArrayList<>();

        for (Uri elemento : obtenerElementosCarpeta(carpeta)) {
            if (esCarpeta(elemento)) {
                carpetas.add(elemento);
            } else if (esAudio(elemento)) {
                audios.add(elemento);
            }
        }

        Comparator<Uri> cmp =
                (a, b) -> obtenerNombreElemento(a)
                        .compareToIgnoreCase(
                                obtenerNombreElemento(b)
                        );

        Collections.sort(carpetas, cmp);
        Collections.sort(audios, cmp);

        for (Uri u : carpetas) {
            TextView item = new TextView(this);

            item.setText(
                    "📁  " + obtenerNombreElemento(u)
            );

            item.setTextSize(17);
            item.setTextColor(blanco);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(
                    dp(12), dp(8), dp(8), dp(8)
            );

            item.setOnClickListener(v -> {
                folderStack.add(carpeta);
                mostrarCarpeta(u);
            });

            lista.addView(
                    item,
                    new LinearLayout.LayoutParams(-1, dp(52))
            );
        }

        for (Uri u : audios) {
            TextView item = new TextView(this);

            item.setText(
                    "🎵  " + obtenerNombreElemento(u)
            );

            item.setTextSize(16);
            item.setTextColor(Color.LTGRAY);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(
                    dp(12), dp(8), dp(8), dp(8)
            );

            item.setOnClickListener(v -> {
                listaOriginal.clear();
                listaOriginal.add(u);

                currentAudioList.clear();
                currentAudioList.add(u);

                currentAudioIndex = 0;
                reproducirAudio(u);
            });

            lista.addView(
                    item,
                    new LinearLayout.LayoutParams(-1, dp(52))
            );
        }

        if (carpetas.isEmpty() && audios.isEmpty()) {
            lista.addView(
                    text(
                            "Esta carpeta no contiene música.",
                            17,
                            gris
                    ),
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(60)
                    )
            );
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(fondo);
        scroll.addView(lista);

        setContentView(scroll);
    }

    private void mostrarReproductor() {
        folderStack.clear();
        setContentView(root);
    }

    private void reproducirAudio(Uri audio) {
        try {
            if (reproductor != null) {
                reproductor.release();
            }

            reproductor = new MediaPlayer();
            reproductor.setDataSource(this, audio);

            reproductor.setOnPreparedListener(mp -> {
                progreso.setMax(mp.getDuration());
                progreso.setProgress(0);

                mp.start();
                play.setText("⏸");

                handler.removeCallbacks(actualizarProgreso);
                handler.post(actualizarProgreso);
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

    private void alternarReproduccion() {
        if (reproductor == null) {
            return;
        }

        try {
            if (reproductor.isPlaying()) {
                reproductor.pause();
                play.setText("▶");
            } else {
                reproductor.start();
                play.setText("⏸");
                handler.post(actualizarProgreso);
            }
        } catch (Exception ignored) {
        }
    }

    private void reproducirAnterior() {
        if (reproductor != null) {
            try {
                if (reproductor.getCurrentPosition() > 3000) {
                    reproductor.seekTo(0);
                    progreso.setProgress(0);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        if (currentAudioList.size() <= 1 ||
                currentAudioIndex <= 0) {
            if (reproductor != null) {
                try {
                    reproductor.seekTo(0);
                    progreso.setProgress(0);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        currentAudioIndex--;
        reproducirAudio(
                currentAudioList.get(currentAudioIndex)
        );
    }

    private void reproducirSiguiente() {
        if (currentAudioList.isEmpty()) {
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
        }
    }



    private void reproducirSiguienteAutomatico() {
        if (currentAudioList.isEmpty()) {
            play.setText("▶");
            return;
        }

        if (modoRepeticion == 1) {
            if (currentAudioIndex < 0) {
                currentAudioIndex = 0;
            }
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
        }
    }



    private void actualizarDatosAudio(Uri audio) {
        String nombre = obtenerNombreElemento(audio);

        if (nombre == null ||
                nombre.trim().isEmpty()) {
            nombre = "Audio";
        }

        int punto = nombre.lastIndexOf(".");

        if (punto > 0) {
            nombre = nombre.substring(0, punto);
        }

        String[] info = obtenerInfoAudio(audio);

        String titulo = info[0];
        String artistaNombre = info[1];

        if (titulo == null ||
                titulo.trim().isEmpty()) {
            titulo = nombre;
        }

        if (artistaNombre == null ||
                artistaNombre.trim().isEmpty()) {
            artistaNombre = "Artista desconocido";
        }

        cancion.setText(titulo);
        artista.setText(artistaNombre);
    }

    private String[] obtenerInfoAudio(Uri uri) {
        String titulo = null;
        String artistaNombre = null;

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        try {
            retriever.setDataSource(this, uri);

            titulo = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_TITLE
            );

            artistaNombre = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST
            );

        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }

        return new String[]{titulo, artistaNombre};
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri seleccion = data.getData();

        if (requestCode == REQUEST_AUDIO) {
            currentAudioList.clear();
            currentAudioList.add(seleccion);
            currentAudioIndex = 0;
            reproducirAudio(seleccion);
            return;
        }

        if (requestCode == REQUEST_FOLDER ||
                requestCode == REQUEST_FOLDER_FROM_OPEN) {

            guardarCarpeta(seleccion);
            folderStack.clear();

            if (requestCode == REQUEST_FOLDER_FROM_OPEN) {
                cargarCarpetaCompleta(seleccion);
            } else {
                mostrarCarpeta(seleccion);
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(actualizarProgreso);

        if (reproductor != null) {
            try {
                reproductor.release();
            } catch (Exception ignored) {
            }

            reproductor = null;
        }

        super.onDestroy();
    }
}
