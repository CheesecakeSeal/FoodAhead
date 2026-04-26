package com.example.mobilecomputingproject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;

public class RecipeDbHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
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

    public RecipeDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
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
                        COLUMN_FAT + " TEXT)";

        db.execSQL(createRecipesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECIPES);
        onCreate(db);
    }

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

        return db.insert(TABLE_RECIPES, null, values);
    }

    public ArrayList<Recipe> getAllRecipes() {
        ArrayList<Recipe> recipes = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        String sortOrder = COLUMN_TITLE + " ASC";

        Cursor cursor = db.query(
                TABLE_RECIPES,
                null,
                null,
                null,
                null,
                null,
                sortOrder
        );

        while (cursor.moveToNext()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
            String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
            String ingredients = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INGREDIENTS));
            String instructions = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INSTRUCTIONS));
            String imageUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URI));
            String calories = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CALORIES));
            String protein = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROTEIN));
            String carbs = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CARBS));
            String fat = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FAT));

            recipes.add(new Recipe(
                    id,
                    title,
                    ingredients,
                    instructions,
                    imageUri,
                    calories,
                    protein,
                    carbs,
                    fat
            ));
        }

        cursor.close();
        return recipes;
    }

    public int deleteRecipe(long id) {
        SQLiteDatabase db = this.getWritableDatabase();

        return db.delete(
                TABLE_RECIPES,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

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

        return db.update(
                TABLE_RECIPES,
                values,
                BaseColumns._ID + " = ?",
                new String[]{String.valueOf(recipe.getId())}
        );
    }
}