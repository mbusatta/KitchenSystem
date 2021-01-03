import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.cloudkitchens.codechallenge.courier.CourierDispatcherActor;
import com.cloudkitchens.codechallenge.kitchen.KitchenUnitActor;
import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import com.cloudkitchens.codechallenge.order.ShelfPlacedOrder;
import com.cloudkitchens.codechallenge.shelf.Shelf;
import com.cloudkitchens.codechallenge.shelf.ShelveManagerActor;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrderTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();


    @Test
    public void testOrderWasted(){

        TestProbe<KitchenUnitActor.Message> kitchenUnit =
                testKit.createTestProbe(KitchenUnitActor.Message.class);

        TestProbe<ShelveManagerActor.Message> shelves =
                testKit.createTestProbe(ShelveManagerActor.Message.class);

        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);

        ActorRef<OrderActor.Message> orderActor = testKit.spawn(OrderActor.create(kitchenUnit.getRef(),shelves.getRef(), placedOrder));

        orderActor.tell(new OrderActor.OrderCookedEvent(new CookedPlacedOrder(placedOrder)));
        ShelveManagerActor.PlaceCookedOrder placeCookedOrder = (ShelveManagerActor.PlaceCookedOrder)shelves.receiveMessage();
        assertEquals(placeCookedOrder.cookedPlacedOrder.id, placedOrder.id);

        orderActor.tell(new OrderActor.OrderWastedEvent(
                new ShelfPlacedOrder(new Shelf(Shelf.ShelfType.HOT, 1),new CookedPlacedOrder(placedOrder))));
        KitchenUnitActor.OrderDropped orderDropped =  (KitchenUnitActor.OrderDropped )kitchenUnit.receiveMessage();

        assertEquals(orderDropped.placedOrder.id, placedOrder.id);
    }

    @Test
    public void testOrderManagerFullLifecycle(){

        TestProbe<KitchenUnitActor.Message> kitchenUnit =
                testKit.createTestProbe(KitchenUnitActor.Message.class);

        TestProbe<ShelveManagerActor.Message> shelves =
                testKit.createTestProbe(ShelveManagerActor.Message.class);

        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);

        ActorRef<OrderActor.Message> orderActor = testKit.spawn(OrderActor.create(kitchenUnit.getRef(),shelves.getRef(), placedOrder));
        ActorRef<CourierDispatcherActor.Message> courierActor = testKit.spawn(CourierDispatcherActor.create(orderActor,placedOrder));

        orderActor.tell(new OrderActor.CourierDispatcherAssigned(courierActor));

        orderActor.tell(new OrderActor.OrderCookedEvent(new CookedPlacedOrder(placedOrder)));
        ShelveManagerActor.PlaceCookedOrder placeCookedOrder = (ShelveManagerActor.PlaceCookedOrder)shelves.receiveMessage();
        assertEquals(placeCookedOrder.cookedPlacedOrder.id, placedOrder.id);

        orderActor.tell(new OrderActor.OrderPlacedOnShelfEvent(new ShelfPlacedOrder(new Shelf(Shelf.ShelfType.HOT, 1),new CookedPlacedOrder(placedOrder))));

        // If courier is properly dispatched we will be able to intercept an event of an item being removed from the shelf
        // which only happens when a courier pick ups the order
        ShelveManagerActor.RemoveOrderFromShelf removeOrderFromShelf = (ShelveManagerActor.RemoveOrderFromShelf)shelves.receiveMessage(Duration.ofSeconds(7));

        //After the delivery is confirm we should receive a order closed event
        KitchenUnitActor.OrderDelivered orderClosed =  (KitchenUnitActor.OrderDelivered )kitchenUnit.receiveMessage();
        assertEquals(orderClosed.placedOrder.id, placedOrder.id);

    }

}
