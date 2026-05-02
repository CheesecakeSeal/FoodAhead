package com.example.mobilecomputingproject;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private TextView toolbarTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbarTitle = findViewById(R.id.toolbar_title);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            loadFragment(new RecipeFragment());
            setToolbarTitle("Recipe Manager");
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            if (item.getItemId() == R.id.nav_recipes) {
                selectedFragment = new RecipeFragment();
                setToolbarTitle("Recipe Manager");
            } else if (item.getItemId() == R.id.nav_planner) {
                selectedFragment = new MealPlannerFragment();
                setToolbarTitle("Meal Planner");
            } else if (item.getItemId() == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
                setToolbarTitle("Settings");
            }

            return loadFragment(selectedFragment);
        });

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof RecipeFragment) {
                setToolbarTitle("Recipe Manager");
            } else if (currentFragment instanceof MealPlannerFragment) {
                setToolbarTitle("Meal Planner");
            } else if (currentFragment instanceof SettingsFragment) {
                setToolbarTitle("Settings");
            } else if (currentFragment instanceof AddRecipeFragment) {
                setToolbarTitle("Add Recipe");
            }
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    public void setToolbarTitle(String title) {

        toolbarTitle.setText(title);
    }

    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("FoodAheadPrefs", MODE_PRIVATE);
        boolean lightMode = prefs.getBoolean("light_mode", false);

        AppCompatDelegate.setDefaultNightMode(
                lightMode
                        ? AppCompatDelegate.MODE_NIGHT_NO
                        : AppCompatDelegate.MODE_NIGHT_YES
        );
    }
}