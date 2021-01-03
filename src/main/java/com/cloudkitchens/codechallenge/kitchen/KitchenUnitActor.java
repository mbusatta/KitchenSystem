package com.cloudkitchens.codechallenge.kitchen;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.cloudkitchens.codechallenge.courier.CourierDispatcherActor;
import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.OrderVo;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import com.cloudkitchens.codechallenge.shelf.ShelveManagerActor;
import com.cloudkitchens.codechallenge.shelf.ShelvesProperties;
import com.typesafe.config.Config;

import java.util.HashMap;
import java.util.Map;

public class KitchenUnitActor extends AbstractBehavior<KitchenUnitActor.Message> {

    public interface Message {}

    public interface Command extends Message {}

    public static class OrderRequest implements Command{
        public final OrderVo orderVo;

        public OrderRequest(OrderVo orderVo){
            this.orderVo = orderVo;
        }
    }

    public static enum UpstreamCompleted implements Command {
        INSTANCE
    }
    public static enum UpstreamFailed implements Command {
        INSTANCE
    }

    public interface Event extends Message {}

    public static class OrderDelivered implements Event {
        public final PlacedOrder placedOrder;

        public OrderDelivered(PlacedOrder placedOrder){
            this.placedOrder = placedOrder;
        }
    }

    public static class OrderDropped implements Event {
        public final PlacedOrder placedOrder;

        public OrderDropped(PlacedOrder placedOrder){
            this.placedOrder = placedOrder;
        }
    }

    public static Behavior<Message> create(){
        return Behaviors.setup(KitchenUnitActor::new);
    }

    public KitchenUnitActor(ActorContext<Message> ctx){
        super(ctx);
        setup();
    }

    private Config conf = KitchenUnitConfig.config;
    private ActorRef<ShelveManagerActor.Message> shelveManager;
    private final Map<String, ActorRef<OrderActor.Message>> managedOrders = new HashMap<>();
    private boolean closedUpstream = false;
    private int requestCount;
    private int requestDeliveryCount;
    private int requestDroppedCount;

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(OrderRequest.class, this::onOrderRequest)
                .onMessage(OrderDelivered.class, this::onOrderDelivered)
                .onMessage(OrderDropped.class, this::onOrderDropped)
                .onMessage(UpstreamCompleted.class, this::onUpstreamCompleted)
                .onMessage(UpstreamFailed.class, this::onUpstreamFailed)
                .build();
    }


    private Behavior<Message> onOrderRequest(OrderRequest orderRequest){

        PlacedOrder placedOrder = fromOrderVo(orderRequest.orderVo);

        getContext().getLog().info("[ORDERID: {}] - Order has been placed", placedOrder.id);

        // Create the order actor that will handle with this request
        ActorRef<OrderActor.Message> orderActor =
                getContext().spawn(OrderActor.create(getContext().getSelf(),shelveManager, fromOrderVo(orderRequest.orderVo)),
                        String.format("Order:%s", placedOrder.id));

        // Create the courier dispatcher actor that will handle the picked and delivery of this order
        // and assign it to the orderActor
        ActorRef<CourierDispatcherActor.Message> courierActor =
                getContext().spawn(CourierDispatcherActor.create(orderActor,placedOrder),
                        String.format("CourierDispacher:%s",placedOrder.id));
        orderActor.tell(new OrderActor.CourierDispatcherAssigned(courierActor));

        // Add the order actor on the list of orders that is being managed by this kitchen unit
        managedOrders.put(placedOrder.id, orderActor);

        // Cook the order :)
        CookedPlacedOrder cookedPlacedOrder = cookOrder(placedOrder);
        orderActor.tell(new OrderActor.OrderCookedEvent(cookedPlacedOrder));

        requestCount++;

        return this;
    }

    private Behavior<Message> onOrderDelivered(OrderDelivered orderDelivered){
        getContext().getLog().info("[ORDERID: {}] - Order has been properly delivered", orderDelivered.placedOrder.id);
        managedOrders.remove(orderDelivered.placedOrder.id);
        requestDeliveryCount++;
        return potentialShutdownBehavior();
    }

    private Behavior<Message> onOrderDropped(OrderDropped orderDropped){
        getContext().getLog().info("[ORDERID: {}] - Order has been wasted", orderDropped.placedOrder.id);
        managedOrders.remove(orderDropped.placedOrder.id);
        requestDroppedCount++;
        return potentialShutdownBehavior();
    }

    private Behavior<Message> onUpstreamCompleted(UpstreamCompleted upstreamCompleted){
        getContext().getLog().info("No more orders from upstream. Waiting in progress orders to complete.");
        closedUpstream = true;
        return potentialShutdownBehavior();
    }

    private Behavior<Message> onUpstreamFailed(UpstreamFailed upstreamFailed){
        getContext().getLog().info("Error to get orders from upstream. Waiting in progress orders to complete.");
        closedUpstream = true;
        return potentialShutdownBehavior();
    }

    private PlacedOrder fromOrderVo(OrderVo vo){
        return new PlacedOrder(
                vo.getId(),
                vo.getName(),
                vo.getTemp(),
                vo.getShelfLife(),
                vo.getDecayRate());
    }

    private CookedPlacedOrder cookOrder(PlacedOrder placedOrder){
        return new CookedPlacedOrder(placedOrder);
    }


    private void setup(){
        setupShelvesManager();
    }

    private void setupShelvesManager(){
        Config shelvesConfig = conf.getConfig("shelf-max-capacity");
        shelveManager = getContext().spawn(ShelveManagerActor.create(new ShelvesProperties(
                shelvesConfig.getInt("hot"),
                shelvesConfig.getInt("cold"),
                shelvesConfig.getInt("frozen"),
                shelvesConfig.getInt("overflow")
        )), "shelvesManager");
    }

    private Behavior<Message> potentialShutdownBehavior(){
        if (managedOrders.isEmpty() && closedUpstream){
            printFinalReport();
            return Behaviors.stopped();
        }
        else
            return this;
    }

    private void printFinalReport(){
        getContext().getLog().info("====== Kitchen Unit Report ======");
        getContext().getLog().info("== Received Orders: {}", requestCount);
        getContext().getLog().info("== Delivered Orders: {}", requestDeliveryCount);
        getContext().getLog().info("== Dropped Orders: {}", requestDroppedCount);
        getContext().getLog().info("=================================");
    }

}
