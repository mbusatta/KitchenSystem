package com.cloudkitchens.codechallenge.order;

import com.cloudkitchens.codechallenge.shelf.Shelf;

public class CookedPlacedOrder {

    public final PlacedOrder placedOrder;
    public final String id;
    public final long readySinceMillis;

    public CookedPlacedOrder(PlacedOrder placedOrder){
        readySinceMillis = System.currentTimeMillis();
        this.placedOrder = placedOrder;
        this.id = placedOrder.id;
    }

    public Shelf.ShelfType preferredShelfType(){
        switch (placedOrder.temp){
            case "hot":
                return Shelf.ShelfType.HOT;
            case "cold":
                return Shelf.ShelfType.COLD;
            case "frozen":
                return Shelf.ShelfType.FROZEN;
            default:
                throw new RuntimeException(String.format(
                        "The temperature %s has no supported shelf - order if %s", placedOrder.temp, placedOrder.id));
        }
    }

}
