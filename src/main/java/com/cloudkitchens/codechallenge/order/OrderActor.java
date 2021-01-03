package com.cloudkitchens.codechallenge.order;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.cloudkitchens.codechallenge.kitchen.KitchenUnitActor;
import com.cloudkitchens.codechallenge.shelf.ShelveManagerActor;
import com.cloudkitchens.codechallenge.courier.CourierDispatcherActor;

public class OrderActor extends AbstractBehavior<OrderActor.Message> {

    public interface Message {}

    public interface Command extends Message {}

    public static enum PickupOrderCommand implements Command { INSTANCE }

    public interface Event extends Message{}

    public static class OrderCookedEvent implements Event {
        public final CookedPlacedOrder cookedPlacedOrder;

        public OrderCookedEvent(CookedPlacedOrder cookedPlacedOrder){
            this.cookedPlacedOrder = cookedPlacedOrder;
        }
    }

    public static class CourierDispatcherAssigned implements Event {
        public final ActorRef<CourierDispatcherActor.Message> courierDispatcher;

        public CourierDispatcherAssigned(ActorRef<CourierDispatcherActor.Message> courierDispatcher){
            this.courierDispatcher = courierDispatcher;
        }
    }

    public static class OrderPlacedOnShelfEvent implements Event {
        public final ShelfPlacedOrder shelfPlacedOrder;

        public OrderPlacedOnShelfEvent(ShelfPlacedOrder shelfPlacedOrder){
            this.shelfPlacedOrder = shelfPlacedOrder;
        }
    }

    public static class OrderWastedEvent implements Event {
        public final ShelfPlacedOrder shelfPlacedOrder;

        public OrderWastedEvent(ShelfPlacedOrder shelfPlacedOrder){
            this.shelfPlacedOrder = shelfPlacedOrder;
        }
    }

    public static enum OrderDeliveredEvent implements Command { INSTANCE }

    public static Behavior<Message> create(ActorRef<KitchenUnitActor.Message> kitchenUnit,
                                           ActorRef<ShelveManagerActor.Message> shelvesManager,
                                           PlacedOrder placedOrder){
        return Behaviors.setup(ctx -> new OrderActor(ctx, kitchenUnit, shelvesManager, placedOrder));
    }


    private OrderActor(ActorContext<Message> ctx,
                       ActorRef<KitchenUnitActor.Message> kitchenUnit,
                       ActorRef<ShelveManagerActor.Message> shelvesManager,
                       PlacedOrder placedOrder){

        super(ctx);
        this.kitchenUnit = kitchenUnit;
        this.shelvesManager = shelvesManager;
        this.placedOrder = placedOrder;

        getContext().getLog().info("[ORDERID: {}] - Order actor has been created to manage the lifecycle of this specific order", placedOrder.id);
    }

    private final PlacedOrder placedOrder;
    private final ActorRef<ShelveManagerActor.Message> shelvesManager;
    private final ActorRef<KitchenUnitActor.Message> kitchenUnit;
    private ActorRef<CourierDispatcherActor.Message> courierDispatcher;
    private ShelfPlacedOrder shelfPlacedOrder;


    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(OrderCookedEvent.class, this::onOrderCookedEvent)
                .onMessage(CourierDispatcherAssigned.class, this::onCourierDispatcherAssigned)
                .onMessage(OrderPlacedOnShelfEvent.class, this::onOrderPlacedOnShelfEvent)
                .onMessage(OrderWastedEvent.class, this::onOrderWastedEvent)
                .onMessage(PickupOrderCommand.class, this::onPickupOrderCommand)
                .onMessage(OrderDeliveredEvent.class, this::onOrderDeliveredEvent)
                .build();
    }

    private Behavior<Message> onOrderCookedEvent(OrderCookedEvent orderCookedEvent){
        getContext().getLog().info("[ORDERID: {}] - Order has been cooked. Handing the order to the shelve manager", placedOrder.id);
        shelvesManager.tell(new ShelveManagerActor.PlaceCookedOrder(getContext().getSelf(), orderCookedEvent.cookedPlacedOrder));
        return this;
    }

    private Behavior<Message> onCourierDispatcherAssigned(CourierDispatcherAssigned courierDispatcherAssigned){
        getContext().getLog().info("[ORDERID: {}] - Order has been assigned to a courier.");
        this.courierDispatcher = courierDispatcherAssigned.courierDispatcher;
        return this;
    }

    private Behavior<Message> onOrderPlacedOnShelfEvent(OrderPlacedOnShelfEvent orderPlacedOnShelfEvent){
        this.shelfPlacedOrder = orderPlacedOnShelfEvent.shelfPlacedOrder;
        getContext().getLog().info("[ORDERID: {}, Value: {}] - Order Placed in shelf",
                placedOrder.id, shelfPlacedOrder.orderValue());
        return this;
    }

    private Behavior<Message> onOrderWastedEvent(OrderWastedEvent orderWastedEvent){
        //Since the item is considered wasted at this point we will advice the courier and
        //the kitchen unit actors and shutdown this actor

        getContext().getLog().info("[ORDERID: {}, Value: {}] - This order is being dropped. Order actor is shutting itself down",
                placedOrder.id, orderWastedEvent.shelfPlacedOrder.orderValue());

        if(courierDispatcher != null)
            courierDispatcher.tell(CourierDispatcherActor.OrderCancelled.INSTANCE);
        else
            getContext().getLog().warn("[ORDERID: {}, Value: {}] - No assigned dispatcher.");

        kitchenUnit.tell(new KitchenUnitActor.OrderDropped(placedOrder));

        return Behaviors.stopped();
    }


    private Behavior<Message> onPickupOrderCommand(PickupOrderCommand pickupOrderCommand){

        getContext().getLog().info("[ORDERID: {}, Value: {}] - Handing over order package to the courier", placedOrder.id, shelfPlacedOrder.orderValue());

        shelvesManager.tell(new ShelveManagerActor.RemoveOrderFromShelf(shelfPlacedOrder));

        // Ideally we won't hand the order package to the courier until we don't receive confirmation the item was
        // properly removed from the shelf. It could happen that in the interim this package expired
        // or it was selected to be wasted to make room to a new order.
        courierDispatcher.tell(CourierDispatcherActor.ConfirmOrderAvailable.INSTANCE);

        return this;
    }

    private Behavior<Message> onOrderDeliveredEvent(OrderDeliveredEvent orderDeliveredEvent){
        //Since the item is considered dispatched at this point we will advice the Orders management
        //and shutdown this actor
        kitchenUnit.tell(new KitchenUnitActor.OrderDelivered(placedOrder));
        getContext().getLog().info("[ORDERID: {}, Value:{}] - Order has been delivered. Order actor is shutting itself down",
                placedOrder.id, shelfPlacedOrder.orderValue());
        return Behaviors.stopped();
    }

}
