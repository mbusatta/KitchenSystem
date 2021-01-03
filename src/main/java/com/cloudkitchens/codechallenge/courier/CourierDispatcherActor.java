package com.cloudkitchens.codechallenge.courier;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.cloudkitchens.codechallenge.kitchen.KitchenUnitConfig;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Random;

public class CourierDispatcherActor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public interface Message{}
    public interface Event extends Message{}

    public static enum OrderCancelled implements Event {INSTANCE}
    public static enum ConfirmOrderAvailable implements Event {INSTANCE}
    private static enum ArrivePickupOrder implements Event {INSTANCE}


    public static Behavior<Message> create(ActorRef<OrderActor.Message> orderActor, PlacedOrder placedOrder){
        return Behaviors.withTimers(timer -> new CourierDispatcherActor(timer, orderActor, placedOrder).receive());
    }

    public CourierDispatcherActor(TimerScheduler<Message> timer, ActorRef<OrderActor.Message> orderActor, PlacedOrder placedOrder){
        this.timer = timer;
        this.orderActor = orderActor;
        this.placedOrder = placedOrder;

        Config config = KitchenUnitConfig.config.getConfig("courier-arrival-range-second");
        minArrivalSecs = config.getInt("min");
        maxArrivalSecs = config.getInt("max");

        dispatch();
    }

    private final TimerScheduler<Message> timer;
    private final int minArrivalSecs;
    private final int maxArrivalSecs;
    private final ActorRef<OrderActor.Message> orderActor;
    private final PlacedOrder placedOrder;


    public Behavior<Message> receive() {
        return Behaviors.receive(Message.class)
                .onMessage(ArrivePickupOrder.class, e-> this.onArrivePickupOrder())
                .onMessageEquals(OrderCancelled.INSTANCE, this::onOrderCancelledEvent)
                .onMessageEquals(ConfirmOrderAvailable.INSTANCE, this::onConfirmOrderIsAvailableToPickup)
                .build();
    }

    private void dispatch(){
        int arriveIn = arriveIn();
        log.info("[ORDERID: {}] - Dispatching courier for pickup, ETA(seconds): {}", placedOrder.id, arriveIn);

        // The reason why we need to catch an unsupported operation in the timer is to be able to support
        // behavior test of this actor. As of the current writing of this code the behavior test kit is not
        // able to create a scheduler. This is going to be supported in the 2.11 version which is not yet avaialble.
        // More details here: https://github.com/akka/akka/issues/29900
        try {
            timer.startSingleTimer(placedOrder.id, ArrivePickupOrder.INSTANCE, Duration.ofSeconds(arriveIn));
        }catch(UnsupportedOperationException e){
            log.warn("There is no scheduler support in testing mode, so this courier is never going to be dispached. " +
                    "If you are seeing this message outside testing mode there might be an uncovered bug.");
        }

    }

    private Behavior<Message> onOrderCancelledEvent(){
        log.info("[ORDERID: {}] - Courier receive cancel of the order", placedOrder.id);
        timer.cancel(placedOrder.id);
        return Behaviors.stopped();
    }

    private Behavior<Message> onArrivePickupOrder(){
        log.info("[ORDERID: {}] - Arrived to pickup order", placedOrder.id);
        orderActor.tell(OrderActor.PickupOrderCommand.INSTANCE);
        return Behaviors.same();
    }

    private Behavior<Message> onConfirmOrderIsAvailableToPickup(){
        log.info("[ORDERID: {}] - Courier has just delivered the order (enjoy your meal!)", placedOrder.id);
        orderActor.tell(OrderActor.OrderDeliveredEvent.INSTANCE);
        return Behaviors.stopped();
    }

    private int arriveIn(){
        return (minArrivalSecs + new Random().nextInt((maxArrivalSecs - minArrivalSecs) + 1));
    }
}
