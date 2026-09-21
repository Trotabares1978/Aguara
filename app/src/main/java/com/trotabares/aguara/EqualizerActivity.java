package com.trotabares.aguara;

import android.app.Activity;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

public class EqualizerActivity extends Activity {

    private Equalizer equalizer;
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

        int sessionId = getIntent().getIntExtra("audio_session_id", 0);
        if (sessionId == 0) {
            finish();
            return;
        }

        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(true);
            construirInterfaz();
        } catch (Exception e) {
            if (equalizer != null) {
                try {
                    equalizer.release();
                } catch (Exception ignored) {
                }
                equalizer = null;
            }
            finish();
        }
    }

    private void construirInterfaz() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(18));
        root.setBackgroundColor(fondo);

        TextView titulo = text("ECUALIZADOR", 26, blanco);
        titulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(titulo, new LinearLayout.LayoutParams(-1, dp(52)));

        root.addView(
                text("Ajuste real de las bandas de audio de AGUARÁ", 13, gris),
                new LinearLayout.LayoutParams(-1, dp(38))
        );

        LinearLayout presets = new LinearLayout(this);
        presets.setGravity(Gravity.CENTER);

        agregarPreset(presets, "PLANO", 0);
        agregarPreset(presets, "BAJOS", 1);
        agregarPreset(presets, "VOZ", 2);
        agregarPreset(presets, "ROCK", 3);

        root.addView(
                presets,
                new LinearLayout.LayoutParams(-1, dp(54))
        );

        bandsLayout = new LinearLayout(this);
        bandsLayout.setOrientation(LinearLayout.VERTICAL);

        short min = equalizer.getBandLevelRange()[0];
        short max = equalizer.getBandLevelRange()[1];

        for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
            int frequency = equalizer.getCenterFreq(band) / 1000;

            TextView label = text(
                    formatearFrecuencia(frequency),
                    14,
                    blanco
            );

            LinearLayout fila = new LinearLayout(this);
            fila.setGravity(Gravity.CENTER_VERTICAL);

            fila.addView(
                    label,
                    new LinearLayout.LayoutParams(dp(58), dp(52))
            );

            SeekBar seek = new SeekBar(this);
            seek.setMax(max - min);
            seek.setProgress(equalizer.getBandLevel(band) - min);
            seek.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(dorado)
            );

            final short banda = band;
            seek.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(
                                SeekBar seekBar,
                                int progress,
                                boolean fromUser) {
                            if (!fromUser || equalizer == null) {
                                return;
                            }

                            try {
                                short level = (short) (min + progress);
                                equalizer.setBandLevel(banda, level);
                            } catch (Exception ignored) {
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

            fila.addView(
                    seek,
                    new LinearLayout.LayoutParams(0, dp(52), 1f)
            );

            bandsLayout.addView(fila);
        }

        root.addView(
                bandsLayout,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        Button cerrar = new Button(this);
        cerrar.setText("CERRAR");
        cerrar.setTextColor(blanco);
        cerrar.setOnClickListener(v -> finish());

        root.addView(
                cerrar,
                new LinearLayout.LayoutParams(-1, dp(54))
        );

        setContentView(root);
    }

    private void agregarPreset(
            LinearLayout contenedor,
            String nombre,
            int tipo) {

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
        if (equalizer == null) {
            return;
        }

        short min = equalizer.getBandLevelRange()[0];
        short max = equalizer.getBandLevelRange()[1];
        short bandas = equalizer.getNumberOfBands();

        for (short band = 0; band < bandas; band++) {
            int valor;

            switch (tipo) {
                case 1:
                    valor = new int[]{60, 45, 20, -10, -20}[Math.min(band, (short) 4)];
                    break;
                case 2:
                    valor = new int[]{-20, -10, 20, 45, 35}[Math.min(band, (short) 4)];
                    break;
                case 3:
                    valor = new int[]{45, 20, -10, 25, 45}[Math.min(band, (short) 4)];
                    break;
                default:
                    valor = 0;
                    break;
            }

            int level = valor * 10;
            level = Math.max(min, Math.min(max, level));

            try {
                equalizer.setBandLevel(band, (short) level);
            } catch (Exception ignored) {
            }
        }

        refrescarSliders();
    }

    private void refrescarSliders() {
        if (bandsLayout == null || equalizer == null) {
            return;
        }

        short min = equalizer.getBandLevelRange()[0];

        for (int i = 0; i < bandsLayout.getChildCount(); i++) {
            LinearLayout fila =
                    (LinearLayout) bandsLayout.getChildAt(i);

            if (fila.getChildCount() < 2) {
                continue;
            }

            SeekBar seek = (SeekBar) fila.getChildAt(1);

            try {
                seek.setProgress(
                        equalizer.getBandLevel((short) i) - min
                );
            } catch (Exception ignored) {
            }
        }
    }

    private String formatearFrecuencia(int hz) {
        if (hz >= 1000) {
            return String.format(
                    Locale.ROOT,
                    "%.1fk",
                    hz / 1000.0
            );
        }
        return hz + "Hz";
    }

    @Override
    protected void onDestroy() {
        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Exception ignored) {
            }
            equalizer = null;
        }
        super.onDestroy();
    }
}
