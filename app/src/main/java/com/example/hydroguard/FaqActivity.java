package com.example.hydroguard;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class FaqActivity extends AppCompatActivity {

    private View faqRow1, faqRow2, faqRow3, faqRow4, faqRow5;
    private ImageView faqArrow1, faqArrow2, faqArrow3, faqArrow4, faqArrow5;
    private TextView faqAnswer1, faqAnswer2, faqAnswer3, faqAnswer4, faqAnswer5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        faqRow1 = findViewById(R.id.faqRow1);
        faqRow2 = findViewById(R.id.faqRow2);
        faqRow3 = findViewById(R.id.faqRow3);
        faqRow4 = findViewById(R.id.faqRow4);
        faqRow5 = findViewById(R.id.faqRow5);

        faqArrow1 = findViewById(R.id.faqArrow1);
        faqArrow2 = findViewById(R.id.faqArrow2);
        faqArrow3 = findViewById(R.id.faqArrow3);
        faqArrow4 = findViewById(R.id.faqArrow4);
        faqArrow5 = findViewById(R.id.faqArrow5);

        faqAnswer1 = findViewById(R.id.faqAnswer1);
        faqAnswer2 = findViewById(R.id.faqAnswer2);
        faqAnswer3 = findViewById(R.id.faqAnswer3);
        faqAnswer4 = findViewById(R.id.faqAnswer4);
        faqAnswer5 = findViewById(R.id.faqAnswer5);

        setupFaqToggle(faqRow1, faqArrow1, faqAnswer1);
        setupFaqToggle(faqRow2, faqArrow2, faqAnswer2);
        setupFaqToggle(faqRow3, faqArrow3, faqAnswer3);
        setupFaqToggle(faqRow4, faqArrow4, faqAnswer4);
        setupFaqToggle(faqRow5, faqArrow5, faqAnswer5);
    }

    private void setupFaqToggle(@NonNull View row, @NonNull ImageView arrow, @NonNull TextView answer) {
        answer.setVisibility(View.GONE);
        arrow.setRotation(0f);

        row.setOnClickListener(v -> {
            boolean isOpen = answer.getVisibility() == View.VISIBLE;
            answer.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            arrow.animate().rotation(isOpen ? 0f : 180f).setDuration(180).start();
        });
    }
}
