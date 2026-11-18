/*
 * Copyright (c) 2025 Amr Aldeeb @Eluea
 * GitHub: https://github.com/Eluea
 * Telegram: https://t.me/Eluea
 *
 * Licensed under the GPLv3.
 */
package tn.eluea.kgpt.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tn.eluea.kgpt.R;
import tn.eluea.kgpt.ui.main.MainActivity;
import tn.eluea.kgpt.util.LocaleHelper;
import tn.eluea.kgpt.util.MaterialYouManager;

public class LanguageSelectionActivity extends AppCompatActivity implements LanguageAdapter.OnLanguageSelectedListener {

    private LanguageAdapter adapter;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply theme using MaterialYouManager as used in other activities

        setContentView(R.layout.activity_language_selection);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        etSearch = findViewById(R.id.et_search);
        RecyclerView rvLanguages = findViewById(R.id.rv_languages);
        rvLanguages.setLayoutManager(new LinearLayoutManager(this));

        List<LanguageItem> languages = getLanguages();
        adapter = new LanguageAdapter(this, languages, this);
        rvLanguages.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private List<LanguageItem> getLanguages() {
    List<LanguageItem> items = new ArrayList<>();
    String currentLangCode = LocaleHelper.getLanguage(this);

    items.add(new LanguageItem("en", "English", "", "en".equals(currentLangCode)));
    items.add(new LanguageItem("ru", "Русский", "Russian", "ru".equals(currentLangCode)));

    return items;
}

@Override
protected void attachBaseContext(android.content.Context newBase) {
    super.attachBaseContext(LocaleHelper.onAttach(newBase));
}


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLanguageSelected(LanguageItem language) {
        LocaleHelper.setLocale(this, language.getCode());

        // Restart app to apply changes fully
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
