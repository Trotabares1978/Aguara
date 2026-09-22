package com.trotabares.aguara;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private ScrollView audioScroll;
    private SeekBar preampSeek;
    private SeekBar bassBoostSeek;
    private SeekBar tubeSeek;
    private SeekBar vinylSeek;
    private Switch limiterSwitch;

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

    private void estilizarBoton(Button boton) {
        GradientDrawable fondoBoton = new GradientDrawable();
        fondoBoton.setShape(GradientDrawable.RECTANGLE);
        fondoBoton.setColor(Color.rgb(35, 35, 35));
        fondoBoton.setCornerRadius(dp(12));
        fondoBoton.setStroke(dp(1), Color.rgb(75, 75, 75));
        boton.setBackground(fondoBoton);
        boton.setPadding(dp(6), 0, dp(6), 0);
        boton.setMinHeight(0);
        boton.setMinimumHeight(0);
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
        estilizarBoton(simple);
        simple.setOnClickListener(v -> mostrarModoSimple());
        modos.addView(simple, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button avanzado = new Button(this);
        avanzado.setText("MODO AVANZADO");
        avanzado.setTextColor(blanco);
        estilizarBoton(avanzado);
        avanzado.setOnClickListener(v -> mostrarModoAvanzado());
        modos.addView(avanzado, new LinearLayout.LayoutParams(0, dp(48), 1f));

        root.addView(modos, new LinearLayout.LayoutParams(-1, dp(54)));

        ScrollView scroll = new ScrollView(this);
        audioScroll = scroll;
        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(0, dp(6), 0, dp(8));

        bandsLayout = construirEcualizador();
        contenido.addView(bandsLayout);

        advancedLayout = construirAvanzado();
        contenido.addView(advancedLayout);

        scroll.addView(contenido);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button reset = new Button(this);
        reset.setText("↺  RESETEAR AUDIO");
        reset.setTextColor(blanco);
        estilizarBoton(reset);
        reset.setOnClickListener(v -> resetearAudio());
        root.addView(reset, new LinearLayout.LayoutParams(-1, dp(48)));

        Button cerrar = new Button(this);
        cerrar.setText("CERRAR");
        cerrar.setTextColor(blanco);
        estilizarBoton(cerrar);
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
        presets.setOrientation(LinearLayout.VERTICAL);

        LinearLayout fila1 = new LinearLayout(this);
        fila1.setGravity(Gravity.CENTER);
        agregarPreset(fila1, "PLANO", 0);
        agregarPreset(fila1, "BAJOS", 1);
        agregarPreset(fila1, "VOZ", 2);
        presets.addView(fila1, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout fila2 = new LinearLayout(this);
        fila2.setGravity(Gravity.CENTER);
        agregarPreset(fila2, "ROCK", 3);
        agregarPreset(fila2, "ACÚSTICO", 4);
        agregarPreset(fila2, "LO-FI", 5);
        presets.addView(fila2, new LinearLayout.LayoutParams(-1, dp(50)));

        panel.addView(presets, new LinearLayout.LayoutParams(-1, dp(104)));

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
        limiterSwitch = limiter;
        panel.addView(limiter, new LinearLayout.LayoutParams(-1, dp(52)));

        agregarControl(panel, "BASS BOOST", "0", "12 dB", 0f, 12f,
                player.getBassBoost(), value -> player.setBassBoost(value));

        agregarControl(panel, "EFECTO VÁLVULA", "Limpio", "12", 0f, 12f,
                player.getTubeDrive(), value -> player.setTubeDrive(value));

        agregarControl(panel, "EFECTO VINILO", "0", "100", 0f, 100f,
                player.getVinylAmount(), value -> player.setVinylAmount(value));

        TextView ambientes = text("AMBIENTE", 18, blanco);
        ambientes.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ambientes.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(ambientes, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout filaAmbientes = new LinearLayout(this);
        filaAmbientes.setGravity(Gravity.CENTER);
        String[] nombresAmbiente = {"SECO", "SALA", "TEATRO", "CONCIERTO", "AIRE LIBRE", "ESTADIO"};
        for (int i = 0; i < nombresAmbiente.length; i++) {
            final int modo = i;
            Button boton = new Button(this);
            boton.setText(nombresAmbiente[i]);
            boton.setTextSize(9);
            boton.setTextColor(blanco);
            estilizarBoton(boton);
            boton.setOnClickListener(v -> {
                player.setEnvironmentMode(modo);
                actualizarBotonesAmbiente(filaAmbientes);
            });
            filaAmbientes.addView(boton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        panel.addView(filaAmbientes, new LinearLayout.LayoutParams(-1, dp(58)));
        actualizarBotonesAmbiente(filaAmbientes);

        Button guazu = new Button(this);
        guazu.setText(player.isGuazuMode() ? "✓ 🐆 MODO GUAZÚ" : "🐆 MODO GUAZÚ");
        guazu.setTextColor(blanco);
        estilizarBoton(guazu);
        guazu.setOnClickListener(v -> {
            player.aplicarModoGuazu();
            guazu.setText("✓ 🐆 MODO GUAZÚ");
            actualizarBotonesAmbiente(filaAmbientes);
            refrescarSlidersAvanzados();
        });
        panel.addView(guazu, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView karaokeTitulo = text("KARAOKE", 18, blanco);
        karaokeTitulo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        karaokeTitulo.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(karaokeTitulo, new LinearLayout.LayoutParams(-1, dp(42)));

        Button karaoke = new Button(this);
        karaoke.setText(player.getKaraokeAmount() > 0.5f ? "✓ KARAOKE" : "KARAOKE");
        karaoke.setTextColor(blanco);
        estilizarBoton(karaoke);
        karaoke.setOnClickListener(v -> {
            boolean activo = player.getKaraokeAmount() <= 0.5f;
            player.setKaraokeAmount(activo ? 100f : 0f);
            karaoke.setText(activo ? "✓ KARAOKE" : "KARAOKE");
        });
        panel.addView(karaoke, new LinearLayout.LayoutParams(-1, dp(52)));

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

        if (nombre.equals("PREAMP")) preampSeek = seek;
        else if (nombre.equals("BASS BOOST")) bassBoostSeek = seek;
        else if (nombre.equals("EFECTO VÁLVULA")) tubeSeek = seek;
        else if (nombre.equals("EFECTO VINILO")) vinylSeek = seek;

        TextView range = text(minimo + "                                      " + maximo, 11, gris);
        panel.addView(range, new LinearLayout.LayoutParams(-1, dp(24)));
    }

    private String formatear(float value) {
        if (Math.abs(value) < 0.05f) return "0";
        return String.format(java.util.Locale.US, "%.1f", value);
    }


    private void resetearAudio() {
        if (player == null) return;

        // Restablece absolutamente todos los parámetros de audio.
        for (int i = 0; i < player.getBandCount(); i++) {
            player.setBandGain(i, 0f);
        }
        player.setPreampDb(0f);
        player.setBassBoost(0f);
        player.setTubeDrive(0f);
        player.setVinylAmount(0f);
        player.setKaraokeAmount(0f);
        player.setEnvironmentMode(0);
        player.setLimiterEnabled(false);

        refrescarSliders();

        if (preampSeek != null) preampSeek.setProgress(1200);
        if (bassBoostSeek != null) bassBoostSeek.setProgress(0);
        if (tubeSeek != null) tubeSeek.setProgress(0);
        if (vinylSeek != null) vinylSeek.setProgress(0);
        if (limiterSwitch != null) limiterSwitch.setChecked(false);
    }

    private void mostrarModoSimple() {
        bandsLayout.setVisibility(View.VISIBLE);
        advancedLayout.setVisibility(View.GONE);
    }

    private void mostrarModoAvanzado() {
        bandsLayout.setVisibility(View.VISIBLE);
        advancedLayout.setVisibility(View.VISIBLE);

        // Primero dejamos que Android recalcule la posición real de
        // AUDIO LAB al hacerse visible y recién después desplazamos.
        advancedLayout.requestLayout();
        audioScroll.postDelayed(() -> {
            int destino = Math.max(0, advancedLayout.getTop());
            audioScroll.smoothScrollTo(0, destino);
        }, 120);
    }

    private void agregarPreset(LinearLayout contenedor, String nombre, int tipo) {
        Button boton = new Button(this);
        boton.setText(nombre);
        boton.setTextSize(11);
        boton.setTextColor(blanco);
        estilizarBoton(boton);
        boton.setOnClickListener(v -> aplicarPreset(tipo));

        contenedor.addView(
                boton,
                new LinearLayout.LayoutParams(0, dp(46), 1f)
        );
    }

    private void aplicarPreset(int tipo) {
        if (player == null) return;

        float[][] presets = {
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},                         // Plano
                {4, 5, 4, 2, 0, -1, -1, -1, -2, -2},                     // Bajos
                {-2, -1, 0, 1, 3, 4, 3, 1, 0, -1},                       // Voz
                {4, 3, 1, -1, -1, 1, 3, 4, 4, 3},                       // Rock
                {1, 2, 1, 0, 1, 2, 2, 1, 0, -1},                       // Acústico
                {3, 2, 0, -2, -1, 1, 3, 1, -2, -4}                      // Lo-Fi
        };

        float[] valores = presets[Math.max(0, Math.min(tipo, presets.length - 1))];

        for (int i = 0; i < player.getBandCount(); i++) {
            player.setBandGain(i, valores[Math.min(i, valores.length - 1)]);
        }

        refrescarSliders();
    }

    private void actualizarBotonesAmbiente(LinearLayout fila) {
        if (fila == null || player == null) return;
        for (int i = 0; i < fila.getChildCount(); i++) {
            View vista = fila.getChildAt(i);
            if (vista instanceof Button) {
                Button boton = (Button) vista;
                boolean activo = !player.isGuazuMode() && i == player.getEnvironmentMode();
                boton.setText((activo ? "✓ " : "") + new String[]{"SECO", "SALA", "TEATRO", "CONCIERTO", "AIRE LIBRE", "ESTADIO"}[i]);
            }
        }
    }

    private void refrescarSlidersAvanzados() {
        if (preampSeek != null) preampSeek.setProgress(Math.round((player.getPreampDb() + 12f) * 100f));
        if (bassBoostSeek != null) bassBoostSeek.setProgress(Math.round(player.getBassBoost() * 100f));
        if (tubeSeek != null) tubeSeek.setProgress(Math.round(player.getTubeDrive() * 100f));
        if (vinylSeek != null) vinylSeek.setProgress(Math.round(player.getVinylAmount() * 100f));
        if (limiterSwitch != null) limiterSwitch.setChecked(player.isLimiterEnabled());
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
