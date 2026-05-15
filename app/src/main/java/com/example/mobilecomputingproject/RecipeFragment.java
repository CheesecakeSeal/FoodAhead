package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;

/**
 * RecipeFragment displays the Recipe Manager screen.
 *
 * This screen is responsible for:
 * - Loading saved recipes from SQLite.
 * - Displaying recipes in a RecyclerView.
 * - Opening the Add Recipe screen.
 * - Opening the Recipe Detail screen when a recipe is tapped.
 * - Sorting recipes alphabetically.
 * - Filtering recipes by tags.
 */
public class RecipeFragment extends Fragment {

    /*
     * RecyclerView displays the scrollable list of recipe items.
     * RecipeAdapter controls how each recipe row is displayed.
     */
    private RecyclerView recipeRecyclerView;
    private RecipeAdapter recipeAdapter;

    /*
     * allRecipes stores every recipe loaded from SQLite.
     * displayedRecipes stores the recipes currently visible after filtering/sorting.
     * selectedTags stores the active tag filters chosen by the user.
     */
    private ArrayList<Recipe> allRecipes;
    private ArrayList<Recipe> displayedRecipes;
    private ArrayList<String> selectedTags;
    private Button addRecipeButton;
    private Button filterSortButton;
    private RecipeDbHelper dbHelper;

    /*
     * sortMode controls the current alphabetical order.
     *
     * 0 = A-Z
     * 1 = Z-A
     */
    private int sortMode = 0;

    public RecipeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_recipe, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new RecipeDbHelper(requireContext());

        selectedTags = new ArrayList<>();

        recipeRecyclerView = view.findViewById(R.id.recipe_recycler_view);
        recipeRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        addRecipeButton = view.findViewById(R.id.add_recipe_button);
        filterSortButton = view.findViewById(R.id.filter_sort_button);

        loadRecipes();

        addRecipeButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new AddRecipeFragment())
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle("Add Recipe");
        });

        filterSortButton.setOnClickListener(v -> showFilterSortDialog());
    }

    /**
     * Loads all recipes from the database.
     *
     * Filtering and sorting are applied after loading so the displayed list always respects the
     * current selected tags and sort mode.
     */
    private void loadRecipes() {
        allRecipes = dbHelper.getAllRecipes();
        applyFiltersAndSorting();
    }

    /**
     * Applies the current tag filters and sort mode, then refreshes the RecyclerView.
     */
    private void applyFiltersAndSorting() {
        displayedRecipes = new ArrayList<>();

        /*
         * First apply tag filtering.
         *
         * If no tags are selected, every recipe is displayed.
         * If tags are selected, only recipes matching at least one selected tag are displayed.
         */
        for (Recipe recipe : allRecipes) {
            if (selectedTags.isEmpty() || recipeMatchesSelectedTags(recipe)) {
                displayedRecipes.add(recipe);
            }
        }

        /*
         * Then apply alphabetical sorting.
         */
        if (sortMode == 0) {
            Collections.sort(displayedRecipes, (r1, r2) ->
                    r1.getTitle().compareToIgnoreCase(r2.getTitle())
            );
        } else {
            Collections.sort(displayedRecipes, (r1, r2) ->
                    r2.getTitle().compareToIgnoreCase(r1.getTitle())
            );
        }

        /*
         * Create/update the adapter with the filtered and sorted recipe list.
         *
         * When a recipe is clicked, open the detail screen for that recipe.
         */
        recipeAdapter = new RecipeAdapter(displayedRecipes, recipe -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, RecipeDetailFragment.newInstance(recipe))
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle(recipe.getTitle());
        });

        recipeRecyclerView.setAdapter(recipeAdapter);

        /*
         * Update the Filter / Sort button so the user can see whether filtering/sorting is active.
         */
        updateFilterSortButtonText();
    }

    /**
     * Checks whether a recipe matches the currently selected tag filters.
     *
     * This uses "match any" behaviour:
     * - If the user selects Vegan and High Protein,
     * - a recipe with either Vegan or High Protein will be shown.
     */
    private boolean recipeMatchesSelectedTags(Recipe recipe) {
        String recipeTags = recipe.getTags();

        if (recipeTags == null || recipeTags.trim().isEmpty()) {
            return false;
        }

        /*
         * Tags are stored in SQLite as comma-separated text, so split them before comparing.
         */
        String[] splitTags = recipeTags.split(",");

        for (String recipeTag : splitTags) {
            String cleanedRecipeTag = recipeTag.trim();

            for (String selectedTag : selectedTags) {
                if (cleanedRecipeTag.equalsIgnoreCase(selectedTag.trim())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Shows the main Filter / Sort menu.
     */
    private void showFilterSortDialog() {
        String[] options = {
                "Sort A-Z",
                "Sort Z-A",
                "Filter by Tags",
                "Clear Filters"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Filter / Sort Recipes")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        /*
                         * Sort alphabetically from A to Z.
                         */
                        sortMode = 0;
                        applyFiltersAndSorting();
                    } else if (which == 1) {
                        /*
                         * Sort alphabetically from Z to A.
                         */
                        sortMode = 1;
                        applyFiltersAndSorting();
                    } else if (which == 2) {
                        /*
                         * Open a second dialog where the user can choose tag filters.
                         */
                        showTagFilterDialog();
                    } else if (which == 3) {
                        /*
                         * Reset to the default view.
                         */
                        clearFilters();
                    }
                })
                .show();
    }

    /**
     * Shows a multi-choice dialog containing all tags used by saved recipes.
     *
     * The user can select one or more tags to filter the recipe list.
     */
    private void showTagFilterDialog() {
        ArrayList<String> allTags = dbHelper.getAllTags();

        if (allTags.isEmpty()) {
            Toast.makeText(getContext(), "No tags found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] tagArray = allTags.toArray(new String[0]);
        boolean[] checkedItems = new boolean[tagArray.length];

        /*
         * Pre-check tags that are already selected, so reopening the dialog reflects the current
         * filter state.
         */
        for (int i = 0; i < tagArray.length; i++) {
            checkedItems[i] = containsSelectedTag(tagArray[i]);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Filter by Tags")
                .setMultiChoiceItems(tagArray, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    /*
                     * Replace the current selected tag list with the checked tags from the dialog.
                     */
                    selectedTags.clear();

                    for (int i = 0; i < tagArray.length; i++) {
                        if (checkedItems[i]) {
                            selectedTags.add(tagArray[i]);
                        }
                    }

                    applyFiltersAndSorting();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Checks whether a tag is currently selected.
     *
     * Used to pre-check boxes in the tag filter dialog.
     */
    private boolean containsSelectedTag(String tag) {
        for (String selectedTag : selectedTags) {
            if (selectedTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clears all tag filters and resets sorting to A-Z.
     */
    private void clearFilters() {
        selectedTags.clear();
        sortMode = 0;
        applyFiltersAndSorting();

        Toast.makeText(getContext(), "Filters cleared", Toast.LENGTH_SHORT).show();
    }

    /**
     * Updates the Filter / Sort button text to reflect the current view state.
     *
     * This gives quick feedback about whether the list is filtered or sorted differently.
     */
    private void updateFilterSortButtonText() {
        if (selectedTags.isEmpty() && sortMode == 0) {
            filterSortButton.setText("Filter / Sort");
            return;
        }

        String sortText = sortMode == 0 ? "A-Z" : "Z-A";

        if (selectedTags.isEmpty()) {
            filterSortButton.setText("Sort: " + sortText);
        } else {
            filterSortButton.setText("Filtered (" + selectedTags.size() + ") / " + sortText);
        }
    }
}