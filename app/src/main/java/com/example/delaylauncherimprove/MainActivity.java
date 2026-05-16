private void eseguiSequenzaLancio() {
        if (prefs == null) return;
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        if (pkgLauncher.isEmpty()) {
            Toast.makeText(this, "Seleziona un Launcher finale!", Toast.LENGTH_LONG).show();
            return;
        }

        isCountingDown = true;
        
        layoutConfigurazione.setVisibility(View.GONE);
        // Nasconde il layout della configurazione

        // --- RIPRISTINO COUNTDOWN ORIGINALE ---
        // Mostra il layout del countdown (il cerchio verde e il numero grande)
        layoutCountdown.setVisibility(View.VISIBLE);
        // --- --- --- --- --- --- --- --- ---

        if (!pkg1.isEmpty()) avviaApplicazioneInvisibile(pkg1);
        if (!pkg2.isEmpty()) avviaApplicazioneInvisibile(pkg2);

        Intent richiamaSopra = new Intent(this, MainActivity.class);
        richiamaSopra.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(richiamaSopra);
        overridePendingTransition(0, 0);

        tempoRimanente = currentDelay;

        if (tempoRimanente >= 4) {
            riproduciAudioLoop(R.raw.countdown_loop);
        }

        countdownHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isCountingDown) return;

                if (tempoRimanente > 0) {
                    // --- RIPRISTINO COUNTDOWN ORIGINALE ---
                    // Aggiorna il testo del countdown
                    tvCountdownBig.setText(String.valueOf(tempoRimanente));
                    // Calcola e aggiorna la barra di progresso circolare
                    int percentuale = (tempoRimanente * 100) / currentDelay;
                    progressCircolare.setProgress(percentuale);
                    // --- --- --- --- --- --- --- --- ---

                    if (tempoRimanente == 3) {
                        fermaAudioLoop();
                        riproduciAudioSingolo(R.raw.tick_soft);
                    } else if (tempoRimanente == 2 || tempoRimanente == 1) {
                        riproduciAudioSingolo(R.raw.tick_soft);
                    }

                    tempoRimanente--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    // --- RIPRISTINO COUNTDOWN ORIGINALE ---
                    // Al termine, imposta a 0
                    tvCountdownBig.setText("0");
                    progressCircolare.setProgress(0);
                    // --- --- --- --- --- --- --- --- ---

                    riproduciAudioSingolo(R.raw.chime_soft);
                    
                    countdownHandler.postDelayed(() -> {
                        avviaApplicazioneInvisibile(pkgLauncher);
                        fermaSequenza();
                    }, 500);
                }
            }
        });
    }
