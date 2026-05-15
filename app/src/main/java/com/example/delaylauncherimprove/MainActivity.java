package com.example.delaylauncherimprove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
    private List<AppInfo> installedApps = new ArrayList<>();
    private boolean isUpdatingSpinners = false;

    private final Handler repeatUpdateHandler = new Handler();
    private boolean mAutoIncrement = false;
    private boolean mAutoDecrement = false;
    private static final long REPEAT_DELAY = 100;

    private final Runnable incrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoIncrement) {
                if (currentDelay < 60) {
                    currentDelay++;
                    tvDelayValue.setText(String.valueOf(currentDelay));
                    saveDelay();
                    updateTabellaRiepilogo();
                    repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
                } else {
                    mAutoIncrement = false;
                    Toast.makeText(MainActivity.this, "Massimo 60 secondi consentiti!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private final Runnable decrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoDecrement) {
                if (currentDelay > 3) {
                    currentDelay--;
                    tvDelayValue.setText(String.valueOf(currentDelay));
                    saveDelay();
                    updateTabellaRiepilogo();
                    repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
                } else {
                    mAutoDecrement = false;
                    Toast.makeText(MainActivity.this, "Minimo 3 secondi richiesti!", Toast.LENGTH_SHORT).show();
                }
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

        // Inizializzazione con l'adattatore custom grafico
        popolaSpinner(spinnerApp1, installedApps);
        popolaSpinner(spinnerApp2, installedApps);
        popolaSpinner(spinnerLauncher, installedApps);

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
            Toast.makeText(MainActivity.this, "Avvio manuale sequenza...", Toast.LENGTH_SHORT).show();
        });

        aggiornaFiltriEsclusione();
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

    private void popolaSpinner(Spinner spinner, List<AppInfo> listaApps) {
        CustomAppAdapter adapter = new CustomAppAdapter(this, listaApps);
        spinner.setAdapter(adapter);
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
                if (isUpdatingSpinners) return;

                CustomAppAdapter adapter = (CustomAppAdapter) parent.getAdapter();
                if (adapter == null) return;
                
                AppInfo selectedApp = adapter.getItem(position);
                String targetPackage = (selectedApp != null) ? selectedApp.getPackageName() : "";

                prefs.edit().putString(prefKey, targetPackage).apply();
                
                if (spinner == spinnerApp1 || spinner == spinnerApp2) {
                    aggiornaFiltriEsclusione();
                }
                updateTabellaRiepilogo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void aggiornaFiltriEsclusione() {
        isUpdatingSpinners = true;

        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");

        List<AppInfo> listaPerApp2 = new ArrayList<>();
        for (AppInfo app : installedApps) {
            if (app.getPackageName().isEmpty() || !app.getPackageName().equals(pkg1)) {
                listaPerApp2.add(app);
            }
        }
        popolaSpinner(spinnerApp2, listaPerApp2);
        setSpinnerSelection(spinnerApp2, pkg2);

        List<AppInfo> listaPerApp1 = new ArrayList<>();
        for (AppInfo app : installedApps) {
            if (app.getPackageName().isEmpty() || !app.getPackageName().equals(pkg2)) {
                listaPerApp1.add(app);
            }
        }
        popolaSpinner(spinnerApp1, listaPerApp1);
        setSpinnerSelection(spinnerApp1, pkg1);

        isUpdatingSpinners = false;
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

        if (app1.length() > 14) app1 = app1.substring(0, 12) + "..";
        if (app2.length() > 14) app2 = app2.substring(0, 12) + "..";
        if (launcher.length() > 14) launcher = launcher.substring(0, 12) + "..";

        String testoApplicazioni = "App 1:\n" + app1 + "\nApp 2:\n" + app2 + "\nLauncher:\n" + launcher;
        tvColApplicazioni.setText(testoApplicazioni);

        String testoDelay = currentDelay + " secondi\n\nCountdown\nattivo";
        tvColDelay.setText(testoDelay);

        if (pkgLauncher.isEmpty()) {
            tvColStato.setText("⚠ Errore\n\nScegli Home");
        } else {
            tvColStato.setText("✓ Pronto\n\nConfigurato");
        }
    }

    // ADATTATORE GRAFICO PERSONALIZZATO PER SELEZIONE E BOLD
    private static class CustomAppAdapter extends ArrayAdapter<AppInfo> {
        private final LayoutInflater inflater;

        public CustomAppAdapter(@NonNull Context context, @NonNull List<AppInfo> objects) {
            super(context, 0, objects);
            inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createCustomView(position, convertView, parent, false);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createCustomView(position, convertView, parent, true);
        }

        private View createCustomView(int position, View convertView, ViewGroup parent, boolean isDropdown) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.spinner_item_custom, parent, false);
            }

            ImageView iconView = convertView.findViewById(R.id.spinner_app_icon);
            TextView labelView = convertView.findViewById(R.id.spinner_app_label);
            TextView packageView = convertView.findViewById(R.id.spinner_app_package);

            AppInfo app = getItem(position);

            if (app != null) {
                labelView.setText(app.getLabel());
                
                if (app.getPackageName().isEmpty()) {
                    // Impostazioni per "Nessuna Applicazione"
                    packageView.setText("Nessuna azione pianificata");
                    labelView.setTypeface(null, Typeface.NORMAL);
                    labelView.setTextColor(0xFF76FF03); // Verde acido per la voce nulla
                    iconView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                } else {
                    // Impostazioni per app reale selezionata
                    packageView.setText(app.getPackageName());
                    labelView.setTextColor(0xFFFFFFFF); // Bianco
                    
                    if (!isDropdown) {
                        // Se l'app è selezionata nel box chiuso, diventa BOLD
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
