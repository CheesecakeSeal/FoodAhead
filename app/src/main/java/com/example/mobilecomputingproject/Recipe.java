package com.example.mobilecomputingproject;

public class Recipe {
    private long id;
    private String title;
    private String ingredients;
    private String instructions;
    private String imageUri;
    private String calories;
    private String protein;
    private String carbs;
    private String fat;
    private String tags;

    public Recipe(long id, String title, String ingredients, String instructions,
                  String imageUri, String calories, String protein, String carbs, String fat,
                  String tags) {
        this.id = id;
        this.title = title;
        this.ingredients = ingredients;
        this.instructions = instructions;
        this.imageUri = imageUri;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.tags = tags;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getIngredients() {
        return ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getImageUri() {
        return imageUri;
    }

    public String getCalories() {
        return calories;
    }

    public String getProtein() {
        return protein;
    }

    public String getCarbs() {
        return carbs;
    }

    public String getFat() {
        return fat;
    }

    public String getTags() {
        return tags;
    }
}