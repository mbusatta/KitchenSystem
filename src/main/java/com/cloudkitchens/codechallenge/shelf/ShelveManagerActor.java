package com.cloudkitchens.codechallenge.shelf;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.ShelfPlacedOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class ShelveManagerActor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public interface Message {}

    public interface Command extends Message{}

    public static class PlaceCookedOrder implements Command {
        public final CookedPlacedOrder cookedPlacedOrder;
        public final ActorRef<OrderActor.Message> order;

        public PlaceCookedOrder(ActorRef<OrderActor.Message> order, CookedPlacedOrder cookedPlacedOrder){
            this.cookedPlacedOrder = cookedPlacedOrder;
            this.order = order;
        }
    }

    public static class RemoveOrderFromShelf implements Command {
        public final ShelfPlacedOrder shelfPlacedOrder;

        public RemoveOrderFromShelf(ShelfPlacedOrder shelfPlacedOrder){
            this.shelfPlacedOrder = shelfPlacedOrder;
        }
    }

    private static class ExpirePlacedOrder implements Command {
        public final ShelfPlacedOrder shelfPlacedOrder;
        public final ActorRef<OrderActor.Message> order;

        public ExpirePlacedOrder(ShelfPlacedOrder shelfPlacedOrder, ActorRef<OrderActor.Message> order){
            this.shelfPlacedOrder = shelfPlacedOrder;
            this.order = order;
        }
    }

    private static class OrderLocation{
        public final ActorRef<OrderActor.Message> orderActor;
        public final Shelf shelf;

        public OrderLocation(ActorRef<OrderActor.Message> orderActor, Shelf shelf){
            this.orderActor = orderActor;
            this.shelf = shelf;
        }
    }


    public static Behavior<Message> create(ShelvesProperties shelvesProperties){
        return Behaviors.withTimers(timer -> new ShelveManagerActor(timer, shelvesProperties).receive());
    }

    private ShelveManagerActor(TimerScheduler<Message> timer, ShelvesProperties shelvesProperties){
        this.timer = timer;
        this.setupShelves(shelvesProperties);
    }


    private final TimerScheduler<Message> timer;
    private HashMap<Shelf.ShelfType, Shelf> shelves = new HashMap<>(4);

    // Need to keep a reference of the actor around in case this order is chosen to be dropped
    // to make room for a new order (it sucks but it can happen if shelves are flooded with requests.
    // This also servers another purpose, which is to keep track of which shelf each order is located.
    // We cannot 100% trust in the ShelfPlacedOrder object that we hand over to the order. Although
    // it is an immutable object it could happen that the shelve manager moves order to different shelves
    // and in that case the order will be temporarily holding an old version of the ShelfPlacedOrder object.
    // Such object will be eventually replaced since we emmit an event, but it might be to late and the
    // courier might be already arrived and the order has issue a command to remove its order from the order shelf
    // before we get the change to propagate the change.
    // Therefore this actor upon receiving a removal request form the order should only rely on its vision
    // of where the order is located.
    private HashMap<String, OrderLocation> orderLocator = new HashMap<>();

    private void setupShelves(ShelvesProperties shelvesProperties){

        Arrays.stream(Shelf.ShelfType.values())
                .forEach(stype ->
                        shelves.put(stype, new Shelf(stype, shelvesProperties.getMaxCapacityByType(stype))));

    }

    public Behavior<Message> receive(){
        return Behaviors.receive(Message.class)
                .onMessage(PlaceCookedOrder.class, this::onPlaceCookedOrder)
                .onMessage(ExpirePlacedOrder.class, this::onShelfPlacedOrderExpired)
                .onMessage(RemoveOrderFromShelf.class, this::onRemoveOrderFromShelf)
                .build();
    }


    private Behavior<Message> onPlaceCookedOrder(PlaceCookedOrder placeCookedOrder){
        log.info("[ORDERID: {}] - Receiving placement of cooked order," +
                " with preferable {} shelf ", placeCookedOrder.cookedPlacedOrder.id, placeCookedOrder.cookedPlacedOrder.preferredShelfType());
        placeOnShelf(placeCookedOrder);

        logCurrentShelveInventory();

        return Behaviors.same();
    }

    private Behavior<Message> onShelfPlacedOrderExpired(ExpirePlacedOrder shelfPlacedOrderExpired){

        ShelfPlacedOrder spo = shelfPlacedOrderExpired.shelfPlacedOrder;
        String orderId = spo.id;
        OrderLocation orderLocation = orderLocator.get(orderId);

        log.info("[ORDERID: {}, Value: {}] - Shelf Placed Order has Expired and is going to be wasted from the {} shelf,",
                spo.id, spo.orderValue(), orderLocation.shelf.type);

        //Remove expired (wasted) order from its shelf.
        orderLocation.shelf.takeOrder(orderId);
        orderLocator.remove(orderId);

        shelfPlacedOrderExpired.order.tell(new OrderActor.OrderWastedEvent(spo));

        logCurrentShelveInventory();

        return Behaviors.same();
    }

    private Behavior<Message> onRemoveOrderFromShelf(RemoveOrderFromShelf removeOrderFromShelf){

        ShelfPlacedOrder spo = removeOrderFromShelf.shelfPlacedOrder;

        log.info("[ORDERID: {}, Value: {}] - Receiving request to remove order from shelf", spo.id, spo.orderValue());

        timer.cancel(spo.id);

        orderLocator.get(spo.id).shelf.takeOrder(spo);
        orderLocator.remove(spo.id);

        logCurrentShelveInventory();

        return Behaviors.same();
    }

    private void placeOnShelf(PlaceCookedOrder placeCookedOrder){
        Shelf shelf = shelves.get(placeCookedOrder.cookedPlacedOrder.preferredShelfType());
        if(shelf.isFull()){
            log.info("[ORDERID: {}] - {} Shelf is full moving this order to the overflow shelf", placeCookedOrder.cookedPlacedOrder.id, shelf.type);
            placeOnOverFlowShelf(placeCookedOrder);
        }
        else{
            placeOnShelf(shelf, placeCookedOrder);
        }
    }

    private void placeOnOverFlowShelf(PlaceCookedOrder placeCookedOrder){
        Shelf shelf = shelves.get(Shelf.ShelfType.OVERFLOW);
        if(shelf.isFull()){
            log.info("[ORDERID: {}] - {} Shelf is full, going to try find an allowable room for other orders", placeCookedOrder.cookedPlacedOrder.id, shelf.type);
            placeOnAllowableShelf(placeCookedOrder);
        }else {
            placeOnShelf(shelf, placeCookedOrder);
        }
    }

    private void placeOnAllowableShelf(PlaceCookedOrder placeCookedOrder){

        ShelfPlacedOrder candidateToMove = getOverflowCandidateToMove();
        Shelf overflowShelf = shelves.get(Shelf.ShelfType.OVERFLOW);

        if (candidateToMove == null) {

            log.info("[ORDERID: {}] - Not able to find an allowable room for any overflow order, going to pick a random order to drop",
                    placeCookedOrder.cookedPlacedOrder.id);

            // There is no order we can accommodate in other shelves.
            // Therefore we need to make room in the overflow shelf.
            ShelfPlacedOrder toBeDropped = overflowShelf.removeRandom();

            log.info("[ORDERID: {}] - Random order {} picked to be wasted and will not be available for pick up",
                    placeCookedOrder.cookedPlacedOrder.id, toBeDropped.id);

            // Cancel time of wasted order
            timer.cancel(toBeDropped.id);

            //Propagate waste event to order management
            orderLocator.get(toBeDropped.id).orderActor.tell(new OrderActor.OrderWastedEvent(toBeDropped));
            orderLocator.remove(toBeDropped.id);

        } else {

            //Remove from the overflow shelf
            overflowShelf.takeOrder(candidateToMove);

            // Move candidate from the overflow to the suitable available shelf
            placeOnShelf(shelves.get(candidateToMove.cookedPlacedOrder.preferredShelfType()),
                    new PlaceCookedOrder(
                            orderLocator.get(candidateToMove.id).orderActor,
                            candidateToMove.cookedPlacedOrder));

            log.info("[ORDERID: {}] - Order {} is going to be moved from the overflow to the {} shelf to make room for the current order",
                    placeCookedOrder.cookedPlacedOrder.id, candidateToMove.id, candidateToMove.shelfType);
        }

        //Place current order on the overflow shelf
        placeOnShelf(overflowShelf, placeCookedOrder);

    }

    private ShelfPlacedOrder getOverflowCandidateToMove(){

        List<Shelf> shelfList = getAvailableShelfOrderedByMostCapacity();
        List<ShelfPlacedOrder> shelfPlacedOrders =
                shelves.get(Shelf.ShelfType.OVERFLOW).getShelfPlacedOrdersOrderedByOrderValue();

        if(shelfList.size() > 0){
            for(Shelf s: shelfList){
                for(ShelfPlacedOrder spo: shelfPlacedOrders){
                    if (s.type.equals(spo.cookedPlacedOrder.preferredShelfType())){
                        return spo;
                    }
                }
            }
        }

        return null;

    }


    private List<Shelf> getAvailableShelfOrderedByMostCapacity() {
        return shelves.values().stream()
                // If we get so far it is kind of obvious that the current shelf this order needs is full
                // as well as the overflow shelf, but too keep clarity I'd prefer make this very explicity in the
                // below filter
                .filter(e -> e.type != Shelf.ShelfType.OVERFLOW & e.availableCapacity() > 0)
                .sorted(Collections.reverseOrder(Comparator.comparing(e -> e.availableCapacity())))
                .collect(Collectors.toList());
    }

    private void placeOnShelf(Shelf shelf, PlaceCookedOrder placeCookedOrder){
        ShelfPlacedOrder spo = shelf.placeOrder(placeCookedOrder.cookedPlacedOrder);
        log.info("[ORDERID: {}, Value: {}] - Order put on the {} Shelf", spo.id, spo.orderValue(), shelf.type);
        ActorRef<OrderActor.Message> order = placeCookedOrder.order;
        if (spo.wasted()){
            // This is just a defensive piece of code just in case an already expired order tries to
            // make its way to a shelf. This could only happen if the mailbox of this actor
            // is extremelly backlogged.
            shelf.takeOrder(spo);
            order.tell(new OrderActor.OrderWastedEvent(spo));
        }else{
            // Send a timer message to itself when this order expires.
            timer.startSingleTimer(spo.id, new ExpirePlacedOrder(spo,order), Duration.ofMillis(spo.remainingForWasteMillis()));
            // Need to keep a reference of the actor around in case this order is chosen to be dropped
            // to make room for a new order (it sucks but it can happen if shelves are flooded with requests
            orderLocator.put(spo.id, new OrderLocation(order,shelf));

            // Send an event to the order actor to advice its package was placed in a shelf
            order.tell(new OrderActor.OrderPlacedOnShelfEvent(spo));
        }
    }


    private void logCurrentShelveInventory(){

        log.info("*** Current Shelves Inventory: HOT[{}], COLD[{}], FROZEN[{}], OVERFLOW[{}]",
                shelves.get(Shelf.ShelfType.HOT).size(),
                shelves.get(Shelf.ShelfType.COLD).size(),
                shelves.get(Shelf.ShelfType.FROZEN).size(),
                shelves.get(Shelf.ShelfType.OVERFLOW).size());

    }

}
