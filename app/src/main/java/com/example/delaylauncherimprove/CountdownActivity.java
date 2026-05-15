package com.example.delaylauncherimprove;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_countdown);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        totalDelaySeconds = prefs.getInt("delay_seconds", 20);

        progressCountdown = findViewById(R.id.progress_countdown);
        tvCountdownNumber = findViewById(R.id.tv_countdown_number);
        View countdownRoot = findViewById(countdown_root);

        // Configurazione iniziale della grafica del cerchio
        tvCountdownNumber.setText(String.valueOf(totalDelaySeconds));
        progressCountdown.setMax(totalDelaySeconds * 100);
        progressCountdown.setProgress(totalDelaySeconds * 100);

        // Inizializzazione dei file Audio presenti in res/raw/
        mpTick = MediaPlayer.create(this, R.raw.tick_soft);
        mpChime = MediaPlayer.create(this, R.raw.chime_soft);
        mpLoop = MediaPlayer.create(this, R.raw.countdown_loop);

        if (mpLoop != null) {
            mpLoop.setLooping(true);
            mpLoop.setVolume(0.4f, 0.4f); // Volume di sottofondo leggermente attenuato
        }

        // Intercettazione del tocco su tutto lo schermo per ANNULLARE la sequenza
        countdownRoot.setOnClickListener(v -> interruptSequence());

        // FASE 1: Lancio immediato delle prime due applicazioni in sequenza prima del timer
        lanciaApplicazioniIniziali();

        // FASE 2: Avvio del sottofondo sonoro ciclico e del countdown grafico
        if (mpLoop != null) mpLoop.start();
        startCountdown();
    }

    private void lanciaApplicazioniIniziali() {
        String app1Pkg = prefs.getString("app1_package", "");
        String app2Pkg = prefs.getString("app2_package", "");

        // Avvio App 1 se configurata
        if (!app1Pkg.isEmpty()) {
            Intent i1 = getPackageManager().getLaunchIntentForPackage(app1Pkg);
            if (i1 != null) {
                i1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i1);
            }
        }

        // Avvio App 2 se configurata
        if (!app2Pkg.isEmpty()) {
            Intent i2 = getPackageManager().getLaunchIntentForPackage(app2Pkg);
            if (i2 != null) {
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i2);
            }
        }
    }

    private void startCountdown() {
        // Carichiamo l'animazione di pulsazione per il testo del cerchio
        final Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse);
        final long totalMillis = totalDelaySeconds * 1000L;

        // Utilizziamo intervalli frequenti (50ms) per rendere fluida la riduzione dell'anello verde
        countDownTimer = new CountDownTimer(totalMillis, 50) {
            int lastSecondsRemaining = totalDelaySeconds;

            @Override
            public void onTick(long millisUntilFinished) {
                if (isInterrupted) return;

                // Aggiornamento preciso della barra circolare progressiva
                progressCountdown.setProgress((int) (millisUntilFinished / 10));

                int secondsRemaining = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvCountdownNumber.setText(String.valueOf(secondsRemaining));

                // Scatta ogni volta che cambia il secondo effettivo
                if (secondsRemaining < lastSecondsRemaining) {
                    lastSecondsRemaining = secondsRemaining;
                    
                    // Riproduce il suono del "Tick" sincrono
                    if (mpTick != null) {
                        mpTick.seekTo(0);
                        mpTick.start();
                    }
                    // Attiva l'animazione grafica di sobbalzo sul cerchio numerico
                    tvCountdownNumber.startAnimation(pulseAnim);
                }
            }

            @Override
            public void onFinish() {
                if (isInterrupted) return;

                tvCountdownNumber.setText("0");
                progressCountdown.setProgress(0);

                // Ferma il loop audio di sottofondo
                if (mpLoop != null && mpLoop.isPlaying()) {
                    mpLoop.stop();
                }

                // Riproduce il suono di completamento "Chime"
                if (mpChime != null) {
                    mpChime.start();
                    // Aspettiamo un istante che il suono parta prima di passare al Launcher
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
                finish(); // Chiude definitivamente l'attività per liberare RAM
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
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        
        // Rilascio e blocco immediato di tutti i flussi audio per evitare rumori residui
        liberaMediaPlayers();

        Toast.makeText(this, "🛑 LANCIO INTERROTTO DALL'UTENTE", Toast.LENGTH_SHORT).show();
        tornaAllaConfigurazione();
    }

    private void tornaAllaConfigurazione() {
        // Se siamo partiti dal Boot, l'utente toccando lo schermo deve poter accedere alla UI di configurazione
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
        // Massima sicurezza: se l'attività viene chiusa dal sistema, puliamo la memoria audio
        liberaMediaPlayers();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
