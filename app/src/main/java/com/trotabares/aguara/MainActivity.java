package com.trotabares.aguara;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

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

        int fondo = Color.rgb(18, 18, 18);
        int blanco = Color.WHITE;
        int gris = Color.rgb(185, 185, 185);
        int dorado = Color.rgb(224, 166, 74);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(20));
        root.setBackgroundColor(fondo);

        TextView titulo = text("AGUARÁ", 30, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        root.addView(titulo, new LinearLayout.LayoutParams(
                -1, dp(50)));

        TextView subtitulo = text("REPRODUCTOR MUSICAL", 12, gris);
        root.addView(subtitulo, new LinearLayout.LayoutParams(
                -1, dp(30)));

        TextView portada = text("🐺", 82, blanco);
        portada.setBackgroundColor(Color.rgb(32, 32, 32));
        root.addView(portada, new LinearLayout.LayoutParams(
                dp(280), dp(280)));

        TextView cancion = text("Ninguna canción", 23, blanco);
        cancion.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams cancionParams =
                new LinearLayout.LayoutParams(-1, dp(45));
        cancionParams.topMargin = dp(18);
        root.addView(cancion, cancionParams);

        TextView artista = text("Seleccioná música para comenzar", 15, gris);
        root.addView(artista, new LinearLayout.LayoutParams(
                -1, dp(30)));

        SeekBar progreso = new SeekBar(this);
        progreso.setProgress(0);
        progreso.setMax(100);
        progreso.setProgressTintList(
                android.content.res.ColorStateList.valueOf(dorado));

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, dp(45));
        seekParams.topMargin = dp(10);
        root.addView(progreso, seekParams);

        LinearLayout controles = new LinearLayout(this);
        controles.setOrientation(LinearLayout.HORIZONTAL);
        controles.setGravity(Gravity.CENTER);

        Button anterior = new Button(this);
        anterior.setText("⏮");

        Button play = new Button(this);
        play.setText("▶");

        Button siguiente = new Button(this);
        siguiente.setText("⏭");

        controles.addView(anterior, new LinearLayout.LayoutParams(
                dp(75), dp(60)));

        controles.addView(play, new LinearLayout.LayoutParams(
                dp(90), dp(60)));

        controles.addView(siguiente, new LinearLayout.LayoutParams(
                dp(75), dp(60)));

        root.addView(controles);

        Button biblioteca = new Button(this);
        biblioteca.setText("🎵  BIBLIOTECA");
        biblioteca.setTextColor(blanco);

        LinearLayout.LayoutParams bibliotecaParams =
                new LinearLayout.LayoutParams(-1, dp(55));
        bibliotecaParams.topMargin = dp(12);
        root.addView(biblioteca, bibliotecaParams);

        setContentView(root);
    }
}
