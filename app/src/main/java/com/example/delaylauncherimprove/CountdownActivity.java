package com.example.delaylauncherimprove;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CountdownActivity extends AppCompatActivity {

    private ProgressBar progressCountdown;
    private TextView tvCountdownNumber;
    
    private CountDownTimer countDownTimer;
    private SharedPreferences prefs;
    private int totalDelaySeconds;
    
    private MediaPlayer mpTick, mpChime, mpLoop;
    private boolean isInterrupted = false;
    private final Handler scaglionaHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_countdown);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        totalDelaySeconds = prefs.getInt("delay_seconds", 20);

        progressCountdown = findViewById(R.id.progress_countdown);
        tvCountdownNumber = findViewById(R.id.tv_countdown_number);
        
        View countdownRoot = findViewById(R.id.countdown_root);

        tvCountdownNumber.setText(String.valueOf(totalDelaySeconds));
        progressCountdown.setMax(totalDelaySeconds * 100);
        progressCountdown.setProgress(totalDelaySeconds * 100);

        mpTick = MediaPlayer.create(this, R.raw.tick_soft);
        mpChime = MediaPlayer.create(this, R.raw.chime_soft);
        mpLoop = MediaPlayer.create(this, R.raw.countdown_loop);

        if (mpLoop != null) {
            mpLoop.setLooping(true);
            mpLoop.setVolume(0.4f, 0.4f);
        }

        if (countdownRoot != null) {
            countdownRoot.setOnClickListener(v -> interruptSequence());
        }

        // FASE 1: Avvio del piano di lancio scaglionato
        pianificaLanciScaglionati();

        if (mpLoop != null) mpLoop.start();
        startCountdown();
    }

    private void pianificaLanciScaglionati() {
        String app1Pkg = prefs.getString("app1_package", "");
        String app2Pkg = prefs.getString("app2_package", "");

        // 1. App 1 parte IMMEDIATAMENTE al secondo zero del countdown
        if (!app1Pkg.isEmpty()) {
            Intent i1 = getPackageManager().getLaunchIntentForPackage(app1Pkg);
            if (i1 != null) {
                i1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i1);
            }
        }

        // 2. App 2 parte con un ritardo calcolato per non accavallarsi
        if (!app2Pkg.isEmpty()) {
            // Se il delay totale è alto usiamo 3 secondi (3000ms), altrimenti la metà del tempo totale
            long ritardoApp2 = (totalDelaySeconds >= 6) ? 3000L : (totalDelaySeconds * 1000L) / 2;

            scaglionaHandler.postDelayed(() -> {
                if (!isInterrupted) {
                    Intent i2 = getPackageManager().getLaunchIntentForPackage(app2Pkg);
                    if (i2 != null) {
                        i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i2);
                    }
                }
            }, ritardoApp2);
        }
    }

    private void startCountdown() {
        final Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
        final long totalMillis = totalDelaySeconds * 1000L;

        countDownTimer = new CountDownTimer(totalMillis, 50) {
            int lastSecondsRemaining = totalDelaySeconds;

            @Override
            public void onTick(long millisUntilFinished) {
                if (isInterrupted) return;

                progressCountdown.setProgress((int) (millisUntilFinished / 10));
                int secondsRemaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvCountdownNumber.setText(String.valueOf(secondsRemaining));

                if (secondsRemaining < lastSecondsRemaining) {
                    lastSecondsRemaining = secondsRemaining;
                    
                    if (mpTick != null) {
                        mpTick.seekTo(0);
                        mpTick.start();
                    }
                    if (pulseAnim != null) {
                        tvCountdownNumber.startAnimation(pulseAnim);
                    }
                }
            }

            @Override
            public void onFinish() {
                if (isInterrupted) return;

                tvCountdownNumber.setText("0");
                progressCountdown.setProgress(0);

                if (mpLoop != null && mpLoop.isPlaying()) {
                    mpLoop.stop();
                }

                if (mpChime != null) {
                    mpChime.start();
                    mpChime.setOnCompletionListener(mp -> lanciaLauncherFinale());
                } else {
                    lanciaLauncherFinale();
                }
            }
        }.start();
    }

    private void lanciaLauncherFinale() {
        String launcherPkg = prefs.getString("launcher_package", "");
        if (!launcherPkg.isEmpty()) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(launcherPkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Impossibile avviare il Launcher selezionato!", Toast.LENGTH_LONG).show();
                tornaAllaConfigurazione();
            }
        } else {
            tornaAllaConfigurazione();
        }
    }

    private void interruptSequence() {
        isInterrupted = true;
        // Cancella eventuali lanci in coda dell'Handler se l'utente interrompe subito
        scaglionaHandler.removeCallbacksAndMessages(null);
        
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        liberaMediaPlayers();
        Toast.makeText(this, "🛑 LANCIO INTERROTTO DALL'UTENTE", Toast.LENGTH_SHORT).show();
        tornaAllaConfigurazione();
    }

    private void tornaAllaConfigurazione() {
        boolean isAutoStart = getIntent().getBooleanExtra("auto_start", false);
        if (isAutoStart) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
        finish();
    }

    private void liberaMediaPlayers() {
        if (mpTick != null) { mpTick.release(); mpTick = null; }
        if (mpChime != null) { mpChime.release(); mpChime = null; }
        if (mpLoop != null) {
            if (mpLoop.isPlaying()) { mpLoop.stop(); }
            mpLoop.release();
            mpLoop = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scaglionaHandler.removeCallbacksAndMessages(null);
        liberaMediaPlayers();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
