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

public class RecipeFragment extends Fragment {

    private RecyclerView recipeRecyclerView;
    private RecipeAdapter recipeAdapter;

    private ArrayList<Recipe> allRecipes;
    private ArrayList<Recipe> displayedRecipes;
    private ArrayList<String> selectedTags;

    private Button addRecipeButton;
    private Button filterSortButton;

    private RecipeDbHelper dbHelper;

    private int sortMode = 0;
    // 0 = A-Z
    // 1 = Z-A

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

    private void loadRecipes() {
        allRecipes = dbHelper.getAllRecipes();
        applyFiltersAndSorting();
    }

    private void applyFiltersAndSorting() {
        displayedRecipes = new ArrayList<>();

        for (Recipe recipe : allRecipes) {
            if (selectedTags.isEmpty() || recipeMatchesSelectedTags(recipe)) {
                displayedRecipes.add(recipe);
            }
        }

        if (sortMode == 0) {
            Collections.sort(displayedRecipes, (r1, r2) ->
                    r1.getTitle().compareToIgnoreCase(r2.getTitle())
            );
        } else {
            Collections.sort(displayedRecipes, (r1, r2) ->
                    r2.getTitle().compareToIgnoreCase(r1.getTitle())
            );
        }

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

        updateFilterSortButtonText();
    }

    private boolean recipeMatchesSelectedTags(Recipe recipe) {
        String recipeTags = recipe.getTags();

        if (recipeTags == null || recipeTags.trim().isEmpty()) {
            return false;
        }

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
                        sortMode = 0;
                        applyFiltersAndSorting();
                    } else if (which == 1) {
                        sortMode = 1;
                        applyFiltersAndSorting();
                    } else if (which == 2) {
                        showTagFilterDialog();
                    } else if (which == 3) {
                        clearFilters();
                    }
                })
                .show();
    }

    private void showTagFilterDialog() {
        ArrayList<String> allTags = dbHelper.getAllTags();

        if (allTags.isEmpty()) {
            Toast.makeText(getContext(), "No tags found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] tagArray = allTags.toArray(new String[0]);
        boolean[] checkedItems = new boolean[tagArray.length];

        for (int i = 0; i < tagArray.length; i++) {
            checkedItems[i] = containsSelectedTag(tagArray[i]);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Filter by Tags")
                .setMultiChoiceItems(tagArray, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Apply", (dialog, which) -> {
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

    private boolean containsSelectedTag(String tag) {
        for (String selectedTag : selectedTags) {
            if (selectedTag.equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }

    private void clearFilters() {
        selectedTags.clear();
        sortMode = 0;
        applyFiltersAndSorting();

        Toast.makeText(getContext(), "Filters cleared", Toast.LENGTH_SHORT).show();
    }

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