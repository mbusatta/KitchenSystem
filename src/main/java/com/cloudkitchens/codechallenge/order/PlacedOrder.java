package com.cloudkitchens.codechallenge.order;

public class PlacedOrder {

    public final String id;
    public final String name;
    public final String temp;
    public final Integer shelfLife;
    public final float decayRate;

    public PlacedOrder(String id, String name, String temp, Integer shelfLife, float decayRate){
        this.id = id;
        this.name = name;
        this.temp = temp;
        this.shelfLife = shelfLife;
        this.decayRate = decayRate;
    }

}
