import akka.actor.testkit.typed.javadsl.*;
import akka.actor.typed.ActorRef;
import com.cloudkitchens.codechallenge.order.CookedPlacedOrder;
import com.cloudkitchens.codechallenge.order.OrderActor;
import com.cloudkitchens.codechallenge.order.PlacedOrder;
import com.cloudkitchens.codechallenge.shelf.Shelf;
import com.cloudkitchens.codechallenge.shelf.ShelveManagerActor;
import com.cloudkitchens.codechallenge.shelf.ShelvesProperties;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;

import java.time.Duration;
import java.util.UUID;

public class ShelveManagerTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testPlaceOnShelfAndExpired(){

        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 1, 0.9F);

        TestProbe<OrderActor.Message> probe =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(2,2,2,2)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder)));

        OrderActor.Message event = probe.receiveMessage(Duration.ofSeconds(1));
        assertTrue(event instanceof OrderActor.OrderPlacedOnShelfEvent);
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent = (OrderActor.OrderPlacedOnShelfEvent)event;
        assertEquals(orderPlacedOnShelfEvent.shelfPlacedOrder.id, placedOrder.id);
        //Make sure it went to the right type of Shelf
        assertEquals(orderPlacedOnShelfEvent.shelfPlacedOrder.shelfType, Shelf.ShelfType.HOT);
        event = probe.receiveMessage(Duration.ofSeconds(2));
        assertTrue(event instanceof OrderActor.OrderWastedEvent);
        OrderActor.OrderWastedEvent orderWastedEvent = (OrderActor.OrderWastedEvent)event;
        assertEquals(orderWastedEvent.shelfPlacedOrder.id, placedOrder.id);

    }

    @Test
    public void testPlaceOnOverflowShelfAndExpired(){
        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder2 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder3 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder4 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder5 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);

        TestProbe<OrderActor.Message> probe =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(2,2,2,2)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder2)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder3)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder4)));


        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent2 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent3 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent4 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();

        assertEquals(orderPlacedOnShelfEvent.shelfPlacedOrder.shelfType, Shelf.ShelfType.HOT);
        assertEquals(orderPlacedOnShelfEvent2.shelfPlacedOrder.shelfType, Shelf.ShelfType.HOT);
        assertEquals(orderPlacedOnShelfEvent3.shelfPlacedOrder.shelfType, Shelf.ShelfType.OVERFLOW);
        assertEquals(orderPlacedOnShelfEvent4.shelfPlacedOrder.shelfType, Shelf.ShelfType.OVERFLOW);

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder5)));
        OrderActor.OrderWastedEvent wastedOrder = (OrderActor.OrderWastedEvent)probe.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent5 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();

        assertNotEquals(placedOrder5.id, wastedOrder.shelfPlacedOrder.id);
        assertEquals(Shelf.ShelfType.OVERFLOW, wastedOrder.shelfPlacedOrder.shelfType);
        assertEquals(placedOrder5.id, orderPlacedOnShelfEvent5.shelfPlacedOrder.id);

    }

    @Test
    public void testPlaceOnOverflowShelfMoves() throws InterruptedException {
        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "cold", 1, 0.7F);
        PlacedOrder placedOrder2 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "cold", 5, 0.7F);
        PlacedOrder placedOrder3 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "cold", 5, 0.7F);


        TestProbe<OrderActor.Message> probe =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(1,1,1,2)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder2)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder3)));

        probe.receiveSeveralMessages(3);

        Thread.sleep(2000);

        OrderActor.OrderWastedEvent wastedOrder = (OrderActor.OrderWastedEvent)probe.receiveMessage();
        assertEquals(wastedOrder.shelfPlacedOrder.id, placedOrder.id);
        assertEquals(Shelf.ShelfType.COLD, wastedOrder.shelfPlacedOrder.shelfType);

    }

    @Test
    public void testRemoveOrderFromShelf() throws InterruptedException {
        PlacedOrder placedOrder = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "frozen", 1, 0.7F);

        TestProbe<OrderActor.Message> probe =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(1,1,1,2)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder)));
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        shelvesActor.tell(new ShelveManagerActor.RemoveOrderFromShelf(orderPlacedOnShelfEvent.shelfPlacedOrder));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder)));
        orderPlacedOnShelfEvent = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        assertEquals(Shelf.ShelfType.FROZEN, orderPlacedOnShelfEvent.shelfPlacedOrder.shelfType);

    }

    @Test
    public void testPlaceOnOverflowMoveAllowable() throws InterruptedException {
        PlacedOrder placedOrder0 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder1 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "cold", 5, 0.7F);
        PlacedOrder placedOrder2 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "cold", 5, 0.7F);
        PlacedOrder placedOrder3 = new PlacedOrder(UUID.randomUUID().toString(),
                "this is a test", "hot", 5, 0.7F);


        TestProbe<OrderActor.Message> orderActor0 =
                testKit.createTestProbe(OrderActor.Message.class);
        TestProbe<OrderActor.Message> orderActor1 =
                testKit.createTestProbe(OrderActor.Message.class);
        TestProbe<OrderActor.Message> orderActor2 =
                testKit.createTestProbe(OrderActor.Message.class);
        TestProbe<OrderActor.Message> orderActor3 =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(1,1,1,1)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(orderActor0.getRef(), new CookedPlacedOrder(placedOrder0)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(orderActor1.getRef(), new CookedPlacedOrder(placedOrder1)));
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(orderActor2.getRef(), new CookedPlacedOrder(placedOrder2)));
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent0 = (OrderActor.OrderPlacedOnShelfEvent)orderActor0.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent1 = (OrderActor.OrderPlacedOnShelfEvent)orderActor1.receiveMessage();
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent2 = (OrderActor.OrderPlacedOnShelfEvent)orderActor2.receiveMessage();
        assertEquals(Shelf.ShelfType.HOT, orderPlacedOnShelfEvent0.shelfPlacedOrder.shelfType);
        assertEquals(Shelf.ShelfType.COLD, orderPlacedOnShelfEvent1.shelfPlacedOrder.shelfType);
        assertEquals(Shelf.ShelfType.OVERFLOW, orderPlacedOnShelfEvent2.shelfPlacedOrder.shelfType);

        // Remove one of the orders from the COLD shelf. this way we have the COLD shelf empty and when a hot request
        // comes in it will try to make room in the overflow shelf (since the hot shelf is already full at this point)
        // what we expect here is that after we send a hot order the cold one will move from the overflow to the cold shelf
        // and the new order will have space in the overflow shelf to sit
        shelvesActor.tell(new ShelveManagerActor.RemoveOrderFromShelf(orderPlacedOnShelfEvent1.shelfPlacedOrder));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(orderActor3.getRef(), new CookedPlacedOrder(placedOrder3)));
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent3 = (OrderActor.OrderPlacedOnShelfEvent)orderActor3.receiveMessage();
        assertEquals(Shelf.ShelfType.OVERFLOW, orderPlacedOnShelfEvent3.shelfPlacedOrder.shelfType);
        assertEquals(orderPlacedOnShelfEvent3.shelfPlacedOrder.id, placedOrder3.id);

        orderPlacedOnShelfEvent2 = (OrderActor.OrderPlacedOnShelfEvent)orderActor2.receiveMessage();
        assertEquals(Shelf.ShelfType.COLD, orderPlacedOnShelfEvent2.shelfPlacedOrder.shelfType);
        assertEquals(orderPlacedOnShelfEvent2.shelfPlacedOrder.id, placedOrder2.id);

    }

    @Test
    public void testProperDroppedOrder() throws InterruptedException {
        PlacedOrder placedOrder0 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder1 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);
        PlacedOrder placedOrder2 = new PlacedOrder(UUID.randomUUID().toString(),
                "some order", "hot", 5, 0.7F);

        TestProbe<OrderActor.Message> probe =
                testKit.createTestProbe(OrderActor.Message.class);

        ActorRef<ShelveManagerActor.Message> shelvesActor =
                testKit.spawn(ShelveManagerActor.create(new ShelvesProperties(1,1,1,1)));

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder0)));
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent0 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        assertEquals(orderPlacedOnShelfEvent0.shelfPlacedOrder.id, placedOrder0.id);

        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder1)));
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent1 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        assertEquals(orderPlacedOnShelfEvent1.shelfPlacedOrder.id, placedOrder1.id);
        assertEquals(orderPlacedOnShelfEvent1.shelfPlacedOrder.shelfType, Shelf.ShelfType.OVERFLOW);

        //there won't be any room for this order and the only order in the overflow shelf is the one above (since the size of the shelf was set to 1
        //so the one above is expected to be dropped to make room for the new order
        //what we are making sure in this test is that the system will drop the right order and make proper room for the new one
        shelvesActor.tell(new ShelveManagerActor.PlaceCookedOrder(probe.getRef(), new CookedPlacedOrder(placedOrder2)));
        OrderActor.OrderWastedEvent wastedEventForOrder1 = (OrderActor.OrderWastedEvent)probe.receiveMessage();
        assertEquals(wastedEventForOrder1.shelfPlacedOrder.id, placedOrder1.id);
        OrderActor.OrderPlacedOnShelfEvent orderPlacedOnShelfEvent2 = (OrderActor.OrderPlacedOnShelfEvent)probe.receiveMessage();
        assertEquals(orderPlacedOnShelfEvent2.shelfPlacedOrder.id, placedOrder2.id);
        assertEquals(orderPlacedOnShelfEvent2.shelfPlacedOrder.shelfType, Shelf.ShelfType.OVERFLOW);

    }

}
