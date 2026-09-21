package com.trotabares.aguara;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class EqualizerActivity extends Activity {

    private AguaraPcmPlayer player;
    private LinearLayout bandsLayout;

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
        root.setPadding(dp(18), dp(24), dp(18), dp(18));
        root.setBackgroundColor(fondo);

        TextView titulo = text("ECUALIZADOR AGUARÁ", 26, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(titulo, new LinearLayout.LayoutParams(-1, dp(52)));

        root.addView(
                text("DSP propio · 10 bandas · no depende del ecualizador del teléfono", 13, gris),
                new LinearLayout.LayoutParams(-1, dp(44))
        );

        LinearLayout presets = new LinearLayout(this);
        presets.setGravity(Gravity.CENTER);

        agregarPreset(presets, "PLANO", 0);
        agregarPreset(presets, "BAJOS", 1);
        agregarPreset(presets, "VOZ", 2);
        agregarPreset(presets, "ROCK", 3);

        root.addView(presets, new LinearLayout.LayoutParams(-1, dp(54)));

        bandsLayout = new LinearLayout(this);
        bandsLayout.setOrientation(LinearLayout.VERTICAL);

        String[] labels = {"31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"};

        for (int band = 0; band < player.getBandCount(); band++) {
            final int banda = band;

            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = text(labels[Math.min(band, labels.length - 1)], 14, blanco);
            fila.addView(label, new LinearLayout.LayoutParams(dp(58), dp(52)));

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

            fila.addView(seek, new LinearLayout.LayoutParams(0, dp(52), 1f));
            bandsLayout.addView(fila);
        }

        root.addView(bandsLayout, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button cerrar = new Button(this);
        cerrar.setText("CERRAR");
        cerrar.setTextColor(blanco);
        cerrar.setOnClickListener(v -> finish());

        root.addView(cerrar, new LinearLayout.LayoutParams(-1, dp(54)));

        setContentView(root);
    }

    private void agregarPreset(LinearLayout contenedor, String nombre, int tipo) {
        Button boton = new Button(this);
        boton.setText(nombre);
        boton.setTextSize(11);
        boton.setOnClickListener(v -> aplicarPreset(tipo));

        contenedor.addView(
                boton,
                new LinearLayout.LayoutParams(0, dp(48), 1f)
        );
    }

    private void aplicarPreset(int tipo) {
        if (player == null) return;

        float[][] presets = {
                {0, 0, 0, 0, 0},
                {6, 4, 1, -1, -2},
                {-2, -1, 2, 5, 3},
                {5, 2, -1, 3, 5}
        };

        float[] valores = presets[Math.max(0, Math.min(tipo, presets.length - 1))];

        for (int i = 0; i < player.getBandCount(); i++) {
            player.setBandGain(i, valores[Math.min(i, valores.length - 1)]);
        }

        refrescarSliders();
    }

    private void refrescarSliders() {
        if (bandsLayout == null || player == null) return;

        for (int i = 0; i < bandsLayout.getChildCount(); i++) {
            LinearLayout fila = (LinearLayout) bandsLayout.getChildAt(i);
            if (fila.getChildCount() < 2) continue;

            SeekBar seek = (SeekBar) fila.getChildAt(1);
            seek.setProgress(Math.round(player.getBandGain(i) * 10f) + 120);
        }
    }
}
