package com.example.mobilecomputingproject;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * MainActivity is the single Activity used by the app.
 *
 * MainActivity is responsible for:
 * - Applying the saved light/dark theme.
 * - Hosting fragments inside the fragment container.
 * - Handling bottom navigation.
 * - Updating the custom toolbar title.
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private TextView toolbarTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /*
         * Apply the saved theme before setContentView().
         *
         * This is important because changing the theme after the layout is drawn can cause
         * flickering or force the Activity to recreate unnecessarily.
         */
        applySavedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbarTitle = findViewById(R.id.toolbar_title);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        /*
         * Bottom navigation controls the main areas of the app.
         *
         * Each selected item creates and loads the corresponding fragment into the
         * fragment_container.
         */
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            if (item.getItemId() == R.id.nav_home) {
                selectedFragment = new HomeFragment();
                setToolbarTitle("Home");
            } else if (item.getItemId() == R.id.nav_recipes) {
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

        /*
         * Only load the default Home screen when the Activity is first created.
         *
         * If savedInstanceState is not null, Android is restoring the Activity after something
         * like a rotation or theme change, so we should not force the user back to Home manually.
         */
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        /*
         * Keep the toolbar title correct when the user presses the phone's back button.
         *
         * Without this, returning from a recipe detail page could leave the recipe name in the
         * toolbar even though the visible fragment has changed.
         */
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);

            updateToolbarForFragment(currentFragment);
        });
    }

    /**
     * Replaces the current fragment with the provided fragment.
     *
     * This method is used for the main bottom-navigation screens. These transactions are not added
     * to the back stack because bottom navigation should behave like switching between main tabs.
     */
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

    /**
     * Updates the toolbar title based on the currently visible fragment.
     *
     * Some fragments, like RecipeDetailFragment, set their own toolbar title because the title
     * depends on the selected recipe name rather than a fixed screen name.
     */
    private void updateToolbarForFragment(Fragment currentFragment) {
        if (currentFragment instanceof HomeFragment) {
            setToolbarTitle("Home");
        } else if (currentFragment instanceof RecipeFragment) {
            setToolbarTitle("Recipe Manager");
        } else if (currentFragment instanceof MealPlannerFragment) {
            setToolbarTitle("Meal Planner");
        } else if (currentFragment instanceof SettingsFragment) {
            setToolbarTitle("Settings");
        } else if (currentFragment instanceof AddRecipeFragment) {
            setToolbarTitle("Add Recipe");
        } else if (currentFragment instanceof RecipeDetailFragment) {
        }
    }

    /**
     * Allows fragments to update the toolbar title.
     *
     * Example:
     * - AddRecipeFragment sets "Add Recipe"
     * - RecipeDetailFragment sets the selected recipe name
     */
    public void setToolbarTitle(String title) {
        toolbarTitle.setText(title);
    }

    /**
     * Applies the user's saved appearance preference.
     *
     * light_mode = true  -> light mode
     * light_mode = false -> dark mode
     *
     * Dark mode is the default because I value people's eyes
     */
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