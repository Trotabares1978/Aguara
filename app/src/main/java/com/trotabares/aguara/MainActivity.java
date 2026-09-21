package com.trotabares.aguara;

import android.app.Activity;
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

public class MainActivity extends Activity {
    private static final int REQUEST_FOLDER = 1001;
    private static final int REQUEST_AUDIO = 1002;
    private static final int REQUEST_FOLDER_FROM_OPEN = 1003;

    private Uri treeUri;
    private Uri currentFolderUri;
    private final ArrayList<Uri> folderStack = new ArrayList<>();
    private final ArrayList<Uri> currentAudioList = new ArrayList<>();
    private int currentAudioIndex = -1;

    private TextView cancion;
    private TextView artista;
    private Button play;
    private SeekBar progreso;
    private MediaPlayer reproductor;
    private final Handler handler = new Handler();

    private final Runnable actualizarProgreso = new Runnable() {
        @Override public void run() {
            if (reproductor != null && reproductor.isPlaying()) {
                progreso.setProgress(reproductor.getCurrentPosition());
                handler.postDelayed(this, 500);
            }
        }
    };

    private LinearLayout root;
    private int fondo = Color.rgb(18,18,18);
    private int blanco = Color.WHITE;
    private int gris = Color.rgb(185,185,185);
    private int dorado = Color.rgb(224,166,74);

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
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
        root.addView(construirReproductor(),
                new LinearLayout.LayoutParams(-1,-1));
        setContentView(root);
    }

    private LinearLayout construirReproductor() {
        LinearLayout vista = new LinearLayout(this);
        vista.setOrientation(LinearLayout.VERTICAL);
        vista.setGravity(Gravity.CENTER_HORIZONTAL);
        vista.setPadding(dp(22),dp(28),dp(22),dp(20));
        vista.setBackgroundColor(fondo);

        TextView titulo = text("AGUARÁ",30,blanco);
        titulo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        vista.addView(titulo,new LinearLayout.LayoutParams(-1,dp(50)));

        vista.addView(text("REPRODUCTOR MUSICAL",12,gris),
                new LinearLayout.LayoutParams(-1,dp(30)));

        ImageView portada = new ImageView(this);
        portada.setImageResource(R.drawable.aguara_cover);
        portada.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vista.addView(portada,new LinearLayout.LayoutParams(dp(280),dp(280)));

        cancion = text("Ninguna canción",23,blanco);
        cancion.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,dp(45));
        cp.topMargin = dp(18);
        vista.addView(cancion,cp);

        artista = text("Seleccioná música para comenzar",15,gris);
        vista.addView(artista,new LinearLayout.LayoutParams(-1,dp(30)));

        progreso = new SeekBar(this);
        progreso.setMax(100);
        progreso.setProgressTintList(
                android.content.res.ColorStateList.valueOf(dorado));
        progreso.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(
                            SeekBar s,int p,boolean fromUser) {
                        if(fromUser && reproductor != null) {
                            try { reproductor.seekTo(p); } catch(Exception ignored) {}
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar s) {}
                    @Override public void onStopTrackingTouch(SeekBar s) {}
                });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,dp(45));
        sp.topMargin = dp(10);
        vista.addView(progreso,sp);

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

        controles.addView(anterior,new LinearLayout.LayoutParams(dp(75),dp(60)));
        controles.addView(play,new LinearLayout.LayoutParams(dp(90),dp(60)));
        controles.addView(siguiente,new LinearLayout.LayoutParams(dp(75),dp(60)));
        vista.addView(controles);

        Button biblioteca = new Button(this);
        biblioteca.setText("🎵  BIBLIOTECA");
        biblioteca.setTextColor(blanco);
        biblioteca.setOnClickListener(v -> abrirBiblioteca());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1,dp(55));
        bp.topMargin = dp(12);
        vista.addView(biblioteca,bp);

        Button abrir = new Button(this);
        abrir.setText("📂  ABRIR");
        abrir.setTextColor(blanco);
        abrir.setOnClickListener(v -> mostrarDialogoAbrir());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1,dp(55));
        ap.topMargin = dp(8);
        vista.addView(abrir,ap);

        return vista;
    }

    private void abrirBiblioteca() {
        if(treeUri != null) {
            folderStack.clear();
            mostrarCarpeta(treeUri);
        } else {
            elegirCarpeta(REQUEST_FOLDER);
        }
    }

    private void mostrarDialogoAbrir() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("¿Qué querés abrir?")
                .setItems(new String[]{"🎵 Archivo de audio","📂 Carpeta"},
                        (dialog,which) -> {
                            if(which == 0) {
                                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                i.setType("audio/*");
                                i.addCategory(Intent.CATEGORY_OPENABLE);
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                                startActivityForResult(i,REQUEST_AUDIO);
                            } else {
                                elegirCarpeta(REQUEST_FOLDER_FROM_OPEN);
                            }
                        }).show();
    }

    private void elegirCarpeta(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,requestCode);
    }

    private void guardarCarpeta(Uri carpeta) {
        treeUri = carpeta;
        currentFolderUri = carpeta;
        try {
            getContentResolver().takePersistableUriPermission(
                    carpeta,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) {}
        getSharedPreferences("aguara",MODE_PRIVATE).edit()
                .putString("music_tree_uri",carpeta.toString()).apply();
    }

    private ArrayList<Uri> obtenerElementosCarpeta(Uri carpeta) {
        ArrayList<Uri> r = new ArrayList<>();
        try {
            String id = DocumentsContract.getDocumentId(carpeta);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(carpeta,id);
            String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID};
            try(Cursor c=getContentResolver().query(children,projection,null,null,null)) {
                if(c!=null) while(c.moveToNext()) {
                    r.add(DocumentsContract.buildDocumentUriUsingTree(
                            carpeta,c.getString(0)));
                }
            }
        } catch(Exception ignored) {}
        return r;
    }

    private String obtenerNombreElemento(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,null,null)) {
            if(c!=null && c.moveToFirst()) return c.getString(0);
        } catch(Exception ignored) {}
        return "Sin nombre";
    }

    private boolean esCarpeta(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},
                null,null,null)) {
            if(c!=null && c.moveToFirst())
                return DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0));
        } catch(Exception ignored) {}
        return false;
    }

    private boolean esAudio(Uri uri) {
        String n=obtenerNombreElemento(uri).toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3")||n.endsWith(".m4a")||n.endsWith(".flac")||
                n.endsWith(".wav")||n.endsWith(".ogg")||n.endsWith(".aac")||
                n.endsWith(".opus")||n.endsWith(".wma")||n.endsWith(".aiff")||
                n.endsWith(".aif");
    }

    private void mostrarCarpeta(Uri carpeta) {
        currentFolderUri=carpeta;

        LinearLayout lista=new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(dp(12),dp(16),dp(12),dp(20));
        lista.setBackgroundColor(fondo);

        LinearLayout barra=new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);

        Button volver=new Button(this);
        volver.setText("‹");
        volver.setTextSize(22);
        volver.setOnClickListener(v -> {
            if(!folderStack.isEmpty()) {
                mostrarCarpeta(folderStack.remove(folderStack.size()-1));
            } else {
                mostrarReproductor();
            }
        });
        barra.addView(volver,new LinearLayout.LayoutParams(dp(55),dp(55)));

        TextView nombre=text(obtenerNombreElemento(carpeta),20,blanco);
        nombre.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        barra.addView(nombre,new LinearLayout.LayoutParams(0,dp(55),1));

        Button cambiar=new Button(this);
        cambiar.setText("📂");
        cambiar.setOnClickListener(v -> elegirCarpeta(REQUEST_FOLDER));
        barra.addView(cambiar,new LinearLayout.LayoutParams(dp(55),dp(55)));
        lista.addView(barra);

        ArrayList<Uri> carpetas=new ArrayList<>();
        currentAudioList.clear();

        for(Uri u:obtenerElementosCarpeta(carpeta)) {
            if(esCarpeta(u)) carpetas.add(u);
            else if(esAudio(u)) currentAudioList.add(u);
        }

        Comparator<Uri> cmp=(a,b)->obtenerNombreElemento(a)
                .compareToIgnoreCase(obtenerNombreElemento(b));
        Collections.sort(carpetas,cmp);
        Collections.sort(currentAudioList,cmp);

        for(Uri u:carpetas) {
            TextView item=new TextView(this);
            item.setText("📁  "+obtenerNombreElemento(u));
            item.setTextSize(17); item.setTextColor(blanco);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12),dp(8),dp(8),dp(8));
            item.setOnClickListener(v->{folderStack.add(carpeta);mostrarCarpeta(u);});
            lista.addView(item,new LinearLayout.LayoutParams(-1,dp(52)));
        }

        for(int i=0;i<currentAudioList.size();i++) {
            final int index=i; Uri u=currentAudioList.get(i);
            TextView item=new TextView(this);
            item.setText("🎵  "+obtenerNombreElemento(u));
            item.setTextSize(16); item.setTextColor(Color.LTGRAY);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12),dp(8),dp(8),dp(8));
            item.setOnClickListener(v->{
                currentAudioIndex=index;
                reproducirAudio(u);
            });
            lista.addView(item,new LinearLayout.LayoutParams(-1,dp(52)));
        }

        if(carpetas.isEmpty() && currentAudioList.isEmpty()) {
            lista.addView(text("Esta carpeta no contiene música.",17,gris),
                    new LinearLayout.LayoutParams(-1,dp(60)));
        }

        ScrollView scroll=new ScrollView(this);
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
            if(reproductor!=null) reproductor.release();
            reproductor=new MediaPlayer();
            reproductor.setDataSource(this,audio);
            reproductor.setOnPreparedListener(mp->{
                progreso.setMax(mp.getDuration());
                progreso.setProgress(0);
                mp.start();
                play.setText("⏸");
                handler.removeCallbacks(actualizarProgreso);
                handler.post(actualizarProgreso);
            });
            reproductor.setOnCompletionListener(mp->{
                progreso.setProgress(0);
                play.setText("▶");
                reproducirSiguiente();
            });
            reproductor.setOnErrorListener((mp,what,extra)->{
                play.setText("▶"); return false;
            });
            actualizarDatosAudio(audio);
            setContentView(root);
            reproductor.prepareAsync();
        } catch(Exception e) {
            if(reproductor!=null) { reproductor.release(); reproductor=null; }
            play.setText("▶");
        }
    }

    private void alternarReproduccion() {
        if(reproductor==null) return;
        try {
            if(reproductor.isPlaying()) {
                reproductor.pause(); play.setText("▶");
            } else {
                reproductor.start(); play.setText("⏸");
                handler.post(actualizarProgreso);
            }
        } catch(Exception ignored) {}
    }

    private void reproducirAnterior() {
        if(currentAudioList.isEmpty() || currentAudioIndex<=0) return;
        currentAudioIndex--;
        reproducirAudio(currentAudioList.get(currentAudioIndex));
    }

    private void reproducirSiguiente() {
        if(currentAudioList.isEmpty()) return;
        if(currentAudioIndex+1>=currentAudioList.size()) {
            currentAudioIndex=-1; play.setText("▶"); return;
        }
        currentAudioIndex++;
        reproducirAudio(currentAudioList.get(currentAudioIndex));
    }

    private void actualizarDatosAudio(Uri audio) {
        String nombre=obtenerNombreElemento(audio);
        if(nombre==null || nombre.trim().isEmpty()) nombre="Audio";
        int p=nombre.lastIndexOf(".");
        if(p>0) nombre=nombre.substring(0,p);

        String[] info=obtenerInfoAudio(audio);
        String titulo=info[0], artistaNombre=info[1];
        if(titulo==null || titulo.trim().isEmpty()) titulo=nombre;
        if(artistaNombre==null || artistaNombre.trim().isEmpty())
            artistaNombre="Artista desconocido";
        cancion.setText(titulo);
        artista.setText(artistaNombre);
    }

    private String[] obtenerInfoAudio(Uri uri) {
        String titulo=null, artistaNombre=null;
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try {
            r.setDataSource(this,uri);
            titulo=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artistaNombre=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        } catch(Exception ignored) {
        } finally {
            try { r.release(); } catch(Exception ignored) {}
        }
        return new String[]{titulo,artistaNombre};
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;

        Uri seleccion=data.getData();

        if(requestCode==REQUEST_AUDIO) {
            currentAudioList.clear();
            currentAudioIndex=-1;
            reproducirAudio(seleccion);
            return;
        }

        if(requestCode==REQUEST_FOLDER || requestCode==REQUEST_FOLDER_FROM_OPEN) {
            guardarCarpeta(seleccion);
            folderStack.clear();
            mostrarCarpeta(seleccion);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(actualizarProgreso);
        if(reproductor!=null) {
            try { reproductor.release(); } catch(Exception ignored) {}
            reproductor=null;
        }
        super.onDestroy();
    }
}
