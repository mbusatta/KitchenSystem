package com.cloudkitchens.codechallenge.shelf;

import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.ShelfPlacedOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Shelf {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public final int maxCapacity;
    public final ShelfType type;
    private final HashMap<String, ShelfPlacedOrder> map;

    public static enum ShelfType{
        COLD,
        HOT,
        FROZEN,
        OVERFLOW
    }

    public Shelf(ShelfType type, int maxCapacity){
        this.type = type;
        this.maxCapacity = maxCapacity;
        this.map = new HashMap<>(maxCapacity);
    }

    public ShelfPlacedOrder placeOrder(CookedPlacedOrder cookedPlacedOrder){
        if (isFull())
            throw new RuntimeException(
                    String.format("The shelf %s is full and will not allow the placement of the order %s", type, cookedPlacedOrder.id));
        ShelfPlacedOrder placedOrder = new ShelfPlacedOrder(this, cookedPlacedOrder);
        map.put(cookedPlacedOrder.id, placedOrder);
        log.info("{} Shelf: Order {} has been added to the shelf", type, cookedPlacedOrder.id);
        return placedOrder;
    }

    public ShelfPlacedOrder takeOrder(ShelfPlacedOrder placedOrder){
        return takeOrder(placedOrder.id);
    }

    public ShelfPlacedOrder takeOrder(String orderId){
        log.info("{} Shelf: Order {} has been removed from the shelf", type, orderId);
        return map.remove(orderId);
    }

    public boolean isFull(){
        return (map.size() == maxCapacity);
    }

    public int availableCapacity(){
        return (maxCapacity - map.size());
    }

    public List<ShelfPlacedOrder> getShelfPlacedOrdersOrderedByOrderValue(){
        return map.values().stream()
                .sorted(Comparator.comparingLong(e -> e.remainingForWasteMillis()))
                .collect(Collectors.toList());
    }

    public ShelfPlacedOrder removeRandom(){
        int randIndex = new Random().nextInt(map.size());
        String randKey = new ArrayList<>(map.keySet()).get(randIndex);
        return takeOrder(randKey);
    }

    public int size(){
        return map.size();
    }

}
