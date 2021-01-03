package com.cloudkitchens.codechallenge.shelf;

import com.cloudkitchens.codechallenge.shelf.Shelf;

import java.util.HashMap;

public class ShelvesProperties {

    private HashMap<Shelf.ShelfType, Integer> shelvesProperties = new HashMap<>(3);

    public ShelvesProperties(int hotMaxCapacity, int coldMaxCapacity, int frozenMaxCapacity, int overflowMaxCapacity){
        shelvesProperties.put(Shelf.ShelfType.HOT, hotMaxCapacity);
        shelvesProperties.put(Shelf.ShelfType.COLD, coldMaxCapacity);
        shelvesProperties.put(Shelf.ShelfType.FROZEN, frozenMaxCapacity);
        shelvesProperties.put(Shelf.ShelfType.OVERFLOW, overflowMaxCapacity);
    }

    public int getMaxCapacityByType(Shelf.ShelfType stype){
        return shelvesProperties.get(stype);
    }
}
