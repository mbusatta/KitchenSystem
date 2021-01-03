package com.cloudkitchens.codechallenge.order;

import com.cloudkitchens.codechallenge.shelf.Shelf;

public class ShelfPlacedOrder {

    public final CookedPlacedOrder cookedPlacedOrder;
    public final String id;
    public final Shelf.ShelfType shelfType;
    public final long totalExpirationMillis;

    public ShelfPlacedOrder(Shelf shelf, CookedPlacedOrder cookedPlacedOrder){
        this.cookedPlacedOrder = cookedPlacedOrder;
        this.id = cookedPlacedOrder.id;
        this.shelfType = shelf.type;
        this.totalExpirationMillis = calcTotalExpirationMillis();
    }

    public float orderValue(){

        long orderAge = (System.currentTimeMillis() - cookedPlacedOrder.readySinceMillis)/1000;

        return (cookedPlacedOrder.placedOrder.shelfLife
                - (cookedPlacedOrder.placedOrder.decayRate * orderAge * shelfDecayModifier()))
                    / cookedPlacedOrder.placedOrder.shelfLife;

    }

    public boolean wasted(){
        return (orderValue() <= 0);
    }

    public long remainingForWasteMillis(){
        return totalExpirationMillis - (System.currentTimeMillis() - cookedPlacedOrder.readySinceMillis);
    }

    private int shelfDecayModifier(){
        return (shelfType.equals(Shelf.ShelfType.OVERFLOW)) ? 2 : 1;
    }

    /*
        Based on shelfLife, decay, and decayModifier we can calculate the time this order goes to waste (expired).
        The result here being in milliseconds
     */
    private long calcTotalExpirationMillis(){
        return Float.valueOf(
                ((cookedPlacedOrder.placedOrder.shelfLife/ cookedPlacedOrder.placedOrder.decayRate)/shelfDecayModifier()) * 1000)
                    .longValue();
    }

}
