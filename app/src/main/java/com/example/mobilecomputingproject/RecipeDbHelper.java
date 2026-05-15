package com.example.mobilecomputingproject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;

/**
 * RecipeDbHelper manages the app's local SQLite database.
 *
 * This class stores two main types of data:
 * - Recipes
 * - Meal planner entries
 *
 * SQLite is used because recipes and planner entries should remain saved locally on the device,
 * even after the app is closed.
 */
public class RecipeDbHelper extends SQLiteOpenHelper {

    /*
     * DATABASE_VERSION must be increased whenever the database structure changes.
     *
     * Example:
     * - Adding a new column
     * - Adding a new table
     * - Changing table structure
     *
     * Android uses this number to decide whether onUpgrade() should run.
     */
    public static final int DATABASE_VERSION = 5;
    public static final String DATABASE_NAME = "foodahead.db";
    public static final String TABLE_RECIPES = "recipes";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_INGREDIENTS = "ingredients";
    public static final String COLUMN_INSTRUCTIONS = "instructions";
    public static final String COLUMN_IMAGE_URI = "image_uri";
    public static final String COLUMN_CALORIES = "calories";
    public static final String COLUMN_PROTEIN = "protein";
    public static final String COLUMN_CARBS = "carbs";
    public static final String COLUMN_FAT = "fat";
    public static final String COLUMN_TAGS = "tags";
    private static final String TABLE_MEAL_PLAN = "meal_plan";
    private static final String COLUMN_DAY = "day";
    private static final String COLUMN_HOUR = "hour";
    private static final String COLUMN_RECIPE_ID = "recipe_id";
    private static final String COLUMN_RECIPE_TITLE = "recipe_title";

    public RecipeDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Creates the database tables the first time the app database is created.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        /*
         * Recipe table stores all recipe fields.
         *
         * BaseColumns._ID gives a standard Android-compatible primary key column named "_id".
         */
        String createRecipesTable =
                "CREATE TABLE " + TABLE_RECIPES + " (" +
                        BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_TITLE + " TEXT, " +
                        COLUMN_INGREDIENTS + " TEXT, " +
                        COLUMN_INSTRUCTIONS + " TEXT, " +
                        COLUMN_IMAGE_URI + " TEXT, " +
                        COLUMN_CALORIES + " TEXT, " +
                        COLUMN_PROTEIN + " TEXT, " +
                        COLUMN_CARBS + " TEXT, " +
                        COLUMN_FAT + " TEXT, " +
                        COLUMN_TAGS + " TEXT)";

        db.execSQL(createRecipesTable);

        /*
         * Meal planner table stores one recipe per day/hour slot.
         *
         * recipe_title is stored alongside recipe_id so the planner can display the title quickly.
         * The recipe_id is still used when opening the recipe detail page.
         */
        String createMealPlanTable =
                "CREATE TABLE " + TABLE_MEAL_PLAN + " (" +
                        BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_DAY + " INTEGER, " +
                        COLUMN_HOUR + " INTEGER, " +
                        COLUMN_RECIPE_ID + " INTEGER, " +
                        COLUMN_RECIPE_TITLE + " TEXT)";

