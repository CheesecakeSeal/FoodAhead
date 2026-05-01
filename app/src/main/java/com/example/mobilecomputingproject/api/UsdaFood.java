package com.example.mobilecomputingproject.api;

import java.util.List;

public class UsdaFood {
    private String description;
    private String dataType;
    private List<UsdaNutrient> foodNutrients;

    public String getDescription() {
        return description;
    }

    public String getDataType() {
        return dataType;
    }

    public List<UsdaNutrient> getFoodNutrients() {
        return foodNutrients;
    }
}