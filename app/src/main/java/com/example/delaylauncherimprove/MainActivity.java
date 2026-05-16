package com.example.delaylauncherimprove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private TextView tvDelayValue;
    private TextView tvColApplicazioni, tvColDelay, tvColStato;
    private Button btnMinus, btnPlus, btnAvvio;

    private SharedPreferences prefs;
    private int currentDelay = 20;
    private final List<AppInfo> installedApps = new ArrayList<>();

    // Gestione Ingranaggi Countdown e Sequenza Lancio
    private final Handler countdownHandler = new Handler();
    private int tempoRimanente = 0;
    private boolean isCountingDown = false;

    // Media Players per la gestione dei suoni richiesti
    private MediaPlayer playerLoop = null;
    private MediaPlayer playerTick = null;
    private MediaPlayer playerChime = null;

    // Gestione Auto-Repeat tasti Delay
    private final Handler repeatUpdateHandler = new Handler();
    private boolean mAutoIncrement = false;
    private boolean mAutoDecrement = false;
    private static final long REPEAT_DELAY = 100;

    private final Runnable incrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoIncrement && currentDelay < 60) {
                currentDelay++;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateTabellaRiepilogo();
                repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
            }
        }
    };

    private final Runnable decrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoDecrement && currentDelay > 3) {
                currentDelay--;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateTabellaRiepilogo();
                repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        currentDelay = prefs.getInt("delay_seconds", 20);

        spinnerApp1 = findViewById(R.id.spinner_app1);
        spinnerApp2 = findViewById(R.id.spinner_app2);
        spinnerLauncher = findViewById(R.id.spinner_launcher);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        
        tvColApplicazioni = findViewById(R.id.tv_col_applicazioni);
        tvColDelay = findViewById(R.id.tv_col_delay);
        tvColStato = findViewById(R.id.tv_col_stato);
        
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);
        btnAvvio = findViewById(R.id.btn_avvio);

        tvDelayValue.setText(String.valueOf(currentDelay));

        recuperaApplicazioniInstallate();

        spinnerApp1.setAdapter(new CustomAppAdapter(this, installedApps, spinnerApp1, 1));
        spinnerApp2.setAdapter(new CustomAppAdapter(this, installedApps, spinnerApp2, 2));
        spinnerLauncher.setAdapter(new CustomAppAdapter(this, installedApps, spinnerLauncher, 3));

        String salvatoApp1 = prefs.getString("app1_package", "");
        String salvatoApp2 = prefs.getString("app2_package", "");
        String salvatoLauncher = prefs.getString("launcher_package", "DEFAULT_QUICKSTEP");

        if (salvatoLauncher.equals("DEFAULT_QUICKSTEP")) {
            String quickstepPackage = trovaPackageDaLabel("Quickstep");
            if (!quickstepPackage.isEmpty()) {
                salvatoLauncher = quickstepPackage;
                prefs.edit().putString("launcher_package", quickstepPackage).apply();
            } else {
                salvatoLauncher = "";
                prefs.edit().putString("launcher_package", "").apply();
            }
        }

        setSpinnerSelection(spinnerApp1, salvatoApp1);
        setSpinnerSelection(spinnerApp2, salvatoApp2);
        setSpinnerSelection(spinnerLauncher, salvatoLauncher);

        setupSpinnerListener(spinnerApp1, "app1_package");
        setupSpinnerListener(spinnerApp2, "app2_package");
        setupSpinnerListener(spinnerLauncher, "launcher_package");

        btnMinus.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                mAutoDecrement = true;
                repeatUpdateHandler.post(decrementRunnable);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mAutoDecrement = false;
                return true;
            }
            return false;
        });

        btnPlus.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                mAutoIncrement = true;
                repeatUpdateHandler.post(incrementRunnable);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mAutoIncrement = false;
                return true;
            }
            return false;
        });

        btnAvvio.setOnClickListener(v -> {
            if (isCountingDown) {
                fermaSequenza();
            } else {
                eseguiSequenzaLancio();
            }
        });

        updateTabellaRiepilogo();
    }

    private void recuperaApplicazioniInstallate() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        installedApps.clear();
        Drawable defaultIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel);
        installedApps.add(new AppInfo("Nessuna Applicazione", "", defaultIcon));

        List<AppInfo> temporanea = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String nomeLabel = app.loadLabel(pm).toString();
                Drawable iconaApp = app.loadIcon(pm);
                temporanea.add(new AppInfo(nomeLabel, app.packageName, iconaApp));
            }
        }
        Collections.sort(temporanea, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        installedApps.addAll(temporanea);
    }

    private String trovaPackageDaLabel(String targetLabel) {
        for (AppInfo app : installedApps) {
            if (app.getLabel().equalsIgnoreCase(targetLabel)) return app.getPackageName();
        }
        for (AppInfo app : installedApps) {
            if (app.getLabel().toLowerCase().contains(targetLabel.toLowerCase())) return app.getPackageName();
        }
        return "";
    }

    private void setSpinnerSelection(Spinner spinner, String packageName) {
        CustomAppAdapter adapter = (CustomAppAdapter) spinner.getAdapter();
        if (adapter == null) return;
        if (packageName.isEmpty()) {
            spinner.setSelection(0);
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            AppInfo app = adapter.getItem(i);
            if (app != null && app.getPackageName().equals(packageName)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void setupSpinnerListener(Spinner spinner, String prefKey) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CustomAppAdapter adapter = (CustomAppAdapter) parent.getAdapter();
                if (adapter == null) return;
                
                AppInfo selectedApp = adapter.getItem(position);
                String targetPackage = (selectedApp != null) ? selectedApp.getPackageName() : "";

                prefs.edit().putString(prefKey, targetPackage).apply();
                
                ((CustomAppAdapter) spinnerApp1.getAdapter()).notifyDataSetChanged();
                ((CustomAppAdapter) spinnerApp2.getAdapter()).notifyDataSetChanged();
                
                updateTabellaRiepilogo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveDelay() {
        prefs.edit().putInt("delay_seconds", currentDelay).apply();
    }

    private void updateTabellaRiepilogo() {
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        String app1 = "Nessuna";
        String app2 = "Nessuna";
        String launcher = "Non impostato";

        for (AppInfo app : installedApps) {
            if (!app.getPackageName().isEmpty()) {
                if (app.getPackageName().equals(pkg1)) app1 = app.getLabel();
                if (app.getPackageName().equals(pkg2)) app2 = app.getLabel();
                if (app.getPackageName().equals(pkgLauncher)) launcher = app.getLabel();
            }
        }

        if (app1.length() > 16) app1 = app1.substring(0, 14) + "..";
        if (app2.length() > 16) app2 = app2.substring(0, 14) + "..";
        if (launcher.length() > 16) launcher = launcher.substring(0, 14) + "..";

        tvColApplicazioni.setText("App 1:\n" + app1 + "\nApp 2:\n" + app2 + "\nLauncher:\n" + launcher);

        if (!isCountingDown) {
            tvColDelay.setText(currentDelay + " secondi\n\nCountdown pronto");
            if (pkgLauncher.isEmpty()) {
                tvColStato.setText("⚠ Errore\n\nScegli Home");
            } else {
                tvColStato.setText("✓ Pronto\n\nConfigurato");
            }
        }
    }

    // MOTORE CON AUDIO INTEGRATO E CONTROLLO DEI SECONDI ESATTI
    private void eseguiSequenzaLancio() {
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        if (pkgLauncher.isEmpty()) {
            Toast.makeText(this, "Impossibile avviare: Seleziona un Launcher finale!", Toast.LENGTH_LONG).show();
            return;
        }

        isCountingDown = true;
        btnAvvio.setText("⏹ FERMA AVVIO");
        btnAvvio.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF1744));
        tvColStato.setText("⏳ In corso...\n\nSequenza attiva");

        // Fase 1 e 2: Lancio background immediato
        if (!pkg1.isEmpty()) avviaApplicazioneSingola(pkg1);
        if (!pkg2.isEmpty()) avviaApplicazioneSingola(pkg2);

        tempoRimanente = currentDelay;

        // Fino a 4 secondi compresi facciamo partire il LOOP audio continuo
        if (tempoRimanente >= 4) {
            riproduciAudioLoop(R.raw.countdown_loop);
        }

        countdownHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isCountingDown) return;

                if (tempoRimanente > 0) {
                    tvColDelay.setText(tempoRimanente + " secondi\n\nAttendere...");

                    // GESTIONE CAMBIO SUONI DINAMICI
                    if (tempoRimanente == 3) {
                        fermaAudioLoop(); // Spegne il loop continuo a 4 secondi passati
                        riproduciAudioSingolo(R.raw.tick_soft); // Primo Tick a 3 secondi
                    } else if (tempoRimanente == 2 || tempoRimanente == 1) {
                        riproduciAudioSingolo(R.raw.tick_soft); // Tick per 2 e 1 secondi
                    }

                    tempoRimanente--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    // SECONDO ZERO: Chime finale e lancio launcher domestico
                    tvColDelay.setText("0 secondi\n\nLancio Home!");
                    riproduciAudioSingolo(R.raw.chime_soft);
                    
                    countdownHandler.postDelayed(() -> {
                        avviaApplicazioneSingola(pkgLauncher);
                        fermaSequenza();
                    }, 500); // Mezzo secondo di respiro per far sentire il Chime prima di cambiare schermo
                }
            }
        });
    }

    private void fermaSequenza() {
        isCountingDown = false;
        countdownHandler.removeCallbacksAndMessages(null);
        fermaAudioLoop();
        liberaRisorseAudio();
        btnAvvio.setText("▶  AVVIO");
        btnAvvio.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF76FF03));
        updateTabellaRiepilogo();
    }

    private void avviaApplicazioneSingola(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Errore nel lancio di: " + packageName, Toast.LENGTH_SHORT).show();
        }
    }

    // FUNZIONI DI SUPPORTO AUDIO PER I PLAYER DI SISTEMA
    private void riproduciAudioLoop(int resId) {
        try {
            fermaAudioLoop();
            playerLoop = MediaPlayer.create(this, resId);
            if (playerLoop != null) {
                playerLoop.setLooping(true);
                playerLoop.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fermaAudioLoop() {
        if (playerLoop != null) {
            try {
                if (playerLoop.isPlaying()) {
                    playerLoop.stop();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            playerLoop.release();
            playerLoop = null;
        }
    }

    private void riproduciAudioSingolo(int resId) {
        try {
            MediaPlayer mp = MediaPlayer.create(this, resId);
            if (mp != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void liberaRisorseAudio() {
        fermaAudioLoop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        liberaRisorseAudio();
    }

    // ADATTATORE FLUIDO DROPDOWN
    private class CustomAppAdapter extends ArrayAdapter<AppInfo> {
        private final int spinnerId;
        private final LayoutInflater inflater;

        public CustomAppAdapter(@NonNull Context context, @NonNull List<AppInfo> objects, Spinner spinner, int spinnerId) {
            super(context, 0, objects);
            this.spinnerId = spinnerId;
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createViewFromResource(position, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            AppInfo app = getItem(position);
            if (app == null) return super.getDropDownView(position, convertView, parent);

            String currentPkg = app.getPackageName();
            if (!currentPkg.isEmpty()) {
                String pkg1 = prefs.getString("app1_package", "");
                String pkg2 = prefs.getString("app2_package", "");

                if (spinnerId == 1 && currentPkg.equals(pkg2)) {
                    return new View(getContext());
                }
                if (spinnerId == 2 && currentPkg.equals(pkg1)) {
                    return new View(getContext());
                }
            }
            return createViewFromResource(position, convertView, parent, true);
        }

        private View createViewFromResource(int position, View convertView, ViewGroup parent, boolean isDropdown) {
            if (convertView == null || convertView.getClass() == View.class) {
                convertView = inflater.inflate(R.layout.spinner_item_custom, parent, false);
            }

            ImageView iconView = convertView.findViewById(R.id.spinner_app_icon);
            TextView labelView = convertView.findViewById(R.id.spinner_app_label);
            TextView packageView = convertView.findViewById(R.id.spinner_app_package);

            AppInfo app = getItem(position);

            if (app != null) {
                labelView.setText(app.getLabel());
                
                if (app.getPackageName().isEmpty()) {
                    packageView.setText("Nessuna azione pianificata");
                    labelView.setTypeface(null, Typeface.NORMAL);
                    labelView.setTextColor(0xFF76FF03);
                    iconView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                } else {
                    packageView.setText(app.getPackageName());
                    labelView.setTextColor(0xFFFFFFFF);
                    
                    if (!isDropdown) {
                        labelView.setTypeface(null, Typeface.BOLD);
                    } else {
                        labelView.setTypeface(null, Typeface.NORMAL);
                    }
                    
                    if (app.getIcon() != null) {
                        iconView.setImageDrawable(app.getIcon());
                    } else {
                        iconView.setImageResource(android.R.mipmap.sym_def_app_icon);
                    }
                }
            }
            return convertView;
        }
    }
}