        db.execSQL(createMealPlanTable);
    }

    /**
     * Handles database upgrades.
     *
     * For this app, I just drop and recreate the tables. This is not professional. Duh.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*
         * Drop meal_plan first because it references recipes.
         */
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEAL_PLAN);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECIPES);
        onCreate(db);
    }

    /**
     * Handles database downgrades.
     *
     * Uses the same reset approach as onUpgrade().
     */
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    /**
     * Inserts a new recipe into the recipes table.
     *
     * Returns the new row ID, or -1 if insertion fails.
     */
    public long insertRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_INGREDIENTS, recipe.getIngredients());
        values.put(COLUMN_INSTRUCTIONS, recipe.getInstructions());
        values.put(COLUMN_IMAGE_URI, recipe.getImageUri());
        values.put(COLUMN_CALORIES, recipe.getCalories());
        values.put(COLUMN_PROTEIN, recipe.getProtein());
        values.put(COLUMN_CARBS, recipe.getCarbs());
        values.put(COLUMN_FAT, recipe.getFat());
        values.put(COLUMN_TAGS, recipe.getTags());

        return db.insert(TABLE_RECIPES, null, values);
    }

    /**
     * Returns all saved recipes, sorted alphabetically by title.
     */
    public ArrayList<Recipe> getAllRecipes() {
        ArrayList<Recipe> recipes = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_RECIPES,
                null,
                null,
                null,
                null,
                null,
                COLUMN_TITLE + " ASC"
        );

        /*
         * Convert each database row into a Recipe object.
         */
        while (cursor.moveToNext()) {
            recipes.add(createRecipeFromCursor(cursor));
        }

        cursor.close();
        return recipes;
    }

    /**
     * Finds and returns a single recipe by its SQLite ID.
     *
     * Returns null if no recipe exists with that ID.
     */
    public Recipe getRecipeById(long recipeId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_RECIPES,
                null,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(recipeId)},
                null,
                null,
                null
        );

        Recipe recipe = null;

        if (cursor.moveToFirst()) {
            recipe = createRecipeFromCursor(cursor);
        }

        cursor.close();
        return recipe;
    }

    /**
     * Converts the current row of a Cursor into a Recipe object.
     *
     * This avoids duplicating the same cursor-reading code in getAllRecipes() and getRecipeById().
     */
    private Recipe createRecipeFromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
        String ingredients = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INGREDIENTS));
        String instructions = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INSTRUCTIONS));
        String imageUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URI));
        String calories = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CALORIES));
        String protein = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROTEIN));
        String carbs = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CARBS));
        String fat = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FAT));
        String tags = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAGS));

        return new Recipe(
                id,
                title,
                ingredients,
                instructions,
                imageUri,
                calories,
                protein,
                carbs,
                fat,
                tags
        );
    }

    /**
     * Deletes a recipe from the database.
     *
     * Before deleting the recipe, any meal planner entries using that recipe are also removed.
     * This prevents the planner from pointing to a recipe that no longer exists.
     */
    public int deleteRecipe(long id) {
        SQLiteDatabase db = this.getWritableDatabase();

        clearMealPlanEntriesForRecipe(id);

        return db.delete(
                TABLE_RECIPES,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    /**
     * Updates an existing recipe.
     *
     * If the recipe title changes, the planner table is also updated so filled planner slots show
     * the new title.
     */
    public int updateRecipe(Recipe recipe) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, recipe.getTitle());
        values.put(COLUMN_INGREDIENTS, recipe.getIngredients());
        values.put(COLUMN_INSTRUCTIONS, recipe.getInstructions());
        values.put(COLUMN_IMAGE_URI, recipe.getImageUri());
        values.put(COLUMN_CALORIES, recipe.getCalories());
        values.put(COLUMN_PROTEIN, recipe.getProtein());
        values.put(COLUMN_CARBS, recipe.getCarbs());
        values.put(COLUMN_FAT, recipe.getFat());
        values.put(COLUMN_TAGS, recipe.getTags());

        updateMealPlanRecipeTitle(recipe.getId(), recipe.getTitle());

        return db.update(
                TABLE_RECIPES,
                values,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(recipe.getId())}
        );
    }

    /**
     * Returns a unique list of all tags used by saved recipes.
     *
     * Tags are stored inside each recipe as comma-separated text, so this method:
     * - Reads every recipe's tags field.
     * - Splits each tags string by commas.
     * - Trims whitespace.
     * - Removes duplicates case-insensitively.
     *
     * Used by AddRecipeFragment and RecipeFragment for tag selection/filtering.
     */
    public ArrayList<String> getAllTags() {
        ArrayList<String> tagsList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_RECIPES,
                new String[]{COLUMN_TAGS},
                null,
                null,
                null,
                null,
                COLUMN_TAGS + " ASC"
        );

        while (cursor.moveToNext()) {
            String tags = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAGS));

            if (tags != null && !tags.trim().isEmpty()) {
                String[] splitTags = tags.split(",");

                for (String tag : splitTags) {
                    String cleanedTag = tag.trim();

                    if (!cleanedTag.isEmpty() && !containsIgnoreCase(tagsList, cleanedTag)) {
                        tagsList.add(cleanedTag);
                    }
                }
            }
        }

        cursor.close();
        return tagsList;
    }

    /**
     * Checks whether a list already contains a value, ignoring case.
     *
     * This prevents duplicate tags like:
     * - Vegan
     * - vegan
     * - VEGAN
     */
    private boolean containsIgnoreCase(ArrayList<String> list, String value) {
        for (String item : list) {
            if (item.equalsIgnoreCase(value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Saves a recipe into a specific meal planner slot.
     *
     * If a meal already exists at the selected day/hour, it is updated.
     * If the slot is empty, a new row is inserted.
     */
    public void saveMealPlan(int day, int hour, long recipeId, String recipeTitle) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_DAY, day);
        values.put(COLUMN_HOUR, hour);
        values.put(COLUMN_RECIPE_ID, recipeId);
        values.put(COLUMN_RECIPE_TITLE, recipeTitle);

        /*
         * Check whether this planner slot already exists.
         */
        Cursor cursor = db.rawQuery(
                "SELECT " + BaseColumns._ID + " FROM " + TABLE_MEAL_PLAN +
                        " WHERE " + COLUMN_DAY + "=? AND " + COLUMN_HOUR + "=?",
                new String[]{String.valueOf(day), String.valueOf(hour)}
        );

        if (cursor.moveToFirst()) {
            /*
             * Slot already exists, so update it.
             */
            db.update(
                    TABLE_MEAL_PLAN,
                    values,
                    COLUMN_DAY + "=? AND " + COLUMN_HOUR + "=?",
                    new String[]{String.valueOf(day), String.valueOf(hour)}
            );
        } else {
            /*
             * Slot does not exist yet, so insert it.
             */
            db.insert(TABLE_MEAL_PLAN, null, values);
        }

        cursor.close();
    }

    /**
     * Returns the recipe title saved in a specific meal planner slot.
     *
     * Returns an empty string if the slot has no meal.
     */
    public String getMealForSlot(int day, int hour) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_RECIPE_TITLE +
                        " FROM " + TABLE_MEAL_PLAN +
                        " WHERE " + COLUMN_DAY + "=? AND " + COLUMN_HOUR + "=?",
                new String[]{String.valueOf(day), String.valueOf(hour)}
        );

        if (cursor.moveToFirst()) {
            String title = cursor.getString(0);
            cursor.close();
            return title;
        }

        cursor.close();
        return "";
    }

    /**
     * Returns the recipe ID saved in a specific meal planner slot.
     *
     * Returns -1 if the slot has no saved recipe.
     */
    public long getMealRecipeIdForSlot(int day, int hour) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_RECIPE_ID +
                        " FROM " + TABLE_MEAL_PLAN +
                        " WHERE " + COLUMN_DAY + "=? AND " + COLUMN_HOUR + "=?",
                new String[]{String.valueOf(day), String.valueOf(hour)}
        );

        if (cursor.moveToFirst()) {
            long recipeId = cursor.getLong(0);
            cursor.close();
            return recipeId;
        }

        cursor.close();
        return -1;
    }

    /**
     * Clears one meal planner slot.
     */
    public void clearMealSlot(int day, int hour) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.delete(
                TABLE_MEAL_PLAN,
                COLUMN_DAY + "=? AND " + COLUMN_HOUR + "=?",
                new String[]{String.valueOf(day), String.valueOf(hour)}
        );
    }

    /**
     * Removes all meal planner entries that reference a deleted recipe.
     *
     * This is called before deleting a recipe.
     */
    private void clearMealPlanEntriesForRecipe(long recipeId) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.delete(
                TABLE_MEAL_PLAN,
                COLUMN_RECIPE_ID + "=?",
                new String[]{String.valueOf(recipeId)}
        );
    }

    /**
     * Updates the displayed recipe title in all planner entries for a recipe.
     *
     * This keeps the meal planner consistent if the user edits a recipe's name.
     */
    private void updateMealPlanRecipeTitle(long recipeId, String newTitle) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_RECIPE_TITLE, newTitle);

        db.update(
                TABLE_MEAL_PLAN,
                values,
                COLUMN_RECIPE_ID + "=?",
                new String[]{String.valueOf(recipeId)}
        );
    }

    /**
     * Deletes all saved recipes.
     */
    public void clearAllRecipes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECIPES, null, null);
    }

    /**
     * Deletes all meal planner entries.
     */
    public void clearMealPlan() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MEAL_PLAN, null, null);
    }

    /**
     * Clears all locally stored app data managed by SQLite.
     *
     * Meal planner entries are cleared first so planner data does not reference deleted recipes.
     */
    public void clearAllData() {
        clearMealPlan();
        clearAllRecipes();
    }
}