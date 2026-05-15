package com.example.mobilecomputingproject;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter for displaying saved recipes in the Recipe Manager screen.
 *
 * The adapter is responsible for taking a List<Recipe> and binding each Recipe object to the
 * recipe_item.xml layout.
 *
 * Each item displays:
 * - Recipe image thumbnail
 * - Recipe title
 * - Recipe tags
 *
 * Clicking an item is passed back to RecipeFragment through OnRecipeClickListener.
 */
public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    /**
     * Listener interface used to notify RecipeFragment when a recipe item is clicked.
     *
     * This keeps navigation logic out of the adapter. The adapter only handles displaying list
     * items, while the fragment decides what should happen when a recipe is selected.
     */
    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }
    private final List<Recipe> recipes;
    private final OnRecipeClickListener listener;

    public RecipeAdapter(List<Recipe> recipes, OnRecipeClickListener listener) {
        this.recipes = recipes;
        this.listener = listener;
    }

    /**
     * Creates a new ViewHolder by inflating recipe_item.xml.
     *
     * RecyclerView calls this only when it needs a new row layout. Existing rows are reused for
     * better performance.
     */
    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recipe_item, parent, false);

        return new RecipeViewHolder(view);
    }

    /**
     * Binds recipe data to one row in the RecyclerView.
     *
     * This method is called whenever RecyclerView needs to display a recipe at a specific position.
     */
    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        Recipe recipe = recipes.get(position);

        /*
         * Display the recipe title.
         */
        holder.title.setText(recipe.getTitle());

        /*
         * Display tags under the recipe name.
         *
         * If no tags were entered, show "Tags: None" so the UI remains consistent and the user
         * understands that the recipe simply has no tags.
         */
        if (recipe.getTags() != null && !recipe.getTags().trim().isEmpty()) {
            holder.tags.setText("Tags: " + recipe.getTags());
        } else {
            holder.tags.setText("Tags: None");
        }

        /*
         * Display the selected recipe image if one exists.
         *
         * Images are stored as URI strings in SQLite. If the URI is missing, a default gallery icon
         * is shown instead.
         */
        if (recipe.getImageUri() != null && !recipe.getImageUri().isEmpty()) {
            holder.image.setImageURI(Uri.parse(recipe.getImageUri()));
        } else {
            holder.image.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        /*
         * Forward click events to the fragment.
         *
         * The adapter does not directly open RecipeDetailFragment because navigation should be
         * controlled by the fragment/activity layer.
         */
        holder.itemView.setOnClickListener(v -> listener.onRecipeClick(recipe));
    }

    /**
     * Returns the number of recipes currently being displayed.
     */
    @Override
    public int getItemCount() {
        return recipes.size();
    }

    /**
     * ViewHolder stores references to the views inside one recipe list item.
     *
     * This avoids repeatedly calling findViewById while scrolling, which improves RecyclerView
     * performance.
     */
    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView tags;
        ImageView image;

        public RecipeViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.recipe_title);
            tags = itemView.findViewById(R.id.recipe_tags);
            image = itemView.findViewById(R.id.recipe_item_image);
        }
    }
}