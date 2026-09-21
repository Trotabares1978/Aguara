package com.trotabares.aguara;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class EqualizerActivity extends Activity {

    private AguaraPcmPlayer player;
    private LinearLayout bandsLayout;
    private LinearLayout advancedLayout;

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

        player = AguaraPcmPlayer.getActivePlayer();

        if (player == null) {
            finish();
            return;
        }

        construirInterfaz();
    }

    private void construirInterfaz() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(14));
        root.setBackgroundColor(fondo);

        TextView titulo = text("AUDIO AGUARÁ", 26, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(titulo, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout modos = new LinearLayout(this);
        modos.setGravity(Gravity.CENTER);

        Button simple = new Button(this);
        simple.setText("MODO SIMPLE");
        simple.setTextColor(blanco);
        simple.setOnClickListener(v -> mostrarModoSimple());
        modos.addView(simple, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button avanzado = new Button(this);
        avanzado.setText("MODO AVANZADO");
        avanzado.setTextColor(blanco);
        avanzado.setOnClickListener(v -> mostrarModoAvanzado());
        modos.addView(avanzado, new LinearLayout.LayoutParams(0, dp(48), 1f));

        root.addView(modos, new LinearLayout.LayoutParams(-1, dp(54)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(0, dp(6), 0, dp(8));

        bandsLayout = construirEcualizador();
        contenido.addView(bandsLayout);

        advancedLayout = construirAvanzado();
        contenido.addView(advancedLayout);

        scroll.addView(contenido);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button cerrar = new Button(this);
        cerrar.setText("CERRAR");
        cerrar.setTextColor(blanco);
        cerrar.setOnClickListener(v -> finish());
        root.addView(cerrar, new LinearLayout.LayoutParams(-1, dp(52)));

        setContentView(root);
        mostrarModoSimple();
    }

    private LinearLayout construirEcualizador() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView subtitulo = text("ECUALIZADOR · 10 BANDAS", 16, blanco);
        subtitulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(subtitulo, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout presets = new LinearLayout(this);
        presets.setGravity(Gravity.CENTER);

        agregarPreset(presets, "PLANO", 0);
        agregarPreset(presets, "BAJOS", 1);
        agregarPreset(presets, "VOZ", 2);
        agregarPreset(presets, "ROCK", 3);

        panel.addView(presets, new LinearLayout.LayoutParams(-1, dp(52)));

        String[] labels = {
                "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
                "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
        };

        for (int band = 0; band < player.getBandCount(); band++) {
            final int banda = band;

            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = text(labels[Math.min(band, labels.length - 1)], 14, blanco);
            fila.addView(label, new LinearLayout.LayoutParams(dp(58), dp(48)));

            SeekBar seek = new SeekBar(this);
            seek.setMax(240);
            seek.setProgress(Math.round(player.getBandGain(band) * 10f) + 120);
            seek.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(dorado)
            );

            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && player != null) {
                        player.setBandGain(banda, (progress - 120) / 10f);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });

            fila.addView(seek, new LinearLayout.LayoutParams(0, dp(48), 1f));
            panel.addView(fila);
        }

        return panel;
    }

    private LinearLayout construirAvanzado() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(14), 0, dp(8));
        panel.setVisibility(View.GONE);

        TextView titulo = text("AUDIO LAB", 20, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(titulo, new LinearLayout.LayoutParams(-1, dp(42)));

        agregarControl(panel, "PREAMP", "-12 dB", "+12 dB", -12f, 12f,
                player.getPreampDb(), value -> player.setPreampDb(value));

        Switch limiter = new Switch(this);
        limiter.setText("LIMITADOR DE SALIDA");
        limiter.setTextColor(blanco);
        limiter.setTextSize(15);
        limiter.setGravity(Gravity.CENTER_VERTICAL);
        limiter.setChecked(player.isLimiterEnabled());
        limiter.setOnCheckedChangeListener((buttonView, isChecked) ->
                player.setLimiterEnabled(isChecked));
        panel.addView(limiter, new LinearLayout.LayoutParams(-1, dp(52)));

        agregarControl(panel, "BASS BOOST", "0", "12 dB", 0f, 12f,
                player.getBassBoost(), value -> player.setBassBoost(value));

        agregarControl(panel, "VÁLVULA · DRIVE", "Limpio", "12", 0f, 12f,
                player.getTubeDrive(), value -> player.setTubeDrive(value));

        agregarControl(panel, "VINILO · AMBIENTE", "0", "100", 0f, 100f,
                player.getVinylAmount(), value -> player.setVinylAmount(value));

        TextView nota = text(
                "Válvula y vinilo son coloraciones DSP. El limitador protege la salida frente a picos.",
                12, gris);
        nota.setGravity(Gravity.CENTER);
        nota.setPadding(0, dp(8), 0, dp(8));
        panel.addView(nota, new LinearLayout.LayoutParams(-1, dp(54)));

        return panel;
    }

    private interface FloatSetter {
        void set(float value);
    }

    private void agregarControl(
            LinearLayout panel,
            String nombre,
            String minimo,
            String maximo,
            float min,
            float max,
            float valor,
            FloatSetter setter) {

        TextView label = text(nombre + "   " + formatear(valor), 15, blanco);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(label, new LinearLayout.LayoutParams(-1, dp(30)));

        SeekBar seek = new SeekBar(this);
        int escala = 100;
        seek.setMax(Math.round((max - min) * escala));
        seek.setProgress(Math.round((valor - min) * escala));
        seek.setProgressTintList(
                android.content.res.ColorStateList.valueOf(dorado)
        );

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float value = min + progress / (float) escala;
                label.setText(nombre + "   " + formatear(value));
                if (fromUser) setter.set(value);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        panel.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView range = text(minimo + "                                      " + maximo, 11, gris);
        panel.addView(range, new LinearLayout.LayoutParams(-1, dp(24)));
    }

    private String formatear(float value) {
        if (Math.abs(value) < 0.05f) return "0";
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private void mostrarModoSimple() {
        bandsLayout.setVisibility(View.VISIBLE);
        advancedLayout.setVisibility(View.GONE);
    }

    private void mostrarModoAvanzado() {
        bandsLayout.setVisibility(View.VISIBLE);
        advancedLayout.setVisibility(View.VISIBLE);
    }

    private void agregarPreset(LinearLayout contenedor, String nombre, int tipo) {
        Button boton = new Button(this);
        boton.setText(nombre);
        boton.setTextSize(11);
        boton.setOnClickListener(v -> aplicarPreset(tipo));

        contenedor.addView(
                boton,
                new LinearLayout.LayoutParams(0, dp(46), 1f)
        );
    }

    private void aplicarPreset(int tipo) {
        if (player == null) return;

        float[][] presets = {
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {4, 5, 4, 2, 0, -1, -1, -1, -2, -2},
                {-2, -1, 0, 1, 3, 4, 3, 1, 0, -1},
                {4, 3, 1, -1, -1, 1, 3, 4, 4, 3}
        };

        float[] valores = presets[Math.max(0, Math.min(tipo, presets.length - 1))];

        for (int i = 0; i < player.getBandCount(); i++) {
            player.setBandGain(i, valores[Math.min(i, valores.length - 1)]);
        }

        refrescarSliders();
    }

    private void refrescarSliders() {
        if (bandsLayout == null || player == null) return;

        int filaIndice = 2;
        for (int i = 0; i < player.getBandCount(); i++) {
            if (filaIndice >= bandsLayout.getChildCount()) break;
            LinearLayout fila = (LinearLayout) bandsLayout.getChildAt(filaIndice++);
            if (fila.getChildCount() < 2) continue;

            SeekBar seek = (SeekBar) fila.getChildAt(1);
            seek.setProgress(Math.round(player.getBandGain(i) * 10f) + 120);
        }
    }
}
