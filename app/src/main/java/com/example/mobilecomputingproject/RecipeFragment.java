package com.example.mobilecomputingproject;

import android.widget.Button;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class RecipeFragment extends Fragment {

    private RecyclerView recipeRecyclerView;
    private RecipeAdapter recipeAdapter;
    private ArrayList<Recipe> recipeList;
    private Button addRecipeButton;

    public RecipeFragment() {
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

        recipeRecyclerView = view.findViewById(R.id.recipe_recycler_view);
        recipeRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        RecipeDbHelper dbHelper = new RecipeDbHelper(requireContext());
        recipeList = dbHelper.getAllRecipes();

        recipeAdapter = new RecipeAdapter(recipeList, recipe -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, RecipeDetailFragment.newInstance(recipe))
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle(recipe.getTitle());
        });

        recipeRecyclerView.setAdapter(recipeAdapter);

        addRecipeButton = view.findViewById(R.id.add_recipe_button);

        addRecipeButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new AddRecipeFragment())
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle("Add Recipe");
        });
    }
}