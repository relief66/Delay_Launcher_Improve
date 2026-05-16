package com.example.delaylauncherimprove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutConfigurazione;
    private RelativeLayout layoutCountdown;
    private Spinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private TextView tvDelayValue, tvCountdownBig, tvRiSecondi;
    private ProgressBar progressCircolare;
    private TextView tvColApplicazioni, tvColStato, tvColStatoDesc;
    private Button btnMinus, btnPlus, btnAvvio;
    
    // Riferimenti ai componenti interni dei Box per aggiornarli dinamici
    private TextView txtNameApp1, txtPkgApp1, txtNameApp2, txtPkgApp2, txtNameLauncher, txtPkgLauncher;
    private ImageView iconApp1, iconApp2, iconLauncher;

    private SharedPreferences prefs;
    private int currentDelay = 25;
    
    private final List<AppInfo> installedApps = new ArrayList<>();
    private final List<AppInfo> launcherApps = new ArrayList<>();

    private final Handler countdownHandler = new Handler();
    private int tempoRimanente = 0;
    private boolean isCountingDown = false;
    private MediaPlayer playerLoop = null;

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
                tvRiSecondi.setText(String.valueOf(currentDelay));
                saveDelay();
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
                tvRiSecondi.setText(String.valueOf(currentDelay));
                saveDelay();
                repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutConfigurazione = findViewById(R.id.layout_configurazione);
        layoutCountdown = findViewById(R.id.layout_countdown);

        spinnerApp1 = findViewById(R.id.spinner_app1);
        spinnerApp2 = findViewById(R.id.spinner_app2);
        spinnerLauncher = findViewById(R.id.spinner_launcher);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        
        txtNameApp1 = findViewById(R.id.txt_name_app1);
        txtPkgApp1 = findViewById(R.id.txt_pkg_app1);
        iconApp1 = findViewById(R.id.icon_app1);
        
        txtNameApp2 = findViewById(R.id.txt_name_app2);
        txtPkgApp2 = findViewById(R.id.txt_pkg_app2);
        iconApp2 = findViewById(R.id.icon_app2);
        
        txtNameLauncher = findViewById(R.id.txt_name_launcher);
        txtPkgLauncher = findViewById(R.id.txt_pkg_launcher);
        iconLauncher = findViewById(R.id.icon_launcher);

        tvColApplicazioni = findViewById(R.id.tv_col_applicazioni);
        tvRiSecondi = findViewById(R.id.tv_riepilogo_secondi);
        tvColStato = findViewById(R.id.tv_col_stato);
        tvColStatoDesc = findViewById(R.id.tv_col_stato_desc);
        
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);
        btnAvvio = findViewById(R.id.btn_avvio);
        tvCountdownBig = findViewById(R.id.tv_countdown_big);
        progressCircolare = findViewById(R.id.progress_circolare);

        spinnerApp1.setAdapter(new CustomAppAdapter(this, installedApps));
        spinnerApp2.setAdapter(new CustomAppAdapter(this, installedApps));
        spinnerLauncher.setAdapter(new CustomAppAdapter(this, launcherApps));

        new Thread(() -> {
            prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
            currentDelay = prefs.getInt("delay_seconds", 25);
            recuperaEFlitraApplicazioni();

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                tvDelayValue.setText(String.valueOf(currentDelay));
                tvRiSecondi.setText(String.valueOf(currentDelay));

                if (spinnerApp1.getAdapter() != null) ((CustomAppAdapter) spinnerApp1.getAdapter()).notifyDataSetChanged();
                if (spinnerApp2.getAdapter() != null) ((CustomAppAdapter) spinnerApp2.getAdapter()).notifyDataSetChanged();
                if (spinnerLauncher.getAdapter() != null) ((CustomAppAdapter) spinnerLauncher.getAdapter()).notifyDataSetChanged();

                String salvatoApp1 = prefs.getString("app1_package", "");
                String salvatoApp2 = prefs.getString("app2_package", "");
                String salvatoLauncher = prefs.getString("launcher_package", "");

                setSpinnerSelection(spinnerApp1, salvatoApp1);
                setSpinnerSelection(spinnerApp2, salvatoApp2);
                setSpinnerSelection(spinnerLauncher, salvatoLauncher);

                setupSpinnerListener(spinnerApp1, "app1_package", 1);
                setupSpinnerListener(spinnerApp2, "app2_package", 2);
                setupSpinnerListener(spinnerLauncher, "launcher_package", 3);

                updateGraficaETabella();
            });
        }).start();

        layoutCountdown.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                fermaSequenza();
                return true;
            }
            return false;
        });

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

        btnAvvio.setOnClickListener(v -> eseguiSequenzaLancio());
    }

    private void recuperaEFlitraApplicazioni() {
        PackageManager pm = getPackageManager();
        String myPackageName = getPackageName();

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homeApps = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        Set<String> launcherPackageSet = new HashSet<>();
        
        for (ResolveInfo info : homeApps) {
            if (info.activityInfo != null) {
                launcherPackageSet.add(info.activityInfo.packageName);
            }
        }

        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        installedApps.clear();
        launcherApps.clear();

        Drawable defaultIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel);
        installedApps.add(new AppInfo("Nessuna Applicazione", "", defaultIcon));
        launcherApps.add(new AppInfo("Scegli Home (Launcher)", "", defaultIcon));

        List<AppInfo> tempNormalApps = new ArrayList<>();
        List<AppInfo> tempLauncherApps = new ArrayList<>();

        for (ApplicationInfo app : allApps) {
            if (app.packageName.equals(myPackageName)) continue;

            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String label = app.loadLabel(pm).toString();
                Drawable icon = app.loadIcon(pm);
                AppInfo appObject = new AppInfo(label, app.packageName, icon);

                if (launcherPackageSet.contains(app.packageName)) {
                    tempLauncherApps.add(appObject);
                } else {
                    tempNormalApps.add(appObject);
                }
            }
        }

        Collections.sort(tempNormalApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        Collections.sort(tempLauncherApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        installedApps.addAll(tempNormalApps);
        launcherApps.addAll(tempLauncherApps);
    }

    private void setSpinnerSelection(Spinner spinner, String packageName) {
        CustomAppAdapter adapter = (CustomAppAdapter) spinner.getAdapter();
        if (adapter == null) return;
        if (packageName == null || packageName.isEmpty()) {
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

    private void setupSpinnerListener(Spinner spinner, String prefKey, int targetBox) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CustomAppAdapter adapter = (CustomAppAdapter) parent.getAdapter();
                if (adapter == null) return;
                
                AppInfo selectedApp = adapter.getItem(position);
                String targetPackage = (selectedApp != null) ? selectedApp.getPackageName() : "";
                
                if (prefs != null) {
                    prefs.edit().putString(prefKey, targetPackage).apply();
                }
                
                updateGraficaETabella();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveDelay() {
        if (prefs != null) {
            prefs.edit().putInt("delay_seconds", currentDelay).apply();
        }
    }

    private void updateGraficaETabella() {
        if (prefs == null) return;
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        AppInfo app1Obj = trovaApp(pkg1, installedApps, true);
        AppInfo app2Obj = trovaApp(pkg2, installedApps, true);
        AppInfo launcherObj = trovaApp(pkgLauncher, launcherApps, false);

        // Aggiorna BOX 1
        txtNameApp1.setText(app1Obj.getLabel());
        txtPkgApp1.setText(app1Obj.getPackageName().isEmpty() ? "Disattivato" : app1Obj.getPackageName());
        iconApp1.setImageDrawable(app1Obj.getIcon());

        // Aggiorna BOX 2
        txtNameApp2.setText(app2Obj.getLabel());
        txtPkgApp2.setText(app2Obj.getPackageName().isEmpty() ? "Disattivato" : app2Obj.getPackageName());
        iconApp2.setImageDrawable(app2Obj.getIcon());

        // Aggiorna BOX LAUNCHER
        txtNameLauncher.setText(launcherObj.getLabel());
        txtPkgLauncher.setText(launcherObj.getPackageName().isEmpty() ? "Richiesto" : launcherObj.getPackageName());
        iconLauncher.setImageDrawable(launcherObj.getIcon());

        // Aggiorna la seconda colonna del riepilogo in basso
        String riepilogoApp1 = app1Obj.getPackageName().isEmpty() ? "Disattivata" : app1Obj.getLabel();
        String riepilogoApp2 = app2Obj.getPackageName().isEmpty() ? "Disattivata" : app2Obj.getLabel();
        String riepilogoLauncher = launcherObj.getPackageName().isEmpty() ? "Non impostato" : launcherObj.getLabel();

        tvColApplicazioni.setText("App 1: " + riepilogoApp1 + "\n\nApp 2: " + riepilogoApp2 + "\n\nHome: " + riepilogoLauncher);

        // Aggiorna lo stato nell'ultima colonna
        if (pkgLauncher.isEmpty()) {
            tvColStato.setText("⚠ Errore");
            tvColStato.setTextColor(0xFFFF2222);
            tvColStatoDesc.setText("Seleziona una Home per completare la sequenza.");
        } else {
            tvColStato.setText("✓ Pronto");
            tvColStato.setTextColor(0xFF76FF03);
            tvColStatoDesc.setText("Tutte le applicazioni selezionate sono disponibili");
        }
    }

    private AppInfo trovaApp(String pkg, List<AppInfo> lista, boolean isNormalApp) {
        for (AppInfo app : lista) {
            if (app.getPackageName().equals(pkg)) return app;
        }
        Drawable defIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel);
        return new AppInfo(isNormalApp ? "Nessuna Applicazione" : "Scegli Home (Launcher)", "", defIcon);
    }

    private void eseguiSequenzaLancio() {
        if (prefs == null) return;
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        if (pkgLauncher.isEmpty()) {
            Toast.makeText(this, "Seleziona una Home finale nel quarto box!", Toast.LENGTH_LONG).show();
            return;
        }

        isCountingDown = true;
        layoutConfigurazione.setVisibility(View.GONE);
        findViewById(R.id.layout_riepilogo_container).setVisibility(View.GONE);
        findViewById(R.id.layout_header).setVisibility(View.GONE);
        layoutCountdown.setVisibility(View.VISIBLE);

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
                    tvCountdownBig.setText(String.valueOf(tempoRimanente));
                    int percentuale = (tempoRimanente * 100) / currentDelay;
                    progressCircolare.setProgress(percentuale);

                    if (tempoRimanente == 3) {
                        fermaAudioLoop();
                        riproduciAudioSingolo(R.raw.tick_soft);
                    } else if (tempoRimanente == 2 || tempoRimanente == 1) {
                        riproduciAudioSingolo(R.raw.tick_soft);
                    }

                    tempoRimanente--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    tvCountdownBig.setText("0");
                    progressCircolare.setProgress(0);
                    riproduciAudioSingolo(R.raw.chime_soft);
                    
                    countdownHandler.postDelayed(() -> {
                        avviaApplicazioneInvisibile(pkgLauncher);
                        fermaSequenza();
                    }, 500);
                }
            }
        });
    }

    private void avviaApplicazioneInvisibile(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fermaSequenza() {
        isCountingDown = false;
        countdownHandler.removeCallbacksAndMessages(null);
        fermaAudioLoop();
        
        layoutCountdown.setVisibility(View.GONE);
        layoutConfigurazione.setVisibility(View.VISIBLE);
        findViewById(R.id.layout_riepilogo_container).setVisibility(View.VISIBLE);
        findViewById(R.id.layout_header).setVisibility(View.VISIBLE);
        updateGraficaETabella();
    }

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
                if (playerLoop.isPlaying()) playerLoop.stop();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fermaAudioLoop();
        repeatUpdateHandler.removeCallbacksAndMessages(null);
        countdownHandler.removeCallbacksAndMessages(null);
    }

    private class CustomAppAdapter extends ArrayAdapter<AppInfo> {
        private final LayoutInflater inflater;

        public CustomAppAdapter(@NonNull Context context, @NonNull List<AppInfo> objects) {
            super(context, 0, objects);
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createViewFromResource(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createViewFromResource(position, convertView, parent);
        }

        private View createViewFromResource(int position, View convertView, ViewGroup parent) {
            if (convertView == null || convertView.getClass() == View.class) {
                convertView = inflater.inflate(R.layout.spinner_item_custom, parent, false);
            }

            ImageView iconView = convertView.findViewById(R.id.spinner_app_icon);
            TextView labelView = convertView.findViewById(R.id.spinner_app_label);
            TextView packageView = convertView.findViewById(R.id.spinner_app_package);

            AppInfo app = getItem(position);

            if (app != null) {
                labelView.setText(app.getLabel());
                packageView.setText(app.getPackageName().isEmpty() ? "Disattivato" : app.getPackageName());
                if (app.getIcon() != null) {
                    iconView.setImageDrawable(app.getIcon());
                } else {
                    iconView.setImageResource(android.R.mipmap.sym_def_app_icon);
                }
            }
            return convertView;
        }
    }
}
